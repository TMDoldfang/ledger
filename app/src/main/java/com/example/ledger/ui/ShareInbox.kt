package com.example.ledger.ui

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

/** 從系統「分享」或相簿選到、等著辨識的截圖。只放在記憶體,辨識完就清掉。 */
object ShareInbox {
    val uri = MutableStateFlow<Uri?>(null)
}
