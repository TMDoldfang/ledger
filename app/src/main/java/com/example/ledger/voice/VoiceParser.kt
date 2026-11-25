package com.example.ledger.voice

import com.example.ledger.data.AccountType
import com.example.ledger.data.SystemNames
import java.text.Normalizer
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** 使用者目前有的帳戶(名稱 + 類型)。 */
data class AccountRef(val name: String, val type: AccountType)

/** 解析時要知道的使用者資料;純資料,不依賴 Android / Room。 */
data class VoiceContext(
    val expenseCategories: List<String>,
    val incomeCategories: List<String>,
    val accounts: List<AccountRef>,
)

/**
 * 解析結果。categoryName / account 為 null 代表句子裡沒提到(或對不上),
 * 由呼叫端決定預設值。dayOffset:0 今天、-1 昨天、-2 前天……
 */
data class ParsedVoice(
    val amount: Long?,
    val isExpense: Boolean,
    val categoryName: String?,
    val account: AccountRef?,
    val dayOffset: Int,
    val note: String,
    /** 代墊:「我的份」。null = 沒說,全部算我的。 */
    val myShare: Long? = null,
    /** 領現金:記成「現金領出」分類,帳戶是被領錢的那個(通常是銀行),不是現金帳戶。 */
    val isCashOut: Boolean = false,
    /** 繳卡費、投資這類帳戶之間的轉帳;不為 null 時 account 就是付款帳戶。 */
    val transfer: ParsedTransfer? = null,
)

enum class TransferKind { CARD_BILL, INVEST }

/** from / to 為 null 代表句子沒提到,由呼叫端挑預設帳戶(信用卡帳單 → 第一張信用卡,投資 → 投資帳戶)。 */
data class ParsedTransfer(val kind: TransferKind, val from: AccountRef?, val to: AccountRef?)

/**
 * 規則式語音記帳解析:「午餐 120 悠遊卡」「昨天晚餐花了兩百五用現金」。
 * 想調整辨識結果,主要改下面幾張關鍵字表(EXPENSE_KEYWORDS、ACCOUNT_KEYWORDS 等)。
 */
object VoiceParser {

    fun parse(raw: String, ctx: VoiceContext): ParsedVoice {
        val text = normalize(raw)
        if (text.isEmpty()) return ParsedVoice(null, true, null, null, 0, "")

        val removals = mutableListOf<IntRange>()   // 這些區段不算備註

        // 「晚餐 1200 我的 300」:先抓出「我的份」,遮住後再找總額
        val share = findShare(text)
        var amount = findAmount(if (share != null) mask(text, share.range) else text)
        var myShare: Long? = share?.value
        if (share != null && amount == null) {
            // 只有一個數字,「我的」其實是在講總額
            amount = findAmount(text)
            myShare = null
        }
        if (amount != null) removals += amount.removeRange
        if (myShare != null) {
            if (amount != null && myShare < amount.value) removals += share!!.range else myShare = null
        }

        val date = findDate(text)
        removals += date.ranges

        val isExpense = detectExpense(text)

        // 繳卡費、投資:帳戶之間的轉帳,不是消費
        if (isExpense) {
            val transfer = detectTransfer(text, ctx)
            if (transfer != null) {
                removals += transfer.accountRanges
                return ParsedVoice(
                    amount = amount?.value,
                    isExpense = true,
                    categoryName = null,
                    account = transfer.parsed.from,
                    dayOffset = date.offset,
                    note = buildNote(text, removals, null),
                    transfer = transfer.parsed,
                )
            }
        }

        // 領現金:支出是「領出的那一筆」,不是現金帳戶
        val cashOut = if (isExpense) CASH_OUT.find(text) else null
        if (cashOut != null) {
            removals += cashOut.range
            val acc = pickAccount(mask(text, cashOut.range), ctx.accounts)
            if (acc != null) removals += acc.ranges
            val bank = acc?.ref?.takeIf { it.type != AccountType.CASH }
            return ParsedVoice(
                amount = amount?.value,
                isExpense = true,
                categoryName = SystemNames.CASH_OUT.takeIf { it in ctx.expenseCategories },
                account = bank,
                dayOffset = date.offset,
                note = buildNote(text, removals, null),
                isCashOut = true,
            )
        }

        val category = pickCategory(
            text,
            if (isExpense) ctx.expenseCategories else ctx.incomeCategories,
            isExpense,
        )

        val account = pickAccount(text, ctx.accounts)
        if (account != null) removals += account.ranges

        return ParsedVoice(
            amount = amount?.value,
            isExpense = isExpense,
            categoryName = category,
            account = account?.ref,
            dayOffset = date.offset,
            note = buildNote(text, removals, category),
            myShare = if (isExpense) myShare else null,
        )
    }

