package com.example.ledger.data

/**
 * 統計的計算(純 Kotlin,不依賴 Android,方便單元測試)。
 *
 * - 支出 = 實付全額(含代墊);收入不含代墊收回。
 * - 代墊收回(朋友轉來的錢)獨立一欄,列入結餘:結餘 = 收入 − 支出 + 代墊收回。
 * - 圓環圖:各分類只算我自己負擔的,另外一塊「代墊」是別人該付的;加起來等於支出。
 *   代墊按「支出發生的月份」算,之後收回不會改掉那個月的數字。
 */
object StatsMath {
    const val PROXY_NAME = "代墊"
    const val PROXY_ICON = "🤝"

    fun totals(list: List<TransactionItem>): MonthTotals {
        val normal = list.filter { it.countsInStats }
        val expenses = normal.filter { it.isExpense }
        val proxy = expenses.sumOf { it.proxy }
        return MonthTotals(
            expense = expenses.sumOf { it.amount },
            income = normal.filter { !it.isExpense }.sumOf { it.amount },
            settledIn = list.filter { it.isSettlement }.sumOf { it.amount },
            proxy = proxy,
            // 已收回不可能超過別人該付的
            proxyReceived = expenses.filter { it.proxy > 0 }.sumOf { minOf(it.settledTotal, it.proxy) },
        )
    }

    /** 圓環圖的各塊(大的在前)。 */
    fun categorySums(list: List<TransactionItem>, formatMoney: (Long) -> String = { "NT$$it" }): List<CategorySum> {
        val expenses = list.filter { it.isExpense && it.countsInStats }
        val byCategory = expenses
            .groupBy { it.categoryId }
            .map { (_, rows) -> CategorySum(rows.first().categoryName, rows.first().categoryIcon, rows.sumOf { it.burden }) }
            .filter { it.total > 0 }
        val t = totals(list)
        val proxySlice = if (t.proxy > 0) {
            listOf(
                CategorySum(
                    PROXY_NAME, PROXY_ICON, t.proxy, isProxy = true,
                    note = "已收回 ${formatMoney(t.proxyReceived)} · 待收 ${formatMoney(t.proxy - t.proxyReceived)}",
                )
            )
        } else emptyList()
        return (byCategory + proxySlice).sortedByDescending { it.total }
    }
}

/** 待收款放太久的提醒(純 Kotlin)。 */
object ReceivableAging {
    data class Overdue(val names: List<String>, val total: Long, val hasUnassigned: Boolean)

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** 超過 [days] 天還沒收回的待收款;沒有就回傳 null。 */
    fun overdue(rows: List<ReceivableDetail>, days: Int, now: Long): Overdue? {
        val old = rows.filter { now - it.timestamp >= days * DAY_MS && it.outstanding > 0 }
        if (old.isEmpty()) return null
        return Overdue(
            names = old.mapNotNull { it.personName }.distinct(),
            total = old.sumOf { it.outstanding },
            hasUnassigned = old.any { it.personId == null },
        )
    }

    /** 上次提醒是哪一天(epochDay)?距離今天夠久才再提醒,避免天天吵。 */
    fun shouldNotify(lastEpochDay: Long?, todayEpochDay: Long, intervalDays: Int = 7): Boolean =
        lastEpochDay == null || todayEpochDay - lastEpochDay >= intervalDays
}
