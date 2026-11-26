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
 * 定期定額提醒:每一組在自己的提醒日中午檢查,這個月(扣款日所在的月份)有沒有記錄轉入投資帳戶,沒有就通知。
 * 每次檢查完會排好下個月的,所以只要 App 啟動過一次就會一直運作。扣款日、提醒日、組數都可以隨時改。
 */
object InvestReminder {
    internal const val CHANNEL_ID = "invest_reminder"
    private const val TAG = "invest-reminder"

    /** App 啟動時呼叫;只有開關是開的才會排,同一次提醒重複呼叫不會重複排程。 */
    fun ensureScheduled(context: Context) {
        if (!ReminderSettings.isInvestEnabled(context)) return
        val now = LocalDateTime.now()
        ReminderSettings.investPlans(context).forEach { plan ->
            schedule(context, plan, InvestSchedule.nextReminder(plan, now), now)
        }
    }

    /** 設定改了(新增、修改、刪除、開關):取消舊的,依新的設定重新排。 */
    fun reschedule(context: Context) {
        cancel(context)
        ensureScheduled(context)
    }

    internal fun schedule(context: Context, plan: InvestPlan, at: LocalDateTime, now: LocalDateTime = LocalDateTime.now()) {
        val request = OneTimeWorkRequestBuilder<InvestWorker>()
            .setInitialDelay(Duration.between(now, at).coerceAtLeast(Duration.ZERO))
            .addTag(TAG)
            .setInputData(workDataOf(InvestWorker.KEY_PLAN_ID to plan.id, InvestWorker.KEY_AT to at.toString()))
            .build()
        // 每一組、每一次提醒一個名字:Worker 在執行中排「下個月」時,不會被自己擋住
        WorkManager.getInstance(context)
            .enqueueUniqueWork("invest-reminder-${plan.id}-${at.toLocalDate()}", ExistingWorkPolicy.KEEP, request)
    }

    /** 關掉開關或要重新排程時,取消所有已排好的提醒。 */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }
}

class InvestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val planId = inputData.getInt(KEY_PLAN_ID, -1)
        val at = inputData.getString(KEY_AT)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        if (planId < 0 || at == null) return Result.success() // 舊版留下的排程:不處理
        val ctx = applicationContext
        // 預設關閉;關著就什麼都不做,也不排下一次
        if (!ReminderSettings.isInvestEnabled(ctx)) return Result.success()
        val plans = ReminderSettings.investPlans(ctx)
        val plan = plans.firstOrNull { it.id == planId } ?: return Result.success() // 這組已被刪除
        try {
            val zone = ZoneId.systemDefault()
            val start = InvestSchedule.windowStart(plan, at).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = System.currentTimeMillis()
            val recorded = (ctx as LedgerApp).repository.investTransferCount(start, end)
            if (recorded < InvestSchedule.transfersNeeded(plan, plans, at)) notifyMissing(ctx, plan)
        } finally {
            // 不管這次有沒有提醒,都排好下個月的
            val next = InvestSchedule.remindAt(plan, YearMonth.from(at).plusMonths(1))
            InvestReminder.schedule(ctx, plan, next)
        }
        return Result.success()
    }

    private fun notifyMissing(ctx: Context, plan: InvestPlan) {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return

        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(InvestReminder.CHANNEL_ID, "定期定額提醒", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val code = REQUEST_BASE + plan.id
        val open = PendingIntent.getActivity(
            ctx,
            code,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(ctx, InvestReminder.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("「${plan.name}」還沒記錄")
            .setContentText("每月 ${plan.debitDay} 號的扣款(遇假日順延)已經過了,點開記一筆吧")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(code, notification)
    }

    companion object {
        const val KEY_PLAN_ID = "plan_id"
        const val KEY_AT = "remind_at"
        private const val REQUEST_BASE = 900_100
    }
}
