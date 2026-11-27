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
    /** 這筆代墊裡「不收了」的金額(各人分攤的 forgiven 加總) */
    val forgiven: Long = 0,
    /** 這筆代墊到目前為止已收回的總額 */
    val settledTotal: Long = 0,
) {
    val isTransfer: Boolean get() = transferToAccountId != null
    val isSettlement: Boolean get() = settlesId != null

    /** 我自己真正負擔的:有「我的份」就是它(加上不收了的),否則是實付全額。 */
    val burden: Long get() = if (myShare != null) minOf(amount, myShare + forgiven) else amount

    /** 別人該付的代墊總額(不論收回了沒);非代墊為 0。 */
    val proxy: Long get() = if (isExpense && myShare != null && !isTransfer) maxOf(0L, amount - burden) else 0L

    /** 舊名稱:統計用的「我的份」。 */
    val countedAmount: Long get() = burden

    /** 轉帳和代墊收回只是帳戶間的錢在動,不算收入也不算支出。 */
    val countsInStats: Boolean get() = !isTransfer && !isSettlement
}

/**
 * 待收款的一列:某筆代墊裡「某個人」還沒還的錢。
 * [personId] 為 null 代表「還沒指定是誰」的那一部分。不分月份,沒收回就一直在。
 */
data class ReceivableDetail(
    val txId: Long,
    val personId: Long?,
    val personName: String?,
    val txAmount: Long,
    val myShare: Long,
    val note: String,
    val timestamp: Long,
    val categoryName: String,
    val categoryIcon: String,
    val accountName: String,
    /** 這個人(或未指定)原本該付的 */
    val shareAmount: Long,
    /** 還沒收回的 */
    val outstanding: Long,
)

data class AccountWithBalance(
    val id: Long,
    val name: String,
    val type: String,
    val balance: Long,
)

/** 圓環圖的一塊。[isProxy] 是「代墊」那一塊,[note] 是圖例上的補充說明(已收回 / 待收)。 */
data class CategorySum(
    val name: String,
    val icon: String,
    val total: Long,
    val isProxy: Boolean = false,
    val note: String? = null,
)

data class MonthPoint(val month: YearMonth, val expense: Long, val income: Long)

/**
 * 一個月的收支(現金流角度):
 * [expense] 是實付全額(含代墊);[settledIn] 是這個月收到的代墊收回(不算收入);
 * [proxy] 是這個月的支出裡別人該付的代墊,[proxyReceived] 是其中已收回的(不論哪個月收到)。
 */
data class MonthTotals(
    val expense: Long = 0,
    /** 含代墊收回(settledIn)。 */
    val income: Long = 0,
    val settledIn: Long = 0,
    val proxy: Long = 0,
    val proxyReceived: Long = 0,
) {
    val balance: Long get() = income - expense
}

/** 每個人的代墊累計(管理頁用)。 */
data class PersonTotals(
    val personId: Long,
    val name: String,
    /** 累計該付的 */
    val total: Long,
    /** 不收了的 */
    val forgiven: Long,
    /** 已收回的 */
    val settled: Long,
) {
    val outstanding: Long get() = maxOf(0L, total - forgiven - settled)
}

/** 某個人在某一筆代墊裡的明細。 */
data class PersonShareRow(
    val txId: Long,
    val timestamp: Long,
    val note: String,
    val categoryName: String,
    val categoryIcon: String,
    val shareAmount: Long,
    val forgiven: Long,
    val settled: Long,
) {
    val outstanding: Long get() = maxOf(0L, shareAmount - forgiven - settled)
}

data class StatsState(
    val expenseByCategory: List<CategorySum> = emptyList(),
    val totals: MonthTotals = MonthTotals(),
    val trend: List<MonthPoint> = emptyList(),
)
