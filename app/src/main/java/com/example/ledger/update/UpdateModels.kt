package com.example.ledger.update

import java.io.File

/** GitHub Release 上最新版本的資訊。versionCode 來自 tag(v12 → 12)。 */
data class ReleaseInfo(
    val versionCode: Long,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
)

/** 訊息可以直接顯示給使用者看。 */
class UpdateException(message: String) : Exception(message)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object NotConfigured : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val info: ReleaseInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}
