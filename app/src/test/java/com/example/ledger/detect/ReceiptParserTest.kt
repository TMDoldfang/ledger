package com.example.ledger.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ReceiptParserTest {
    // 中信 uniopen 卡的刷卡通知頁(實際截圖的 OCR 文字,卡號已遮)
    private val uniopen = listOf(
        "刷卡通知", "NT$99", "卡末四碼:", "交易時間:2026/11/25 12:25", "商店名稱:統一超商-國企",
        "*商店名稱僅供參考,實際以帳單為準",
        "*OPENPOINT預計於商店請款後5個營業日內完成一般消費回饋。",
        "*不回饋交易項目含部分醫療院所/分期/歐洲實體商店等",
    )

    @Test fun ctbcUniopenPage() {
        val r = ReceiptParser.parse(uniopen)
        assertEquals(99L, r.amount)
        assertEquals(LocalDate.of(2026, 11, 25), r.date)
        assertEquals(LocalTime.of(12, 25), r.time)
        assertEquals("統一超商-國企", r.merchant)
    }

    @Test fun ctbcLinePayPageWithFullWidthStore() {
        val r = ReceiptParser.parse(
            listOf("刷卡通知", "NT$13", "交易時間:2026/11/25 12:23", "商店名稱:全家便利店ＬＩＮＥ Ｐａｙ",
                "*國內外一般消費1%,於商店請款後5個營業日內撥入"),
        )
        assertEquals(13L, r.amount)
        assertEquals("全家便利店LINE Pay", r.merchant)
    }

    @Test fun otherBankLabelledAmountAndRocYear() {
        val r = ReceiptParser.parse(
            listOf("信用卡消費通知", "消費金額 NT$1,280", "消費日期 115/11/25 18:30", "特店名稱 星巴克 信義店"),
        )
        assertEquals(1280L, r.amount)
        assertEquals(LocalDate.of(2026, 11, 25), r.date)
        assertEquals(LocalTime.of(18, 30), r.time)
        assertEquals("星巴克 信義店", r.merchant)
    }

    @Test fun amountWithYuanOnly() =
        assertEquals(350L, ReceiptParser.parse(listOf("您已刷卡消費 350 元", "2026-11-25 09:10")).amount)

    @Test fun amountSplitAcrossLines() =
        assertEquals(88L, ReceiptParser.parse(listOf("NT$", "88")).amount)

    @Test fun merchantOnNextLine() =
        assertEquals("麥當勞", ReceiptParser.parse(listOf("商店名稱", "麥當勞", "NT$120")).merchant)

    @Test fun rewardPercentIsNotTheAmount() =
        assertNull(ReceiptParser.parse(listOf("刷卡通知", "回饋 NT$5", "一般消費1%")).amount)

    @Test fun noAmountFound() = assertNull(ReceiptParser.parse(listOf("你好", "謝謝")).amount)

    @Test fun cardNumberNeverInMerchant() {
        val r = ReceiptParser.parse(
            listOf("NT$200", "卡號 4311-2233-4455-6677", "商店名稱:某某商店 1234 ****5678"),
        )
        assertEquals(200L, r.amount)
        assertFalse(Regex("[0-9]{4,}").containsMatchIn(r.merchant.orEmpty()))
        assertFalse(r.merchant.orEmpty().contains("*"))
    }

    @Test fun cardLastFourNotTakenAsAmount() =
        assertNull(ReceiptParser.parse(listOf("卡末四碼:1234", "商店名稱:某店")).amount)

    @Test fun invalidDateIgnored() = assertNull(ReceiptParser.parse(listOf("NT$5", "2026/13/45 10:00")).date)
}
