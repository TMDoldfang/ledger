package com.example.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Account::class, Category::class, LedgerTransaction::class],
    version = 2,
    exportSchema = false,
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao

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

        fun create(context: Context): LedgerDatabase =
            Room.databaseBuilder(context, LedgerDatabase::class.java, "ledger.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
