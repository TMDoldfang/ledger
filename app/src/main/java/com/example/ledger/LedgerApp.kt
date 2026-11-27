package com.example.ledger

import android.app.Application
import com.example.ledger.data.LedgerDatabase
import com.example.ledger.data.LedgerRepository
import com.example.ledger.detect.NoticeHandler
import com.example.ledger.detect.PendingReminder
import com.example.ledger.reminder.BudgetChecker
import com.example.ledger.reminder.InvestReminder
import com.example.ledger.reminder.OwedByReminder
import com.example.ledger.reminder.ReceivableReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LedgerApp : Application() {
    // 專案小,先用手動注入;之後規模變大再換 Hilt / Koin。
    val database by lazy { LedgerDatabase.create(this) }
    val repository by lazy { LedgerRepository(database) }
    val noticeHandler by lazy { NoticeHandler(repository) }

    override fun onCreate() {
        super.onCreate()
        com.example.ledger.settings.AppSettings.migrateSources(this)
        // 代墊還沒填「誰欠我」:30 分鐘後提醒
        repository.onNeedsOwedBy = { id -> OwedByReminder.schedule(this, id) }
        // 0050 定期定額:每月 8 號中午檢查,沒記就提醒
        InvestReminder.ensureScheduled(this)
        // 自動偵測的待確認:開著才排,每晚 21:00 提醒
        PendingReminder.ensureScheduled(this)
        // 待收款放太久:開著才排,每天晚上檢查
        ReceivableReminder.ensureScheduled(this)
        // 月額上限:每次儲存帳目後檢查;啟動時也檢查一次
        repository.onSaved = { BudgetChecker.check(this, repository) }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repository.seedIfEmpty()
            runCatching { BudgetChecker.check(this@LedgerApp, repository) }
        }
    }
}
