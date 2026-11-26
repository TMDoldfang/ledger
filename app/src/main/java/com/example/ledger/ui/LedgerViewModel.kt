package com.example.ledger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledger.data.AccountType
import com.example.ledger.data.CategorySum
import com.example.ledger.data.LedgerRepository
import com.example.ledger.data.LedgerTransaction
import com.example.ledger.data.MonthPoint
import com.example.ledger.data.MonthTotals
import com.example.ledger.data.PersonShareRow
import com.example.ledger.data.PersonTotals
import com.example.ledger.data.StatsMath
import com.example.ledger.data.Share
import com.example.ledger.data.ShareInput
import com.example.ledger.data.StatsState
import com.example.ledger.data.SystemNames
import com.example.ledger.data.TransactionItem
import com.example.ledger.util.formatMoney
import com.example.ledger.util.startMillis
import com.example.ledger.voice.TransactionDraft
import com.example.ledger.util.toLocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(private val repo: LedgerRepository) : ViewModel() {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val whileSubscribed = SharingStarted.WhileSubscribed(5_000)

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    /** 一般分類(不含系統用的「轉帳」「代墊收回」,它們不該出現在選單裡)。 */
    val categories = repo.categories
        .map { list -> list.filter { it.name !in SystemNames.HIDDEN_CATEGORIES } }
        .stateIn(viewModelScope, whileSubscribed, emptyList())
    val accounts = repo.accounts.stateIn(viewModelScope, whileSubscribed, emptyList())

    /** 代墊對象。 */
    val people = repo.people.stateIn(viewModelScope, whileSubscribed, emptyList())

    /** 每個人的代墊累計(管理頁)。 */
    val peopleTotals = repo.peopleTotals.stateIn(viewModelScope, whileSubscribed, emptyList())

    fun personRows(personId: Long): kotlinx.coroutines.flow.Flow<List<PersonShareRow>> = repo.personRows(personId)

    /** 待收款(朋友還沒轉給我的部分)。 */
    val receivables = repo.receivables.stateIn(viewModelScope, whileSubscribed, emptyList())

    private val monthItems: StateFlow<List<TransactionItem>> = _month
        .flatMapLatest { m -> repo.items(m.startMillis(zone), m.plusMonths(1).startMillis(zone)) }
        .stateIn(viewModelScope, whileSubscribed, emptyList())

    /** 帳目列表(套用搜尋)。 */
    val items: StateFlow<List<TransactionItem>> = combine(monthItems, _query) { list, q ->
        val key = q.trim()
        if (key.isEmpty()) list
        else list.filter {
            it.note.contains(key, ignoreCase = true) ||
                it.categoryName.contains(key, ignoreCase = true) ||
                it.accountName.contains(key, ignoreCase = true)
        }
    }.stateIn(viewModelScope, whileSubscribed, emptyList())

    /** 當月收支(不受搜尋影響)。 */
    /** 月額上限用的「已花」(目前顯示的那個月),和提醒用同一套算法。 */
    val budgetSpent: StateFlow<Long> = _month
        .flatMapLatest { m -> repo.observeBudgetSpent(m.startMillis(zone), m.plusMonths(1).startMillis(zone)) }
        .stateIn(viewModelScope, whileSubscribed, 0L)

    val totals: StateFlow<MonthTotals> = monthItems
        .map { totalsOf(it) }
        .stateIn(viewModelScope, whileSubscribed, MonthTotals())

    /** 統計頁:當月分類支出 + 近 6 個月趨勢。 */
    val stats: StateFlow<StatsState> = _month
        .flatMapLatest { m ->
            repo.items(m.minusMonths(5).startMillis(zone), m.plusMonths(1).startMillis(zone))
                .map { buildStats(m, it) }
        }
        .stateIn(viewModelScope, whileSubscribed, StatsState())

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    fun prevMonth() { _month.value = _month.value.minusMonths(1) }
    fun nextMonth() { _month.value = _month.value.plusMonths(1) }
    fun setQuery(q: String) { _query.value = q }

    /** 語音解析後要帶到編輯頁的草稿;編輯頁開啟時取走一次。 */
    private var pendingDraft: TransactionDraft? = null
    fun setDraft(d: TransactionDraft) { pendingDraft = d }
    fun consumeDraft(): TransactionDraft? = pendingDraft.also { pendingDraft = null }

    /** 在畫面下方跳出一則提示。 */
    fun say(text: String) {
        viewModelScope.launch { messageChannel.send(text) }
    }

    suspend fun load(id: Long): LedgerTransaction? = repo.getTransaction(id)

    fun save(t: LedgerTransaction, shares: List<ShareInput> = emptyList(), pendingId: Long? = null) {
        viewModelScope.launch { repo.save(t, shares, pendingId) }
    }

    /** 自動偵測到、等你確認的刷卡通知。 */
    val pending = repo.pending.stateIn(viewModelScope, whileSubscribed, emptyList())

    /** 把截圖辨識出的金額補到待確認的那一筆。 */
    fun fillPendingAmount(id: Long, amount: Long) {
        viewModelScope.launch {
            repo.fillPendingAmount(id, amount)
            messageChannel.send("已補上金額 NT$$amount")
        }
    }

    fun deletePending(id: Long) {
        viewModelScope.launch { repo.deletePending(id) }
    }

    suspend fun loadShares(id: Long): List<Share> = repo.sharesOf(id)

    /** 新增代墊對象;[onDone] 收到這個人的 id(名字空白時為 null)。 */
    fun addPerson(name: String, onDone: (Long?) -> Unit = {}) {
        viewModelScope.launch { onDone(repo.addPerson(name)) }
    }

    fun deletePerson(id: Long) {
        viewModelScope.launch {
            if (!repo.deletePerson(id)) messageChannel.send("這個人已有代墊紀錄,無法刪除")
        }
    }

    /** 朋友轉來 [amount] 元到 [accountId],沖銷這個人在這筆代墊的待收款。 */
    fun settle(txId: Long, personId: Long?, amount: Long, accountId: Long) {
        viewModelScope.launch {
            val ok = repo.settle(txId, personId, amount, accountId, System.currentTimeMillis())
            messageChannel.send(if (ok) "已記錄收回 NT$$amount" else "收回失敗")
        }
    }

    /** 「不收了」:這一部分改算成我自己的支出。 */
    fun forgive(txId: Long, personId: Long?, amount: Long) {
        viewModelScope.launch {
            val done = repo.forgive(txId, personId, amount)
            messageChannel.send(if (done > 0) "已標記不收了 NT$$done,改算成你的支出" else "沒有可處理的金額")
        }
    }

    /** 事後指定「未指定」的那一部分是誰欠的。 */
    fun assign(txId: Long, personId: Long, amount: Long) {
        viewModelScope.launch {
            if (!repo.assignShare(txId, personId, amount)) messageChannel.send("指定失敗")
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteTransaction(id) }
    }

    fun addAccount(name: String, type: AccountType, initialBalance: Long) {
        viewModelScope.launch { repo.addAccount(name, type, initialBalance) }
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch {
            if (!repo.deleteAccount(id)) messageChannel.send("這個帳戶已有帳目,無法刪除")
        }
    }

    fun addCategory(name: String, icon: String, isExpense: Boolean) {
        viewModelScope.launch { repo.addCategory(name, icon, isExpense) }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            if (!repo.deleteCategory(id)) messageChannel.send("這個分類已有帳目,無法刪除")
        }
    }

    /** 支出算實付全額(含代墊);代墊收回另算,見 StatsMath。 */
    private fun totalsOf(list: List<TransactionItem>): MonthTotals = StatsMath.totals(list)

    private fun monthOf(item: TransactionItem): YearMonth =
        YearMonth.from(item.timestamp.toLocalDate(zone))

    private fun buildStats(selected: YearMonth, list: List<TransactionItem>): StatsState {
        val byMonth = list.groupBy { monthOf(it) }
        val current = byMonth[selected].orEmpty()
        val byCategory = StatsMath.categorySums(current) { formatMoney(it) }
        val trend = (5 downTo 0).map { back ->
            val m = selected.minusMonths(back.toLong())
            val t = totalsOf(byMonth[m].orEmpty())
            MonthPoint(m, t.expense, t.income)
        }
        return StatsState(byCategory, totalsOf(current), trend)
    }
}
