package com.example.ledger.reminder

import android.content.Context

/** 提醒的開關與設定,存在本機。定期定額提醒預設關閉,要到「設定」頁自己打開。 */
object ReminderSettings {
    private const val PREFS = "reminders"
    private const val KEY_INVEST = "invest_enabled"
    private const val KEY_PLANS = "invest_plans"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isInvestEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_INVEST, false)

    /** 開:立刻排好各組的下一次;關:取消已排的。 */
    fun setInvestEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_INVEST, enabled).apply()
        if (enabled) InvestReminder.reschedule(context) else InvestReminder.cancel(context)
    }

    /** 各組定期定額。還沒設定過就是預設一組(6 號扣款、8 號提醒),也就是舊版的行為。 */
    fun investPlans(context: Context): List<InvestPlan> {
        val stored = InvestSchedule.decode(prefs(context).getString(KEY_PLANS, null))
        return stored.ifEmpty { if (prefs(context).contains(KEY_PLANS)) emptyList() else listOf(InvestSchedule.defaultPlan()) }
    }

    /** 新增、修改、刪除之後呼叫:存起來並重新排程(隨時可以調整)。 */
    fun saveInvestPlans(context: Context, plans: List<InvestPlan>) {
        prefs(context).edit().putString(KEY_PLANS, InvestSchedule.encode(plans)).apply()
        if (isInvestEnabled(context)) InvestReminder.reschedule(context)
    }
}
