package com.example.ledger.detect

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.ledger.LedgerApp
import com.example.ledger.MainActivity
import com.example.ledger.settings.AppSettings
import java.time.Duration
import java.time.LocalDateTime

/**
 * 每天晚上 21:00:有「待確認」的刷卡通知就提醒一次,沒有就不打擾。
 * 只有「自動偵測訊息入帳」開著才會排程;每次執行完會排好隔天的。
 */
object PendingReminder {
    internal const val CHANNEL_ID = "pending_reminder"
    private const val TAG = "pending-reminder"

    fun ensureScheduled(context: Context) {
        if (!AppSettings.autoDetectEnabled(context)) return
        schedule(context, LocalDateTime.now())
    }

    internal fun schedule(context: Context, now: LocalDateTime) {
        val at = PendingSchedule.nextReminder(now)
        val request = OneTimeWorkRequestBuilder<PendingWorker>()
            .setInitialDelay(Duration.between(now, at).coerceAtLeast(Duration.ZERO))
            .addTag(TAG)
            .build()
        // 每個日期一個名字,Worker 排隔天時不會被自己擋住
        WorkManager.getInstance(context)
            .enqueueUniqueWork("pending-reminder-${at.toLocalDate()}", ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }
}

class PendingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!AppSettings.autoDetectEnabled(ctx)) return Result.success()
        try {
            val count = (ctx as LedgerApp).repository.pendingCount()
            if (count > 0) notifyPending(ctx, count)
        } finally {
            PendingReminder.schedule(ctx, LocalDateTime.now())
        }
        return Result.success()
    }

    private fun notifyPending(ctx: Context, count: Int) {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(PendingReminder.CHANNEL_ID, "待確認提醒", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            ctx,
            REQUEST_CODE,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(ctx, PendingReminder.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("有 $count 筆刷卡等你確認")
            .setContentText("點開補上金額與分類")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(REQUEST_CODE, notification)
    }

    companion object {
        private const val REQUEST_CODE = 900_002
    }
}
