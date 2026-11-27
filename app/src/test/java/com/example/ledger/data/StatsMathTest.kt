package com.example.ledger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsMathTest {
    private fun tx(
        id: Long, amount: Long, isExpense: Boolean = true, category: String = "餐飲",
        myShare: Long? = null, forgiven: Long = 0, settledTotal: Long = 0,
        transferTo: Long? = null, settlesId: Long? = null,
    ) = TransactionItem(
        id = id, amount = amount, isExpense = isExpense, note = "", timestamp = 0,
        categoryId = category.hashCode().toLong(), categoryName = category, categoryIcon = "x",
        accountId = 1, accountName = "a", myShare = myShare, transferToAccountId = transferTo,
        transferToName = null, settlesId = settlesId, forgiven = forgiven, settledTotal = settledTotal,
    )

    @Test fun expenseIsFullPaid() {
        val t = StatsMath.totals(listOf(tx(1, 1200, myShare = 300)))
        assertEquals(1200L, t.expense)
        assertEquals(900L, t.proxy)
        assertEquals(-1200L, t.balance)
    }

    @Test fun settlementCountsAsIncome() {
        val t = StatsMath.totals(listOf(tx(2, 900, isExpense = false, category = "代墊收回", settlesId = 1)))
        assertEquals(900L, t.income)
        assertEquals(900L, t.settledIn)
        assertEquals(900L, t.balance)
    }

    @Test fun incomeIsSalaryPlusSettlement() {
        val t = StatsMath.totals(listOf(
            tx(1, 1200, myShare = 300),
            tx(2, 900, isExpense = false, category = "代墊收回", settlesId = 1),
            tx(3, 50000, isExpense = false, category = "薪資"),
        ))
        assertEquals(50900L, t.income)
        assertEquals(1200L, t.expense)
        assertEquals(49700L, t.balance)
    }

    @Test fun transfersAreIgnored() {
        val t = StatsMath.totals(listOf(tx(3, 3000, transferTo = 9)))
        assertEquals(0L, t.expense)
    }

    @Test fun crossMonthProxyShowsReceivedInExpenseMonth() {
        // 11 月的帳:12 月收到了 900。11 月的資料裡 settledTotal 已經是 900
        val nov = StatsMath.totals(listOf(tx(1, 1200, myShare = 300, settledTotal = 900)))
        assertEquals(900L, nov.proxy)
        assertEquals(900L, nov.proxyReceived)
        // 12 月只看得到收回那一筆
        val dec = StatsMath.totals(listOf(tx(2, 900, isExpense = false, category = "代墊收回", settlesId = 1)))
        assertEquals(0L, dec.expense)
        assertEquals(900L, dec.settledIn)
    }

    @Test fun pieHasProxySliceAndAddsUpToExpense() {
        val list = listOf(tx(1, 1200, myShare = 300), tx(2, 500, category = "交通"))
        val sums = StatsMath.categorySums(list)
        assertEquals(StatsMath.totals(list).expense, sums.sumOf { it.total })
        val proxy = sums.single { it.isProxy }
        assertEquals(900L, proxy.total)
        assertEquals("餐飲", sums.first { it.name == "餐飲" }.name)
        assertEquals(300L, sums.first { it.name == "餐飲" }.total)
    }

    @Test fun noProxySliceWhenNoSharing() =
        assertFalse(StatsMath.categorySums(listOf(tx(1, 100))).any { it.isProxy })

    @Test fun forgivenBecomesMyBurden() {
        // 朋友 300 不收了:我的負擔 300 + 300 = 600,別人還欠 600
        val item = tx(1, 1200, myShare = 300, forgiven = 300)
        assertEquals(600L, item.burden)
        assertEquals(600L, item.proxy)
        val sums = StatsMath.categorySums(listOf(item))
        assertEquals(600L, sums.first { !it.isProxy }.total)
        assertEquals(1200L, sums.sumOf { it.total })
    }

    @Test fun receivedNeverExceedsProxy() {
        val t = StatsMath.totals(listOf(tx(1, 1000, myShare = 600, settledTotal = 5000)))
        assertEquals(400L, t.proxyReceived)
    }

    @Test fun proxyNoteMentionsReceivedAndPending() {
        val sums = StatsMath.categorySums(listOf(tx(1, 1200, myShare = 300, settledTotal = 400)))
        assertTrue(sums.single { it.isProxy }.note!!.contains("500"))
    }
}

class ReceivableAgingTest {
    private val day = 24L * 60 * 60 * 1000
    private fun row(name: String?, outstanding: Long, ageDays: Long, now: Long) = ReceivableDetail(
        txId = 1, personId = if (name == null) null else 1, personName = name, txAmount = 0, myShare = 0, note = "",
        timestamp = now - ageDays * day, categoryName = "", categoryIcon = "", accountName = "",
        shareAmount = outstanding, outstanding = outstanding,
    )

    @Test fun onlyOldOnesCount() {
        val now = 100 * day
        val o = ReceivableAging.overdue(listOf(row("小明", 300, 40, now), row("小華", 500, 5, now)), 30, now)!!
        assertEquals(listOf("小明"), o.names)
        assertEquals(300L, o.total)
    }

    @Test fun nothingOverdue() = assertNull(ReceivableAging.overdue(listOf(row("小明", 300, 5, 100 * day)), 30, 100 * day))

    @Test fun unassignedFlagged() {
        val now = 100 * day
        assertTrue(ReceivableAging.overdue(listOf(row(null, 100, 60, now)), 30, now)!!.hasUnassigned)
    }

    @Test fun notifyIntervals() {
        assertTrue(ReceivableAging.shouldNotify(null, 100))
        assertFalse(ReceivableAging.shouldNotify(96, 100))
        assertTrue(ReceivableAging.shouldNotify(93, 100))
    }
}

class PersonTotalsTest {
    @Test fun outstandingSubtractsSettledAndForgiven() =
        assertEquals(200L, PersonTotals(1, "小明", total = 1000, forgiven = 300, settled = 500).outstanding)

    @Test fun outstandingNeverNegative() =
        assertEquals(0L, PersonTotals(1, "小明", total = 100, forgiven = 0, settled = 500).outstanding)

    @Test fun rowOutstanding() =
        assertEquals(50L, PersonShareRow(1, 0, "", "餐飲", "x", shareAmount = 100, forgiven = 20, settled = 30).outstanding)
}