    private fun mask(text: String, r: IntRange): String =
        text.replaceRange(r, "_".repeat(r.last - r.first + 1))

    // ───────────────────────── 代墊:我的份 ─────────────────────────

    private class ShareHit(val value: Long, val range: IntRange)

    private val SHARE_REGEX = Regex(
        "(?:我的份|我的|我那份|我分攤|我負擔|我分到|我只出|我只付|我出|自己的|自己出|自己付|我自己)" +
            "(?:是|為|了|大概|約|共|只有|只要)?\\s*" +
            "((?:${ChineseNumber.TOKEN_PATTERN})+)\\s*(?:塊錢|塊|元|圓)?"
    )

    /** 取最後一個符合的(通常先講總額、後講「我的」)。 */
    private fun findShare(text: String): ShareHit? {
        for (m in SHARE_REGEX.findAll(text).toList().asReversed()) {
            val value = ChineseNumber.parse(m.groupValues[1])?.roundToLong() ?: continue
            if (value in 0L..999_999_999L) return ShareHit(value, m.range)
        }
        return null
    }

    // ───────────────────────── 領現金 / 轉帳 ─────────────────────────

    private val CASH_OUT = Regex("領現金|提現金|提款|領錢|提領|領出|提錢|領零錢")

    private val CARD_BILL = Regex("(?:繳|還|付)?(?:信用)?卡(?:費|款|債)|還(?:信用)?卡|繳(?:信用)?卡")
    private val INVEST_WORDS = listOf("0050", "定期定額", "投資", "買股", "etf", "基金", "股票")

    private class TransferHit(val parsed: ParsedTransfer, val accountRanges: List<IntRange>)

    private fun detectTransfer(text: String, ctx: VoiceContext): TransferHit? {
        val bill = CARD_BILL.find(text)
        if (bill != null) {
            // 帳單用語本身含「信用卡」,先遮掉,免得被當成付款帳戶
            val src = pickAccount(mask(text, bill.range), ctx.accounts)
            val from = src?.ref?.takeIf { it.type != AccountType.CARD }
            val to = ctx.accounts
                .filter { it.type == AccountType.CARD && it.name.length >= 2 && text.contains(it.name, ignoreCase = true) }
                .maxByOrNull { it.name.length }
            return TransferHit(
                ParsedTransfer(TransferKind.CARD_BILL, from, to),
                if (from != null) src?.ranges.orEmpty() else emptyList(),
            )
        }
        if (INVEST_WORDS.any { text.contains(it, ignoreCase = true) }) {
            val src = pickAccount(text, ctx.accounts)
            val from = src?.ref?.takeIf { it.type != AccountType.INVEST }
            val to = ctx.accounts
                .filter { it.type == AccountType.INVEST && it.name.length >= 2 && text.contains(it.name, ignoreCase = true) }
                .maxByOrNull { it.name.length }
            return TransferHit(
                ParsedTransfer(TransferKind.INVEST, from, to),
                if (from != null) src?.ranges.orEmpty() else emptyList(),
            )
        }
        return null
    }

    // ───────────────────────── 前處理 ─────────────────────────

