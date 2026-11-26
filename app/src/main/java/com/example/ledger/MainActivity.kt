package com.example.ledger

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ledger.ui.LedgerRoot
import com.example.ledger.ui.LedgerViewModel
import com.example.ledger.ui.ShareInbox
import com.example.ledger.ui.theme.LedgerTheme

class MainActivity : ComponentActivity() {
    private val vm: LedgerViewModel by viewModels {
        viewModelFactory {
            initializer { LedgerViewModel((application as LedgerApp).repository) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShare(intent)
        setContent {
            LedgerTheme {
                LedgerRoot(vm)
            }
        }
    }

    // 在別的 App(例如相簿、LINE)按「分享」選簡記,圖片會送到這裡等著辨識
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return
        val uri = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
        if (uri != null) ShareInbox.uri.value = uri
    }
}
