package com.example.ledger.ui

import kotlinx.coroutines.flow.MutableStateFlow

/** 點了「待補金額」通知:要求首頁打開待確認清單並只顯示待補金額。只放記憶體,用完清掉。 */
object PendingNav {
    val openMissing = MutableStateFlow(false)
    const val EXTRA_OPEN_MISSING = "open_pending_missing"
}
