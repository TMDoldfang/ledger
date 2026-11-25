@file:OptIn(ExperimentalLayoutApi::class)

package com.example.ledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ledger.data.AccountType
import com.example.ledger.ui.AddPersonDialog
import com.example.ledger.ui.LedgerViewModel
import com.example.ledger.ui.ScreenHeader
import com.example.ledger.ui.SectionTitle
import com.example.ledger.ui.UpdateSection
import com.example.ledger.update.UpdateViewModel
import com.example.ledger.util.formatMoney

@Composable
fun ManageScreen(vm: LedgerViewModel, updateVm: UpdateViewModel) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var showAccountDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    val people by vm.people.collectAsStateWithLifecycle()
    var showPersonDialog by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("管理") }

        item { SectionTitle("帳戶", "新增") { showAccountDialog = true } }
        items(accounts, key = { "a${it.id}" }) { a ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        AccountType.from(a.type).label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(formatMoney(a.balance), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { vm.deleteAccount(a.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "刪除帳戶")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("淨資產", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text(formatMoney(accounts.sumOf { it.balance }), style = MaterialTheme.typography.labelLarge)
            }
        }

        item { SectionTitle("分類", "新增") { showCategoryDialog = true } }
        items(categories, key = { "c${it.id}" }) { c ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.icon, fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                Text(c.name, Modifier.weight(1f))
                Text(
                    if (c.isExpense) "支出" else "收入",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { vm.deleteCategory(c.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "刪除分類")
                }
            }
        }

        item { SectionTitle("代墊對象", "新增") { showPersonDialog = true } }
        if (people.isEmpty()) {
            item {
                Text(
                    "還沒有人。記代墊時可以直接新增,也可以在這裡先加。",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(people, key = { "p${it.id}" }) { p ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(p.name, Modifier.weight(1f))
                IconButton(onClick = { vm.deletePerson(p.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "刪除代墊對象")
                }
            }
        }

        item { SectionTitle("關於與更新") }
        item { UpdateSection(updateVm) }
    }

    if (showAccountDialog) {
        AddAccountDialog(
            onDismiss = { showAccountDialog = false },
            onConfirm = { name, type, initial ->
                vm.addAccount(name, type, initial)
                showAccountDialog = false
            },
        )
    }
    if (showPersonDialog) {
        AddPersonDialog(
            onDismiss = { showPersonDialog = false },
            onAdd = { name ->
                vm.addPerson(name)
                showPersonDialog = false
            },
        )
    }
    if (showCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showCategoryDialog = false },
            onConfirm = { name, icon, isExpense ->
                vm.addCategory(name, icon, isExpense)
                showCategoryDialog = false
            },
        )
    }
}

@Composable
private fun AddAccountDialog(onDismiss: () -> Unit, onConfirm: (String, AccountType, Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.CASH) }
    var initial by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增帳戶") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("名稱") },
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                    }
                }
                OutlinedTextField(
                    value = initial,
                    onValueChange = { initial = it.filter(Char::isDigit).take(9) },
                    label = { Text("初始餘額") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), type, initial.toLongOrNull() ?: 0L) },
            ) { Text("新增") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AddCategoryDialog(onDismiss: () -> Unit, onConfirm: (String, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("📦") }
    var isExpense by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增分類") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = isExpense, onClick = { isExpense = true }, label = { Text("支出") })
                    FilterChip(selected = !isExpense, onClick = { isExpense = false }, label = { Text("收入") })
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(12) },
                    label = { Text("名稱") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it.take(4) },
                    label = { Text("圖示(emoji)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && icon.isNotBlank(),
                onClick = { onConfirm(name.trim(), icon.trim(), isExpense) },
            ) { Text("新增") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
