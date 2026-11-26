package com.example.ledger.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.ledger.detect.NoticeAccess
import com.example.ledger.detect.PendingReminder
import com.example.ledger.reminder.InvestPlan
import com.example.ledger.reminder.ReceivableReminder
import com.example.ledger.reminder.InvestSchedule
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
    "代墊:實付與「我的份」、選人、待收款、已收到、不收了;管理頁可看每個人的代墊統計",
    "轉帳(繳卡費、投資 0050)與現金領出,不會重複計算支出",
    "代墊「誰欠我」30 分鐘提醒(目前一律啟用)",
    "線上更新:有新版本時自動跳出視窗",
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

        // 定期定額提醒(預設關閉):可以有很多組,扣款日與提醒日都能隨時修改
        var investOn by remember { mutableStateOf(ReminderSettings.isInvestEnabled(context)) }
        var plans by remember { mutableStateOf(ReminderSettings.investPlans(context)) }
        var editingPlan by remember { mutableStateOf<InvestPlan?>(null) }
        var addingPlan by remember { mutableStateOf(false) }
        val notificationBlocked = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        FeatureRow(
            title = "定期定額提醒",
            description = "依你設定的扣款日和提醒日:提醒日中午如果這個月還沒記錄轉入投資帳戶,就通知你補記。" +
                "金額不固定所以不自動入帳。扣款日遇假日會順延的話,提醒日請設晚幾天。",
            checked = investOn,
            onCheckedChange = {
                investOn = it
                ReminderSettings.setInvestEnabled(context, it)
            },
            warning = if ((investOn || budgetOn) && notificationBlocked) {
                "目前沒有通知權限,請到系統設定允許通知,否則提醒不會響"
            } else null,
        )
        if (investOn) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                plans.forEach { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "每月 ${p.debitDay} 號扣款 · ${p.remindDay} 號中午提醒",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { editingPlan = p }) { Text("修改") }
                        TextButton(onClick = {
                            plans = plans.filter { it.id != p.id }
                            ReminderSettings.saveInvestPlans(context, plans)
                        }) { Text("刪除") }
                    }
                }
                if (plans.isEmpty()) {
                    Text(
                        "目前沒有任何一組,不會提醒。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (plans.size < InvestSchedule.MAX_PLANS) {
                    TextButton(onClick = { addingPlan = true }) { Text("新增一組") }
                }
            }
        }
        if (addingPlan || editingPlan != null) {
            val editing = editingPlan
            PlanDialog(
                initial = editing,
                onDismiss = { addingPlan = false; editingPlan = null },
                onConfirm = { name, debit, remind ->
                    plans = if (editing == null) {
                        plans + InvestPlan(InvestSchedule.nextId(plans), name, debit, remind)
                    } else {
                        plans.map { if (it.id == editing.id) it.copy(name = name, debitDay = debit, remindDay = remind) else it }
                    }
                    ReminderSettings.saveInvestPlans(context, plans)
                    addingPlan = false
                    editingPlan = null
                },
            )
        }

        // 待收款提醒:代墊放太久沒收回就通知(預設關閉,天數可隨時改)
        var recvOn by remember { mutableStateOf(AppSettings.receivableReminderEnabled(context)) }
        var recvDays by remember { mutableStateOf(AppSettings.receivableReminderDays(context)) }
        var showDaysDialog by remember { mutableStateOf(false) }
        FeatureRow(
            title = "待收款提醒",
            description = "代墊超過設定的天數還沒收回,就通知你提醒對方。每天晚上 19:00 檢查,同樣的待收款最多每 7 天提醒一次。",
            checked = recvOn,
            onCheckedChange = {
                recvOn = it
                AppSettings.setReceivableReminderEnabled(context, it)
                if (it) ReceivableReminder.ensureScheduled(context) else ReceivableReminder.cancel(context)
            },
        )
        if (recvOn) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("超過 $recvDays 天就提醒", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { showDaysDialog = true }) { Text("修改") }
            }
        }
        if (showDaysDialog) {
            var text by remember { mutableStateOf(recvDays.toString()) }
            val value = text.toIntOrNull() ?: 0
            AlertDialog(
                onDismissRequest = { showDaysDialog = false },
                title = { Text("超過幾天提醒?") },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { s -> text = s.filter(Char::isDigit).take(3) },
                        label = { Text("天數(1~365)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                },
                confirmButton = {
                    TextButton(enabled = value in 1..365, onClick = {
                        AppSettings.setReceivableReminderDays(context, value)
                        recvDays = value
                        showDaysDialog = false
                    }) { Text("儲存") }
                },
                dismissButton = { TextButton(onClick = { showDaysDialog = false }) { Text("取消") } },
            )
        }

        // 自動偵測訊息入帳:預設關閉;要在系統設定允許「通知存取」才會真的運作
        var autoOn by remember { mutableStateOf(AppSettings.autoDetectEnabled(context)) }
        var accessOn by remember { mutableStateOf(NoticeAccess.isGranted(context)) }
        var showAccessHelp by remember { mutableStateOf(false) }
        var lastDetected by remember { mutableStateOf(AppSettings.lastDetected(context)) }
        var lastTitle by remember { mutableStateOf(AppSettings.lastLineTitle(context)) }
        LifecycleResumeEffect(Unit) {
            accessOn = NoticeAccess.isGranted(context)
            lastDetected = AppSettings.lastDetected(context)
            lastTitle = AppSettings.lastLineTitle(context)
            onPauseOrDispose { }
        }
        FeatureRow(
            title = "自動偵測訊息入帳",
            description = "偵測 LINE 裡中國信託、LINE錢包的刷卡通知,先放進「待確認」(不算進統計),每晚 21:00 提醒你補分類。" +
                "LINE Pay 卡會自動帶金額;統一企業卡(uniopen)通知沒有金額,要自己補。只讀這兩個官方帳號的通知,其他一律不看、不存。",
            checked = autoOn,
            onCheckedChange = { on ->
                autoOn = on
                AppSettings.setAutoDetectEnabled(context, on)
                if (on) {
                    PendingReminder.ensureScheduled(context)
                    if (!accessOn) showAccessHelp = true
                } else {
                    PendingReminder.cancel(context)
                }
            },
        )
        if (autoOn) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (accessOn) "通知存取:已開啟" else "通知存取:尚未開啟(沒開就偵測不到)",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (accessOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    )
                    if (!accessOn) TextButton(onClick = { showAccessHelp = true }) { Text("怎麼開") }
                }
                lastDetected?.let { (text, _) ->
                    Text("最近一次偵測:$text", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                lastTitle?.let {
                    Text("最近一則 LINE 通知的標題:$it(除錯用,只存標題)", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (showAccessHelp) {
            AlertDialog(
                onDismissRequest = { showAccessHelp = false },
                title = { Text("開啟通知存取") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("1. 因為這個 App 不是從 Play 商店安裝的,Android 會先擋住這個權限。請先到「應用程式資訊」,按右上角「⋮」→「允許受限制的設定」。")
                        Text("2. 再到「通知存取」,找到本 App 並打開。")
                        Text("沒有看到「⋮」或「允許受限制的設定」的話,直接做第 2 步即可。")
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            )
                        }) { Text("開啟「應用程式資訊」") }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showAccessHelp = false
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) { Text("前往通知存取") }
                },
                dismissButton = { TextButton(onClick = { showAccessHelp = false }) { Text("稍後") } },
            )
        }
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

/** 新增或修改一組定期定額:名稱、每月幾號扣款、每月幾號中午提醒(1~31,超過該月天數就用月底)。 */
@Composable
private fun PlanDialog(initial: InvestPlan?, onDismiss: () -> Unit, onConfirm: (String, Int, Int) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: InvestSchedule.DEFAULT_NAME) }
    var debit by remember { mutableStateOf((initial?.debitDay ?: 6).toString()) }
    var remind by remember { mutableStateOf((initial?.remindDay ?: 8).toString()) }
    val debitDay = debit.toIntOrNull()
    val remindDay = remind.toIntOrNull()
    val valid = debitDay in 1..31 && remindDay in 1..31
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增定期定額" else "修改定期定額") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("名稱(例如 0050)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = debit,
                    onValueChange = { s -> debit = s.filter(Char::isDigit).take(2) },
                    label = { Text("每月幾號扣款") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = remind,
                    onValueChange = { s -> remind = s.filter(Char::isDigit).take(2) },
                    label = { Text("每月幾號中午提醒") },
                    singleLine = true,
                    supportingText = { Text("1~31;遇假日會順延的話,設晚幾天") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(InvestSchedule.cleanName(name), debitDay!!, remindDay!!) }) {
                Text("儲存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
