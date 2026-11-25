package com.example.ledger.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ledger.reminder.ReminderSettings
import com.example.ledger.settings.AppSettings
import com.example.ledger.ui.ScreenHeader
import com.example.ledger.ui.SectionTitle
import com.example.ledger.util.formatMoney

private val BASIC_FEATURES = listOf(
    "語音記帳與手動記帳(含確認卡)",
    "帳目列表:按月、依日分組、搜尋、編輯、刪除",
    "帳戶(現金、銀行、信用卡、電子支付、投資)與即時餘額",
    "分類管理,以及統計(支出圓環圖、近 6 個月收支)",
    "代墊:實付與「我的份」、待收款、已收到",
    "轉帳(繳卡費、投資 0050)與現金領出,不會重複計算支出",
    "代墊「誰欠我」30 分鐘提醒(目前一律啟用)",
    "線上更新(在「管理」頁)",
)

/** 設定頁:上面是一直開著的基本功能,下面是可以自己開關的附加功能。 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("設定")

        SectionTitle("基本功能(一直開著)")
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BASIC_FEATURES.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }

        SectionTitle("附加功能(可自行開啟)")

        // 月額上限與提醒:打開時先設定金額(取消就維持關閉),之後隨時可以按「修改」
        var budgetOn by remember { mutableStateOf(AppSettings.budgetEnabled(context)) }
        var limit by remember { mutableStateOf(AppSettings.budgetLimit(context)) }
        var showLimitDialog by remember { mutableStateOf(false) }
        var enablingBudget by remember { mutableStateOf(false) }
        FeatureRow(
            title = "月額上限與提醒",
            description = "設定每月支出上限,本月達 80%、100% 時各通知一次。算法:支出實付全額(含現金領出)+ 轉到投資帳戶,朋友轉回來的代墊收回後扣掉;繳卡費不算,避免和刷卡重複。",
            checked = budgetOn,
            onCheckedChange = { on ->
                if (on) {
                    enablingBudget = true
                    showLimitDialog = true
                } else {
                    budgetOn = false
                    AppSettings.setBudgetEnabled(context, false)
                }
            },
        )
        if (budgetOn) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "每月上限 " + if (limit > 0) formatMoney(limit) else "尚未設定",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = {
                    enablingBudget = false
                    showLimitDialog = true
                }) { Text(if (limit > 0) "修改" else "設定") }
            }
        }
        if (showLimitDialog) {
            LimitDialog(
                initial = limit,
                enabling = enablingBudget,
                onDismiss = { showLimitDialog = false },
                onConfirm = { value ->
                    AppSettings.setBudgetLimit(context, value)
                    limit = value
                    if (enablingBudget) {
                        AppSettings.setBudgetEnabled(context, true)
                        budgetOn = true
                    }
                    showLimitDialog = false
                },
            )
        }

        // 0050 定期定額提醒(預設關閉)
        var investOn by remember { mutableStateOf(ReminderSettings.isInvestEnabled(context)) }
        val notificationBlocked = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        FeatureRow(
            title = "0050 定期定額提醒",
            description = "每月 6 號扣款(遇假日順延),8 號中午如果這個月還沒記錄投資,就通知你補記。",
            checked = investOn,
            onCheckedChange = {
                investOn = it
                ReminderSettings.setInvestEnabled(context, it)
            },
            warning = if ((investOn || budgetOn) && notificationBlocked) {
                "目前沒有通知權限,請到系統設定允許通知,否則提醒不會響"
            } else null,
        )

        // 自動偵測訊息入帳:預設關閉,功能還沒推出
        FeatureRow(
            title = "自動偵測訊息入帳",
            description = "尚未推出。規劃從 LINE 的刷卡通知偵測消費,先放進「待確認」清單,再提醒你分類。預設關閉,推出後在這裡開啟。",
            checked = false,
            onCheckedChange = null,
            enabled = false,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
    warning: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (warning != null) {
                Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** 設定每月上限。開啟功能時按「取消」會維持關閉;之後隨時可以從「修改」重新進來。 */
@Composable
private fun LimitDialog(initial: Long, enabling: Boolean, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by remember { mutableStateOf(initial.takeIf { it > 0 }?.toString() ?: "") }
    val value = text.toLongOrNull() ?: 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (enabling) "先設定每月上限" else "修改每月上限") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { s -> text = s.filter(Char::isDigit).take(9) },
                label = { Text("每月上限") },
                prefix = { Text("NT$ ") },
                singleLine = true,
                supportingText = { if (enabling) Text("設好金額才會開啟;取消則維持關閉") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(enabled = value > 0, onClick = { onConfirm(value) }) {
                Text(if (enabling) "開啟" else "儲存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
