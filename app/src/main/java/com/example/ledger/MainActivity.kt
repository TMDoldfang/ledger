package com.example.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ledger.ui.LedgerRoot
import com.example.ledger.ui.LedgerViewModel
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
        setContent {
            LedgerTheme {
                LedgerRoot(vm)
            }
        }
    }
}
