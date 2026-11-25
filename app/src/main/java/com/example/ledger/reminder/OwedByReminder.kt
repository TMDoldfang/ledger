package com.example.ledger.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.ledger.LedgerApp
import com.example.ledger.MainActivity
import com.example.ledger.util.formatMoney
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 代墊記完之後,「誰欠我」不用當下填。30 分鐘後如果還是空的,就用通知提醒一次。
 * 用 WorkManager 排程:App 被關掉、手機重開機都還會照常提醒。
 */
object OwedByReminder {
    const val DELAY_MINUTES = 30L
    internal const val CHANNEL_ID = "owed_by_reminder"

    fun schedule(context: Context, transactionId: Long) {
        val request = OneTimeWorkRequestBuilder<OwedByWorker>()
            .setInitialDelay(DELAY_MINUTES, TimeUnit.MINUTES)
            .setInputData(workDataOf(OwedByWorker.KEY_ID to transactionId))
            .build()
        // KEEP:同一筆重複儲存時不重新計時
        WorkManager.getInstance(context)
            .enqueueUniqueWork("owed-by-$transactionId", ExistingWorkPolicy.KEEP, request)
    }
}

class OwedByWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, 0L)
        if (id == 0L) return Result.success()

        val repo = (applicationContext as LedgerApp).repository
        // 這筆還有「沒指定是誰」的待收款(沒被刪、沒補完),才需要提醒
        val receivable = repo.receivables.first().firstOrNull { it.txId == id && it.personId == null }
            ?: return Result.success()

        val ctx = applicationContext
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return Result.success()

        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(OwedByReminder.CHANNEL_ID, "代墊提醒", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val open = PendingIntent.getActivity(
            ctx,
            id.toInt(),
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = receivable.note.ifBlank { receivable.categoryName }
        val notification = NotificationCompat.Builder(ctx, OwedByReminder.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("還沒填是誰欠的")
            .setContentText("「$title」還有 ${formatMoney(receivable.outstanding)} 沒指定是誰,點開補上")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id.toInt(), notification)
        return Result.success()
    }

    companion object {
        const val KEY_ID = "transaction_id"
    }
}
