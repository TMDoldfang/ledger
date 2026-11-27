package com.example.ledger.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LedgerRepository(private val db: LedgerDatabase) {
    private val transactions = db.transactionDao()
    private val accountDao = db.accountDao()
    private val categoryDao = db.categoryDao()
    private val personDao = db.personDao()
    private val shareDao = db.shareDao()
    private val pendingDao = db.pendingDao()

    val accounts: Flow<List<AccountWithBalance>> = accountDao.observeWithBalance()
    val categories: Flow<List<Category>> = categoryDao.observeAll()

    fun items(start: Long, end: Long): Flow<List<TransactionItem>> =
        transactions.observeItems(start, end)

    suspend fun getTransaction(id: Long): LedgerTransaction? = transactions.getById(id)

    /** 待收款(不分月份):每個人、每筆代墊還沒收回的金額;personId 為 null 是還沒指定是誰的部分。 */
    val receivables: Flow<List<ReceivableDetail>> = transactions.observeReceivables()

    val people: Flow<List<Person>> = personDao.observeAll()
    val peopleTotals: Flow<List<PersonTotals>> = personDao.observeTotals()
    fun personRows(personId: Long): Flow<List<PersonShareRow>> = transactions.observePersonRows(personId)

    // 自動偵測的待確認
    val pending: Flow<List<PendingEntry>> = pendingDao.observeAll()
    suspend fun addPending(card: String, amount: Long?, timestamp: Long): Long =
        pendingDao.insert(PendingEntry(timestamp = timestamp, card = card, amount = amount))
    suspend fun fillPendingAmount(id: Long, amount: Long) = pendingDao.setAmount(id, amount)
    suspend fun deletePending(id: Long) = pendingDao.deleteById(id)
    suspend fun pendingAwaitingAmount(since: Long): List<PendingEntry> = pendingDao.awaitingAmount(since)
    suspend fun pendingCount(): Int = pendingDao.count()
    /** 還沒填金額的筆數(不分日期)。 */
    suspend fun pendingMissingAmountCount(): Int = pendingDao.countMissingAmount()

    suspend fun sharesOf(transactionId: Long): List<Share> = shareDao.forTransaction(transactionId)

    /** 新增代墊對象;名字已存在就直接回傳舊的 id,空白回傳 null。 */
    suspend fun addPerson(name: String): Long? {
        val n = name.trim().take(20)
        if (n.isEmpty()) return null
        val id = personDao.insert(Person(name = n))
        return if (id > 0) id else personDao.findId(n)
    }

    /** 這個人有分攤或收回紀錄就不能刪,回傳 false。 */
    suspend fun deletePerson(id: Long): Boolean {
        if (personDao.usageCount(id) > 0) return false
        personDao.deleteById(id)
        return true
    }

    suspend fun save(t: LedgerTransaction, shares: List<ShareInput> = emptyList(), pendingId: Long? = null) {
        val clean = when {
            // 轉帳:固定歸「轉帳」分類、算付款方的支出側,不能同時有「我的份」
            t.transferToAccountId != null -> t.copy(
                categoryId = categoryDao.findId(SystemNames.TRANSFER, true) ?: t.categoryId,
                isExpense = true,
                myShare = null,
                owedBy = null,
                settlesId = null,
                settlesPersonId = null,
            )
            // 沒有真的分攤(我的份 ≥ 實付,或收入)就不要留「我的份」
            t.myShare != null && (!t.isExpense || t.myShare >= t.amount) -> t.copy(myShare = null, owedBy = null)
            else -> t.copy(owedBy = null)
        }
        val owed = if (clean.myShare != null) clean.amount - clean.myShare else 0L
        val normalized = ShareMath.normalize(shares, owed)
        val id = db.withTransaction {
            val savedId = if (clean.id == 0L) transactions.insert(clean) else clean.id.also { transactions.update(clean) }
            if (pendingId != null) pendingDao.deleteById(pendingId) // 從待確認轉成正式帳目
            // 重寫分攤前,先記住每個人「不收了」的金額,存完要還原(不能因為改個備註就變回待收)
            val oldForgiven = shareDao.forTransaction(savedId).associate { it.personId to it.forgiven }
            shareDao.deleteForTransaction(savedId)
            if (normalized.isNotEmpty()) {
                shareDao.insertAll(
                    normalized.map {
                        Share(
                            transactionId = savedId, personId = it.personId, amount = it.amount,
                            forgiven = minOf(oldForgiven[it.personId] ?: 0L, it.amount),
                        )
                    }
                )
            }
            savedId
        }
        // 還有一部分沒指定是誰:請外面排一個提醒(30 分鐘後還沒補就通知)
        if (ShareMath.unassigned(clean.amount, clean.myShare, normalized.sumOf { it.amount }) > 0) {
            onNeedsOwedBy?.invoke(id)
        }
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

    /** 這段時間內「轉入投資帳戶」的筆數。 */
    suspend fun investTransferCount(start: Long, end: Long): Int =
        transactions.countInvestTransfers(start, end)

    /** 由 LedgerApp 設定:新增或修改了還沒填「誰欠我」的代墊時呼叫,參數是帳目 id。 */
    var onNeedsOwedBy: ((Long) -> Unit)? = null

    /**
     * 朋友轉錢給我:記一筆「代墊收回」收入到指定帳戶,沖銷這個人(personId 為 null = 未指定的部分)在這筆代墊的待收款。
     * 它不算收入(統計不會多出一筆收入),但帳戶餘額會增加。
     */
    suspend fun settle(txId: Long, personId: Long?, amount: Long, accountId: Long, timestamp: Long): Boolean {
        val original = transactions.getById(txId) ?: return false
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
                settlesId = txId,
                settlesPersonId = personId,
            )
        )
        return true
    }

    /**
     * 「不收了」:這一部分不再是待收款,改算成我自己的負擔。回傳實際處理的金額(0 = 沒東西可處理)。
     * 某個人的:記在他那一列的 forgiven(保留「他欠過多少」的紀錄);未指定的:直接加到我的份。
     */
    suspend fun forgive(txId: Long, personId: Long?, amount: Long): Long = db.withTransaction {
        if (amount <= 0) return@withTransaction 0L
        val t = transactions.getById(txId) ?: return@withTransaction 0L
        if (personId != null) {
            val row = shareDao.forTransaction(txId).firstOrNull { it.personId == personId } ?: return@withTransaction 0L
            val outstanding = row.amount - row.forgiven - transactions.settledBy(txId, personId)
            val give = minOf(amount, outstanding)
            if (give <= 0) return@withTransaction 0L
            shareDao.addForgiven(row.id, give)
            give
        } else {
            val mine = t.myShare ?: return@withTransaction 0L
            val assigned = shareDao.forTransaction(txId).sumOf { it.amount }
            val outstanding = ShareMath.unassigned(t.amount, mine, assigned) - transactions.settledUnassigned(txId)
            val give = minOf(amount, outstanding)
            if (give <= 0) return@withTransaction 0L
            transactions.update(t.copy(myShare = mine + give))
            give
        }
    }

    /** 把「還沒指定是誰」的金額分一部分給某個人(最多到未指定的餘額)。 */
    suspend fun assignShare(txId: Long, personId: Long, amount: Long): Boolean = db.withTransaction {
        val t = transactions.getById(txId) ?: return@withTransaction false
        val existing = shareDao.forTransaction(txId)
        val left = ShareMath.unassigned(t.amount, t.myShare, existing.sumOf { it.amount })
        val give = minOf(amount, left)
        if (give <= 0) return@withTransaction false
        val merged = ShareMath.normalize(
            existing.map { ShareInput(it.personId, it.amount) } + ShareInput(personId, give),
            t.amount - (t.myShare ?: t.amount),
        )
        val oldForgiven = existing.associate { it.personId to it.forgiven }
        shareDao.deleteForTransaction(txId)
        shareDao.insertAll(
            merged.map {
                Share(
                    transactionId = txId, personId = it.personId, amount = it.amount,
                    forgiven = minOf(oldForgiven[it.personId] ?: 0L, it.amount),
                )
            }
        )
        true
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
