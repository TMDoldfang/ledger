package com.example.ledger.reminder

import android.content.Context

/** 提醒的開關,存在本機。0050 定期定額提醒預設關閉,要到「管理」頁自己打開。 */
object ReminderSettings {
    private const val PREFS = "reminders"
    private const val KEY_INVEST = "invest_enabled"

    fun isInvestEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_INVEST, false)

    /** 開:立刻排好下一次(每月 8 號中午);關:取消已排的。 */
    fun setInvestEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_INVEST, enabled).apply()
        if (enabled) InvestReminder.ensureScheduled(context) else InvestReminder.cancel(context)
    }
}
