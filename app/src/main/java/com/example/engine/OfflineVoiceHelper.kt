package com.example.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class OfflineVoiceHelper(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isReady = false

    private val _speakingMessageId = MutableStateFlow<Long?>(null)
    val speakingMessageId: StateFlow<Long?> = _speakingMessageId.asStateFlow()

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (_: Exception) {
            isReady = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            tts?.setSpeechRate(1.03f)
            tts?.setPitch(1.0f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    _speakingMessageId.value = null
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _speakingMessageId.value = null
                }
            })
            isReady = true
        }
    }

    fun toggleSpeak(messageId: Long, rawMarkdownText: String) {
        if (_speakingMessageId.value == messageId) {
            stop()
            return
        }
        val cleanText = rawMarkdownText
            .replace("**", "")
            .replace("`", "")
            .replace("#", "")
            .replace(">", "")
            .trim()

        _speakingMessageId.value = messageId
        if (isReady && tts != null) {
            tts?.speak(
                cleanText,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "leafy_msg_$messageId"
            )
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _speakingMessageId.value = null
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
    }
}
