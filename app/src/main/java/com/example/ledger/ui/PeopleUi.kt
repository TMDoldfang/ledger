package com.example.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.example.ledger.data.ReceivableDetail

/** 新增代墊對象的小視窗(輸入頁、指定是誰、管理頁共用)。 */
@Composable
fun AddPersonDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增代墊對象") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("名字") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onAdd(name.trim()) }) { Text("新增") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 一個人(或「未指定」)的待收總額。 */
data class PersonOwed(val personId: Long?, val name: String, val total: Long, val rows: List<ReceivableDetail>)

/** 依人分組,金額大的在前,「未指定」放最後。 */
fun List<ReceivableDetail>.byPerson(): List<PersonOwed> =
    groupBy { it.personId }
        .map { (id, rows) ->
            PersonOwed(id, rows.first().personName ?: "未指定是誰", rows.sumOf { it.outstanding }, rows)
        }
        .sortedWith(compareBy<PersonOwed> { it.personId == null }.thenByDescending { it.total })
