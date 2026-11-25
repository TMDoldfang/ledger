package com.example.ledger.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ledger.update.ApkInstaller
import com.example.ledger.update.UpdateState
import com.example.ledger.update.UpdateViewModel

/** 管理頁底部的「關於與更新」區塊。 */
@Composable
fun UpdateSection(vm: UpdateViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "目前版本 ${vm.currentName}(build ${vm.currentCode})",
            style = MaterialTheme.typography.bodyMedium,
        )

        when (val s = state) {
            UpdateState.Idle -> CheckButton(vm, "檢查更新")

            UpdateState.NotConfigured -> {
                Text(
                    "還沒設定更新來源。請把專案裡 app/src/main/res/values/config.xml 的 update_repo " +
                        "改成「你的 GitHub 帳號/倉庫名」(倉庫必須是 Public),重新安裝後就能用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("檢查中…")
            }

            UpdateState.UpToDate -> {
                Text("已經是最新版本")
                CheckButton(vm, "再檢查一次")
            }

            is UpdateState.Available -> {
                Text("有新版本:${s.info.versionName}", style = MaterialTheme.typography.titleSmall)
                if (s.info.notes.isNotBlank()) {
                    Text(
                        s.info.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = vm::download) { Text("下載更新" + sizeLabel(s.info.sizeBytes)) }
            }

            is UpdateState.Downloading -> {
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                Text("下載中 ${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            }

            is UpdateState.ReadyToInstall -> {
                Text(
                    "下載完成。按「安裝」後由系統接手;第一次會要你允許這個 App 安裝應用程式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        if (!ApkInstaller.install(context, s.file)) {
                            Toast.makeText(
                                context,
                                "請先在設定頁允許安裝,回來後再按一次「安裝」",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                ) { Text("安裝") }
            }

            is UpdateState.Failed -> {
                Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                CheckButton(vm, "重試")
            }
        }
    }
}

@Composable
private fun CheckButton(vm: UpdateViewModel, label: String) {
    OutlinedButton(onClick = { vm.check() }) { Text(label) }
}

private fun sizeLabel(bytes: Long): String =
    if (bytes > 0) "(%.1f MB)".format(bytes / 1048576f) else ""
