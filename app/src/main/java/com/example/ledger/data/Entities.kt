package com.example.ledger.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** AccountType 的 name:CASH / BANK / CARD / EPAY */
    val type: String,
    val initialBalance: Long = 0,
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isExpense: Boolean,
    /** 以 emoji 當圖示,免去圖片資源 */
    val icon: String,
)

/**
 * 金額以「整數元」儲存(Long)。台幣沒有小數,可以避開浮點誤差;
 * 之後要做多幣別,再改成以最小單位(分)儲存並加上 currency 欄位。
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("timestamp"), Index("settlesId")],
)
data class LedgerTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 實際付出(或收到)的金額;帳戶餘額依這個數字增減。 */
    val amount: Long,
    val isExpense: Boolean,
    val categoryId: Long,
    val accountId: Long,
    /** epoch millis */
    val timestamp: Long,
    val note: String = "",
    /**
     * 代墊:「我的份」。null = 全部都是我的。
     * 例:晚餐實付 1200、我的 300 → amount = 1200、myShare = 300,
     * 統計只算 300,其餘 900 變成「待收款」,等朋友轉帳。
     */
    val myShare: Long? = null,
    /** 代墊時「誰欠我」,可以事後再補(記帳當下沒空填也沒關係)。 */
    val owedBy: String? = null,
    /** 不為 null 代表這是轉帳:從 accountId 轉到這個帳戶,不算收入也不算支出(例:繳卡費、投資 0050)。 */
    val transferToAccountId: Long? = null,
    /** 不為 null 代表這是一筆「代墊收回」:朋友轉錢給我,用來沖銷該筆支出的待收款,不算收入。 */
    val settlesId: Long? = null,
)
