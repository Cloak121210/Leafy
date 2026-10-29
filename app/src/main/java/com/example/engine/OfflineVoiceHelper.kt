package com.example.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class OfflineVoiceHelper(context: Context) : TextToSpeech.OnInitListener {

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var isReady = false

    @Volatile
    private var initFailed = false

    @Volatile
    private var pendingUtterance: Pair<Long, String>? = null

    private val _speakingMessageId = MutableStateFlow<Long?>(null)
    val speakingMessageId: StateFlow<Long?> = _speakingMessageId.asStateFlow()

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (_: Exception) {
            isReady = false
            initFailed = true
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            val langResult = engine.setLanguage(Locale.getDefault())
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
            }
            engine.setSpeechRate(1.03f)
            engine.setPitch(1.0f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    clearSpeakingIfMatching(utteranceId)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    clearSpeakingIfMatching(utteranceId)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    clearSpeakingIfMatching(utteranceId)
                }
            })
            isReady = true
            pendingUtterance?.let { (msgId, text) ->
                pendingUtterance = null
                speakNow(msgId, text)
            }
        } else {
            initFailed = true
            pendingUtterance = null
            _speakingMessageId.value = null
        }
    }

    private fun clearSpeakingIfMatching(utteranceId: String?) {
        val currentId = _speakingMessageId.value ?: return
        if (utteranceId == null || utteranceId == "leafy_msg_$currentId") {
            _speakingMessageId.value = null
        }
    }

    fun toggleSpeak(messageId: Long, rawMarkdownText: String) {
        if (_speakingMessageId.value == messageId) {
            stop()
            return
        }
        val cleanText = sanitizeForSpeech(rawMarkdownText)
        if (cleanText.isBlank() || initFailed || tts == null) {
            _speakingMessageId.value = null
            return
        }

        if (isReady) {
            speakNow(messageId, cleanText)
        } else {
            _speakingMessageId.value = messageId
            pendingUtterance = messageId to cleanText
        }
    }

    private fun speakNow(messageId: Long, cleanText: String) {
        _speakingMessageId.value = messageId
        val result = tts?.speak(
            cleanText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "leafy_msg_$messageId"
        )
        if (result == TextToSpeech.ERROR) {
            _speakingMessageId.value = null
        }
    }

    private fun sanitizeForSpeech(raw: String): String {
        val maxTtsChars = try {
            TextToSpeech.getMaxSpeechInputLength().coerceIn(500, MAX_SAFE_SPEECH_CHARS)
        } catch (_: Throwable) {
            MAX_SAFE_SPEECH_CHARS
        }

        val cleaned = raw
            // Replace closed multi-line code blocks with a brief spoken descriptor
            .replace(CODE_BLOCK_REGEX, " code block omitted. ")
            // Strip any trailing unclosed code fence block
            .let { text ->
                val unclosedIdx = text.indexOf("```")
                if (unclosedIdx >= 0) text.substring(0, unclosedIdx) + " code block omitted. " else text
            }
            // Strip markdown links: [link text](url) -> link text
            .replace(MARKDOWN_LINK_REGEX, "$1")
            // Strip inline code backticks: `code` -> code
            .replace("`", "")
            // Strip bold and italic markers
            .replace(EMPHASIS_REGEX, "")
            // Strip header markers and blockquotes
            .replace(HEADER_QUOTE_REGEX, "")
            // Clean table dividers and dashes
            .replace("|", " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

        if (cleaned.length <= maxTtsChars) return cleaned
        val truncated = cleaned.take(maxTtsChars)
        val lastBoundary = truncated.lastIndexOfAny(charArrayOf('.', '!', '?', ' '))
        return if (lastBoundary > maxTtsChars / 2) {
            truncated.substring(0, lastBoundary).trim()
        } else {
            truncated.trim()
        }
    }

    fun stop() {
        pendingUtterance = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _speakingMessageId.value = null
    }

    fun shutdown() {
        pendingUtterance = null
        isReady = false
        _speakingMessageId.value = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        } finally {
            tts = null
        }
    }

    companion object {
        private const val MAX_SAFE_SPEECH_CHARS = 3800
        private val CODE_BLOCK_REGEX = Regex("```.*?```", RegexOption.DOT_MATCHES_ALL)
        private val MARKDOWN_LINK_REGEX = Regex("\\[([^\\]]+)\\]\\([^)]+\\)")
        private val EMPHASIS_REGEX = Regex("[*_~]{1,3}")
        private val HEADER_QUOTE_REGEX = Regex("^[#>]+\\s*", RegexOption.MULTILINE)
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
