@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.example.ledger.data.PersonShareRow
import com.example.ledger.util.formatMoney
import com.example.ledger.util.toLocalDate
import java.time.ZoneId

/** 某個人的代墊統計:累計、已收回、不收了、待收,以及每一筆的明細(不分月份)。 */
@Composable
fun PersonSheet(vm: LedgerViewModel, personId: Long, name: String, onDismiss: () -> Unit) {
    val flow = remember(personId) { vm.personRows(personId) }
    val rows by flow.collectAsStateWithLifecycle(initialValue = emptyList<PersonShareRow>())
    val zone = remember { ZoneId.systemDefault() }

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
            Text(name, style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth()) {
                Cell("累計代墊", rows.sumOf { it.shareAmount }, Modifier.weight(1f))
                Cell("已收回", rows.sumOf { it.settled }, Modifier.weight(1f))
                Cell("不收了", rows.sumOf { it.forgiven }, Modifier.weight(1f))
                Cell("待收", rows.sumOf { it.outstanding }, Modifier.weight(1f))
            }
            if (rows.isEmpty()) {
                Text("還沒有代墊紀錄", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            rows.forEach { r ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val date = r.timestamp.toLocalDate(zone)
                            Text(
                                "${r.categoryIcon} ${r.note.ifBlank { r.categoryName }}  ${date.monthValue}/${date.dayOfMonth}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "該付 ${formatMoney(r.shareAmount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val status = when {
                            r.outstanding > 0 -> "待收 ${formatMoney(r.outstanding)}"
                            r.forgiven > 0 && r.settled == 0L -> "不收了"
                            else -> "已收回"
                        }
                        Text(
                            status,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (r.outstanding > 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(label: String, value: Long, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatMoney(value), style = MaterialTheme.typography.titleSmall)
    }
}
