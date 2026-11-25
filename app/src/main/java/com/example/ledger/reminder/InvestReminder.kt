package com.example.ledger.reminder

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
import androidx.work.workDataOf
import com.example.ledger.LedgerApp
import com.example.ledger.MainActivity
import java.time.Duration
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * 每月 8 號中午檢查:這個月有沒有記過 0050(轉入投資帳戶),沒有就通知提醒。
 * 每一次檢查完會排好下個月的,所以只要 App 啟動過一次就會一直運作。
 */
object InvestReminder {
    internal const val CHANNEL_ID = "invest_reminder"
    private const val TAG = "invest-reminder"

    /** App 啟動或打開開關時呼叫;只有開關是開的才會排,同一個月重複呼叫不會重複排程。 */
    fun ensureScheduled(context: Context) {
        if (!ReminderSettings.isInvestEnabled(context)) return
        val now = LocalDateTime.now()
        schedule(context, YearMonth.from(InvestSchedule.nextReminder(now)), now)
    }

    internal fun schedule(context: Context, month: YearMonth, now: LocalDateTime = LocalDateTime.now()) {
        val at = month.atDay(InvestSchedule.REMIND_DAY).atTime(InvestSchedule.REMIND_TIME)
        val delay = Duration.between(now, at).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<InvestWorker>()
            .setInitialDelay(delay)
            .addTag(TAG)
            .setInputData(workDataOf(InvestWorker.KEY_MONTH to month.toString()))
            .build()
        // 每個月一個名字:Worker 在執行中排「下個月」時,不會被自己擋住
        WorkManager.getInstance(context)
            .enqueueUniqueWork("invest-reminder-$month", ExistingWorkPolicy.KEEP, request)
    }

    /** 關掉開關時,取消所有已排好的提醒。 */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }
}

class InvestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val month = inputData.getString(KEY_MONTH)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
            ?: return Result.success()
        val ctx = applicationContext
        // 預設關閉;關著就什麼都不做,也不排下個月
        if (!ReminderSettings.isInvestEnabled(ctx)) return Result.success()
        try {
            val zone = ZoneId.systemDefault()
            val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val recorded = (ctx as LedgerApp).repository.hasInvestTransfer(start, end)
            if (!recorded) notifyMissing(ctx, month)
        } finally {
            // 不管這次有沒有提醒,都排好下個月的
            InvestReminder.schedule(ctx, month.plusMonths(1))
        }
        return Result.success()
    }

    private fun notifyMissing(ctx: Context, month: YearMonth) {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return

        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(InvestReminder.CHANNEL_ID, "定期定額提醒", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            ctx,
            REQUEST_CODE,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(ctx, InvestReminder.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("${month.monthValue} 月的 0050 還沒記錄")
            .setContentText("${InvestSchedule.DEBIT_DAY} 號的定期定額(遇假日順延)已經扣款了,點開記一筆吧")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(REQUEST_CODE, notification)
    }

    companion object {
        const val KEY_MONTH = "month"
        private const val REQUEST_CODE = 900_001
    }
}
