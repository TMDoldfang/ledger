package com.example.ledger.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class InvestScheduleTest {
    private val plan = InvestSchedule.defaultPlan() // 6 號扣款、8 號提醒
    private fun next(y: Int, m: Int, d: Int, h: Int = 9, p: InvestPlan = plan) =
        InvestSchedule.nextReminder(p, LocalDateTime.of(y, m, d, h, 0))

    @Test fun beforeThe8th() = assertEquals(LocalDateTime.of(2026, 12, 8, 12, 0), next(2026, 12, 1))
    @Test fun morningOf8th() = assertEquals(LocalDateTime.of(2026, 12, 8, 12, 0), next(2026, 12, 8, 9))
    @Test fun exactlyNoonOf8thGoesToNextMonth() = assertEquals(LocalDateTime.of(2027, 1, 8, 12, 0), next(2026, 12, 8, 12))
    @Test fun afterThe8th() = assertEquals(LocalDateTime.of(2027, 1, 8, 12, 0), next(2026, 12, 20))
    @Test fun yearRollover() = assertEquals(LocalDateTime.of(2027, 1, 8, 12, 0), next(2026, 12, 31, 23))

    @Test fun customRemindDay() =
        assertEquals(LocalDateTime.of(2026, 12, 15, 12, 0), next(2026, 12, 1, p = plan.copy(remindDay = 15)))

    @Test fun remindDay31ClampsToMonthEnd() =
        assertEquals(LocalDateTime.of(2027, 2, 28, 12, 0), next(2027, 2, 1, p = plan.copy(remindDay = 31)))

    @Test fun debitDateSameMonth() =
        assertEquals(LocalDate.of(2026, 12, 6), InvestSchedule.debitDate(plan, LocalDateTime.of(2026, 12, 8, 12, 0)))

    @Test fun remindBeforeDebitMeansLastMonthsDebit() {
        val p = InvestPlan(1, "x", debitDay = 28, remindDay = 2)
        assertEquals(LocalDate.of(2026, 11, 28), InvestSchedule.debitDate(p, LocalDateTime.of(2026, 12, 2, 12, 0)))
        assertEquals(LocalDate.of(2026, 11, 1), InvestSchedule.windowStart(p, LocalDateTime.of(2026, 12, 2, 12, 0)))
    }

    @Test fun twoPlansInOneMonthNeedTwoTransfers() {
        val a = InvestPlan(1, "A", 6, 8)
        val b = InvestPlan(2, "B", 20, 22)
        val all = listOf(a, b)
        // A 提醒時,B 這個月還沒扣款:只需要 1 筆
        assertEquals(1, InvestSchedule.transfersNeeded(a, all, InvestSchedule.remindAt(a, YearMonth.of(2026, 12))))
        // B 提醒時,A 已扣過:要 2 筆
        assertEquals(2, InvestSchedule.transfersNeeded(b, all, InvestSchedule.remindAt(b, YearMonth.of(2026, 12))))
    }

    @Test fun singlePlanNeedsOne() =
        assertEquals(1, InvestSchedule.transfersNeeded(plan, listOf(plan), InvestSchedule.remindAt(plan, YearMonth.of(2026, 12))))

    @Test fun encodeDecodeRoundTrip() {
        val plans = listOf(InvestPlan(1, "0050 定期定額", 6, 8), InvestPlan(3, "0056", 20, 22))
        assertEquals(plans, InvestSchedule.decode(InvestSchedule.encode(plans)))
    }

    @Test fun decodeSkipsBadLinesAndClampsDays() {
        val text = "1\t名稱\t40\t0\nbad line\nx\ty\tz\tw"
        assertEquals(listOf(InvestPlan(1, "名稱", 31, 1)), InvestSchedule.decode(text))
    }

    @Test fun decodeNullIsEmpty() = assertEquals(emptyList<InvestPlan>(), InvestSchedule.decode(null))

    @Test fun nameCannotBreakStorage() {
        val encoded = InvestSchedule.encode(listOf(InvestPlan(1, "a\tb\nc", 6, 8)))
        assertEquals("a b c", InvestSchedule.decode(encoded).single().name)
    }

    @Test fun nextIdIsUnique() =
        assertEquals(4, InvestSchedule.nextId(listOf(InvestPlan(1, "a", 1, 2), InvestPlan(3, "b", 1, 2))))
}
