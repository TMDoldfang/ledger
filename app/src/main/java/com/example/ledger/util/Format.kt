package com.example.ledger.util

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

fun formatMoney(value: Long): String {
    val digits = NumberFormat.getIntegerInstance(Locale.TAIWAN).format(abs(value))
    return (if (value < 0) "-" else "") + "NT$" + digits
}

fun YearMonth.startMillis(zone: ZoneId): Long =
    atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

fun Long.toLocalDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
