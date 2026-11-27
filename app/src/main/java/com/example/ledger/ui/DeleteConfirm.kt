package com.example.ledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ledger.settings.AppSettings

private class PendingDelete(val title: String, val body: String, val onConfirm: () -> Unit)

/**
 * 所有「刪除」共用的確認提示。
 * 回傳一個函式 ask(標題, 說明, 確認後要做的事):
 *  - 平常會跳出確認框,框裡有「以後不再顯示這個提醒」(預設不勾);
 *  - 勾了並按刪除 → 之後直接刪除;到「設定」可以重新打開提醒。
 */
@Composable
fun rememberDeleteConfirm(): (String, String, () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<PendingDelete?>(null) }

    pending?.let { p ->
        var dontAsk by remember(p) { mutableStateOf(false) } // 預設不勾選
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(p.title) },
            text = {
                Column {
                    Text(p.body)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp).clickable { dontAsk = !dontAsk },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
                        Spacer(Modifier.width(4.dp))
                        Text("以後不再顯示這個提醒")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dontAsk) AppSettings.setSkipDeleteConfirm(context, true)
                    pending = null
                    p.onConfirm()
                }) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } },
        )
    }

    return { title, body, action ->
        if (AppSettings.skipDeleteConfirm(context)) action()
        else pending = PendingDelete(title, body, action)
    }
}
