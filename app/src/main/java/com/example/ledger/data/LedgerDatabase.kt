package com.example.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Account::class, Category::class, LedgerTransaction::class, Person::class, Share::class, PendingEntry::class],
    version = 5,
    exportSchema = false,
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun personDao(): PersonDao
    abstract fun shareDao(): ShareDao
    abstract fun pendingDao(): PendingDao

    companion object {
        /** v2:加入「我的份」(代墊)、轉帳、代墊收回。舊資料原封不動。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN myShare INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN owedBy TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN transferToAccountId INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN settlesId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_settlesId` ON `transactions` (`settlesId`)")
            }
        }

        /** v3:代墊對象(people)、每個人的分攤(shares)、代墊收回記錄是哪個人還的。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `people` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_people_name` ON `people` (`name`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `shares` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`transactionId` INTEGER NOT NULL, `personId` INTEGER NOT NULL, `amount` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shares_transactionId` ON `shares` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shares_personId` ON `shares` (`personId`)")
                db.execSQL("ALTER TABLE transactions ADD COLUMN settlesPersonId INTEGER")
            }
        }

        /** v4:自動偵測的刷卡通知先放「待確認」(pending_entries)。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, `card` TEXT NOT NULL, `amount` INTEGER)"
                )
            }
        }

        /** v5:代墊「不收了」(shares.forgiven)。 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shares ADD COLUMN forgiven INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): LedgerDatabase =
            Room.databaseBuilder(context, LedgerDatabase::class.java, "ledger.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
