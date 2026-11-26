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
 * 讀取 LINE 的通知,偵測刷卡。只看 LINE 發出、寄件人是「中國信託」或「LINE錢包」的通知,
 * 其他通知(包含朋友的訊息)一律不看、不存。功能預設關閉,在設定頁開啟後才處理。
 */
class NoticeListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != NoticeParser.LINE_PACKAGE) return
        if (!AppSettings.autoDetectEnabled(this)) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        // 除錯用:只記標題,不記內容
        AppSettings.setLastLineTitle(this, title)
        if (!NoticeParser.isRelevantSender(title)) return

        val text = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
        ).joinToString("\n")
        val handler = (application as LedgerApp).noticeHandler
        scope.launch {
            val result = runCatching { handler.handle(title, text, sbn.postTime, sbn.key) }.getOrNull()
            if (result != null) AppSettings.setLastDetected(this@NoticeListenerService, result, sbn.postTime)
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
