package com.example.ledger.data

/** 代墊時,某個人分擔的金額(輸入用)。 */
data class ShareInput(val personId: Long, val amount: Long)

/** 代墊分攤的計算(純 Kotlin,不依賴 Android,方便單元測試)。 */
object ShareMath {
    /** 把 [total] 平均分成 [n] 份,除不盡的餘數從第一份開始每份多 1 元。 */
    fun split(total: Long, n: Int): List<Long> {
        if (n <= 0) return emptyList()
        if (total <= 0) return List(n) { 0L }
        val base = total / n
        val extra = (total % n).toInt()
        return List(n) { i -> base + if (i < extra) 1 else 0 }
    }

    /**
     * 沒填「我的份」時:總額平均分給 [n] 個人加上自己(n+1 份),
     * 回傳這 n 個人各自的金額;剩下的(含餘數)是我的份。
     */
    fun splitWithMe(amount: Long, n: Int): List<Long> {
        if (n <= 0) return emptyList()
        return split(amount, n + 1).drop(1)
    }

    /**
     * 整理要存的分攤:丟掉 0 以下的、同一個人合併、總和不超過 [owed](別人該付的總額)。
     * 超過的部分從後面的人開始截掉。
     */
    fun normalize(inputs: List<ShareInput>, owed: Long): List<ShareInput> {
        if (owed <= 0) return emptyList()
        val merged = LinkedHashMap<Long, Long>()
        for (i in inputs) if (i.amount > 0) merged[i.personId] = (merged[i.personId] ?: 0L) + i.amount
        var left = owed
        val out = ArrayList<ShareInput>()
        for ((id, amount) in merged) {
            if (left <= 0) break
            val a = minOf(amount, left)
            out += ShareInput(id, a)
            left -= a
        }
        return out
    }

    /** 還沒指定是誰的金額 = 實付 − 我的份 − 已指定給各人的總和(沒有「我的份」就是 0)。 */
    fun unassigned(amount: Long, myShare: Long?, assigned: Long): Long =
        if (myShare == null) 0L else maxOf(0L, amount - myShare - assigned)
}
