package com.example.ledger.detect

import kotlin.math.abs

/** 從 LINE 的刷卡通知判斷出的是哪張卡。 */
enum class DetectedCard(val label: String) {
    LINE_PAY("LINE Pay 卡"),
    UNIOPEN("統一企業卡(uniopen)"),
    UNKNOWN("未知的卡");

    companion object {
        fun from(name: String): DetectedCard = entries.firstOrNull { it.name == name } ?: UNKNOWN

        /** 待確認裡顯示的名稱:自訂來源顯示來源名稱,其他用卡別名稱。 */
        fun labelOf(card: String): String =
            if (card.startsWith(CustomSources.CARD_PREFIX)) card.removePrefix(CustomSources.CARD_PREFIX).ifBlank { "自訂來源" }
            else from(card).label
    }
}

/**
 * 解析 LINE 官方帳號的通知文字(純 Kotlin,不依賴 Android,方便單元測試)。
 *
 * - 中國信託:每次刷卡都會通知,預覽文字寫明是哪張卡,但沒有金額。
 *   例:「🔔LINE Pay聯名卡消費通知🔔」「🔔uniopen聯名卡消費通知🔔」
 * - LINE錢包:LINE Pay 卡刷卡時才有,預覽有金額。例:「LINE Pay 付款 NT$ 13 付款完成。」
 */
object NoticeParser {
    const val LINE_PACKAGE = "jp.naver.line.android"

    private val walletAmount = Regex("""付款\s*NT\$\s*([0-9][0-9,]*)""", RegexOption.IGNORE_CASE)

    fun isCtbc(sender: String): Boolean = sender.contains("中國信託")
    fun isWallet(sender: String): Boolean = sender.contains("LINE錢包")
    fun isRelevantSender(sender: String): Boolean = isCtbc(sender) || isWallet(sender)

    /** 中信的消費通知 → 哪張卡;不是消費通知就回傳 null。 */
    fun ctbcCard(text: String): DetectedCard? {
        if (!text.contains("消費通知")) return null
        return when {
            text.contains("LINE Pay", ignoreCase = true) -> DetectedCard.LINE_PAY
            text.contains("uniopen", ignoreCase = true) -> DetectedCard.UNIOPEN
            else -> DetectedCard.UNKNOWN
        }
    }

    /** LINE錢包的付款通知 → 金額(元);不是付款通知回傳 null。 */
    fun walletAmount(text: String): Long? =
        walletAmount.find(text)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()?.takeIf { it > 0 }

    /** 依卡別猜帳戶:名稱裡有關鍵字就用,猜不到回傳 null(由外面退回預設)。 */
    fun guessAccountId(card: DetectedCard, accounts: List<Pair<Long, String>>): Long? {
        val keys = when (card) {
            DetectedCard.LINE_PAY -> listOf("line")
            DetectedCard.UNIOPEN -> listOf("uniopen", "統一", "企業")
            DetectedCard.UNKNOWN -> return null
        }
        return accounts.firstOrNull { (_, name) -> keys.any { name.contains(it, ignoreCase = true) } }?.first
    }
}

/** 中信通知(LINE Pay 卡)和 LINE錢包通知幾乎同時到,但先後不一定;用時間靠近配對。 */
object Pairing {
    const val WINDOW_MS = 2 * 60 * 1000L

    data class WalletNotice(val amount: Long, val time: Long)
    data class AwaitingRow(val id: Long, val time: Long)

    /** 中信通知到了:在還沒配對的 LINE錢包金額裡,找時間最近的(2 分鐘內)。 */
    fun pickWallet(orphans: List<WalletNotice>, ctbcTime: Long): WalletNotice? =
        orphans.filter { abs(it.time - ctbcTime) <= WINDOW_MS }.minByOrNull { abs(it.time - ctbcTime) }

    /** LINE錢包通知到了:在還沒有金額的 LINE Pay 待確認裡,找時間最近的(2 分鐘內)。 */
    fun pickAwaiting(rows: List<AwaitingRow>, walletTime: Long): AwaitingRow? =
        rows.filter { abs(it.time - walletTime) <= WINDOW_MS }.minByOrNull { abs(it.time - walletTime) }
}

/**
 * 同一則通知被重複送來(LINE 更新通知等)會讓同一筆消費變成兩筆。
 * 判斷依據是通知本身的「發出時間」(Notification.when):同一則通知重送時不變,
 * 新的一次刷卡一定是新的時間。所以內容再像、時間再近,只要發出時間不同就是新的一筆。
 * 拿不到發出時間(0)就不去重,寧可多一筆也不吃掉真的刷卡。
 */
object Dedupe {
    /** 待確認清單裡,同卡、同金額(或同樣沒金額)、2 分鐘內的項目標示「可能重複」(只標示,不刪除)。 */
    const val SUSPECT_MS = 2 * 60_000L

    fun fingerprint(sender: String, text: String, whenMs: Long): String? =
        if (whenMs > 0) "$sender|$text|$whenMs" else null

    fun isDuplicate(history: Collection<String>, fingerprint: String?): Boolean =
        fingerprint != null && fingerprint in history

    data class Row(val id: Long, val card: String, val amount: Long?, val time: Long)

    fun suspects(rows: List<Row>): Set<Long> {
        val out = HashSet<Long>()
        for (i in rows.indices) for (j in i + 1 until rows.size) {
            val a = rows[i]; val b = rows[j]
            if (a.card == b.card && a.amount == b.amount && abs(a.time - b.time) <= SUSPECT_MS) {
                out += a.id; out += b.id
            }
        }
        return out
    }
}
