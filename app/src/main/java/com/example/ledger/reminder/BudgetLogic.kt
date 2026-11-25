package com.example.ledger.reminder

import java.time.YearMonth

/**
 * 月額上限的判斷邏輯(純 Kotlin,不依賴 Android,方便單元測試)。
 * 級別:0 = 未達 80%,80 = 已達 80%,100 = 已達或超過上限。
 */
object BudgetLogic {
    const val WARN_PERCENT = 80

    fun level(spent: Long, limit: Long): Int {
        if (limit <= 0) return 0
        val percent = spent * 100 / limit
        return when {
            percent >= 100 -> 100
            percent >= WARN_PERCENT -> WARN_PERCENT
            else -> 0
        }
    }

    fun percent(spent: Long, limit: Long): Int =
        if (limit <= 0) 0 else (spent * 100 / limit).coerceAtMost(9_999).toInt()

    /** [notifyLevel] > 0 代表現在要通知這個級別;[store] 不為 null 代表要把這個值存起來。 */
    data class Decision(val notifyLevel: Int, val store: String?)

    /**
     * 同一個月每個級別只通知一次。[stored] 是上次存的 "yyyy-MM:級別"。
     * 級別下降(例如調高了上限)時只更新紀錄、不通知,之後再升上去才會再通知。
     */
    fun decide(month: YearMonth, level: Int, stored: String?): Decision {
        val last = lastLevel(month, stored)
        return when {
            level > last -> Decision(level, key(month, level))
            level < last -> Decision(0, key(month, level))
            else -> Decision(0, null)
        }
    }

    private fun key(month: YearMonth, level: Int) = "$month:$level"

    private fun lastLevel(month: YearMonth, stored: String?): Int {
        val parts = stored?.split(":") ?: return 0
        if (parts.size != 2 || parts[0] != month.toString()) return 0
        return parts[1].toIntOrNull() ?: 0
    }
}
