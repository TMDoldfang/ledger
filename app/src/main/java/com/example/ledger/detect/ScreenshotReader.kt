package com.example.ledger.detect

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 在手機上辨識截圖文字(ML Kit 內建中文模型,不需要網路)。
 * 圖片只在記憶體裡處理:不上傳、不複製、不存檔;辨識出的文字也只交給 [ReceiptParser] 取出
 * 金額、日期時間、商店名稱,原始文字用完即丟。
 */
object ScreenshotReader {
    /** @return 辨識出的每一行文字;讀不到圖或辨識失敗回傳 null。 */
    suspend fun readLines(context: Context, uri: Uri): List<String>? {
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        return try {
            val image = InputImage.fromFilePath(context, uri)
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        cont.resume(result.textBlocks.flatMap { b -> b.lines.map { it.text } })
                    }
                    .addOnFailureListener { cont.resume(null) }
            }
        } catch (e: Exception) {
            null
        } finally {
            recognizer.close()
        }
    }
}
