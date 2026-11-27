package com.example.ledger.detect

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
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
        if (sbn.packageName != NoticeParser.LINE_PACKAGE) {
            handleOtherApp(sbn)
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        // 除錯用:只記標題,不記內容
        AppSettings.setLastLineTitle(this, title)
        if (!NoticeParser.isRelevantSender(title)) return
        // 群組摘要通知會重複同一則內容,跳過
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        // 內建來源可以在設定頁各自關掉
        if (NoticeParser.isCtbc(title) && !AppSettings.sourceCtbcEnabled(this)) return
        if (NoticeParser.isWallet(title) && !AppSettings.sourceWalletEnabled(this)) return

        val text = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
        ).joinToString("\n")
        val handler = (application as LedgerApp).noticeHandler
        scope.launch {
            val result = runCatching { handler.handle(title, text, sbn.postTime, sbn.key, sbn.notification.`when`) }.getOrNull()
            if (result != null) AppSettings.setLastDetected(this@NoticeListenerService, result, sbn.postTime)
        }
    }

    /** 其他 App 的通知:先記成「最近通知」(只有 App 與標題)供使用者挑選;符合自訂來源才處理。 */
    @Suppress("DEPRECATION")
    private fun handleOtherApp(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (sbn.isOngoing || (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        if (title.isEmpty()) return
        val pkg = sbn.packageName
        val label = runCatching {
            extras.getParcelable<ApplicationInfo>("android.appInfo")?.loadLabel(packageManager)?.toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: pkg

        scope.launch {
            AppSettings.addRecentNotice(this@NoticeListenerService, RecentNotice(pkg, label, title))
            val source = CustomSources.match(AppSettings.customSources(this@NoticeListenerService), pkg, title) ?: return@launch
            val text = listOfNotNull(
                extras.getCharSequence(Notification.EXTRA_TEXT),
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            ).joinToString("\n")
            val handler = (application as LedgerApp).noticeHandler
            val result = runCatching { handler.handleCustom(source, title, text, sbn.postTime, sbn.key) }.getOrNull()
                ?: return@launch
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
