package com.example.ledger.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

interface SpeechListener {
    fun onListening()
    fun onText(text: String, isFinal: Boolean)
    fun onError(message: String)
}

/**
 * 包裝 Android 內建的 SpeechRecognizer(繁體中文)。
 * 所有方法都要在主執行緒呼叫;用完一定要 release()。
 */
class SpeechController(
    private val context: Context,
    private val listener: SpeechListener,
) {
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        release()
        if (!isAvailable()) {
            listener.onError("這台裝置沒有可用的語音辨識服務(模擬器常見)。可以直接在上面的框裡打字。")
            return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = listener.onListening()
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                listener.onError(describe(error))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let { listener.onText(it, false) }
            }

            override fun onResults(results: Bundle?) {
                val text = firstResult(results)
                if (text != null) listener.onText(text, true) else listener.onError("沒聽清楚,再說一次")
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        r.startListening(intent)
    }

    /** 說完了,請辨識器收尾(會接著送出最終結果)。 */
    fun stopListening() {
        recognizer?.stopListening()
    }

    fun release() {
        recognizer?.let { r ->
            runCatching { r.cancel() }
            runCatching { r.destroy() }
        }
        recognizer = null
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "沒聽到聲音,按麥克風再說一次"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "沒有麥克風權限,請到系統設定開啟"
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER -> "語音辨識需要網路,請確認連線"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "辨識器忙碌中,稍後再試"
        SpeechRecognizer.ERROR_AUDIO -> "麥克風無法錄音"
        else -> "語音辨識失敗(代碼 $error)"
    }
}