    /** 全形轉半形(NFKC),「臺」統一成「台」。 */
    internal fun normalize(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .replace('臺', '台')
            .trim()

    private fun isCjk(c: Char): Boolean = c.code in 0x4E00..0x9FFF

    // ───────────────────────── 金額 ─────────────────────────

    private class AmountHit(val value: Long, val removeRange: IntRange)

    private val NUM_SPAN = Regex("(?:${ChineseNumber.TOKEN_PATTERN})+")

    /** 數字後面接這些字,代表它是數量、日期或時間,不是金額(3 杯、5 號、2 人、8 折)。 */
    private val CLASSIFIER = Regex(
        "^(?:個|杯|份|張|碗|件|次|人|位|瓶|包|條|本|支|盒|袋|天|週|周|月|年|日|號|點|時|小時|分鐘|分|秒|" +
            "歲|台|顆|片|組|間|場|趟|回|隻|套|雙|口|串|折|%|公斤|公里|公尺|公升|公克|公分|克|斤|坪|樓|層|" +
            "期|集|頁|章|題|倍|成)"
    )

    private val MONEY_AFTER = listOf("新台幣", "台幣", "台币", "塊錢", "块钱", "元整", "元", "圓", "圆", "nt$", "nt")
    private val MONEY_BEFORE = listOf("新台幣", "台幣", "台币", "nt$", "nt", "$")
    private const val MONEY_FOLLOWERS = "用刷付現悠信行電轉"

    private val FILLER_BEFORE = Regex("(?:花費了|花了|花費|付了|刷了|支付了|花|共計|總共|一共|共|大概|大約|約)\\s*$")

    /** 店名裡的數字不是金額,先遮起來(長度不變,索引才不會錯位)。 */
    private val STORE_NAMES = Regex("7-?eleven|7-11|711(?!\\s*[元塊块圓圆])|(?<![0-9])00[0-9]{2,4}[a-z]?(?![0-9])", RegexOption.IGNORE_CASE)

    private fun moneyAfterLength(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i] == ' ') i++
        for (w in MONEY_AFTER) {
            if (text.startsWith(w, i, ignoreCase = true)) return i - from + w.length
        }
        if (text.startsWith("塊", i) || text.startsWith("块", i)) {
            // 「五十塊」是錢,「一塊蛋糕」是數量:塊後面接中文字(用/刷/付…除外)就當數量
            val next = text.getOrNull(i + 1)
            if (next == null || !isCjk(next) || next in MONEY_FOLLOWERS) return i - from + 1
        }
        return 0
    }

    private fun moneyBeforeLength(text: String, to: Int): Int {
        val head = text.substring(0, to).trimEnd()
        for (w in MONEY_BEFORE) {
            if (head.endsWith(w, ignoreCase = true)) return to - head.length + w.length
        }
        return 0
    }

    private fun findAmount(text: String): AmountHit? {
        val masked = STORE_NAMES.replace(text) { "_".repeat(it.value.length) }
        var best: AmountHit? = null
        var bestScore = -1
        var bestLen = -1

        for (m in NUM_SPAN.findAll(masked)) {
            val span = m.value
            val start = m.range.first
            val end = m.range.last + 1

            val moneyAfter = moneyAfterLength(masked, end)
            val moneyBefore = moneyBeforeLength(masked, start)
            val money = moneyAfter > 0 || moneyBefore > 0

            val hasDigit = span.any { it in '0'..'9' }
            val hasUnit = span.any { it in "十百千萬万" }
            val loneCnDigit = span.length == 1 && !hasDigit && !hasUnit   // 一下、三明治
            val loneUnit = span.length == 1 && hasUnit                    // 百貨、十分

            if (!money) {
                val prev = masked.getOrNull(start - 1)
                val next = masked.getOrNull(end)
                val sepAround = (prev != null && prev in ":/-") || (next != null && next in ":/-")
                if (sepAround) continue                                    // 12:30、11/18、7-11
                if (CLASSIFIER.containsMatchIn(masked.substring(end).trimStart())) continue
            }

            val score = when {
                money -> 3
                loneCnDigit || loneUnit -> 0
                hasDigit -> 2
                else -> 1
            }
            if (score == 0) continue

            val value = (ChineseNumber.parse(span) ?: continue).roundToLong()
            if (value !in 1L..999_999_999L) continue

            if (score > bestScore || (score == bestScore && span.length > bestLen)) {
                val pre = FILLER_BEFORE.find(masked.substring(0, start))
                var removeStart = pre?.range?.first ?: start
                if (moneyBefore > 0) removeStart = minOf(removeStart, start - moneyBefore)
                best = AmountHit(value, removeStart..(end + moneyAfter - 1))
                bestScore = score
                bestLen = span.length
            }
        }
        return best
    }

    // ───────────────────────── 日期 ─────────────────────────

    private class DateHit(val offset: Int, val ranges: List<IntRange>)

    private val DAYS_AGO = Regex("(\\d+|[零〇一二兩两三四五六七八九十]+)天前")

    private val DAY_WORDS = listOf(
        "大前天" to -3, "前天" to -2,
        "昨天" to -1, "昨日" to -1, "昨晚" to -1, "昨夜" to -1, "昨早" to -1,
        "今天" to 0, "今日" to 0, "今早" to 0, "今晚" to 0, "剛剛" to 0, "剛才" to 0,
    )

    private fun findDate(text: String): DateHit {
        DAYS_AGO.find(text)?.let { m ->
            val n = ChineseNumber.parse(m.groupValues[1])?.roundToInt() ?: 0
            if (n in 1..60) return DateHit(-n, listOf(m.range))
        }
        for ((word, offset) in DAY_WORDS) {
            val i = text.indexOf(word)
            if (i >= 0) return DateHit(offset, listOf(i until i + word.length))
        }
        return DateHit(0, emptyList())
    }

    // ───────────────────────── 收入 / 支出 ─────────────────────────

    private val INCOME_WORDS = listOf(
        "薪水", "薪資", "月薪", "發薪", "入帳", "進帳", "收入", "獎金", "年終", "股息", "股利",
        "利息", "退款", "退費", "退稅", "收到", "賺", "中獎", "紅利",
    )
    private val EXPENSE_VERBS = listOf("花", "買", "付", "繳", "刷", "支出", "消費")

    private fun detectExpense(text: String): Boolean {
        val income = INCOME_WORDS.any { text.contains(it) }
        if (!income) return true
        return EXPENSE_VERBS.any { text.contains(it) }
    }

    // ───────────────────────── 分類 ─────────────────────────

    // key 必須是預設分類的名稱;使用者改名或自建的分類,靠「直接說出分類名稱」來對應。
    private val EXPENSE_KEYWORDS: List<Pair<String, List<String>>> = listOf(
        "餐飲" to listOf(
            "早餐", "午餐", "晚餐", "宵夜", "早午餐", "午飯", "晚飯", "吃飯", "便當", "自助餐", "小吃", "餐廳",
            "火鍋", "燒肉", "拉麵", "牛肉麵", "水餃", "雞排", "鹽酥雞", "滷味", "麥當勞", "肯德基", "摩斯",
            "星巴克", "路易莎", "咖啡", "飲料", "手搖", "奶茶", "珍奶", "果汁", "蛋糕", "麵包", "甜點", "點心",
            "零食", "外送", "foodpanda", "ubereats", "uber eats", "聚餐", "吃", "喝",
            "7-11", "711", "全家", "萊爾富", "小七", "超商", "便利商店",
        ),
        "交通" to listOf(
            "捷運", "公車", "客運", "計程車", "小黃", "uber", "高鐵", "台鐵", "火車", "機車", "加油", "油錢",
            "汽油", "停車", "過路費", "etag", "ubike", "youbike", "搭車", "車資", "車費", "交通", "機票",
            "船票", "租車",
        ),
        "購物" to listOf(
            "衣服", "褲子", "外套", "鞋", "包包", "蝦皮", "momo", "pchome", "網購", "購物", "百貨", "超市",
            "全聯", "家樂福", "好市多", "costco", "日用品", "衛生紙", "化妝品", "保養品", "禮物",
        ),
        "娛樂" to listOf(
            "電影", "遊戲", "ktv", "唱歌", "演唱會", "netflix", "spotify", "disney", "youtube", "訂閱",
            "steam", "switch", "桌遊", "旅遊", "旅行", "住宿", "門票", "娛樂", "展覽", "漫畫",
        ),
        "居住" to listOf(
            "房租", "租金", "水費", "電費", "瓦斯", "網路費", "管理費", "電話費", "手機費", "電信費", "水電",
            "家具", "家電", "修繕", "清潔", "洗衣",
        ),
        "醫療" to listOf(
            "看醫生", "掛號", "醫院", "診所", "牙醫", "健保", "看診", "藥局", "藥", "眼科", "皮膚科", "復健",
            "體檢", "健檢", "疫苗",
        ),
        "學習" to listOf(
            "課程", "學費", "補習", "線上課", "教材", "考試", "報名費", "jlpt", "日檢", "日文", "英文", "書",
            "文具", "學習",
        ),
    )

    private val INCOME_KEYWORDS: List<Pair<String, List<String>>> = listOf(
        "薪資" to listOf("薪水", "薪資", "月薪", "發薪"),
        "獎金" to listOf("獎金", "年終", "紅包", "紅利", "中獎"),
        "投資" to listOf("股息", "股利", "利息", "投資", "基金", "股票"),
    )

    private fun pickCategory(text: String, names: List<String>, isExpense: Boolean): String? {
        if (names.isEmpty()) return null
        val scores = HashMap<String, Double>()

        // 1) 直接說出分類名稱(包含使用者自建的)
        for (n in names) {
            if (n.length >= 2 && text.contains(n, ignoreCase = true)) {
                scores[n] = (scores[n] ?: 0.0) + 10.0 + n.length
            }
        }
        // 2) 關鍵字,越長的關鍵字分數越高
        for ((cat, words) in if (isExpense) EXPENSE_KEYWORDS else INCOME_KEYWORDS) {
            if (cat !in names) continue
            var s = 0.0
            for (w in words) if (text.contains(w, ignoreCase = true)) s += w.length
            if (s > 0) scores[cat] = (scores[cat] ?: 0.0) + s
        }
        // 3) 只有「買」沒有其他線索,就當購物
        if (isExpense && scores.isEmpty() && "購物" in names && (text.contains("買") || text.contains("購"))) {
            return "購物"
        }
        // 平手時依分類清單順序,結果才穩定
        return names.filter { scores.containsKey(it) }.maxByOrNull { scores.getValue(it) }
    }

    // ───────────────────────── 帳戶 ─────────────────────────

    private class AccountHit(val ref: AccountRef?, val ranges: List<IntRange>)

    private val ACCOUNT_KEYWORDS: List<Pair<AccountType, List<String>>> = listOf(
        AccountType.EPAY to listOf(
            "悠遊卡", "悠遊付", "悠遊", "一卡通", "icash", "linepay", "line pay", "街口", "applepay", "apple pay",
            "googlepay", "google pay", "行動支付", "手機支付", "電子支付", "台灣pay", "twpay", "全支付", "歐付寶",
            "全盈",
        ),
        AccountType.CASH to listOf("現金", "零錢", "錢包"),
        AccountType.BANK to listOf("銀行", "轉帳", "提款卡", "簽帳", "金融卡", "匯款", "atm"),
        AccountType.CARD to listOf("信用卡", "刷卡", "visa", "mastercard", "jcb"),
    )

    /** 找出「用現金」「刷悠遊卡付款」這整段,備註才不會殘留。 */
    private fun phraseRanges(text: String, keyword: String): List<IntRange> {
        val re = Regex(
            "(?:使用|透過|支付|用|刷|付|以|從)?\\s*" + Regex.escape(keyword) + "\\s*(?:付款|支付|扣款|轉帳|付|扣)?",
            RegexOption.IGNORE_CASE,
        )
        val m = re.find(text) ?: return emptyList()
        return listOf(m.range)
    }

    private fun pickAccount(text: String, accounts: List<AccountRef>): AccountHit? {
        // 1) 直接說出帳戶名稱(例如自己建的「玉山卡」「LINE Pay」),取最長的
        val named = accounts
            .filter { it.name.length >= 2 && text.contains(it.name, ignoreCase = true) }
            .maxByOrNull { it.name.length }
        if (named != null) return AccountHit(named, phraseRanges(text, named.name))

        // 2) 關鍵字 → 帳戶類型(使用者沒有該類型的帳戶時,仍會把這段字從備註拿掉)
        for ((type, words) in ACCOUNT_KEYWORDS) {
            val kw = words.firstOrNull { text.contains(it, ignoreCase = true) } ?: continue
            return AccountHit(accounts.firstOrNull { it.type == type }, phraseRanges(text, kw))
        }
        // 3) 只說「刷」,當信用卡
        if (text.contains("刷")) {
            return AccountHit(accounts.firstOrNull { it.type == AccountType.CARD }, phraseRanges(text, "刷"))
        }
        return null
    }

    // ───────────────────────── 備註 ─────────────────────────

    private val LEADING_FILLER = Regex("^\\s*(?:幫我記一筆|幫我記|記一筆|記帳|記錄|新增|我)\\s*")
    private val PUNCT_RUN = Regex("[\\s,，。.!！?？、;；:：]+")

    private fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
        val sorted = ranges.filter { !it.isEmpty() }.sortedBy { it.first }
        val out = mutableListOf<IntRange>()
        for (r in sorted) {
            val last = out.lastOrNull()
            if (last != null && r.first <= last.last + 1) {
                out[out.lastIndex] = last.first..maxOf(last.last, r.last)
            } else {
                out += r
            }
        }
        return out
    }

    private fun buildNote(text: String, removals: List<IntRange>, category: String?): String {
        val sb = StringBuilder(text)
        for (r in mergeRanges(removals).asReversed()) {
            val s = r.first.coerceAtLeast(0)
            val e = (r.last + 1).coerceAtMost(sb.length)
            if (s < e) sb.delete(s, e)
        }
        var note = LEADING_FILLER.replace(sb.toString(), " ")
        note = PUNCT_RUN.replace(note, " ").trim()
        if (category != null && note == category) return ""
        return note.take(60)
    }
}
