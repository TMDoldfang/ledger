package com.example.ledger.voice

import kotlin.math.floor

/**
 * 把「一百二」「兩萬五」「3千5」「1,200」這類寫法轉成數字。
 * 純 Kotlin,沒有 Android 依賴,可以直接跑單元測試。
 */
object ChineseNumber {
    /** 一段數字由這些小單位接起來:阿拉伯數字(可含千分位與小數點),或單一個中文數字 / 單位字。 */
    const val TOKEN_PATTERN =
        """\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?|[零〇一二兩两三四五六七八九十百千萬万]"""

    private val TOKEN = Regex(TOKEN_PATTERN)

    private fun cnDigit(c: Char): Int = when (c) {
        '零', '〇' -> 0
        '一' -> 1
        '二', '兩', '两' -> 2
        '三' -> 3
        '四' -> 4
        '五' -> 5
        '六' -> 6
        '七' -> 7
        '八' -> 8
        '九' -> 9
        else -> -1
    }

    private fun unitOf(c: Char): Long = when (c) {
        '十' -> 10L
        '百' -> 100L
        '千' -> 1000L
        '萬', '万' -> 10000L
        else -> 0L
    }

    /**
     * 解析一段純數字文字,例如「一百二」→ 120、「兩萬五」→ 25000、「1.5萬」→ 15000。
     * 口語省略規則:單位後面緊接一個個位數,代表下一個較小的單位
     * (一百二 = 120、三萬五 = 35000);中間有「零」就照字面加(一千零五 = 1005)。
     */
    fun parse(span: String): Double? {
        var total = 0.0          // 已結算的「萬」以上部分
        var section = 0.0        // 目前這一段(萬以下)
        var pending: Double? = null   // 還沒遇到單位的數字
        var lastUnit = 0L
        var zeroSeen = false
        var any = false

        for (m in TOKEN.findAll(span)) {
            any = true
            val t = m.value
            val c = t[0]

            if (c in '0'..'9') {
                val v = t.replace(",", "").toDoubleOrNull() ?: continue
                val p = pending
                if (p != null) section += p
                pending = v
                continue
            }

            val d = cnDigit(c)
            if (d >= 0) {
                val p = pending
                if (d == 0) {
                    if (p != null) pending = p * 10 else zeroSeen = true
                } else {
                    pending = if (p == null) d.toDouble() else p * 10 + d
                }
                continue
            }

            val u = unitOf(c)
            if (u == 10000L) {
                section += pending ?: 0.0
                pending = null
                if (section == 0.0) section = 1.0
                total += section * 10000.0
                section = 0.0
                lastUnit = 10000L
                zeroSeen = false
            } else {
                val n = pending ?: 1.0
                section += n * u
                pending = null
                lastUnit = u
            }
        }

        if (!any) return null
        val p = pending
        if (p != null) {
            val single = p >= 1.0 && p <= 9.0 && p == floor(p)
            section += if (single && lastUnit >= 10L && !zeroSeen) p * (lastUnit / 10).toDouble() else p
        }
        return total + section
    }
}
