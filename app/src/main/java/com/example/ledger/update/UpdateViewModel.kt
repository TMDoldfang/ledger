package com.example.ledger.update

import android.app.Application
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledger.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentCode: Long
    val currentName: String

    init {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        currentCode = PackageInfoCompat.getLongVersionCode(info)
        currentName = info.versionName ?: "?"
    }

    /** res/values/config.xml 的 update_repo,格式「帳號/倉庫名」。 */
    private fun repo(): String = getApplication<Application>().getString(R.string.update_repo).trim()

    /** silent = true:App 啟動時的背景檢查,沒網路或失敗都不打擾使用者。 */
    fun check(silent: Boolean = false) {
        val current = _state.value
        if (current is UpdateState.Checking ||
            current is UpdateState.Downloading ||
            current is UpdateState.ReadyToInstall
        ) return

        val repo = repo()
        if (repo.isEmpty() || repo.startsWith("YOUR_")) {
            if (!silent) _state.value = UpdateState.NotConfigured
            return
        }

        viewModelScope.launch {
            if (!silent) _state.value = UpdateState.Checking
            try {
                val info = withContext(Dispatchers.IO) { GitHubReleases.fetchLatest(repo) }
                _state.value =
                    if (info.versionCode > currentCode) UpdateState.Available(info) else UpdateState.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!silent) _state.value = UpdateState.Failed(describe(e))
            }
        }
    }

    fun download() {
        val info = (_state.value as? UpdateState.Available)?.info ?: return
        val app = getApplication<Application>()
        val target = File(app.cacheDir, "updates/ledger-update.apk")

        viewModelScope.launch {
            _state.value = UpdateState.Downloading(info, 0f)
            try {
                withContext(Dispatchers.IO) {
                    GitHubReleases.download(info, target, { isActive }) { p ->
                        _state.value = UpdateState.Downloading(info, p)
                    }
                    GitHubReleases.verifyApk(app, target, currentCode)
                }
                _state.value = UpdateState.ReadyToInstall(info, target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                target.delete()
                _state.value = UpdateState.Failed(describe(e))
            }
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is UpdateException -> e.message ?: "更新失敗"
        is UnknownHostException,
        is ConnectException,
        is SocketTimeoutException -> "連不上網路,請確認連線後再試"
        else -> "更新失敗:${e.javaClass.simpleName}"
    }
}
