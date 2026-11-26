package com.example.ledger.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class NoticeParserTest {
    @Test fun ctbcLinePay() =
        assertEquals(DetectedCard.LINE_PAY, NoticeParser.ctbcCard("🔔LINE Pay聯名卡消費通知🔔"))

    @Test fun ctbcUniopen() =
        assertEquals(DetectedCard.UNIOPEN, NoticeParser.ctbcCard("🔔uniopen聯名卡消費通知🔔"))

    @Test fun ctbcUnknownCard() =
        assertEquals(DetectedCard.UNKNOWN, NoticeParser.ctbcCard("🔔某某聯名卡消費通知🔔"))

    @Test fun ctbcNotAPurchase() = assertNull(NoticeParser.ctbcCard("本月帳單已產生"))

    @Test fun walletAmountWithSpace() =
        assertEquals(13L, NoticeParser.walletAmount("LINE Pay 付款 NT$ 13 付款完成。(本交易成立與否應依商店系統顯示結果為準。)"))

    @Test fun walletAmountNoSpace() = assertEquals(216L, NoticeParser.walletAmount("LINE Pay 付款 NT$216 付款完成。"))

    @Test fun walletAmountThousands() = assertEquals(1234L, NoticeParser.walletAmount("LINE Pay 付款 NT$ 1,234 付款完成。"))

    @Test fun walletNotPayment() = assertNull(NoticeParser.walletAmount("LINE Points 回饋 5 點"))

    @Test fun senders() {
        assertEquals(true, NoticeParser.isRelevantSender("中國信託"))
        assertEquals(true, NoticeParser.isRelevantSender("LINE錢包"))
        assertEquals(false, NoticeParser.isRelevantSender("Dee / 美繡"))
    }

    @Test fun guessAccount() {
        val accounts = listOf(1L to "現金", 2L to "LINE Pay 卡", 3L to "統一企業卡")
        assertEquals(2L, NoticeParser.guessAccountId(DetectedCard.LINE_PAY, accounts))
        assertEquals(3L, NoticeParser.guessAccountId(DetectedCard.UNIOPEN, accounts))
        assertNull(NoticeParser.guessAccountId(DetectedCard.UNKNOWN, accounts))
        assertNull(NoticeParser.guessAccountId(DetectedCard.UNIOPEN, listOf(1L to "現金")))
    }
}

class PairingTest {
    private val t = 1_000_000L

    @Test fun walletFirstThenCtbc() {
        val picked = Pairing.pickWallet(listOf(Pairing.WalletNotice(13, t - 3_000)), t)
        assertEquals(13L, picked?.amount)
    }

    @Test fun pickClosestWallet() {
        val picked = Pairing.pickWallet(
            listOf(Pairing.WalletNotice(10, t - 100_000), Pairing.WalletNotice(20, t - 2_000)), t,
        )
        assertEquals(20L, picked?.amount)
    }

    @Test fun walletTooOldIgnored() =
        assertNull(Pairing.pickWallet(listOf(Pairing.WalletNotice(13, t - 3 * 60_000)), t))

    @Test fun ctbcFirstThenWallet() {
        val picked = Pairing.pickAwaiting(listOf(Pairing.AwaitingRow(5, t - 1_000)), t)
        assertEquals(5L, picked?.id)
    }

    @Test fun awaitingTooOldIgnored() =
        assertNull(Pairing.pickAwaiting(listOf(Pairing.AwaitingRow(5, t - 5 * 60_000)), t))
}

class PendingScheduleTest {
    @Test fun beforeNinePmIsToday() =
        assertEquals(LocalDateTime.of(2026, 11, 26, 21, 0), PendingSchedule.nextReminder(LocalDateTime.of(2026, 11, 26, 10, 0)))

    @Test fun afterNinePmIsTomorrow() =
        assertEquals(LocalDateTime.of(2026, 11, 27, 21, 0), PendingSchedule.nextReminder(LocalDateTime.of(2026, 11, 26, 21, 0)))

    @Test fun monthEndRollsOver() =
        assertEquals(LocalDateTime.of(2026, 12, 1, 21, 0), PendingSchedule.nextReminder(LocalDateTime.of(2026, 11, 30, 22, 30)))
}
