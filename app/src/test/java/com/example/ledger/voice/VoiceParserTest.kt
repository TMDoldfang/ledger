package com.example.ledger.voice

import com.example.ledger.data.AccountType
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 語音解析的單元測試。想讓 App 聽懂新說法,先在這裡加一行,再去改 VoiceParser。
 * 在 Android Studio 對這個檔案按右鍵 → Run 即可,不需要模擬器。
 */
class VoiceParserTest {
    private val cash = AccountRef("現金", AccountType.CASH)
    private val bank = AccountRef("銀行", AccountType.BANK)
    private val card = AccountRef("信用卡", AccountType.CARD)
    private val epay = AccountRef("電子支付", AccountType.EPAY)

    private val ctx = VoiceContext(
        expenseCategories = listOf("餐飲", "交通", "購物", "娛樂", "居住", "醫療", "學習", "其他"),
        incomeCategories = listOf("薪資", "獎金", "投資", "其他"),
        accounts = listOf(cash, bank, card, epay),
    )

    private fun parse(text: String, c: VoiceContext = ctx) = VoiceParser.parse(text, c)

    // ── 中文數字 ──
    @Test fun chineseNumbers() {
        fun n(s: String) = ChineseNumber.parse(s)?.toLong()
        assertEquals(120L, n("一百二"))
        assertEquals(250L, n("兩百五"))
        assertEquals(35L, n("三十五"))
        assertEquals(12L, n("十二"))
        assertEquals(20L, n("二十"))
        assertEquals(365L, n("三百六十五"))
        assertEquals(1005L, n("一千零五"))
        assertEquals(2050L, n("兩千零五十"))
        assertEquals(102L, n("一百零二"))
        assertEquals(1200L, n("一千二百"))
        assertEquals(12000L, n("一萬二"))
        assertEquals(12500L, n("一萬兩千五百"))
        assertEquals(35000L, n("三萬五"))
        assertEquals(25000L, n("兩萬五千"))
        assertEquals(100000L, n("十萬"))
        assertEquals(3500L, n("3千5"))
        assertEquals(15000L, n("1.5萬"))
        assertEquals(1200L, n("1,200"))
        assertEquals(120L, n("120"))
    }

    // ── 金額 ──
    @Test fun amountWithSpaces() {
        val r = parse("午餐 120 悠遊卡")
        assertEquals(120L, r.amount)
        assertEquals(true, r.isExpense)
        assertEquals("餐飲", r.categoryName)
        assertEquals(epay, r.account)
        assertEquals("午餐", r.note)
    }

    @Test fun chineseAmountAndCash() {
        val r = parse("晚餐花了兩百五用現金")
        assertEquals(250L, r.amount)
        assertEquals("餐飲", r.categoryName)
        assertEquals(cash, r.account)
        assertEquals("晚餐", r.note)
    }

    @Test fun yesterdayCreditCard() {
        val r = parse("昨天買衣服一千二 信用卡")
        assertEquals(1200L, r.amount)
        assertEquals("購物", r.categoryName)
        assertEquals(card, r.account)
        assertEquals(-1, r.dayOffset)
        assertEquals("買衣服", r.note)
    }

    @Test fun transport() {
        val r = parse("搭捷運 三十五")
        assertEquals(35L, r.amount)
        assertEquals("交通", r.categoryName)
    }

    @Test fun rent() {
        val r = parse("房租 一萬二")
        assertEquals(12000L, r.amount)
        assertEquals("居住", r.categoryName)
    }

    @Test fun moneyWordYuan() {
        val r = parse("咖啡 85元")
        assertEquals(85L, r.amount)
        assertEquals("餐飲", r.categoryName)
        assertEquals("咖啡", r.note)
    }

    @Test fun quantityIsNotAmount() {
        assertEquals(85L, parse("一杯咖啡 85").amount)
        assertEquals(55L, parse("三明治 55").amount)
        assertEquals(600L, parse("兩個人吃飯 600 現金").amount)
        assertEquals(160L, parse("一碗牛肉麵 一百六").amount)
        assertEquals(1200L, parse("百貨公司買衣服 1200").amount)
    }

    @Test fun dollarSignAndThousandsSeparator() {
        val r = parse("NT$1,200 買鞋子")
        assertEquals(1200L, r.amount)
        assertEquals("購物", r.categoryName)
    }

    @Test fun chineseTensWithKuai() {
        val r = parse("麥當勞 兩百五十塊 刷卡")
        assertEquals(250L, r.amount)
        assertEquals("餐飲", r.categoryName)
        assertEquals(card, r.account)
    }

    @Test fun storeNameDigitsAreIgnored() {
        assertEquals(55L, parse("7-11 買咖啡 55").amount)
    }

    @Test fun timeAndDateDigitsAreIgnored() {
        assertEquals(120L, parse("12:30 午餐 120").amount)
        assertEquals(120L, parse("11/18 午餐 120").amount)
    }

    @Test fun decimalWan() {
        assertEquals(15000L, parse("房租 1.5萬").amount)
    }

    @Test fun noAmount() {
        assertNull(parse("今天好累").amount)
        assertNull(parse("").amount)
    }

    // ── 日期 ──
    @Test fun daysAgo() {
        val r = parse("三天前買書 三百五")
        assertEquals(-3, r.dayOffset)
        assertEquals(350L, r.amount)
        assertEquals("學習", r.categoryName)
        assertEquals("買書", r.note)
    }

    @Test fun dayBeforeYesterday() {
        assertEquals(-2, parse("前天 午餐 100").dayOffset)
        assertEquals(-3, parse("大前天 午餐 100").dayOffset)
    }

    // ── 收入 ──
    @Test fun salary() {
        val r = parse("薪水入帳 五萬三")
        assertEquals(53000L, r.amount)
        assertEquals(false, r.isExpense)
        assertEquals("薪資", r.categoryName)
    }

