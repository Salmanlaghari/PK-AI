package com.salmanlaghari.pkai.ui.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TTSManager(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit
) {

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var onSpeechDoneCallback: (() -> Unit)? = null

    init {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Try setting default locale
                val result = textToSpeech?.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to US English
                    textToSpeech?.setLanguage(Locale.US)
                }

                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        onSpeechDoneCallback?.invoke()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        super.onError(utteranceId, errorCode)
                    }
                })

                isInitialized = true
                onInitComplete(true)
            } else {
                isInitialized = false
                onInitComplete(false)
            }
        }
    }

    fun speak(text: String, onSpeechDone: () -> Unit) {
        if (!isInitialized || text.isBlank()) {
            onSpeechDone()
            return
        }

        onSpeechDoneCallback = onSpeechDone
        val utteranceId = "PKAI_TTS_UTTERANCE_${System.currentTimeMillis()}"
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        if (isInitialized) {
            textToSpeech?.stop()
        }
    }

    fun shutdown() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
    }
}
