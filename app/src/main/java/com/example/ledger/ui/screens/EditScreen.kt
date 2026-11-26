@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.ledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ledger.data.LedgerTransaction
import com.example.ledger.data.ShareInput
import com.example.ledger.data.ShareMath
import com.example.ledger.ui.AddPersonDialog
import com.example.ledger.ui.LedgerViewModel
import com.example.ledger.ui.ScreenHeader
import com.example.ledger.util.formatMoney
import com.example.ledger.util.toLocalDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.TRADITIONAL_CHINESE)

private const val KIND_EXPENSE = "expense"
private const val KIND_INCOME = "income"
private const val KIND_TRANSFER = "transfer"

/** 選到的代墊對象與各自金額,存成 "id:金額,id:金額"(rememberSaveable 只能存簡單型別)。 */
private fun decodePicked(text: String): LinkedHashMap<Long, String> {
    val out = LinkedHashMap<Long, String>()
    text.split(',').filter { it.contains(':') }.forEach {
        val (id, amt) = it.split(':', limit = 2)
        id.toLongOrNull()?.let { key -> out[key] = amt }
    }
    return out
}

private fun encodePicked(map: Map<Long, String>): String =
    map.entries.joinToString(",") { "${it.key}:${it.value}" }

@Composable
fun EditScreen(vm: LedgerViewModel, id: Long, onDone: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()

    // 從語音記帳帶過來的草稿(只有「新增」才會有,取走一次)
    val draft = remember { if (id == 0L) vm.consumeDraft() else null }

    var kind by rememberSaveable {
        mutableStateOf(
            when {
                draft?.isTransfer == true -> KIND_TRANSFER
                draft?.isExpense == false -> KIND_INCOME
                else -> KIND_EXPENSE
            }
        )
    }
    var amountText by rememberSaveable { mutableStateOf(draft?.amount?.toString() ?: "") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(draft?.categoryId) }
    var accountId by rememberSaveable { mutableStateOf<Long?>(draft?.accountId) }
    var toAccountId by rememberSaveable { mutableStateOf<Long?>(draft?.transferToAccountId) }
    var shareText by rememberSaveable { mutableStateOf(draft?.myShare?.toString() ?: "") }
    // 代墊:開關 + 選到的人與各自金額
    var shared by rememberSaveable { mutableStateOf(draft?.myShare != null) }
    var peopleText by rememberSaveable { mutableStateOf("") }
    var showAddPerson by remember { mutableStateOf(false) }
    // 編輯「代墊收回」時要原樣保留它指向的那筆支出
    var settlesId by rememberSaveable { mutableStateOf<Long?>(null) }
    var settlesPersonId by rememberSaveable { mutableStateOf<Long?>(null) }
    var dateText by rememberSaveable { mutableStateOf((draft?.date ?: LocalDate.now()).toString()) }
    var timeText by rememberSaveable { mutableStateOf(LocalTime.now().withSecond(0).withNano(0).toString()) }
    var note by rememberSaveable { mutableStateOf(draft?.note ?: "") }
    var loaded by rememberSaveable { mutableStateOf(id == 0L) }
    var showPicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    // 編輯既有帳目:載入一次
    LaunchedEffect(id) {
        if (id != 0L && !loaded) {
            vm.load(id)?.let { t ->
                kind = when {
                    t.transferToAccountId != null -> KIND_TRANSFER
                    t.isExpense -> KIND_EXPENSE
                    else -> KIND_INCOME
                }
                amountText = t.amount.toString()
                categoryId = t.categoryId
                accountId = t.accountId
                toAccountId = t.transferToAccountId
                shareText = t.myShare?.toString() ?: ""
                shared = t.myShare != null
                peopleText = encodePicked(
                    vm.loadShares(id).associate { it.personId to it.amount.toString() }
                )
                settlesId = t.settlesId
                settlesPersonId = t.settlesPersonId
                dateText = t.timestamp.toLocalDate(zone).toString()
                timeText = Instant.ofEpochMilli(t.timestamp).atZone(zone).toLocalTime()
                    .withSecond(0).withNano(0).toString()
                note = t.note
            }
            loaded = true
        }
    }
    // 新增:直接叫出數字鍵盤,讓「記一筆」少一次點擊
    LaunchedEffect(Unit) {
        if (id == 0L && draft == null) runCatching { focus.requestFocus() }
    }

    val isTransfer = kind == KIND_TRANSFER
    val isSettlement = settlesId != null
    val isExpense = kind != KIND_INCOME

    val visibleCategories = categories.filter { it.isExpense == isExpense }
    val selectedCategory = categoryId?.takeIf { cid -> visibleCategories.any { it.id == cid } }
        ?: visibleCategories.firstOrNull()?.id
    val selectedAccount = accountId?.takeIf { aid -> accounts.any { it.id == aid } }
        ?: accounts.firstOrNull()?.id
    val selectedTo = toAccountId?.takeIf { tid -> accounts.any { it.id == tid } && tid != selectedAccount }
        ?: accounts.firstOrNull { it.id != selectedAccount }?.id
    val amount = amountText.toLongOrNull() ?: 0L
    val share = shareText.toLongOrNull()
    val sharedActive = shared && kind == KIND_EXPENSE && !isSettlement
    val picked = decodePicked(peopleText)
    val pickedIds = picked.keys.filter { id -> people.any { it.id == id } }
    val pickedSum = pickedIds.sumOf { picked[it]?.toLongOrNull() ?: 0L }
    // 我的份:有填就用填的;沒填但選了人,就是實付扣掉大家的份
    val finalShare: Long? = when {
        !sharedActive -> null
        share != null -> share
        pickedIds.isNotEmpty() -> (amount - pickedSum).coerceAtLeast(0L)
        else -> null
    }
    val owed = if (finalShare != null && finalShare < amount) amount - finalShare else 0L
    val unassigned = ShareMath.unassigned(amount, finalShare, pickedSum)
    val shareOk = !sharedActive || pickedSum <= (if (finalShare != null) amount - finalShare else amount)

    /** 把要分的錢平均分給目前選到的人(沒填我的份時,連我自己一起平分)。 */
    fun evenSplit(ids: List<Long>): String {
        val parts = if (share != null) ShareMath.split((amount - share).coerceAtLeast(0L), ids.size)
        else ShareMath.splitWithMe(amount, ids.size)
        return encodePicked(ids.zip(parts.map { it.toString() }).toMap())
    }

    val canSave = amount > 0 && selectedAccount != null && shareOk && when {
        isSettlement -> true
        isTransfer -> selectedTo != null && selectedTo != selectedAccount
        else -> selectedCategory != null
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            title = if (id == 0L) "新增帳目" else "編輯帳目",
            onBack = onDone,
            actions = {
                if (id != 0L) {
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "刪除")
                    }
                }
            },
        )

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isSettlement) {
                Text(
                    "這是一筆「代墊收回」:朋友轉來的錢。它不算收入,只會增加帳戶餘額並沖銷待收款。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == KIND_EXPENSE, onClick = { kind = KIND_EXPENSE }, label = { Text("支出") })
                    FilterChip(selected = kind == KIND_INCOME, onClick = { kind = KIND_INCOME }, label = { Text("收入") })
                    FilterChip(selected = kind == KIND_TRANSFER, onClick = { kind = KIND_TRANSFER }, label = { Text("轉帳") })
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { s -> amountText = s.filter(Char::isDigit).take(9) },
                label = { Text(if (isExpense && !isTransfer) "實付金額" else "金額") },
                prefix = { Text("NT$ ") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )

            if (!isTransfer && !isSettlement) {
                Text("分類", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    visibleCategories.forEach { c ->
                        FilterChip(
                            selected = c.id == selectedCategory,
                            onClick = { categoryId = c.id },
                            label = { Text("${c.icon} ${c.name}") },
                        )
                    }
                }
            }

            Text(if (isTransfer) "從(付款帳戶)" else "帳戶", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { a ->
                    FilterChip(
                        selected = a.id == selectedAccount,
                        onClick = { accountId = a.id },
                        label = { Text(a.name) },
                    )
                }
            }

            if (isTransfer) {
                Text("轉到", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.filter { it.id != selectedAccount }.forEach { a ->
                        FilterChip(
                            selected = a.id == selectedTo,
                            onClick = { toAccountId = a.id },
                            label = { Text(a.name) },
                        )
                    }
                }
                Text(
                    "轉帳不算支出或收入。例如:繳信用卡費(銀行 → 信用卡)、投資 0050(銀行 → 投資)。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 代墊:有朋友分擔時打開,選是誰、各欠多少(都可以之後再補)
            if (kind == KIND_EXPENSE && !isSettlement) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("有人分擔(代墊)", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Switch(checked = shared, onCheckedChange = { shared = it })
                }
            }
            if (sharedActive) {
                OutlinedTextField(
                    value = shareText,
                    onValueChange = { s -> shareText = s.filter(Char::isDigit).take(9) },
                    label = { Text("我的份(選填)") },
                    prefix = { Text("NT$ ") },
                    singleLine = true,
                    supportingText = { Text("沒填的話,會用「實付 − 大家的份」算我的份") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("誰欠我(可多選,也可以之後再補)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEach { p ->
                        FilterChip(
                            selected = p.id in pickedIds,
                            onClick = {
                                val ids = if (p.id in pickedIds) pickedIds - p.id else pickedIds + p.id
                                peopleText = evenSplit(ids)
                            },
                            label = { Text(p.name) },
                        )
                    }
                    AssistChip(onClick = { showAddPerson = true }, label = { Text("＋新增") })
                }
                pickedIds.forEach { pid ->
                    val name = people.firstOrNull { it.id == pid }?.name ?: return@forEach
                    OutlinedTextField(
                        value = picked[pid] ?: "",
                        onValueChange = { v ->
                            peopleText = encodePicked(picked.toMutableMap().also { it[pid] = v.filter(Char::isDigit).take(9) })
                        },
                        label = { Text("$name 欠") },
                        prefix = { Text("NT$ ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (pickedIds.size >= 2) {
                    TextButton(onClick = { peopleText = evenSplit(pickedIds) }) { Text("重新平均分攤") }
                }
                if (!shareOk) {
                    Text(
                        "大家的份加起來超過可分攤的金額了",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (finalShare != null) {
                    Text(
                        "我的份 ${formatMoney(finalShare)}(統計只算這個)" +
                            if (unassigned > 0) ",還有 ${formatMoney(unassigned)} 沒指定是誰,30 分鐘後會提醒你補" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "選了人或填了我的份,才會算成待收款",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text("日期", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { showPicker = true }) {
                Text(LocalDate.parse(dateText).format(DateFormatter))
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(60) },
                label = { Text("備註(選填)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                enabled = canSave,
                onClick = {
                    val ts = LocalDateTime.of(LocalDate.parse(dateText), LocalTime.parse(timeText))
                        .atZone(zone).toInstant().toEpochMilli()
                    vm.save(
                        LedgerTransaction(
                            id = id,
                            amount = amount,
                            isExpense = isExpense,
                            // 轉帳、代墊收回的分類由系統決定(Repository 會處理),這裡保留原值
                            categoryId = if (isTransfer || isSettlement) (categoryId ?: 0L) else selectedCategory!!,
                            accountId = selectedAccount!!,
                            timestamp = ts,
                            note = note.trim(),
                            myShare = finalShare,
                            transferToAccountId = if (isTransfer) selectedTo else null,
                            settlesId = settlesId,
                            settlesPersonId = settlesPersonId,
                        ),
                        shares = if (sharedActive) {
                            pickedIds.map { ShareInput(it, picked[it]?.toLongOrNull() ?: 0L) }
                        } else emptyList(),
                        pendingId = draft?.pendingId,
                    )
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("儲存") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddPerson) {
        AddPersonDialog(
            onDismiss = { showAddPerson = false },
            onAdd = { name ->
                showAddPerson = false
                vm.addPerson(name) { newId ->
                    // 新增的人直接選起來,並重新平分
                    if (newId != null) {
                        val cur = decodePicked(peopleText).keys.toList()
                        if (newId !in cur) peopleText = evenSplit(cur + newId)
                    }
                }
            },
        )
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(dateText)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        dateText = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showPicker = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("刪除這筆帳目?") },
            text = { Text("刪除後無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.delete(id)
                    onDone()
                }) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } },
        )
    }
}
