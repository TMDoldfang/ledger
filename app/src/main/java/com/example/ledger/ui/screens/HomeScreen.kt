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
import androidx.compose.material.icons.filled.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
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
import com.example.ledger.data.ReceivableDetail
import com.example.ledger.reminder.BudgetLogic
import com.example.ledger.settings.AppSettings
import com.example.ledger.data.TransactionItem
import com.example.ledger.ui.LedgerViewModel
import com.example.ledger.ui.MonthSwitcher
import com.example.ledger.ui.PendingNav
import com.example.ledger.ui.PendingSheet
import com.example.ledger.ui.ShareInbox
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
    val pending by vm.pending.collectAsStateWithLifecycle()
    var showPending by remember { mutableStateOf(false) }
    var pendingMissingOnly by remember { mutableStateOf(false) }
    // 點了「待補金額」通知:直接打開清單並只顯示待補金額(App 開著或剛被叫起來都一樣)
    val openMissing by PendingNav.openMissing.collectAsStateWithLifecycle()
    LaunchedEffect(openMissing) {
        if (openMissing) {
            pendingMissingOnly = true
            showPending = true
            PendingNav.openMissing.value = false
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ShareInbox.uri.value = uri
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MonthSwitcher(month, vm::prevMonth, vm::nextMonth)
            SummaryCard(totals)
            BudgetLine(budgetSpent)
            if (pending.isNotEmpty()) PendingBanner(pending.size, pending.count { it.amount == null }) { pendingMissingOnly = false; showPending = true }
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
            SmallFloatingActionButton(onClick = {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Filled.Image, contentDescription = "從相簿選刷卡截圖辨識")
            }
            SmallFloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "手動新增帳目")
            }
            FloatingActionButton(onClick = { showVoice = true }) {
                Icon(Icons.Filled.Mic, contentDescription = "語音記帳")
            }
        }
    }

    if (showReceivables) ReceivablesSheet(vm = vm, onDismiss = { showReceivables = false })

    if (showPending) {
        PendingSheet(
            vm = vm,
            initialMissingOnly = pendingMissingOnly,
            onDismiss = { showPending = false },
            onOpenEditor = { draft ->
                vm.setDraft(draft)
                showPending = false
                onAdd()
            },
        )
    }

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
        // 代墊的說明只用一行小字,不佔版面:支出含代墊,收回的錢另外列在結餘裡
        if (totals.proxy > 0 || totals.settledIn > 0) {
            Text(
                listOfNotNull(
                    if (totals.proxy > 0) "支出含代墊 ${formatMoney(totals.proxy)}" else null,
                    if (totals.settledIn > 0) "本月收回 +${formatMoney(totals.settledIn)}(已含在收入)" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
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

/** 首頁的待確認提示:自動偵測到、還沒記成帳目的刷卡。 */
@Composable
private fun PendingBanner(count: Int, noAmount: Int, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("待確認刷卡 $count 筆", style = MaterialTheme.typography.titleSmall)
            if (noAmount > 0) {
                Text(
                    "其中 $noAmount 筆待補金額",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** 首頁的待收款提示;點開看清單、補「誰欠我」、標記已收到。 */
@Composable
private fun ReceivableBanner(list: List<ReceivableDetail>, onClick: () -> Unit) {
    val missing = list.count { it.personId == null }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                "待收款 ${formatMoney(list.sumOf { it.outstanding })} · ${list.map { it.txId }.distinct().size} 筆",
                style = MaterialTheme.typography.titleSmall,
            )
            if (missing > 0) {
                Text(
                    "有 $missing 筆還沒指定是誰欠的",
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
    // 當天的錢進出:支出算實付全額,代墊收回算進來,轉帳不算
    val net = rows.filter { !it.isTransfer }.sumOf { if (it.isExpense) -it.amount else it.amount }
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
                    item.myShare?.let { "我的 ${formatMoney(item.burden)}" },
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
                text = (if (item.isExpense) "-" else "+") + formatMoney(item.amount),
                style = MaterialTheme.typography.titleMedium,
                color = if (item.isExpense) expenseColor() else incomeColor(),
            )
        }
    }
}
