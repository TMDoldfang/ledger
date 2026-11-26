package com.example.ledger.detect

import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime

/** 從截圖辨識出的欄位。刻意只有這四個:不含卡號、帳號、姓名,所以不可能被存下來。 */
data class ParsedShot(
    val amount: Long?,
    val date: LocalDate?,
    val time: LocalTime?,
    val merchant: String?,
)

/**
 * 解析刷卡通知截圖的 OCR 文字(純 Kotlin,不依賴 Android)。
 * 不綁定任何一間銀行:靠通用的「金額 / 日期時間 / 商店」關鍵字與格式找,
 * 支援 NT$ / NTD / TWD / $ / 元、西元與民國年、常見的商店欄位名稱。
 *
 * 資安:只輸出金額、日期時間、商店名稱。商店名稱裡連續 4 位以上的數字(可能是卡號)與遮罩字串會被移除。
 */
object ReceiptParser {
    private val currency = Regex("""(?:NT\$|NTD|TWD|新台幣|\$)\s*([0-9][0-9,]{0,10})""", RegexOption.IGNORE_CASE)
    private val labelled = Regex("""(?:消費金額|交易金額|刷卡金額|付款金額|扣款金額|金額)\s*[:：]?\s*(?:NT\$|NTD|TWD|\$)?\s*([0-9][0-9,]{0,10})""", RegexOption.IGNORE_CASE)
    private val yuan = Regex("""([0-9][0-9,]{0,10})\s*元""")
    // 回饋、點數、百分比、餘額這些行的數字不是消費金額
    private val skipLine = Regex("""回饋|點數|%|％|餘額|額度|累積|紅利|OPENPOINT|利率""", RegexOption.IGNORE_CASE)

    private val dateTime = Regex("""(\d{2,4})\s*[/.\-年]\s*(\d{1,2})\s*[/.\-月]\s*(\d{1,2})\s*日?(?:\s*(\d{1,2})\s*:\s*(\d{2}))?""")

    private val merchantLabel = Regex("""(?:特約商店|特店名稱|消費商店|交易商店|商家名稱|店家名稱|商店名稱|商店|店家)""")
    private val cardLike = Regex("""[0-9]{4,}|[*•●＊Xx]{2,}[0-9]*""")

    fun parse(rawLines: List<String>): ParsedShot {
        val lines = rawLines.map { normalize(it) }.filter { it.isNotBlank() }
        val body = lines.filterNot { isFootnote(it) }
        return ParsedShot(
            amount = amount(body),
            date = null,
            time = null,
            merchant = merchant(body),
        ).let { base ->
            val dt = dateTime(body)
            base.copy(date = dt?.first, time = dt?.second)
        }
    }

    private fun normalize(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC).trim()

    private fun isFootnote(line: String) = line.startsWith("*") || line.startsWith("※")

    private fun amount(lines: List<String>): Long? {
        val usable = lines.filterNot { skipLine.containsMatchIn(it) }
        for (re in listOf(currency, labelled, yuan)) {
            for (line in usable) {
                toAmount(re.find(line)?.groupValues?.get(1))?.let { return it }
            }
        }
        // OCR 有時把「NT$」和數字拆成兩行
        toAmount(currency.find(usable.joinToString(" "))?.groupValues?.get(1))?.let { return it }
        return null
    }

    private fun toAmount(raw: String?): Long? =
        raw?.replace(",", "")?.toLongOrNull()?.takeIf { it in 1..9_999_999 }

    private fun dateTime(lines: List<String>): Pair<LocalDate, LocalTime?>? {
        for (line in lines) {
            val m = dateTime.find(line) ?: continue
            val (y, mo, d, h, mi) = m.destructured
            val year = when (y.length) {
                4 -> y.toInt()
                3 -> y.toInt() + 1911 // 民國年
                else -> continue
            }
            val date = runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull() ?: continue
            val time = if (h.isNotEmpty()) runCatching { LocalTime.of(h.toInt(), mi.toInt()) }.getOrNull() else null
            return date to time
        }
        return null
    }

    private fun merchant(lines: List<String>): String? {
        for ((i, line) in lines.withIndex()) {
            val m = merchantLabel.find(line) ?: continue
            var value = line.substring(m.range.last + 1).trimStart(':', ':', ' ', '　').trim()
            if (value.isEmpty()) value = lines.getOrNull(i + 1)?.takeUnless { merchantLabel.containsMatchIn(it) }.orEmpty()
            val clean = scrub(value)
            if (clean.isNotEmpty()) return clean
        }
        return null
    }

    /** 移除可能是卡號的數字與遮罩,整理空白,最多 30 字。 */
    internal fun scrub(text: String): String =
        text.replace(cardLike, "").replace(Regex("""\s+"""), " ").trim().take(30)
}
