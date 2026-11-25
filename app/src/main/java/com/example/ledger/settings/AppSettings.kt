package com.example.ledger.settings

import android.content.Context

/** 附加功能的開關與設定,存在手機本機。除非特別說明,附加功能預設都是關閉。 */
object AppSettings {
    private const val PREFS = "app_settings"
    private const val KEY_BUDGET_ENABLED = "budget_enabled"
    private const val KEY_BUDGET_LIMIT = "budget_limit"
    private const val KEY_BUDGET_NOTIFIED = "budget_notified"
    private const val KEY_AUTO_DETECT = "auto_detect_enabled"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // 月額上限與提醒
    fun budgetEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_BUDGET_ENABLED, false)
    fun setBudgetEnabled(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_BUDGET_ENABLED, on).apply()

    /** 每月上限(元);0 = 還沒設定 */
    fun budgetLimit(context: Context): Long = prefs(context).getLong(KEY_BUDGET_LIMIT, 0L)
    fun setBudgetLimit(context: Context, limit: Long) =
        prefs(context).edit().putLong(KEY_BUDGET_LIMIT, limit.coerceAtLeast(0L)).apply()

    fun budgetNotified(context: Context): String? = prefs(context).getString(KEY_BUDGET_NOTIFIED, null)
    fun setBudgetNotified(context: Context, value: String) =
        prefs(context).edit().putString(KEY_BUDGET_NOTIFIED, value).apply()

    // 自動偵測訊息入帳:預設關閉。功能還沒推出,這個值先留著,之後才會有作用。
    fun autoDetectEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_DETECT, false)
    fun setAutoDetectEnabled(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_AUTO_DETECT, on).apply()
}
