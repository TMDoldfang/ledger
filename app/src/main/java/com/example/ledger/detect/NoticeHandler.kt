package com.example.ledger.detect

import com.example.ledger.data.LedgerRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 收到 LINE 官方帳號的通知後,依規則放進「待確認」:
 *  - 中信「LINE Pay聯名卡」通知:新增一筆;有配對到 LINE錢包的金額就一起填上
 *  - 中信「uniopen聯名卡」等其他卡:新增一筆,金額空白
 *  - LINE錢包的付款通知:只用來補 LINE Pay 卡的金額,本身不會變成一筆帳目
 */
class NoticeHandler(private val repo: LedgerRepository) {
    private val mutex = Mutex()
    private val orphans = ArrayList<Pairing.WalletNotice>()
    private val seen = ArrayDeque<String>()
    private val recent = ArrayDeque<String>()

    /** @return 一段簡短的處理結果(給設定頁顯示用),不相關的通知回傳 null。 */
    suspend fun handle(sender: String, text: String, postTime: Long, key: String, whenMs: Long = 0L): String? = mutex.withLock {
        // LINE 可能重複發同一則通知(更新),用 key + 時間去重
        val dedupe = "$key@$postTime"
        if (dedupe in seen) return null
        seen.addLast(dedupe)
        while (seen.size > 50) seen.removeFirst()

        // 同一則通知被重送(發出時間相同)就不重複建立;新的刷卡發出時間一定不同,照常建立
        val fingerprint = Dedupe.fingerprint(sender, text, whenMs)
        if (Dedupe.isDuplicate(recent, fingerprint)) return null
        if (fingerprint != null) {
            recent.addLast(fingerprint)
            while (recent.size > 100) recent.removeFirst()
        }

        orphans.removeAll { postTime - it.time > Pairing.WINDOW_MS * 2 }

        if (NoticeParser.isCtbc(sender)) {
            val card = NoticeParser.ctbcCard(text) ?: return null
            var amount: Long? = null
            if (card == DetectedCard.LINE_PAY) {
                val wallet = Pairing.pickWallet(orphans, postTime)
                if (wallet != null) {
                    orphans.remove(wallet)
                    amount = wallet.amount
                }
            }
            repo.addPending(card.name, amount, postTime)
            return "${card.label}" + (amount?.let { " NT$$it" } ?: "(金額待補)")
        }

        if (NoticeParser.isWallet(sender)) {
            val amount = NoticeParser.walletAmount(text) ?: return null
            val awaiting = repo.pendingAwaitingAmount(postTime - Pairing.WINDOW_MS)
                .map { Pairing.AwaitingRow(it.id, it.timestamp) }
            val row = Pairing.pickAwaiting(awaiting, postTime)
            if (row != null) {
                repo.fillPendingAmount(row.id, amount)
                return "LINE Pay 卡 NT$$amount"
            }
            // 中信通知還沒到:先記著,等它來了再配對
            orphans.add(Pairing.WalletNotice(amount, postTime))
            return "LINE錢包 NT$$amount(等中信通知)"
        }
        return null
    }

    /**
     * 自訂來源的通知:用通用規則抓金額,抓不到就直接忽略(不建立待補金額,避免雜訊)。
     * @return 處理結果文字,忽略時回傳 null。
     */
    suspend fun handleCustom(source: CustomSource, title: String, text: String, postTime: Long, key: String, whenMs: Long = 0L): String? =
        mutex.withLock {
            val dedupe = "$key@$postTime"
            if (dedupe in seen) return null
            seen.addLast(dedupe)
            while (seen.size > 50) seen.removeFirst()

            val fp = Dedupe.fingerprint(title, text, whenMs)
            if (Dedupe.isDuplicate(recent, fp)) return null
            val amount = GenericAmount.find("$title\n$text") ?: return null
            if (fp != null) {
                recent.addLast(fp)
                while (recent.size > 100) recent.removeFirst()
            }
            repo.addPending(CustomSources.cardOf(source), amount, postTime)
            "${source.titleKey} NT$$amount"
        }
}
