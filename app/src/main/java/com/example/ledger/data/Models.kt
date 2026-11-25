package com.example.ledger.data

import java.time.YearMonth

enum class AccountType(val label: String) {
    CASH("現金"),
    BANK("銀行"),
    CARD("信用卡"),
    EPAY("電子支付"),
    INVEST("投資");

    companion object {
        fun from(name: String): AccountType = entries.firstOrNull { it.name == name } ?: CASH
    }
}

/** 內建分類的名稱。轉帳與代墊收回是系統用的,不會出現在一般的分類選單。 */
object SystemNames {
    const val TRANSFER = "轉帳"
    const val SETTLEMENT = "代墊收回"
    const val CASH_OUT = "現金領出"
    const val INVEST_ACCOUNT = "投資"

    val HIDDEN_CATEGORIES = setOf(TRANSFER, SETTLEMENT)
}

/** 帳目列表用:交易 + 分類 + 帳戶 的扁平結果(欄位名稱需與 DAO 查詢的別名一致)。 */
data class TransactionItem(
    val id: Long,
    val amount: Long,
    val isExpense: Boolean,
    val note: String,
    val timestamp: Long,
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String,
    val accountId: Long,
    val accountName: String,
    val myShare: Long?,
    val transferToAccountId: Long?,
    val transferToName: String?,
    val settlesId: Long?,
) {
    val isTransfer: Boolean get() = transferToAccountId != null
    val isSettlement: Boolean get() = settlesId != null

    /** 統計時真正算進支出的金額:有「我的份」就用它,否則用實付。 */
    val countedAmount: Long get() = myShare ?: amount

    /** 轉帳和代墊收回只是帳戶間的錢在動,不算收入也不算支出。 */
    val countsInStats: Boolean get() = !isTransfer && !isSettlement
}

/** 待收款:我先墊了、朋友還沒轉給我的部分。 */
data class Receivable(
    val id: Long,
    val amount: Long,
    val myShare: Long,
    val note: String,
    /** 誰欠我;還沒補就是 null */
    val owedBy: String?,
    val timestamp: Long,
    val categoryName: String,
    val categoryIcon: String,
    val accountName: String,
    /** 還沒收回的金額 */
    val outstanding: Long,
)

data class AccountWithBalance(
    val id: Long,
    val name: String,
    val type: String,
    val balance: Long,
)

data class CategorySum(val name: String, val icon: String, val total: Long)

data class MonthPoint(val month: YearMonth, val expense: Long, val income: Long)

data class MonthTotals(val expense: Long = 0, val income: Long = 0) {
    val balance: Long get() = income - expense
}

data class StatsState(
    val expenseByCategory: List<CategorySum> = emptyList(),
    val totals: MonthTotals = MonthTotals(),
    val trend: List<MonthPoint> = emptyList(),
)
