package com.example.ledger.reminder

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class InvestScheduleTest {
    private fun next(y: Int, m: Int, d: Int, h: Int = 9) =
        InvestSchedule.nextReminder(LocalDateTime.of(y, m, d, h, 0))

    @Test fun beforeThe8th() = assertEquals(LocalDateTime.of(2026, 12, 8, 12, 0), next(2026, 12, 1))
    @Test fun morningOf8th() = assertEquals(LocalDateTime.of(2026, 12, 8, 12, 0), next(2026, 12, 8, 9))
    @Test fun exactlyNoonOf8thGoesToNextMonth() = assertEquals(LocalDateTime.of(2027, 1, 8, 12, 0), next(2026, 12, 8, 12))
    @Test fun afterThe8th() = assertEquals(LocalDateTime.of(2027, 1, 8, 12, 0), next(2026, 12, 20))
    @Test fun yearRollover() = assertEquals(LocalDateTime.of(2027, 1, 8, 12, 0), next(2026, 12, 31, 23))
}
