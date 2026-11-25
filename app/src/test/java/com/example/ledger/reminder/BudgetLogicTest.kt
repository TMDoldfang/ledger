package com.example.ledger.reminder

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetLogicTest {
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)

    @Test fun levels() {
        assertEquals(0, BudgetLogic.level(0, 10000))
        assertEquals(0, BudgetLogic.level(7999, 10000))
        assertEquals(80, BudgetLogic.level(8000, 10000))
        assertEquals(80, BudgetLogic.level(9999, 10000))
        assertEquals(100, BudgetLogic.level(10000, 10000))
        assertEquals(100, BudgetLogic.level(25000, 10000))
    }

    @Test fun noLimitMeansNoLevel() {
        assertEquals(0, BudgetLogic.level(5000, 0))
        assertEquals(0, BudgetLogic.percent(5000, 0))
    }

    @Test fun percentValue() = assertEquals(85, BudgetLogic.percent(8500, 10000))

    @Test fun firstCrossNotifies() {
        val d = BudgetLogic.decide(nov, 80, null)
        assertEquals(80, d.notifyLevel)
        assertEquals("2026-11:80", d.store)
    }

    @Test fun sameLevelDoesNotNotifyTwice() {
        val d = BudgetLogic.decide(nov, 80, "2026-11:80")
        assertEquals(0, d.notifyLevel)
        assertNull(d.store)
    }

    @Test fun escalationNotifies() {
        val d = BudgetLogic.decide(nov, 100, "2026-11:80")
        assertEquals(100, d.notifyLevel)
        assertEquals("2026-11:100", d.store)
    }

    @Test fun newMonthResets() {
        val d = BudgetLogic.decide(dec, 80, "2026-11:100")
        assertEquals(80, d.notifyLevel)
    }

    @Test fun levelDropRecordsWithoutNotifying() {
        val d = BudgetLogic.decide(nov, 0, "2026-11:100")
        assertEquals(0, d.notifyLevel)
        assertEquals("2026-11:0", d.store)
        // 再升回 80 會通知
        assertEquals(80, BudgetLogic.decide(nov, 80, "2026-11:0").notifyLevel)
    }

    @Test fun garbageStoredValueIsIgnored() {
        assertEquals(80, BudgetLogic.decide(nov, 80, "亂碼").notifyLevel)
    }
}
