package com.example.ledger.detect

/**
 * 使用者自己加的通知來源(純 Kotlin,方便單元測試)。
 * 一個來源 = 哪個 App + 通知標題要包含的關鍵字。不提供「只比對 App」,避免雜訊。
 * 只存 App 與標題關鍵字,不存通知內容。
 */
data class CustomSource(
    val id: String,
    val pkg: String,
    val appLabel: String,
    val titleKey: String,
    val enabled: Boolean = true,
    val count: Int = 0,
)

/** 最近收到的通知(只有 App 與標題),讓使用者從中挑選要新增的來源。 */
data class RecentNotice(val pkg: String, val appLabel: String, val title: String)

object CustomSources {
    private const val F = '\u001F'
    const val MAX_RECENT = 30
    /** 自訂來源在待確認裡的卡別前綴:CUSTOM:來源名稱 */
    const val CARD_PREFIX = "CUSTOM:"

    private fun clean(s: String) = s.replace(F, ' ').replace('\n', ' ').replace('\r', ' ')

    fun encode(list: List<CustomSource>): String = list.joinToString("\n") {
        listOf(it.id, it.pkg, it.appLabel, it.titleKey, if (it.enabled) "1" else "0", it.count.toString())
            .joinToString(F.toString()) { f -> clean(f) }
    }

    fun decode(text: String): List<CustomSource> = text.lines().filter { it.isNotBlank() }.mapNotNull {
        val p = it.split(F)
        if (p.size < 6) null
        else CustomSource(p[0], p[1], p[2], p[3], p[4] == "1", p[5].toIntOrNull() ?: 0)
    }

    fun encodeRecent(list: List<RecentNotice>): String =
        list.joinToString("\n") { listOf(it.pkg, it.appLabel, it.title).joinToString(F.toString()) { f -> clean(f) } }

    fun decodeRecent(text: String): List<RecentNotice> = text.lines().filter { it.isNotBlank() }.mapNotNull {
        val p = it.split(F)
        if (p.size < 3) null else RecentNotice(p[0], p[1], p[2])
    }

    /** 新的放最前面;同 App 同標題只留一筆;最多 MAX_RECENT 筆。 */
    fun addRecent(list: List<RecentNotice>, n: RecentNotice): List<RecentNotice> =
        (listOf(n) + list.filterNot { it.pkg == n.pkg && it.title == n.title }).take(MAX_RECENT)

    /** 這則通知符合哪個來源(要開著、App 相同、標題包含關鍵字)。 */
    fun match(sources: List<CustomSource>, pkg: String, title: String): CustomSource? =
        sources.firstOrNull {
            it.enabled && it.pkg == pkg && it.titleKey.isNotBlank() && title.contains(it.titleKey, ignoreCase = true)
        }

    fun bump(list: List<CustomSource>, id: String): List<CustomSource> =
        list.map { if (it.id == id) it.copy(count = it.count + 1) else it }

    fun cardOf(source: CustomSource) = CARD_PREFIX + source.appLabel
}

/**
 * 通用金額辨識:依可信度分三層,第一層有找到就只看第一層。
 * 同一層出現不同的數字(不確定哪個是消費)就回傳 null,寧可不收也不要抓錯。
 */
object GenericAmount {
    private const val NUM = """([0-9][0-9,]*)"""
    private val levels = listOf(
        Regex("""金額\s*[:：]?\s*(?:NT\s?\$|TWD|新台幣|\$)?\s*$NUM""", RegexOption.IGNORE_CASE),
        Regex("""(?:NT\s?\$|TWD|新台幣|\$)\s*$NUM""", RegexOption.IGNORE_CASE),
        Regex("""$NUM\s*元"""),
    )

    fun find(text: String): Long? {
        for (re in levels) {
            val values = re.findAll(text)
                .mapNotNull { it.groupValues[1].replace(",", "").toLongOrNull() }
                .filter { it in 1..10_000_000 }
                .toSet()
            if (values.size == 1) return values.first()
            if (values.size > 1) return null
        }
        return null
    }
}
