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
import java.time.LocalTime

/**
 * 每天在設定的時間(預設 21:00)提醒:有「待補金額」就通知一次,沒有就不打擾。
 * 只有「自動偵測訊息入帳」開著才會排程;每次執行完會排好隔天的。
 */
object PendingReminder {
    internal const val CHANNEL_ID = "pending_reminder"
    private const val TAG = "pending-reminder"

    /** 開 App / 開機後呼叫:功能與提醒都開著才排程。 */
    fun ensureScheduled(context: Context) {
        if (!AppSettings.autoDetectEnabled(context) || !AppSettings.pendingRemindEnabled(context)) return
        schedule(context, LocalDateTime.now())
    }

    /** 改時間或開關之後呼叫:先取消舊的,再依新設定排一次(只排下一次,沒有週期性工作)。 */
    fun reschedule(context: Context) {
        cancel(context)
        ensureScheduled(context)
    }

    fun remindTime(context: Context): LocalTime {
        val m = AppSettings.pendingRemindMinuteOfDay(context)
        return LocalTime.of(m / 60, m % 60)
    }

    internal fun schedule(context: Context, now: LocalDateTime) {
        val at = PendingSchedule.nextReminder(now, remindTime(context))
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
        if (!AppSettings.autoDetectEnabled(ctx) || !AppSettings.pendingRemindEnabled(ctx)) return Result.success()
        try {
            // 不分日期,所有還沒填金額的都算;0 筆就不打擾
            val count = (ctx as LedgerApp).repository.pendingMissingAmountCount()
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
            NotificationChannel(PendingReminder.CHANNEL_ID, "待補金額提醒", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            ctx,
            REQUEST_CODE,
            Intent(ctx, MainActivity::class.java)
                .putExtra(com.example.ledger.ui.PendingNav.EXTRA_OPEN_MISSING, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(ctx, PendingReminder.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("你有 $count 筆待補金額")
            .setContentText("點開補上金額")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(REQUEST_CODE, notification)
    }

    companion object {
        private const val REQUEST_CODE = 900_002
    }
}
