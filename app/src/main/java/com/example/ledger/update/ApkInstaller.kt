package com.example.ledger.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * 把下載好的 APK 交給系統安裝程式。Android 不允許 App 靜默更新自己,
 * 所以每次都會跳出系統的「要更新這個應用程式嗎?」,按一下確認即可。
 */
object ApkInstaller {
    /**
     * 回傳 true:已交給系統安裝程式。
     * 回傳 false:這個 App 還沒被允許「安裝未知應用程式」,已開啟設定頁,允許後回來再按一次。
     */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