    @Test fun bonus() {
        val r = parse("年終獎金 十萬")
        assertEquals(100000L, r.amount)
        assertEquals(false, r.isExpense)
        assertEquals("獎金", r.categoryName)
    }

    @Test fun earnedToday() {
        val r = parse("今天賺了 五百")
        assertEquals(500L, r.amount)
        assertEquals(false, r.isExpense)
    }

    // ── 分類與帳戶 ──
    @Test fun genericBuyIsShopping() {
        assertEquals("購物", parse("買東西 300").categoryName)
    }

    @Test fun unknownCategoryIsNull() {
        assertNull(parse("隨便 300").categoryName)
    }

    @Test fun customCategoryByName() {
        val c = ctx.copy(expenseCategories = ctx.expenseCategories + "咖啡")
        assertEquals("咖啡", parse("咖啡 85", c).categoryName)
    }

    @Test fun customAccountByName() {
        val linePay = AccountRef("LINE Pay", AccountType.EPAY)
        val c = ctx.copy(accounts = ctx.accounts + linePay)
        assertEquals(linePay, parse("午餐 100 line pay", c).account)
    }

    @Test fun accountTypeMissing() {
        val c = ctx.copy(accounts = listOf(cash))
        assertNull(parse("午餐 100 刷卡", c).account)
    }

    @Test fun swipeEasyCardIsNotCreditCard() {
        val c = ctx.copy(accounts = listOf(cash, card))   // 沒有電子支付帳戶
        assertNull(parse("刷悠遊卡 午餐 100", c).account)
    }

    // ── 備註清理 ──
    @Test fun noteDropsPaymentPhrases() {
        assertEquals("房租", parse("房租 一萬兩千 銀行轉帳").note)
        assertEquals("中午吃麥當勞", parse("今天中午吃麥當勞 150 元 用 line pay", ctx.copy(accounts = ctx.accounts + AccountRef("LINE Pay", AccountType.EPAY))).note)
        assertEquals("晚餐", parse("晚餐 一百八十 刷卡 付款").note)
        assertEquals("", parse("我用悠遊卡付了一百").note)
    }

    @Test fun payVerbBeforeAmount() {
        val r = parse("我用悠遊卡付了一百")
        assertEquals(100L, r.amount)
        assertEquals(epay, r.account)
    }

    @Test fun uberEatsIsFoodNotTransport() {
        assertEquals("餐飲", parse("uber eats 350").categoryName)
        assertEquals("交通", parse("uber 280").categoryName)
    }

    // ── 代墊:總額與我的份 ──
    private val invest = AccountRef("投資", AccountType.INVEST)
    private val ctx2 = ctx.copy(
        expenseCategories = ctx.expenseCategories + "現金領出",
        accounts = ctx.accounts + invest,
    )

    @Test fun shareAfterTotal() {
        val r = parse("晚餐 1200 我的 300 信用卡", ctx2)
        assertEquals(1200L, r.amount)
        assertEquals(300L, r.myShare)
        assertEquals("餐飲", r.categoryName)
        assertEquals(card, r.account)
        assertEquals("晚餐", r.note)
    }

    @Test fun shareChineseNumbers() {
        val r = parse("晚餐一千二 我出三百 刷卡", ctx2)
        assertEquals(1200L, r.amount)
        assertEquals(300L, r.myShare)
        assertEquals(card, r.account)
    }

    @Test fun shareWithShi() {
        val r = parse("晚餐1200，我的是300", ctx2)
        assertEquals(1200L, r.amount)
        assertEquals(300L, r.myShare)
    }

    @Test fun myAloneIsNotShare() {
        val r = parse("我的午餐 120 悠遊卡", ctx2)
        assertEquals(120L, r.amount)
        assertNull(r.myShare)
    }

    @Test fun singleNumberAfterMyIsTotal() {
        val r = parse("我的 120 午餐", ctx2)
        assertEquals(120L, r.amount)
        assertNull(r.myShare)
    }

    // ── 領現金 ──
    @Test fun cashOut() {
        val r = parse("領現金 三千 銀行", ctx2)
        assertEquals(3000L, r.amount)
        assertTrue(r.isCashOut)
        assertEquals("現金領出", r.categoryName)
        assertEquals(bank, r.account)
    }

    @Test fun cashOutWithoutAccountDoesNotPickCashAccount() {
        val r = parse("提款 兩千", ctx2)
        assertEquals(2000L, r.amount)
        assertTrue(r.isCashOut)
        assertNull(r.account)
    }

    // ── 轉帳:繳卡費、投資 ──
    @Test fun cardBill() {
        val r = parse("繳卡費 兩萬 銀行", ctx2)
        assertEquals(20000L, r.amount)
        assertEquals(TransferKind.CARD_BILL, r.transfer?.kind)
        assertEquals(bank, r.account)
        assertNull(r.transfer?.to)
    }

    @Test fun cardBillWithoutSourceDoesNotUseCardAsSource() {
        val r = parse("還信用卡費 一萬五", ctx2)
        assertEquals(15000L, r.amount)
        assertEquals(TransferKind.CARD_BILL, r.transfer?.kind)
        assertNull(r.account)
    }

    @Test fun investEtfCodeIsNotAmount() {
        val r = parse("0050 定期定額 三千", ctx2)
        assertEquals(3000L, r.amount)
        assertEquals(TransferKind.INVEST, r.transfer?.kind)
    }

    @Test fun dividendIsIncomeNotTransfer() {
        val r = parse("0050 股息 入帳 五百", ctx2)
        assertEquals(500L, r.amount)
        assertNull(r.transfer)
        assertEquals(false, r.isExpense)
    }
}
