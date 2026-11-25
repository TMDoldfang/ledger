package com.example.ledger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledger.data.AccountType
import com.example.ledger.data.CategorySum
import com.example.ledger.data.LedgerRepository
import com.example.ledger.data.LedgerTransaction
import com.example.ledger.data.MonthPoint
import com.example.ledger.data.MonthTotals
import com.example.ledger.data.StatsState
import com.example.ledger.data.SystemNames
import com.example.ledger.data.TransactionItem
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

    fun save(t: LedgerTransaction) {
        viewModelScope.launch { repo.save(t) }
    }

    /** 事後補上待收款是誰欠的。 */
    fun setOwedBy(id: Long, names: String) {
        viewModelScope.launch { repo.setOwedBy(id, names) }
    }

    /** 朋友轉來 [amount] 元到 [accountId],沖銷這筆待收款。 */
    fun settle(receivableId: Long, amount: Long, accountId: Long) {
        viewModelScope.launch {
            val ok = repo.settle(receivableId, amount, accountId, System.currentTimeMillis())
            messageChannel.send(if (ok) "已記錄收回 NT$$amount" else "收回失敗")
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

    /** 轉帳與代墊收回不算收支;有「我的份」的支出只算我的份。 */
    private fun totalsOf(list: List<TransactionItem>): MonthTotals {
        val counted = list.filter { it.countsInStats }
        return MonthTotals(
            expense = counted.filter { it.isExpense }.sumOf { it.countedAmount },
            income = counted.filter { !it.isExpense }.sumOf { it.amount },
        )
    }

    private fun monthOf(item: TransactionItem): YearMonth =
        YearMonth.from(item.timestamp.toLocalDate(zone))

    private fun buildStats(selected: YearMonth, list: List<TransactionItem>): StatsState {
        val byMonth = list.groupBy { monthOf(it) }
        val current = byMonth[selected].orEmpty()
        val byCategory = current
            .filter { it.isExpense && it.countsInStats }
            .groupBy { it.categoryId }
            .map { (_, rows) ->
                CategorySum(rows.first().categoryName, rows.first().categoryIcon, rows.sumOf { it.countedAmount })
            }
            .filter { it.total > 0 }
            .sortedByDescending { it.total }
        val trend = (5 downTo 0).map { back ->
            val m = selected.minusMonths(back.toLong())
            val t = totalsOf(byMonth[m].orEmpty())
            MonthPoint(m, t.expense, t.income)
        }
        return StatsState(byCategory, totalsOf(current), trend)
    }
}
