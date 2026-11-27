package com.example.ledger.detect

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.example.ledger.LedgerApp
import com.example.ledger.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 偵測刷卡通知:LINE 裡寄件人是「中國信託」或「LINE錢包」的(內建來源,不看朋友的訊息),
 * 以及使用者在設定頁新增的自訂來源(App + 標題關鍵字)。其他通知不處理、不存內容。
 * 功能預設關閉,在設定頁開啟後才處理。
 */
class NoticeListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!AppSettings.autoDetectEnabled(this)) return
        if (sbn.packageName == packageName) return
        if (sbn.packageName != NoticeParser.LINE_PACKAGE) return // 目前只讀 LINE

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        // 除錯用:只記標題,不記內容
        AppSettings.setLastLineTitle(this, title)
        val text = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
        ).joinToString("\n")
        // 群組摘要通知會重複同一則內容,跳過
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (!NoticeParser.isRelevantSender(title)) {
            handleCustomSender(sbn, title, text)
            return
        }
        // 內建來源可以在設定頁各自關掉
        if (NoticeParser.isCtbc(title) && !AppSettings.sourceCtbcEnabled(this)) return
        if (NoticeParser.isWallet(title) && !AppSettings.sourceWalletEnabled(this)) return

        val handler = (application as LedgerApp).noticeHandler
        scope.launch {
            val result = runCatching { handler.handle(title, text, sbn.postTime, sbn.key, sbn.notification.`when`) }.getOrNull()
            if (result != null) AppSettings.setLastDetected(this@NoticeListenerService, result, sbn.postTime)
        }
    }

    /** LINE 裡不是內建來源的寄件人:記成「最近的 LINE 通知標題」供挑選;符合自訂來源才處理。 */
    private fun handleCustomSender(sbn: StatusBarNotification, title: String, text: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        scope.launch {
            AppSettings.addRecentNotice(this@NoticeListenerService, RecentNotice(NoticeParser.LINE_PACKAGE, "LINE", clean))
            val source = CustomSources.match(
                AppSettings.customSources(this@NoticeListenerService), NoticeParser.LINE_PACKAGE, clean, text,
            ) ?: return@launch
            val handler = (application as LedgerApp).noticeHandler
            val result = runCatching {
                handler.handleCustom(source, clean, text, sbn.postTime, sbn.key, sbn.notification.`when`)
            }.getOrNull() ?: return@launch
            AppSettings.updateCustomSources(this@NoticeListenerService) { CustomSources.bump(it, source.id) }
            AppSettings.setLastDetected(this@NoticeListenerService, result, sbn.postTime)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

object NoticeAccess {
    /** 使用者有沒有在系統設定裡,允許這個 App 讀取通知。 */
    fun isGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}
