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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.example.ledger.data.Receivable
import com.example.ledger.util.formatMoney
import com.example.ledger.util.toLocalDate
import java.time.ZoneId

/** 待收款清單:朋友還沒轉給我的錢。可以補「誰欠我」,收到錢時按「已收到」。 */
@Composable
fun ReceivablesSheet(vm: LedgerViewModel, onDismiss: () -> Unit) {
    val receivables by vm.receivables.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    var settling by remember { mutableStateOf<Receivable?>(null) }
    var naming by remember { mutableStateOf<Receivable?>(null) }

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
                    "共 ${formatMoney(receivables.sumOf { it.outstanding })},${receivables.size} 筆",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                receivables.forEach { r ->
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
                                    "實付 ${formatMoney(r.amount)} · 我的 ${formatMoney(r.myShare)} · 待收 ${formatMoney(r.outstanding)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { naming = r }) {
                                    Text(
                                        if (r.owedBy.isNullOrBlank()) "還沒填誰欠,點此補上" else "誰欠:${r.owedBy}(點此修改)",
                                        color = if (r.owedBy.isNullOrBlank()) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Button(onClick = { settling = r }) { Text("已收到") }
                        }
                    }
                }
            }
        }
    }

    settling?.let { r ->
        var amountText by remember(r.id) { mutableStateOf(r.outstanding.toString()) }
        var accountId by remember(r.id) {
            mutableStateOf(
                accounts.firstOrNull { AccountType.from(it.type) == AccountType.BANK }?.id
                    ?: accounts.firstOrNull()?.id
            )
        }
        val amount = amountText.toLongOrNull() ?: 0L
        AlertDialog(
            onDismissRequest = { settling = null },
            title = { Text("朋友轉來了") },
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
                        vm.settle(r.id, amount, accountId!!)
                        settling = null
                    },
                ) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { settling = null }) { Text("取消") } },
        )
    }

    naming?.let { r ->
        var text by remember(r.id) { mutableStateOf(r.owedBy ?: "") }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text("誰欠我?") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    label = { Text("名字,多人用頓號隔開") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setOwedBy(r.id, text)
                    naming = null
                }) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("取消") } },
        )
    }
}
