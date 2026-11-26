@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ledger.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ledger.data.AccountType
import com.example.ledger.detect.DetectedCard
import com.example.ledger.detect.ParsedShot
import com.example.ledger.detect.ReceiptParser
import com.example.ledger.detect.ScreenshotReader
import com.example.ledger.util.formatMoney
import com.example.ledger.voice.TransactionDraft
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

private sealed interface ShotState {
    data object Reading : ShotState
    data object Failed : ShotState
    data class Done(val shot: ParsedShot) : ShotState
}

/**
 * 辨識刷卡通知截圖:讀出金額、時間、商店,讓你選擇「補到待確認那一筆」或「新增一筆帳目」。
 * 不會自動存任何東西;圖片只在手機上辨識,不上傳、不儲存,卡號不會被記錄。
 */
@Composable
fun ScreenshotSheet(
    vm: LedgerViewModel,
    uri: Uri,
    onDismiss: () -> Unit,
    onOpenEditor: (TransactionDraft) -> Unit,
) {
    val context = LocalContext.current
    val pending by vm.pending.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }

    val state by produceState<ShotState>(ShotState.Reading, uri) {
        val lines = ScreenshotReader.readLines(context, uri)
        value = if (lines == null) ShotState.Failed else ShotState.Done(ReceiptParser.parse(lines))
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
            Text("辨識截圖", style = MaterialTheme.typography.titleLarge)
            Text(
                "只在你的手機上辨識,圖片不會上傳也不會儲存,卡號不會被記錄。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val s = state) {
                ShotState.Reading -> Text("辨識中…")
                ShotState.Failed -> {
                    Text("讀不到這張圖,請換一張再試。", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onDismiss) { Text("關閉") }
                }
                is ShotState.Done -> {
                    val shot = s.shot
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                shot.amount?.let { formatMoney(it) } ?: "沒有辨識到金額",
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (shot.amount == null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            val whenText = listOfNotNull(
                                shot.date?.let { "${it.year}/${it.monthValue}/${it.dayOfMonth}" },
                                shot.time?.let { "%02d:%02d".format(it.hour, it.minute) },
                            ).joinToString(" ")
                            Text("時間:" + whenText.ifEmpty { "沒辨識到" }, style = MaterialTheme.typography.bodyMedium)
                            Text("商店:" + (shot.merchant ?: "沒辨識到"), style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // 補到「待確認」裡還沒有金額的那一筆(時間最接近的排前面)
                    val shotMillis = shot.date?.atTime(shot.time ?: java.time.LocalTime.NOON)
                        ?.atZone(zone)?.toInstant()?.toEpochMilli()
                    val candidates = pending.filter { it.amount == null }
                        .sortedBy { e -> if (shotMillis == null) 0L else abs(e.timestamp - shotMillis) }
                        .take(3)
                    if (shot.amount != null) {
                        candidates.forEach { e ->
                            val at = java.time.Instant.ofEpochMilli(e.timestamp).atZone(zone)
                            Button(
                                onClick = {
                                    vm.fillPendingAmount(e.id, shot.amount)
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "補到 ${at.monthValue}/${at.dayOfMonth} %02d:%02d 的 ${DetectedCard.from(e.card).label}"
                                        .format(at.hour, at.minute)
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val card = accounts.firstOrNull { AccountType.from(it.type) == AccountType.CARD }
                                ?: accounts.firstOrNull()
                            onOpenEditor(
                                TransactionDraft(
                                    isExpense = true,
                                    amount = shot.amount,
                                    categoryId = categories.firstOrNull { it.isExpense && it.name == "其他" }?.id,
                                    accountId = card?.id,
                                    date = shot.date ?: LocalDate.now(),
                                    note = shot.merchant.orEmpty(),
                                    accountIsDefault = true,
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("新增一筆帳目(可再修改)") }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("取消") }
                }
            }
        }
    }
}
