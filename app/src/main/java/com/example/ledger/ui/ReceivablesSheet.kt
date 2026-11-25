@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ledger.data.AccountType
import com.example.ledger.data.ReceivableDetail
import com.example.ledger.util.formatMoney
import com.example.ledger.util.toLocalDate
import java.time.ZoneId

/**
 * 待收款明細:依人分組,每一筆代墊的日期、金額、還欠多少都看得到。
 * 收到錢按「已收到」;還沒指定是誰的那部分,按「指定是誰」補上。不分月份,沒收回就一直在。
 */
@Composable
fun ReceivablesSheet(vm: LedgerViewModel, onDismiss: () -> Unit) {
    val receivables by vm.receivables.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    var settling by remember { mutableStateOf<ReceivableDetail?>(null) }
    var assigning by remember { mutableStateOf<ReceivableDetail?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("待收款", style = MaterialTheme.typography.titleLarge)
            if (receivables.isEmpty()) {
                Text(
                    "目前沒有待收款",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "共 ${formatMoney(receivables.sumOf { it.outstanding })}(不分月份,收回才會消失)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                receivables.byPerson().forEach { group ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            group.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (group.personId == null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(formatMoney(group.total), style = MaterialTheme.typography.titleMedium)
                    }
                    group.rows.forEach { r ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val date = r.timestamp.toLocalDate(zone)
                                    Text(
                                        "${r.categoryIcon} ${r.note.ifBlank { r.categoryName }}  ${date.monthValue}/${date.dayOfMonth}",
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        "實付 ${formatMoney(r.txAmount)} · 原本 ${formatMoney(r.shareAmount)} · 待收 ${formatMoney(r.outstanding)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (r.personId == null) {
                                    OutlinedButton(onClick = { assigning = r }) { Text("指定是誰") }
                                } else {
                                    Button(onClick = { settling = r }) { Text("已收到") }
                                }
                            }
                        }
                    }
                }
                // 還沒指定是誰的部分,也可以直接收錢(例如不想分得那麼細)
                if (receivables.any { it.personId == null }) {
                    Text(
                        "「未指定」的部分請先指定是誰,這樣之後才查得到每個人欠多少。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    settling?.let { r ->
        var amountText by remember(r.txId, r.personId) { mutableStateOf(r.outstanding.toString()) }
        var accountId by remember(r.txId, r.personId) {
            mutableStateOf(
                accounts.firstOrNull { AccountType.from(it.type) == AccountType.BANK }?.id
                    ?: accounts.firstOrNull()?.id
            )
        }
        val amount = amountText.toLongOrNull() ?: 0L
        AlertDialog(
            onDismissRequest = { settling = null },
            title = { Text("${r.personName ?: ""} 轉來了") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("還有 ${formatMoney(r.outstanding)} 沒收回。只收到一部分的話,改金額就好,剩下的會繼續留在待收款。")
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { s -> amountText = s.filter(Char::isDigit).take(9) },
                        label = { Text("收到的金額") },
                        prefix = { Text("NT$ ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text("轉進哪個帳戶", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        accounts.forEach { a ->
                            FilterChip(
                                selected = a.id == accountId,
                                onClick = { accountId = a.id },
                                label = { Text(a.name) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = amount in 1..r.outstanding && accountId != null,
                    onClick = {
                        vm.settle(r.txId, r.personId, amount, accountId!!)
                        settling = null
                    },
                ) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { settling = null }) { Text("取消") } },
        )
    }

    assigning?.let { r ->
        var personId by remember(r.txId) { mutableStateOf<Long?>(null) }
        var amountText by remember(r.txId) { mutableStateOf(r.outstanding.toString()) }
        var showAdd by remember { mutableStateOf(false) }
        val amount = amountText.toLongOrNull() ?: 0L
        AlertDialog(
            onDismissRequest = { assigning = null },
            title = { Text("這 ${formatMoney(r.outstanding)} 是誰欠的?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        people.forEach { p ->
                            FilterChip(
                                selected = personId == p.id,
                                onClick = { personId = p.id },
                                label = { Text(p.name) },
                            )
                        }
                        AssistChip(onClick = { showAdd = true }, label = { Text("＋新增") })
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { s -> amountText = s.filter(Char::isDigit).take(9) },
                        label = { Text("這個人欠的金額") },
                        prefix = { Text("NT$ ") },
                        singleLine = true,
                        supportingText = { Text("只有一部分是他欠的,就改小一點,剩下的可以再指定給別人") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = personId != null && amount in 1..r.outstanding,
                    onClick = {
                        vm.assign(r.txId, personId!!, amount)
                        assigning = null
                    },
                ) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { assigning = null }) { Text("取消") } },
        )
        if (showAdd) {
            AddPersonDialog(
                onDismiss = { showAdd = false },
                onAdd = { name ->
                    vm.addPerson(name) { id -> if (id != null) personId = id }
                    showAdd = false
                },
            )
        }
    }
}
