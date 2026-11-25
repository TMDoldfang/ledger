package com.example.ledger.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ledger.update.ApkInstaller
import com.example.ledger.update.UpdateState
import com.example.ledger.update.UpdateViewModel

/**
 * 有新版本時自動跳出的視窗:更新 → 下載(顯示進度)→ 安裝。
 * 按「稍後」只會讓這個版本在這次開啟 App 期間不再跳出,下次開 App 還會提醒。
 */
@Composable
fun UpdateDialog(updateVm: UpdateViewModel) {
    val state by updateVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dismissedCode by rememberSaveable { mutableLongStateOf(-1L) }

    val info = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.ReadyToInstall -> s.info
        else -> return
    }
    if (info.versionCode == dismissedCode) return

    AlertDialog(
        onDismissRequest = { if (state !is UpdateState.Downloading) dismissedCode = info.versionCode },
        title = { Text("有新版本 ${info.versionName}") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val s = state) {
                    is UpdateState.Downloading -> {
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                        Text("下載中 ${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    is UpdateState.ReadyToInstall ->
                        Text("下載完成。按「安裝」後由系統接手;第一次會要你允許這個 App 安裝應用程式。")
                    else -> {
                        Text("要現在更新嗎?你的帳目資料會保留。")
                        if (info.notes.isNotBlank()) {
                            Text(
                                info.notes.take(300),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (val s = state) {
                is UpdateState.Downloading -> TextButton(enabled = false, onClick = {}) { Text("下載中…") }
                is UpdateState.ReadyToInstall -> TextButton(onClick = {
                    if (!ApkInstaller.install(context, s.file)) {
                        Toast.makeText(context, "請先在設定頁允許安裝,回來後再按一次「安裝」", Toast.LENGTH_LONG).show()
                    }
                }) { Text("安裝") }
                else -> TextButton(onClick = updateVm::download) { Text("更新") }
            }
        },
        dismissButton = {
            if (state !is UpdateState.Downloading) {
                TextButton(onClick = { dismissedCode = info.versionCode }) { Text("稍後") }
            }
        },
    )
}
