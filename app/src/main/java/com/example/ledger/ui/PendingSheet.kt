@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ledger.data.AccountType
import com.example.ledger.data.PendingEntry
import com.example.ledger.detect.DetectedCard
import com.example.ledger.detect.NoticeParser
import com.example.ledger.util.formatMoney
import com.example.ledger.util.toLocalDate
import com.example.ledger.voice.TransactionDraft
import java.time.Instant
import java.time.ZoneId

/**
 * 待確認:自動偵測到的刷卡通知。按「記帳」會帶著金額、卡別、日期進編輯頁,
 * 補好分類存檔後,這一筆待確認就會消失;不要的按垃圾桶刪掉。
 */
@Composable
fun PendingSheet(vm: LedgerViewModel, onDismiss: () -> Unit, onOpenEditor: (TransactionDraft) -> Unit) {
    val pending by vm.pending.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }

    fun draftOf(e: PendingEntry): TransactionDraft {
        val card = DetectedCard.from(e.card)
        val guessed = NoticeParser.guessAccountId(card, accounts.map { it.id to it.name })
        val account = guessed
            ?: accounts.firstOrNull { AccountType.from(it.type) == AccountType.CARD }?.id
            ?: accounts.firstOrNull()?.id
        return TransactionDraft(
            isExpense = true,
            amount = e.amount,
            categoryId = categories.firstOrNull { it.isExpense && it.name == "其他" }?.id,
            accountId = account,
            date = Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalDate(),
            note = "",
            accountIsDefault = guessed == null,
            pendingId = e.id,
        )
    }

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
            Text("待確認", style = MaterialTheme.typography.titleLarge)
            Text(
                "自動偵測到的刷卡,還沒算進帳目和統計。補好分類(和金額)記帳後就會消失。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pending.isEmpty()) {
                Text("目前沒有待確認的刷卡", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            pending.forEach { e ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            val dt = Instant.ofEpochMilli(e.timestamp).atZone(zone)
                            val date = dt.toLocalDate()
                            Text(
                                DetectedCard.from(e.card).label,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "${date.monthValue}/${date.dayOfMonth} %02d:%02d · ".format(dt.hour, dt.minute) +
                                    (e.amount?.let { formatMoney(it) } ?: "金額未填"),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (e.amount == null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = { onOpenEditor(draftOf(e)) }) { Text("記帳") }
                        IconButton(onClick = { vm.deletePending(e.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "刪除這筆待確認")
                        }
                    }
                }
            }
        }
    }
}
