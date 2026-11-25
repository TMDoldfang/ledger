@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ledger.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ledger.data.LedgerTransaction
import com.example.ledger.ui.theme.expenseColor
import com.example.ledger.ui.theme.incomeColor
import com.example.ledger.util.formatMoney
import com.example.ledger.voice.SpeechController
import com.example.ledger.voice.SpeechListener
import com.example.ledger.voice.TransactionDraft
import com.example.ledger.voice.VoiceParser
import com.example.ledger.voice.draftFrom
import com.example.ledger.voice.voiceContextOf
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ShortDate = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.TRADITIONAL_CHINESE)

private fun dayLabel(offset: Int, date: LocalDate): String = when (offset) {
    0 -> "今天"
    -1 -> "昨天"
    -2 -> "前天"
    else -> date.format(ShortDate)
}

/**
 * 語音記帳:開啟就開始聽,說完顯示確認卡。
 * 辨識出的文字可以直接改(改完會重新解析),所以模擬器沒有語音服務時,打字也能用。
 */
@Composable
fun VoiceSheet(
    vm: LedgerViewModel,
    onDismiss: () -> Unit,
    onOpenEditor: (TransactionDraft) -> Unit,
) {
    val context = LocalContext.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()

    var transcript by rememberSaveable { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val controller = remember {
        SpeechController(context, object : SpeechListener {
            override fun onListening() {
                listening = true
                status = null
            }

            override fun onText(text: String, isFinal: Boolean) {
                transcript = text
                if (isFinal) listening = false
            }

            override fun onError(message: String) {
                listening = false
                status = message
            }
        })
    }
    DisposableEffect(Unit) { onDispose { controller.release() } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) controller.start()
        else status = "沒有麥克風權限,無法語音輸入。可以直接打字,或到系統設定開啟權限。"
    }

    val startListening = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) controller.start() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) { startListening() }

    val voiceContext = remember(categories, accounts) { voiceContextOf(categories, accounts) }
    val parsed = remember(transcript, voiceContext) { VoiceParser.parse(transcript, voiceContext) }
    val draft = remember(parsed, categories, accounts) { draftFrom(parsed, categories, accounts) }

    val category = categories.firstOrNull { it.id == draft.categoryId }
    val account = accounts.firstOrNull { it.id == draft.accountId }
    val canSave = draft.amount != null && draft.accountId != null &&
        (draft.isTransfer || draft.categoryId != null)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("語音記帳", style = MaterialTheme.typography.titleLarge)
            Text(
                "例如:「午餐 一百二 悠遊卡」「晚餐 一千二 我的三百 信用卡」「繳卡費 兩萬」「領現金 三千」",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = transcript,
                    onValueChange = {
                        controller.release()
                        listening = false
                        transcript = it
                    },
                    placeholder = { Text(if (listening) "請說話…" else "說一句話,或直接打字") },
                    maxLines = 3,
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(
                    onClick = { if (listening) controller.stopListening() else startListening() },
                ) {
                    Icon(
                        imageVector = if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (listening) "停止" else "開始說話",
                    )
                }
            }

            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            if (transcript.isNotBlank()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val amount = draft.amount
                        Text(
                            text = when {
                                draft.isTransfer -> "轉帳 "
                                parsed.isExpense -> "支出 "
                                else -> "收入 "
                            } + (if (amount != null) formatMoney(amount) else "沒聽到金額"),
                            style = MaterialTheme.typography.titleLarge,
                            color = when {
                                amount == null -> MaterialTheme.colorScheme.error
                                draft.isTransfer -> MaterialTheme.colorScheme.onSurface
                                parsed.isExpense -> expenseColor()
                                else -> incomeColor()
                            },
                        )
                        if (draft.isTransfer) {
                            val toAccount = accounts.firstOrNull { it.id == draft.transferToAccountId }
                            InfoLine("從", (account?.name ?: "(沒有帳戶)") + if (draft.accountIsDefault) "(預設)" else "")
                            InfoLine("轉到", (toAccount?.name ?: "") + if (draft.toAccountIsDefault) "(預設)" else "")
                        } else {
                            InfoLine(
                                "分類",
                                (category?.let { "${it.icon} ${it.name}" } ?: "(沒有分類)") +
                                    if (parsed.categoryName == null) "(預設)" else "",
                            )
                            InfoLine(
                                "帳戶",
                                (account?.name ?: "(沒有帳戶)") + if (draft.accountIsDefault) "(預設)" else "",
                            )
                            val share = draft.myShare
                            if (share != null && amount != null) {
                                InfoLine("分攤", "我的 ${formatMoney(share)} · 待收 ${formatMoney(amount - share)}")
                            }
                        }
                        InfoLine("日期", dayLabel(parsed.dayOffset, draft.date))
                        if (parsed.note.isNotBlank()) InfoLine("備註", parsed.note)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                OutlinedButton(
                    enabled = transcript.isNotBlank(),
                    onClick = { onOpenEditor(draft) },
                ) { Text("編輯") }
                Button(
                    enabled = canSave,
                    onClick = {
                        val amount = draft.amount
                        val categoryId = draft.categoryId ?: 0L   // 轉帳的分類由系統決定
                        val accountId = draft.accountId
                        if (amount != null && accountId != null && (draft.isTransfer || categoryId != 0L)) {
                            val timestamp = draft.date.atTime(LocalTime.now())
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            vm.save(
                                LedgerTransaction(
                                    amount = amount,
                                    isExpense = draft.isExpense,
                                    categoryId = categoryId,
                                    accountId = accountId,
                                    timestamp = timestamp,
                                    note = draft.note,
                                    myShare = draft.myShare,
                                    transferToAccountId = draft.transferToAccountId,
                                )
                            )
                            val share = draft.myShare
                            vm.say(
                                when {
                                    draft.isTransfer -> "已記錄轉帳 ${formatMoney(amount)}"
                                    share != null && share < amount ->
                                        "已記錄 ${formatMoney(share)}(待收 ${formatMoney(amount - share)},之後可補是誰欠的)"
                                    else -> "已記錄 ${formatMoney(amount)} · ${category?.name ?: ""}"
                                }
                            )
                            onDismiss()
                        }
                    },
                ) { Text("儲存") }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(
            text = label,
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
