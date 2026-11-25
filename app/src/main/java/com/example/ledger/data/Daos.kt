package com.example.ledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 月額上限算的「已花」(現金流角度):
 *  + 支出的實付全額(含現金領出;有朋友分擔的也先算實付,不是只算我的份)
 *  + 轉到「投資」帳戶的轉帳(例如 0050);其他轉帳(繳卡費等)不算,避免和刷卡重複
 *  − 這段時間內收回的代墊(朋友轉來的錢)
 */
private const val BUDGET_SPENT_SQL = """
    SELECT
        COALESCE(SUM(CASE WHEN t.isExpense = 1 AND t.transferToAccountId IS NULL THEN t.amount ELSE 0 END), 0)
      + COALESCE(SUM(CASE WHEN t.transferToAccountId IS NOT NULL AND ta.type = 'INVEST' THEN t.amount ELSE 0 END), 0)
      - COALESCE(SUM(CASE WHEN t.settlesId IS NOT NULL THEN t.amount ELSE 0 END), 0)
    FROM transactions t
    LEFT JOIN accounts ta ON ta.id = t.transferToAccountId
    WHERE t.timestamp >= :start AND t.timestamp < :end
"""

@Dao
interface TransactionDao {
    @Query(
        """
        SELECT t.id AS id, t.amount AS amount, t.isExpense AS isExpense, t.note AS note,
               t.timestamp AS timestamp,
               c.id AS categoryId, c.name AS categoryName, c.icon AS categoryIcon,
               a.id AS accountId, a.name AS accountName,
               t.myShare AS myShare, t.transferToAccountId AS transferToAccountId,
               ta.name AS transferToName, t.settlesId AS settlesId
        FROM transactions t
        JOIN categories c ON c.id = t.categoryId
        JOIN accounts a ON a.id = t.accountId
        LEFT JOIN accounts ta ON ta.id = t.transferToAccountId
        WHERE t.timestamp >= :start AND t.timestamp < :end
        ORDER BY t.timestamp DESC, t.id DESC
        """
    )
    fun observeItems(start: Long, end: Long): Flow<List<TransactionItem>>

