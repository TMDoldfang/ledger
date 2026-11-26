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
import com.example.ledger.LedgerApp
import com.example.ledger.MainActivity
import com.example.ledger.data.ReceivableAging
import com.example.ledger.settings.AppSettings
import com.example.ledger.util.formatMoney
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 待收款放太久(超過設定的天數)就通知你提醒對方。預設關閉,天數可以隨時改。
 * 每天 19:00 檢查一次,但同樣的待收款最多每 7 天提醒一次,不會天天吵。
 */
object ReceivableReminder {
    internal const val CHANNEL_ID = "receivable_reminder"
    private const val TAG = "receivable-reminder"
    private val REMIND_TIME: LocalTime = LocalTime.of(19, 0)

    fun ensureScheduled(context: Context) {
        if (!AppSettings.receivableReminderEnabled(context)) return
        schedule(context, LocalDateTime.now())
    }

    internal fun schedule(context: Context, now: LocalDateTime) {
        val today = now.toLocalDate().atTime(REMIND_TIME)
        val at = if (now.isBefore(today)) today else today.plusDays(1)
        val request = OneTimeWorkRequestBuilder<ReceivableWorker>()
            .setInitialDelay(Duration.between(now, at).coerceAtLeast(Duration.ZERO))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("receivable-reminder-${at.toLocalDate()}", ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }
}

class ReceivableWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!AppSettings.receivableReminderEnabled(ctx)) return Result.success()
        try {
            val days = AppSettings.receivableReminderDays(ctx)
            val rows = (ctx as LedgerApp).repository.receivables.first()
            val overdue = ReceivableAging.overdue(rows, days, System.currentTimeMillis())
            val today = LocalDate.now().toEpochDay()
            if (overdue != null &&
                ReceivableAging.shouldNotify(AppSettings.receivableLastNotified(ctx), today)
            ) {
                if (notifyOverdue(ctx, days, overdue)) AppSettings.setReceivableLastNotified(ctx, today)
            }
        } finally {
            ReceivableReminder.schedule(ctx, LocalDateTime.now())
        }
        return Result.success()
    }

    private fun notifyOverdue(ctx: Context, days: Int, overdue: ReceivableAging.Overdue): Boolean {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return false
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ReceivableReminder.CHANNEL_ID, "待收款提醒", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            ctx,
            REQUEST_CODE,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val who = overdue.names.take(3).joinToString("、").let { if (overdue.names.size > 3) "$it 等" else it }
        val text = buildString {
            append("共 ${formatMoney(overdue.total)}")
            if (who.isNotEmpty()) append("(").append(who).append(")")
            if (overdue.hasUnassigned) append(",還有沒指定是誰的")
        }
        val notification = NotificationCompat.Builder(ctx, ReceivableReminder.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("有代墊超過 $days 天還沒收回")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(REQUEST_CODE, notification)
        return true
    }

    companion object {
        private const val REQUEST_CODE = 900_003
    }
}
