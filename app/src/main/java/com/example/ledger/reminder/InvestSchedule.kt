package com.example.ledger.reminder

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/**
 * 一組定期定額:每月 [debitDay] 號扣款(遇假日順延由使用者自己把提醒日設晚一點),
 * [remindDay] 號中午如果還沒記錄「轉入投資帳戶」就提醒。扣款日、提醒日都可以隨時改,也可以有很多組。
 * 日期超過該月天數(例如 31 號遇到 2 月)就用該月最後一天。
 */
data class InvestPlan(val id: Int, val name: String, val debitDay: Int, val remindDay: Int)

/** 定期定額的排程與判斷(純 Kotlin、不依賴 Android,方便單元測試)。 */
object InvestSchedule {
    val REMIND_TIME: LocalTime = LocalTime.NOON
    const val DEFAULT_NAME = "0050 定期定額"
    const val MAX_PLANS = 10

    /** 還沒設定過時的預設:每月 6 號扣款、8 號提醒。 */
    fun defaultPlan() = InvestPlan(1, DEFAULT_NAME, debitDay = 6, remindDay = 8)

    private fun dayIn(month: YearMonth, day: Int): LocalDate = month.atDay(day.coerceIn(1, month.lengthOfMonth()))

    /** 這組在 [month] 的提醒時間。 */
    fun remindAt(plan: InvestPlan, month: YearMonth): LocalDateTime = dayIn(month, plan.remindDay).atTime(REMIND_TIME)

    /** [now] 之後(不含剛好同一刻)最近的一次提醒時間。 */
    fun nextReminder(plan: InvestPlan, now: LocalDateTime): LocalDateTime {
        val month = YearMonth.from(now)
        val thisMonth = remindAt(plan, month)
        return if (thisMonth.isAfter(now)) thisMonth else remindAt(plan, month.plusMonths(1))
    }

    /** 在 [remindAt] 這個提醒時間點,「最近一次已經到的扣款日」。提醒日在扣款日之前,就是上個月的扣款日。 */
    fun debitDate(plan: InvestPlan, remindAt: LocalDateTime): LocalDate {
        val month = YearMonth.from(remindAt)
        val candidate = dayIn(month, plan.debitDay)
        return if (!candidate.isAfter(remindAt.toLocalDate())) candidate else dayIn(month.minusMonths(1), plan.debitDay)
    }

    /** 要檢查「有沒有記錄」的起點:扣款日那個月的 1 號。 */
    fun windowStart(plan: InvestPlan, remindAt: LocalDateTime): LocalDate = debitDate(plan, remindAt).withDayOfMonth(1)

    /**
     * 到這個提醒時間點,這個月至少該有幾筆轉入投資的記錄。
     * 同一個月有好幾組時,依扣款日先後各算一筆:第 1 組要 1 筆、第 2 組要 2 筆……,
     * 這樣一筆記錄不會同時滿足兩組。
     */
    fun transfersNeeded(plan: InvestPlan, all: List<InvestPlan>, remindAt: LocalDateTime): Int {
        val month = YearMonth.from(debitDate(plan, remindAt))
        return all.count { p ->
            YearMonth.from(debitDate(p, remindAt)) == month &&
                (p.debitDay < plan.debitDay || (p.debitDay == plan.debitDay && p.id <= plan.id))
        }
    }

    // --- 存取(每組一行,欄位用 Tab 分隔;名稱裡的換行與 Tab 會被拿掉) ---

    fun cleanName(name: String): String =
        name.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim().take(20).ifEmpty { "定期定額" }

    fun encode(plans: List<InvestPlan>): String =
        plans.joinToString("\n") { "${it.id}\t${cleanName(it.name)}\t${it.debitDay}\t${it.remindDay}" }

    fun decode(text: String?): List<InvestPlan> {
        if (text == null) return emptyList()
        return text.lines().mapNotNull { line ->
            val f = line.split('\t')
            if (f.size != 4) return@mapNotNull null
            val id = f[0].toIntOrNull() ?: return@mapNotNull null
            val debit = f[2].toIntOrNull() ?: return@mapNotNull null
            val remind = f[3].toIntOrNull() ?: return@mapNotNull null
            InvestPlan(id, cleanName(f[1]), debit.coerceIn(1, 31), remind.coerceIn(1, 31))
        }.take(MAX_PLANS)
    }

    /** 新增用的下一個 id。 */
    fun nextId(plans: List<InvestPlan>): Int = (plans.maxOfOrNull { it.id } ?: 0) + 1
}
