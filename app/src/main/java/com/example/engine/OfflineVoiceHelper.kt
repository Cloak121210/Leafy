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
            val langResult = tts?.setLanguage(Locale.getDefault())
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
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
        val cleanText = sanitizeForSpeech(rawMarkdownText)

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

    private fun sanitizeForSpeech(raw: String): String {
        return raw
            // Replace multi-line code blocks with a brief spoken descriptor
            .replace(Regex("```[\\s\\S]*?```"), " code block omitted. ")
            // Strip markdown links: [link text](url) -> link text
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
            // Strip inline code backticks: `code` -> code
            .replace("`", "")
            // Strip bold and italic markers
            .replace(Regex("[*_~]{1,3}"), "")
            // Strip header markers and blockquotes
            .replace(Regex("^[#>]+\\s*", RegexOption.MULTILINE), "")
            // Clean table dividers and dashes
            .replace("|", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
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
