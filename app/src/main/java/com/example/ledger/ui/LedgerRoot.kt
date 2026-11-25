package com.example.ledger.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ledger.update.UpdateState
import com.example.ledger.update.UpdateViewModel
import com.example.ledger.ui.screens.EditScreen
import com.example.ledger.ui.screens.HomeScreen
import com.example.ledger.ui.screens.ManageScreen
import com.example.ledger.ui.screens.SettingsScreen
import com.example.ledger.ui.screens.StatsScreen

private data class TopDest(val route: String, val label: String, val icon: ImageVector)

private val topDests = listOf(
    TopDest("home", "帳目", Icons.Filled.Receipt),
    TopDest("stats", "統計", Icons.Filled.PieChart),
    TopDest("manage", "管理", Icons.Filled.AccountBalanceWallet),
    TopDest("settings", "設定", Icons.Filled.Settings),
)

@Composable
fun LedgerRoot(vm: LedgerViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    // 代墊與 0050 的提醒都要發通知:第一次開啟時請使用者允許(Android 13 以上)
    val context = LocalContext.current
    var askedNotification by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (!askedNotification && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askedNotification = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 線上更新:開 App、或從背景回來時背景檢查;有新版本就自動跳出視窗(細節也在「管理」頁)
    val updateVm: UpdateViewModel = viewModel()
    LifecycleResumeEffect(Unit) {
        updateVm.check(silent = true)
        onPauseOrDispose { }
    }
    UpdateDialog(updateVm)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (topDests.any { it.route == route }) {
                NavigationBar {
                    topDests.forEach { d ->
                        NavigationBarItem(
                            selected = route == d.route,
                            onClick = {
                                nav.navigate(d.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(d.icon, contentDescription = d.label) },
                            label = { Text(d.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                HomeScreen(
                    vm = vm,
                    onAdd = { nav.navigate("edit/0") },
                    onEdit = { id -> nav.navigate("edit/$id") },
                )
            }
            composable("stats") { StatsScreen(vm) }
            composable("manage") { ManageScreen(vm, updateVm) }
            composable("settings") { SettingsScreen() }
            composable(
                route = "edit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                EditScreen(
                    vm = vm,
                    id = entry.arguments?.getLong("id") ?: 0L,
                    onDone = { nav.popBackStack() },
                )
            }
        }
    }
}
