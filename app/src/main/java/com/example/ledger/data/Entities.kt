package com.example.ledger.data

import androidx.room.ColumnInfo
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
    /** 已停用(舊版的「誰欠我」文字);改用 shares 表記錄每個人欠多少。欄位保留是為了資料庫相容。 */
    val owedBy: String? = null,
    /** 不為 null 代表這是轉帳:從 accountId 轉到這個帳戶,不算收入也不算支出(例:繳卡費、投資 0050)。 */
    val transferToAccountId: Long? = null,
    /** 不為 null 代表這是一筆「代墊收回」:朋友轉錢給我,用來沖銷該筆支出的待收款,不算收入。 */
    val settlesId: Long? = null,
    /** 代墊收回時,是哪個人還的;null = 還沒指定是誰的那一部分。 */
    val settlesPersonId: Long? = null,
)

/** 代墊對象(朋友)。名字不能重複。 */
@Entity(tableName = "people", indices = [Index(value = ["name"], unique = true)])
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/**
 * 一筆代墊裡,某個人該付多少。總和 ≤ 實付 − 我的份;還沒分配到人的差額視為「未指定」。
 * 刪掉帳目時一併刪除;有分攤紀錄的人不能刪。
 */
@Entity(
    tableName = "shares",
    foreignKeys = [
        ForeignKey(
            entity = LedgerTransaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("transactionId"), Index("personId")],
)
data class Share(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val personId: Long,
    val amount: Long,
    /** 「不收了」的金額:不再是待收款,改算成我自己的負擔(v5 加入) */
    @ColumnInfo(defaultValue = "0") val forgiven: Long = 0,
)

/**
 * 自動偵測到的刷卡通知,先放這裡等使用者確認。[amount] 為 null = 通知沒有金額(例如統一企業卡),之後補。
 * [card] 存 DetectedCard 的名稱。
 */
@Entity(tableName = "pending_entries")
data class PendingEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val card: String,
    val amount: Long? = null,
)