    /**
     * 待收款(不分月份):每一筆代墊裡,每個人還沒還的錢,加上「還沒指定是誰」的差額(personId 為 null)。
     * 已收回 = 同一筆、同一個人的「代墊收回」總和。
     */
    @Query(
        """
        SELECT * FROM (
            SELECT t.id AS txId, s.personId AS personId, p.name AS personName,
                   t.amount AS txAmount, t.myShare AS myShare, t.note AS note, t.timestamp AS timestamp,
                   c.name AS categoryName, c.icon AS categoryIcon, a.name AS accountName,
                   s.amount AS shareAmount,
                   s.amount - COALESCE(
                       (SELECT SUM(x.amount) FROM transactions x
                        WHERE x.settlesId = t.id AND x.settlesPersonId = s.personId), 0
                   ) AS outstanding
            FROM shares s
            JOIN transactions t ON t.id = s.transactionId
            JOIN people p ON p.id = s.personId
            JOIN categories c ON c.id = t.categoryId
            JOIN accounts a ON a.id = t.accountId
            WHERE t.isExpense = 1 AND t.myShare IS NOT NULL AND t.transferToAccountId IS NULL
            UNION ALL
            SELECT t.id AS txId, NULL AS personId, NULL AS personName,
                   t.amount AS txAmount, t.myShare AS myShare, t.note AS note, t.timestamp AS timestamp,
                   c.name AS categoryName, c.icon AS categoryIcon, a.name AS accountName,
                   t.amount - t.myShare - COALESCE((SELECT SUM(s2.amount) FROM shares s2 WHERE s2.transactionId = t.id), 0)
                       AS shareAmount,
                   t.amount - t.myShare - COALESCE((SELECT SUM(s2.amount) FROM shares s2 WHERE s2.transactionId = t.id), 0)
                       - COALESCE((SELECT SUM(x.amount) FROM transactions x
                                   WHERE x.settlesId = t.id AND x.settlesPersonId IS NULL), 0)
                       AS outstanding
            FROM transactions t
            JOIN categories c ON c.id = t.categoryId
            JOIN accounts a ON a.id = t.accountId
            WHERE t.isExpense = 1 AND t.myShare IS NOT NULL AND t.transferToAccountId IS NULL
        )
        WHERE outstanding > 0
        ORDER BY timestamp DESC, txId DESC
        """
    )
    fun observeReceivables(): Flow<List<ReceivableDetail>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): LedgerTransaction?

    @Insert
    suspend fun insert(transaction: LedgerTransaction): Long

    @Update
    suspend fun update(transaction: LedgerTransaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 月額上限用的「已花」:見檔案上方 BUDGET_SPENT_SQL。 */
    @Query(BUDGET_SPENT_SQL)
    suspend fun budgetSpent(start: Long, end: Long): Long

    @Query(BUDGET_SPENT_SQL)
    fun observeBudgetSpent(start: Long, end: Long): Flow<Long>

    /** 這段時間內,有沒有「轉入投資帳戶」的轉帳(用來判斷 0050 這個月記了沒)。 */
    @Query(
        """
        SELECT COUNT(*) FROM transactions t
        JOIN accounts a ON a.id = t.transferToAccountId
        WHERE a.type = 'INVEST' AND t.timestamp >= :start AND t.timestamp < :end
        """
    )
    suspend fun countInvestTransfers(start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    /** 帳戶不論是付款方還是轉入方,只要有帳目就不能刪。 */
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR transferToAccountId = :accountId")
    suspend fun countByAccount(accountId: Long): Int
}

@Dao
interface AccountDao {
    /**
     * 餘額 = 初始餘額 + 收入 − 支出 ± 轉帳,每次由資料庫即時算出,不另存,避免兩邊不一致。
     * 轉帳:付款帳戶減少(isExpense = 1 的那一筆),轉入帳戶增加。
     */
    @Query(
        """
        SELECT a.id AS id, a.name AS name, a.type AS type,
               a.initialBalance
               + COALESCE((
                   SELECT SUM(CASE WHEN t.isExpense = 1 THEN -t.amount ELSE t.amount END)
                   FROM transactions t WHERE t.accountId = a.id
               ), 0)
               + COALESCE((
                   SELECT SUM(t.amount) FROM transactions t WHERE t.transferToAccountId = a.id
               ), 0) AS balance
        FROM accounts a
        ORDER BY a.id
        """
    )
    fun observeWithBalance(): Flow<List<AccountWithBalance>>

    @Insert
    suspend fun insert(account: Account): Long

    @Insert
    suspend fun insertAll(accounts: List<Account>)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM accounts WHERE name = :name")
    suspend fun countByName(name: String): Int
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isExpense DESC, id")
    fun observeAll(): Flow<List<Category>>

    @Insert
    suspend fun insert(category: Category): Long

    @Insert
    suspend fun insertAll(categories: List<Category>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT id FROM categories WHERE name = :name AND isExpense = :isExpense LIMIT 1")
    suspend fun findId(name: String, isExpense: Boolean): Long?
}

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY name")
    fun observeAll(): Flow<List<Person>>

    /** 名字重複時不新增,回傳 -1。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(person: Person): Long

    @Query("SELECT id FROM people WHERE name = :name LIMIT 1")
    suspend fun findId(name: String): Long?

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 這個人有沒有任何分攤或收回紀錄(有的話不能刪)。 */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM shares WHERE personId = :id)
             + (SELECT COUNT(*) FROM transactions WHERE settlesPersonId = :id)
        """
    )
    suspend fun usageCount(id: Long): Int
}

@Dao
interface ShareDao {
    @Query("SELECT * FROM shares WHERE transactionId = :transactionId ORDER BY id")
    suspend fun forTransaction(transactionId: Long): List<Share>

    @Insert
    suspend fun insertAll(shares: List<Share>)

    @Query("DELETE FROM shares WHERE transactionId = :transactionId")
    suspend fun deleteForTransaction(transactionId: Long)

    @Query("DELETE FROM shares WHERE id = :id")
    suspend fun deleteById(id: Long)
}
