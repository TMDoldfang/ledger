package com.example.ledger.reminder

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/**
 * 0050 定期定額:每月 6 號扣款(遇假日順延),金額不固定,所以不自動入帳。
 * 到 8 號中午如果這個月還沒有任何「轉入投資帳戶」的紀錄,就提醒補記一筆。
 * 純 Kotlin、不依賴 Android,方便單元測試。
 */
object InvestSchedule {
    const val DEBIT_DAY = 6
    const val REMIND_DAY = 8
    val REMIND_TIME: LocalTime = LocalTime.NOON

    /** [now] 之後(不含剛好同一刻)最近的一次提醒時間。 */
    fun nextReminder(now: LocalDateTime): LocalDateTime {
        val thisMonth = YearMonth.from(now).atDay(REMIND_DAY).atTime(REMIND_TIME)
        return if (thisMonth.isAfter(now)) thisMonth
        else YearMonth.from(now).plusMonths(1).atDay(REMIND_DAY).atTime(REMIND_TIME)
    }
}
