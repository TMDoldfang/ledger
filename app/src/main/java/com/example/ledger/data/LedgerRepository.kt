package com.example.ledger.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LedgerRepository(private val db: LedgerDatabase) {
    private val transactions = db.transactionDao()
    private val accountDao = db.accountDao()
    private val categoryDao = db.categoryDao()

    val accounts: Flow<List<AccountWithBalance>> = accountDao.observeWithBalance()
    val categories: Flow<List<Category>> = categoryDao.observeAll()

    fun items(start: Long, end: Long): Flow<List<TransactionItem>> =
        transactions.observeItems(start, end)

    suspend fun getTransaction(id: Long): LedgerTransaction? = transactions.getById(id)

    val receivables: Flow<List<Receivable>> = transactions.observeReceivables()

    suspend fun save(t: LedgerTransaction) {
        val clean = when {
            // 轉帳:固定歸「轉帳」分類、算付款方的支出側,不能同時有「我的份」
            t.transferToAccountId != null -> t.copy(
                categoryId = categoryDao.findId(SystemNames.TRANSFER, true) ?: t.categoryId,
                isExpense = true,
                myShare = null,
                owedBy = null,
                settlesId = null,
            )
            // 沒有真的分攤(我的份 ≥ 實付,或收入)就不要留「我的份」與「誰欠我」
            t.myShare != null && (!t.isExpense || t.myShare >= t.amount) -> t.copy(myShare = null, owedBy = null)
            t.myShare == null -> t.copy(owedBy = null)
            else -> t.copy(owedBy = t.owedBy?.trim()?.ifEmpty { null })
        }
        val id = if (clean.id == 0L) transactions.insert(clean) else clean.id.also { transactions.update(clean) }
        // 有待收款、卻還沒填「誰欠我」:請外面排一個提醒(30 分鐘後還沒補就通知)
        if (clean.myShare != null && clean.owedBy == null) onNeedsOwedBy?.invoke(id)
        // 存完之後讓外面做檢查(例如月額上限);檢查失敗不能影響已存好的帳目
        runCatching { onSaved?.invoke() }
    }

    /** 月額上限用的「已花」(實付全額 + 轉到投資 − 已收回的代墊);不會小於 0。 */
    suspend fun budgetSpent(start: Long, end: Long): Long =
        transactions.budgetSpent(start, end).coerceAtLeast(0L)

    fun observeBudgetSpent(start: Long, end: Long): Flow<Long> =
        transactions.observeBudgetSpent(start, end).map { it.coerceAtLeast(0L) }

    /** 由 LedgerApp 設定:每次儲存帳目後呼叫。 */
    var onSaved: (suspend () -> Unit)? = null

    /** 這段時間內有沒有記過投資(轉入投資帳戶)。 */
    suspend fun hasInvestTransfer(start: Long, end: Long): Boolean =
        transactions.countInvestTransfers(start, end) > 0

    /** 事後補上「誰欠我」。 */
    suspend fun setOwedBy(id: Long, names: String) {
        transactions.setOwedBy(id, names.trim().ifEmpty { null })
    }

    /** 由 LedgerApp 設定:新增或修改了還沒填「誰欠我」的代墊時呼叫,參數是帳目 id。 */
    var onNeedsOwedBy: ((Long) -> Unit)? = null

    /**
     * 朋友轉錢給我:記一筆「代墊收回」收入到指定帳戶,沖銷待收款。
     * 它不算收入(統計不會多出一筆收入),但帳戶餘額會增加。
     */
    suspend fun settle(receivableId: Long, amount: Long, accountId: Long, timestamp: Long): Boolean {
        val original = transactions.getById(receivableId) ?: return false
        val categoryId = categoryDao.findId(SystemNames.SETTLEMENT, false) ?: return false
        if (amount <= 0) return false
        transactions.insert(
            LedgerTransaction(
                amount = amount,
                isExpense = false,
                categoryId = categoryId,
                accountId = accountId,
                timestamp = timestamp,
                note = original.note.ifBlank { "代墊收回" },
                settlesId = receivableId,
            )
        )
        return true
    }

    suspend fun deleteTransaction(id: Long) = transactions.deleteById(id)

    suspend fun addAccount(name: String, type: AccountType, initialBalance: Long) {
        accountDao.insert(Account(name = name, type = type.name, initialBalance = initialBalance))
    }

    /** 已有帳目的帳戶不能刪,回傳 false。 */
    suspend fun deleteAccount(id: Long): Boolean {
        if (transactions.countByAccount(id) > 0) return false
        accountDao.deleteById(id)
        return true
    }

    suspend fun addCategory(name: String, icon: String, isExpense: Boolean) {
        categoryDao.insert(Category(name = name, icon = icon, isExpense = isExpense))
    }

    /** 已有帳目的分類不能刪,回傳 false。 */
    suspend fun deleteCategory(id: Long): Boolean {
        if (transactions.countByCategory(id) > 0) return false
        categoryDao.deleteById(id)
        return true
    }

    /** 第一次開啟時放入預設分類與帳戶。 */
    suspend fun seedIfEmpty() {
        if (categoryDao.count() == 0) {
            val expense = listOf(
                "餐飲" to "🍜", "交通" to "🚌", "購物" to "🛍️", "娛樂" to "🎮",
                "居住" to "🏠", "醫療" to "💊", "學習" to "📚", "其他" to "📦",
            ).map { (name, icon) -> Category(name = name, icon = icon, isExpense = true) }
            val income = listOf(
                "薪資" to "💰", "獎金" to "🎁", "投資" to "📈", "其他" to "➕",
            ).map { (name, icon) -> Category(name = name, icon = icon, isExpense = false) }
            categoryDao.insertAll(expense + income)
        }
        if (accountDao.count() == 0) {
            accountDao.insertAll(
                listOf(
                    Account(name = "現金", type = AccountType.CASH.name),
                    Account(name = "銀行", type = AccountType.BANK.name),
                    Account(name = "信用卡", type = AccountType.CARD.name),
                    Account(name = "電子支付", type = AccountType.EPAY.name),
                )
            )
        }
        ensureBuiltIns()
    }

    /** 每次啟動都檢查:新版加的內建分類與帳戶,舊資料庫也會補上。 */
    private suspend fun ensureBuiltIns() {
        ensureCategory(SystemNames.TRANSFER, "🔁", isExpense = true)
        ensureCategory(SystemNames.SETTLEMENT, "🤝", isExpense = false)
        ensureCategory(SystemNames.CASH_OUT, "🏧", isExpense = true)
        if (accountDao.countByName(SystemNames.INVEST_ACCOUNT) == 0) {
            accountDao.insert(Account(name = SystemNames.INVEST_ACCOUNT, type = AccountType.INVEST.name))
        }
    }

    private suspend fun ensureCategory(name: String, icon: String, isExpense: Boolean) {
        if (categoryDao.findId(name, isExpense) == null) {
            categoryDao.insert(Category(name = name, icon = icon, isExpense = isExpense))
        }
    }
}
