package com.example.ledger.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenericAmountTest {
    @Test fun ntDollar() = assertEquals(1234L, GenericAmount.find("您於 11/27 消費 NT$1,234 已授權"))
    @Test fun ntDollarWithSpace() = assertEquals(99L, GenericAmount.find("消費 NT$ 99"))
    @Test fun twd() = assertEquals(500L, GenericAmount.find("刷卡 TWD 500"))
    @Test fun yuan() = assertEquals(320L, GenericAmount.find("本次消費 320 元"))
    @Test fun labeledWins() = assertEquals(100L, GenericAmount.find("消費金額 \$100,帳戶餘額 \$5000"))
    @Test fun ambiguousIsNull() = assertNull(GenericAmount.find("滿 \$500 折 \$50"))
    @Test fun noAmount() = assertNull(GenericAmount.find("登入驗證碼 123456"))
    @Test fun sameAmountTwiceOk() = assertEquals(88L, GenericAmount.find("NT\$88 … 共 NT\$88"))
}

class CustomSourcesTest {
    private val s = CustomSource("1", "com.bank", "某銀行", "消費通知")

    @Test fun roundTrip() {
        val list = listOf(s, s.copy(id = "2", titleKey = "a\u001Fb\nc", enabled = false, count = 7))
        val back = CustomSources.decode(CustomSources.encode(list))
        assertEquals(2, back.size)
        assertEquals(false, back[1].enabled)
        assertEquals(7, back[1].count)
        assertEquals("a b c", back[1].titleKey)
    }

    @Test fun matchNeedsSameAppAndTitle() {
        assertEquals(s, CustomSources.match(listOf(s), "com.bank", "刷卡消費通知"))
        assertNull(CustomSources.match(listOf(s), "com.other", "刷卡消費通知"))
        assertNull(CustomSources.match(listOf(s), "com.bank", "優惠活動"))
    }

    @Test fun pausedNeverMatches() = assertNull(CustomSources.match(listOf(s.copy(enabled = false)), "com.bank", "消費通知"))

    @Test fun blankKeyNeverMatches() = assertNull(CustomSources.match(listOf(s.copy(titleKey = "")), "com.bank", "任何"))

    @Test fun recentDedupesAndCaps() {
        var list = emptyList<RecentNotice>()
        repeat(40) { list = CustomSources.addRecent(list, RecentNotice("p", "A", "t$it")) }
        assertEquals(CustomSources.MAX_RECENT, list.size)
        list = CustomSources.addRecent(list, RecentNotice("p", "A", "t30"))
        assertEquals(CustomSources.MAX_RECENT, list.size)
        assertEquals("t30", list.first().title)
        assertEquals(1, list.count { it.title == "t30" })
    }

    @Test fun bumpCounts() = assertEquals(1, CustomSources.bump(listOf(s), "1").single().count)

    @Test fun recentRoundTrip() {
        val l = listOf(RecentNotice("p", "A", "標題"))
        assertEquals(l, CustomSources.decodeRecent(CustomSources.encodeRecent(l)))
    }

    @Test fun textKeyMustAppearWhenSet() {
        val src = s.copy(textKey = "消費")
        assertEquals(src, CustomSources.match(listOf(src), "com.bank", "消費通知", "您消費 NT$100"))
        assertNull(CustomSources.match(listOf(src), "com.bank", "消費通知", "登入驗證"))
    }

    @Test fun textKeyRoundTripAndOldFormat() {
        val src = s.copy(textKey = "消費")
        assertEquals("消費", CustomSources.decode(CustomSources.encode(listOf(src))).single().textKey)
        // 舊格式(6 欄)仍可讀
        val old = listOf("1", "com.bank", "某銀行", "標題", "1", "3").joinToString("\u001F")
        assertEquals("", CustomSources.decode(old).single().textKey)
    }

    @Test fun activeNamesFollowToggles() {
        val c = listOf(s.copy(titleKey = "臺灣銀行"), s.copy(id = "2", titleKey = "別家", enabled = false))
        assertEquals(listOf("臺灣銀行"), CustomSources.activeNames(false, false, c))
        assertEquals(listOf("中國信託(刷卡通知)", "LINE錢包(付款通知)", "臺灣銀行"), CustomSources.activeNames(true, true, c))
        assertEquals(emptyList<String>(), CustomSources.activeNames(false, false, emptyList()))
    }
}
