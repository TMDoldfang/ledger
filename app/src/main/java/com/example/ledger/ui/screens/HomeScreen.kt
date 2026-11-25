package com.example.ledger.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ledger.data.MonthTotals
import com.example.ledger.data.Receivable
import com.example.ledger.reminder.BudgetLogic
import com.example.ledger.settings.AppSettings
import com.example.ledger.data.TransactionItem
import com.example.ledger.ui.LedgerViewModel
import com.example.ledger.ui.MonthSwitcher
import com.example.ledger.ui.ReceivablesSheet
import com.example.ledger.ui.VoiceSheet
import com.example.ledger.ui.theme.expenseColor
import com.example.ledger.ui.theme.incomeColor
import com.example.ledger.util.formatMoney
import com.example.ledger.util.toLocalDate
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DayFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.TRADITIONAL_CHINESE)

@Composable
fun HomeScreen(vm: LedgerViewModel, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val month by vm.month.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val budgetSpent by vm.budgetSpent.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val grouped = remember(items) { items.groupBy { it.timestamp.toLocalDate(zone) } }
    var showVoice by remember { mutableStateOf(false) }
    val receivables by vm.receivables.collectAsStateWithLifecycle()
    var showReceivables by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MonthSwitcher(month, vm::prevMonth, vm::nextMonth)
            SummaryCard(totals)
            BudgetLine(budgetSpent)
            if (receivables.isNotEmpty()) ReceivableBanner(receivables) { showReceivables = true }
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                singleLine = true,
                placeholder = { Text("搜尋備註、分類或帳戶") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (grouped.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (query.isBlank()) "這個月還沒有帳目\n按右下角麥克風說一句,或按 + 手動記" else "找不到符合的帳目",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    grouped.forEach { (date, rows) ->
                        item(key = "day-$date") { DayHeader(date, rows) }
                        items(rows, key = { it.id }) { row ->
                            TransactionRow(row, onClick = { onEdit(row.id) })
                        }
                    }
                }
            }
        }
        // 語音是主要入口(大按鈕),手動新增是小按鈕
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SmallFloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "手動新增帳目")
            }
            FloatingActionButton(onClick = { showVoice = true }) {
                Icon(Icons.Filled.Mic, contentDescription = "語音記帳")
            }
        }
    }

    if (showReceivables) ReceivablesSheet(vm = vm, onDismiss = { showReceivables = false })

    if (showVoice) {
        VoiceSheet(
            vm = vm,
            onDismiss = { showVoice = false },
            onOpenEditor = { draft ->
                vm.setDraft(draft)
                showVoice = false
                onAdd()
            },
        )
    }
}

@Composable
private fun SummaryCard(totals: MonthTotals) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            SummaryCell("支出", formatMoney(totals.expense), expenseColor(), Modifier.weight(1f))
            SummaryCell("收入", formatMoney(totals.income), incomeColor(), Modifier.weight(1f))
            SummaryCell(
                "結餘", formatMoney(totals.balance),
                MaterialTheme.colorScheme.onSurface, Modifier.weight(1f),
            )
        }
    }
}

/** 有開啟「月額上限」並設定金額時,在摘要下面顯示本月用掉幾 %。 */
@Composable
private fun BudgetLine(spent: Long) {
    val context = LocalContext.current
    val limit = AppSettings.budgetLimit(context)
    if (!AppSettings.budgetEnabled(context) || limit <= 0) return
    val percent = BudgetLogic.percent(spent, limit)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "月額上限 ${formatMoney(limit)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "$percent%",
                style = MaterialTheme.typography.labelMedium,
                color = if (percent >= 100) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { (spent.toFloat() / limit).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/** 首頁的待收款提示;點開看清單、補「誰欠我」、標記已收到。 */
@Composable
private fun ReceivableBanner(list: List<Receivable>, onClick: () -> Unit) {
    val missing = list.count { it.owedBy.isNullOrBlank() }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                "待收款 ${formatMoney(list.sumOf { it.outstanding })} · ${list.size} 筆",
                style = MaterialTheme.typography.titleSmall,
            )
            if (missing > 0) {
                Text(
                    "有 $missing 筆還沒填是誰欠的",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun DayHeader(date: LocalDate, rows: List<TransactionItem>) {
    val net = rows.filter { it.countsInStats }.sumOf { if (it.isExpense) -it.countedAmount else it.amount }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            date.format(DayFormatter),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            formatMoney(net),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransactionRow(item: TransactionItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.categoryIcon, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.categoryName, style = MaterialTheme.typography.bodyLarge)
            val sub = when {
                item.isTransfer -> "${item.accountName} → ${item.transferToName ?: ""}"
                else -> listOfNotNull(
                    item.accountName,
                    item.myShare?.let { "實付 ${formatMoney(item.amount)}" },
                    item.note.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
            }
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            // 轉帳、代墊收回只是錢在帳戶間移動,不算收支,用中性色
            item.isTransfer -> Text(
                text = formatMoney(item.amount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.isSettlement -> Text(
                text = "+" + formatMoney(item.amount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Text(
                text = (if (item.isExpense) "-" else "+") + formatMoney(item.countedAmount),
                style = MaterialTheme.typography.titleMedium,
                color = if (item.isExpense) expenseColor() else incomeColor(),
            )
        }
    }
}
