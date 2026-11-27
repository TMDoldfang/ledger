package com.example.ledger.settings

import android.content.Context
import com.example.ledger.detect.CustomSource
import com.example.ledger.detect.CustomSources
import com.example.ledger.detect.RecentNotice

/** 附加功能的開關與設定,存在手機本機。除非特別說明,附加功能預設都是關閉。 */
object AppSettings {
    private const val PREFS = "app_settings"
    private const val KEY_BUDGET_ENABLED = "budget_enabled"
    private const val KEY_BUDGET_LIMIT = "budget_limit"
    private const val KEY_BUDGET_NOTIFIED = "budget_notified"
    private const val KEY_AUTO_DETECT = "auto_detect_enabled"
    private const val KEY_RECV_ENABLED = "receivable_reminder_enabled"
    private const val KEY_RECV_DAYS = "receivable_reminder_days"
    private const val KEY_RECV_LAST = "receivable_last_notified"
    private const val KEY_LAST_LINE_TITLE = "last_line_title"
    private const val KEY_LAST_DETECTED = "last_detected"
    private const val KEY_LAST_DETECTED_AT = "last_detected_at"
    private const val KEY_PEND_REMIND_ON = "pending_remind_on"
    private const val KEY_PEND_REMIND_MIN = "pending_remind_minute_of_day"
    private const val KEY_SRC_CTBC = "source_ctbc_on"
    private const val KEY_SRC_WALLET = "source_wallet_on"
    private const val KEY_CUSTOM_SOURCES = "custom_sources"
    private const val KEY_RECENT_NOTICES = "recent_notices"
    private const val KEY_SKIP_DELETE_CONFIRM = "skip_delete_confirm"

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

    // 內建來源(LINE 裡的中國信託、LINE錢包):新使用者預設沒有任何來源,自己在「新增來源」加入。
    /** 舊版預設兩個都開。第一次執行新版時,已經開過自動偵測的人把原本的來源明確存下來,不會被清掉。 */
    fun migrateSources(context: Context) {
        val p = prefs(context)
        if (p.getBoolean("sources_migrated", false)) return
        val e = p.edit()
        if (p.getBoolean(KEY_AUTO_DETECT, false)) {
            if (!p.contains(KEY_SRC_CTBC)) e.putBoolean(KEY_SRC_CTBC, true)
            if (!p.contains(KEY_SRC_WALLET)) e.putBoolean(KEY_SRC_WALLET, true)
        }
        e.putBoolean("sources_migrated", true).apply()
    }

    fun sourceCtbcEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_SRC_CTBC, false)
    fun setSourceCtbcEnabled(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_SRC_CTBC, on).apply()
    fun sourceWalletEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_SRC_WALLET, false)
    fun setSourceWalletEnabled(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_SRC_WALLET, on).apply()

    // 自訂來源與最近收到的通知(只存 App 與標題,不存內容)
    fun customSources(context: Context): List<CustomSource> =
        CustomSources.decode(prefs(context).getString(KEY_CUSTOM_SOURCES, "").orEmpty())

    @Synchronized
    fun updateCustomSources(context: Context, change: (List<CustomSource>) -> List<CustomSource>): List<CustomSource> {
        val next = change(customSources(context))
        prefs(context).edit().putString(KEY_CUSTOM_SOURCES, CustomSources.encode(next)).apply()
        return next
    }

    fun recentNotices(context: Context): List<RecentNotice> =
        CustomSources.decodeRecent(prefs(context).getString(KEY_RECENT_NOTICES, "").orEmpty())

    @Synchronized
    fun addRecentNotice(context: Context, n: RecentNotice) {
        val next = CustomSources.addRecent(recentNotices(context), n)
        prefs(context).edit().putString(KEY_RECENT_NOTICES, CustomSources.encodeRecent(next)).apply()
    }

    fun clearRecentNotices(context: Context) =
        prefs(context).edit().remove(KEY_RECENT_NOTICES).apply()

    // 待補金額提醒:預設開、21:00(舊用戶不變)。時間存「一天中的第幾分鐘」。
    fun pendingRemindEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_PEND_REMIND_ON, true)
    fun setPendingRemindEnabled(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_PEND_REMIND_ON, on).apply()

    fun pendingRemindMinuteOfDay(context: Context): Int = prefs(context).getInt(KEY_PEND_REMIND_MIN, 21 * 60)
    fun setPendingRemindMinuteOfDay(context: Context, minute: Int) =
        prefs(context).edit().putInt(KEY_PEND_REMIND_MIN, minute.coerceIn(0, 24 * 60 - 1)).apply()

    // 刪除前的確認提示:預設會問(skip = false);在確認框勾「不再顯示」後變 true,設定頁可改回來。
    fun skipDeleteConfirm(context: Context): Boolean = prefs(context).getBoolean(KEY_SKIP_DELETE_CONFIRM, false)
    fun setSkipDeleteConfirm(context: Context, skip: Boolean) =
        prefs(context).edit().putBoolean(KEY_SKIP_DELETE_CONFIRM, skip).apply()

    // 自動偵測訊息入帳:預設關閉,在設定頁開啟。
    fun autoDetectEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_DETECT, false)
    fun setAutoDetectEnabled(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_AUTO_DETECT, on).apply()

    /** 除錯用:最近一則 LINE 通知的標題(只存標題,不存內容),以及最近一次成功偵測的結果。 */
    fun lastLineTitle(context: Context): String? = prefs(context).getString(KEY_LAST_LINE_TITLE, null)
    fun setLastLineTitle(context: Context, title: String) =
        prefs(context).edit().putString(KEY_LAST_LINE_TITLE, title.take(30)).apply()

    fun lastDetected(context: Context): Pair<String, Long>? {
        val text = prefs(context).getString(KEY_LAST_DETECTED, null) ?: return null
        return text to prefs(context).getLong(KEY_LAST_DETECTED_AT, 0L)
    }
    fun setLastDetected(context: Context, text: String, at: Long) =
        prefs(context).edit().putString(KEY_LAST_DETECTED, text).putLong(KEY_LAST_DETECTED_AT, at).apply()

    // 待收款提醒:放太久沒收回就通知。預設關閉,天數預設 30,可隨時改。
    fun receivableReminderEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_RECV_ENABLED, false)
    fun setReceivableReminderEnabled(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_RECV_ENABLED, on).apply()

    fun receivableReminderDays(context: Context): Int = prefs(context).getInt(KEY_RECV_DAYS, 30)
    fun setReceivableReminderDays(context: Context, days: Int) =
        prefs(context).edit().putInt(KEY_RECV_DAYS, days.coerceIn(1, 365)).apply()

    /** 上次提醒是哪一天(epochDay),避免天天通知。 */
    fun receivableLastNotified(context: Context): Long? =
        prefs(context).getLong(KEY_RECV_LAST, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
    fun setReceivableLastNotified(context: Context, epochDay: Long) =
        prefs(context).edit().putLong(KEY_RECV_LAST, epochDay).apply()
}
