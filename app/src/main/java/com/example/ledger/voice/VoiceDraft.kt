package com.example.ledger.voice

import com.example.ledger.data.AccountType
import com.example.ledger.data.AccountWithBalance
import com.example.ledger.data.Category
import java.time.LocalDate

/** 語音解析後、要存成帳目之前的草稿(分類與帳戶已對應成資料庫 id)。 */
data class TransactionDraft(
    val isExpense: Boolean,
    val amount: Long?,
    /** 轉帳時為 null(系統會歸到「轉帳」分類) */
    val categoryId: Long?,
    val accountId: Long?,
    val date: LocalDate,
    val note: String,
    /** 代墊:我的份;null = 全部都是我的 */
    val myShare: Long? = null,
    /** 轉帳:轉入的帳戶;不為 null 代表這是轉帳(accountId 是付款帳戶) */
    val transferToAccountId: Long? = null,
    /** 哪一個欄位是「預設值」而不是聽出來的,確認卡會標示 */
    val accountIsDefault: Boolean = false,
    val toAccountIsDefault: Boolean = false,
    /** 從「待確認」轉成帳目時,存檔後要把那一筆待確認刪掉 */
    val pendingId: Long? = null,
) {
    val isTransfer: Boolean get() = transferToAccountId != null
}

/** 把目前的分類 / 帳戶轉成解析器要用的純資料。 */
fun voiceContextOf(categories: List<Category>, accounts: List<AccountWithBalance>): VoiceContext =
    VoiceContext(
        expenseCategories = categories.filter { it.isExpense }.map { it.name },
        incomeCategories = categories.filter { !it.isExpense }.map { it.name },
        accounts = accounts.map { AccountRef(it.name, AccountType.from(it.type)) },
    )

private fun List<AccountWithBalance>.find(ref: AccountRef?): AccountWithBalance? =
    ref?.let { r -> firstOrNull { it.name == r.name && AccountType.from(it.type) == r.type } }

private fun List<AccountWithBalance>.firstOfType(type: AccountType): AccountWithBalance? =
    firstOrNull { AccountType.from(it.type) == type }

/**
 * 解析結果 → 草稿。句子沒提到(或對不上)的分類改用「其他」、帳戶改用合理的預設,
 * 確認卡會標示「預設」,讓使用者知道那不是聽出來的。
 * 預設帳戶:一般支出用清單第一個;領現金、繳卡費、投資的付款帳戶用第一個銀行帳戶。
 */
fun draftFrom(
    parsed: ParsedVoice,
    categories: List<Category>,
    accounts: List<AccountWithBalance>,
    today: LocalDate = LocalDate.now(),
): TransactionDraft {
    val date = today.plusDays(parsed.dayOffset.toLong())

    // 轉帳:繳卡費(銀行 → 信用卡)、投資(銀行 → 投資帳戶)
    parsed.transfer?.let { t ->
        val fromHit = accounts.find(t.from)
        val from = fromHit ?: accounts.firstOfType(AccountType.BANK) ?: accounts.firstOrNull()
        val toType = if (t.kind == TransferKind.CARD_BILL) AccountType.CARD else AccountType.INVEST
        val toHit = accounts.find(t.to)
        val to = toHit ?: accounts.firstOfType(toType)
        // 找不到轉入帳戶(例如沒有信用卡帳戶)就退回一般支出,免得存出無效的轉帳
        if (to != null && to.id != from?.id) {
            return TransactionDraft(
                isExpense = true,
                amount = parsed.amount,
                categoryId = null,
                accountId = from?.id,
                date = date,
                note = parsed.note,
                transferToAccountId = to.id,
                accountIsDefault = fromHit == null,
                toAccountIsDefault = toHit == null,
            )
        }
    }

    val pool = categories.filter { it.isExpense == parsed.isExpense }
    val category = parsed.categoryName?.let { name -> pool.firstOrNull { it.name == name } }
        ?: pool.firstOrNull { it.name == "其他" }
        ?: pool.firstOrNull()

    val hit = accounts.find(parsed.account)
    val account = hit
        ?: (if (parsed.isCashOut) accounts.firstOfType(AccountType.BANK) else null)
        ?: accounts.firstOrNull()

    return TransactionDraft(
        isExpense = parsed.isExpense,
        amount = parsed.amount,
        categoryId = category?.id,
        accountId = account?.id,
        date = date,
        note = parsed.note,
        myShare = parsed.myShare,
        accountIsDefault = hit == null,
    )
}
