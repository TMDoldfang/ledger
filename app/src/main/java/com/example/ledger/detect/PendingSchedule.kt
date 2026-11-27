package com.example.ledger.detect

import java.time.LocalDateTime
import java.time.LocalTime

/** 「待確認」每天晚上提醒一次的時間計算(純 Kotlin)。 */
object PendingSchedule {
    val REMIND_TIME: LocalTime = LocalTime.of(21, 0)

    /** 下一次提醒:設定的時間(預設 21:00)今天還沒到就是今天,否則明天。 */
    fun nextReminder(now: LocalDateTime, time: LocalTime = REMIND_TIME): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return if (now.isBefore(today)) today else today.plusDays(1)
    }
}
