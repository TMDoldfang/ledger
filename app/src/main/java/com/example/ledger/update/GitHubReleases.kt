package com.example.ledger.update

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/**
 * 從 GitHub Releases 取得最新版與 APK。不需要任何帳號或 Token,
 * 所以 Repo 必須是 Public(私人 Repo 的 Release 沒有登入就抓不到)。
 *
 * 約定:Release 的 tag 是「v 加數字」(v12),數字就是 versionCode;
 * Release 附件裡要有一個 .apk。GitHub Actions 會自動照這個格式發佈。
 */
object GitHubReleases {
    private const val USER_AGENT = "ledger-app-updater"
    private val TAG_PATTERN = Regex("""^v?(\d+)$""", RegexOption.IGNORE_CASE)

    /** 會做網路連線,必須在背景執行緒呼叫。 */
    fun fetchLatest(repo: String): ReleaseInfo {
        val conn = URL("https://api.github.com/repos/$repo/releases/latest")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", USER_AGENT)
        try {
            when (val code = conn.responseCode) {
                200 -> Unit
                404 -> throw UpdateException("找不到已發佈的版本。請確認 GitHub Repo 是 Public,而且已經有 Release。")
                403, 429 -> throw UpdateException("GitHub 暫時限制查詢次數,請稍後再試。")
                else -> throw UpdateException("GitHub 回應異常(HTTP $code)")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parseRelease(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseRelease(body: String): ReleaseInfo {
        val json = JSONObject(body)
        val tag = json.optString("tag_name")
        val code = TAG_PATTERN.matchEntire(tag)?.groupValues?.get(1)?.toLongOrNull()
            ?: throw UpdateException("版本標籤「$tag」格式不符(要是 v 加數字,例如 v12)")

        val assets = json.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0))
            .mapNotNull { assets?.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?: throw UpdateException("這個版本沒有附上 APK 檔")

        val url = apk.optString("browser_download_url")
        if (url.isBlank()) throw UpdateException("找不到 APK 下載網址")

        // GitHub 近年會在附件資訊裡附 "digest": "sha256:…";有的話就拿來驗證,沒有就略過
        val digest = apk.optString("digest")
        val sha = if (digest.startsWith("sha256:", ignoreCase = true)) digest.substring(7).lowercase() else null

        return ReleaseInfo(
            versionCode = code,
            versionName = "build $code",
            notes = json.optString("body").trim().take(500),
            apkUrl = url,
            sizeBytes = apk.optLong("size", 0L),
            sha256 = sha,
        )
    }

    /** 下載 APK 到 dest。會做網路連線與檔案寫入,必須在背景執行緒呼叫。 */
    fun download(info: ReleaseInfo, dest: File, isActive: () -> Boolean, onProgress: (Float) -> Unit) {
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")

        // GitHub 的下載網址會 302 轉到檔案伺服器(同為 HTTPS),HttpURLConnection 會自動跟隨
        val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        try {
            if (conn.responseCode != 200) throw UpdateException("下載失敗(HTTP ${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            val sha = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastPercent = -1

            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        if (!isActive()) throw CancellationException("已取消下載")
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        sha.update(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }

            info.sha256?.let { expected ->
                val actual = sha.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expected, ignoreCase = true)) {
                    part.delete()
                    throw UpdateException("檔案驗證失敗(SHA-256 不符),請重新下載")
                }
            }
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) throw UpdateException("無法儲存下載的檔案")
        } finally {
            conn.disconnect()
        }
    }

    /** 安裝前的基本檢查:真的是 APK、套件名稱相同、版本比目前新。簽章則由系統安裝程式把關。 */
    fun verifyApk(context: Context, file: File, currentVersionCode: Long) {
        @Suppress("DEPRECATION")
        val pkg = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw UpdateException("下載的檔案不是有效的 APK")
        if (pkg.packageName != context.packageName) throw UpdateException("APK 的套件名稱不符,已中止")
        if (PackageInfoCompat.getLongVersionCode(pkg) <= currentVersionCode) {
            throw UpdateException("APK 的版本不比目前新,已中止")
        }
    }
}
