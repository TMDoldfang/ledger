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
import com.example.ledger.MainActivity
import com.example.ledger.data.LedgerRepository
import com.example.ledger.settings.AppSettings
import com.example.ledger.util.formatMoney
import java.time.YearMonth
import java.time.ZoneId

/**
 * 月額上限檢查:每次儲存帳目後、以及 App 啟動時呼叫。
 * 本月(1 號到月底)支出達 80%、100% 時各通知一次。
 * 已花 = 支出實付全額(含現金領出)+ 轉到投資帳戶 − 這個月已收回的代墊;繳卡費不算,避免和刷卡重複。
 */
object BudgetChecker {
    private const val CHANNEL_ID = "budget_alert"
    private const val NOTIFICATION_ID = 900_002

    suspend fun check(context: Context, repo: LedgerRepository) {
        if (!AppSettings.budgetEnabled(context)) return
        val limit = AppSettings.budgetLimit(context)
        if (limit <= 0) return

        val month = YearMonth.now()
        val zone = ZoneId.systemDefault()
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val spent = repo.budgetSpent(start, end)

        val decision = BudgetLogic.decide(month, BudgetLogic.level(spent, limit), AppSettings.budgetNotified(context))
        decision.store?.let { AppSettings.setBudgetNotified(context, it) }
        if (decision.notifyLevel > 0) notify(context, decision.notifyLevel, spent, limit)
    }

    private fun notify(context: Context, level: Int, spent: Long, limit: Long) {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return

        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "月額上限提醒", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (level >= 100) "本月支出已超過上限" else "本月支出已達上限的 ${BudgetLogic.WARN_PERCENT}%"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText("已花 ${formatMoney(spent)} / 上限 ${formatMoney(limit)}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
