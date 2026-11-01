package com.example.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * Native JNI bridge (`libleafy_runtime_jni.so`) that dynamically loads and executes:
 * - `LLAMA_CPP` (`llama.cpp`): `librnllama_v8_2_dotprod_i8mm.so` / `librnllama_v8.so` (`mybigday/llama.rn` v0.13.0-rc.9, commit `27783c86ce` / `ggml-org/llama.cpp` `0ec4027a2a`)
 * - `BITNET_CPP` (`Bitnet.cpp`): `libbitnet_llama.so` (`microsoft/BitNet` commit `0b341e582a` + `isHuangXin/llama.cpp` `390c307`, ARM NEON I2_S MAD, TL1 LUT & gated squared ReLU)
 * - `T_MAC` (`T-MAC`): `libtmac_llama.so` (`microsoft/T-MAC` commit `7042f8f733` + `kaleid-liner/llama.cpp` `eb07ecf`, AArch64 NEON vqtbl1q LUT kernels)
 *
 * Built with Android NDK 28.2.13676358 (Clang 19.0.1), CMake 3.22.1, minSdk = 24, target ABI arm64-v8a (and x86_64).
 */
data class NativeGenTimings(
    val promptTokens: Int,
    val generatedTokens: Int,
    val prefillMs: Double,
    val decodeMs: Double,
    val prefillTps: Double,
    val decodeTps: Double,
    val errorCode: Int,
    val errorMessage: String
)

fun interface TokenStreamCallback {
    /**
     * Invoked from the native generation loop once per sampled token.
     * Return false to cancel generation immediately.
     */
    fun onTokenPiece(piece: String): Boolean
}

/**
 * Internal callback receiver whose `onTokenPiece([B)V` signature is invoked directly from JNI (`libleafy_runtime_jni.so`).
 */
class NativeBytePieceReceiver(
    private val handle: Long,
    private val delegate: TokenStreamCallback
) {
    @Suppress("unused")
    fun onTokenPiece(bytes: ByteArray) {
        val textPiece = if (bytes.isEmpty()) "" else String(bytes, Charsets.UTF_8)
        val keepGoing = delegate.onTokenPiece(textPiece)
        if (!keepGoing) {
            try {
                LeafyNativeBridge.nativeRequestCancel(handle)
            } catch (_: Throwable) {
            }
        }
    }
}

object LeafyNativeBridge {
    @Volatile
    var isLibraryLoaded: Boolean = false
        private set

    @Volatile
    var loadError: String? = null
        private set

    @Volatile
    private var lastNativeError: String = ""

    private val handleBackendLabels = ConcurrentHashMap<Long, String>()

    init {
        ensureLoaded()
    }

    fun ensureLoaded(): Boolean {
        if (isLibraryLoaded) return true
        return try {
            try {
                System.loadLibrary("c++_shared")
            } catch (_: UnsatisfiedLinkError) {
            }
            System.loadLibrary("leafy_runtime_jni")
            isLibraryLoaded = true
            loadError = null
            true
        } catch (t: Throwable) {
            isLibraryLoaded = false
            loadError = t.message ?: t.javaClass.simpleName
            false
        }
    }

    private fun candidateSharedLibrariesForEngine(engineId: String): List<Pair<String, String>> {
        val normalized = engineId.lowercase()
        return when {
            normalized.contains("bitnet") -> listOf(
                "libbitnet_llama.so" to "Bitnet.cpp I2_S/TL1 NEON (libbitnet_llama.so)"
            )
            normalized.contains("t-mac") || normalized.contains("tmac") -> listOf(
                "libtmac_llama.so" to "T-MAC AArch64 LUT (libtmac_llama.so)"
            )
            else -> listOf(
                "librnllama_v8_2_dotprod_i8mm.so" to "llama.cpp v0.13.0-rc.9 ARMv8.2 DotProd+I8MM (librnllama_v8_2_dotprod_i8mm.so)",
                "librnllama_v8.so" to "llama.cpp v0.13.0-rc.9 ARMv8 (librnllama_v8.so)",
                "librnllama.so" to "llama.cpp v0.13.0-rc.9 (librnllama.so)"
            )
        }
    }

    fun nativeLoadModel(
        engineId: String,
        modelPath: String,
        nCtx: Int,
        nBatch: Int,
        nThreads: Int,
        useMmap: Boolean
    ): Long {
        if (!ensureLoaded()) {
            lastNativeError = loadError ?: "libleafy_runtime_jni.so not loaded"
            return 0L
        }
        val candidates = candidateSharedLibrariesForEngine(engineId)
        val errors = mutableListOf<String>()
        for ((soName, backendLabel) in candidates) {
            try {
                val handle = nativeInitContext(
                    soName = soName,
                    modelPath = modelPath,
                    nCtx = nCtx,
                    nBatch = nBatch,
                    nThreads = nThreads,
                    useMmap = useMmap
                )
                if (handle != 0L) {
                    handleBackendLabels[handle] = backendLabel
                    lastNativeError = ""
                    return handle
                }
            } catch (t: Throwable) {
                errors.add("${soName}: ${t.message ?: t.javaClass.simpleName}")
            }
        }
        lastNativeError = errors.joinToString(" | ").ifBlank { "nativeInitContext returned 0L" }
        return 0L
    }

    fun nativeGetLastError(): String = lastNativeError

    fun nativeGetModelDescription(handle: Long): String {
        if (!isLibraryLoaded || handle == 0L) return ""
        return try {
            nativeGetModelDesc(handle)
        } catch (_: Throwable) {
            ""
        }
    }

    fun nativeGetBackendDescription(handle: Long): String {
        return handleBackendLabels[handle] ?: ""
    }

    fun nativeGenerateStream(
        handle: Long,
        prompt: String,
        nPredict: Int,
        nThreads: Int,
        temperature: Float,
        topP: Float,
        repeatPenalty: Float,
        stopSequences: Array<String>,
        callback: TokenStreamCallback
    ): NativeGenTimings? {
        if (!isLibraryLoaded || handle == 0L) {
            return NativeGenTimings(
                promptTokens = 0,
                generatedTokens = 0,
                prefillMs = 0.0,
                decodeMs = 0.0,
                prefillTps = 0.0,
                decodeTps = 0.0,
                errorCode = -1,
                errorMessage = "Native library or context handle is not initialized."
            )
        }
        return try {
            val receiver = NativeBytePieceReceiver(handle, callback)
            val metrics = nativeComplete(
                handle = handle,
                prompt = prompt,
                nPredict = nPredict,
                nThreads = nThreads,
                temperature = temperature,
                topP = topP,
                repeatPenalty = repeatPenalty,
                stopWords = stopSequences,
                callback = receiver
            )
            if (metrics == null || metrics.size < 4) {
                NativeGenTimings(
                    promptTokens = 0,
                    generatedTokens = 0,
                    prefillMs = 0.0,
                    decodeMs = 0.0,
                    prefillTps = 0.0,
                    decodeTps = 0.0,
                    errorCode = -2,
                    errorMessage = "nativeComplete returned empty timing metrics."
                )
            } else {
                val promptToks = metrics[0].toInt().coerceAtLeast(0)
                val genToks = metrics[1].toInt().coerceAtLeast(0)
                val prefillMs = (metrics[2].coerceAtLeast(0L)).toDouble() / 1_000_000.0
                val decodeMs = (metrics[3].coerceAtLeast(0L)).toDouble() / 1_000_000.0
                val prefillTps = if (prefillMs > 0.0 && promptToks > 0) (promptToks * 1000.0) / prefillMs else 0.0
                val decodeTps = if (decodeMs > 0.0 && genToks > 0) (genToks * 1000.0) / decodeMs else 0.0
                NativeGenTimings(
                    promptTokens = promptToks,
                    generatedTokens = genToks,
                    prefillMs = prefillMs,
                    decodeMs = decodeMs,
                    prefillTps = prefillTps,
                    decodeTps = decodeTps,
                    errorCode = 0,
                    errorMessage = ""
                )
            }
        } catch (t: Throwable) {
            val errMsg = t.message ?: t.javaClass.simpleName
            lastNativeError = errMsg
            NativeGenTimings(
                promptTokens = 0,
                generatedTokens = 0,
                prefillMs = 0.0,
                decodeMs = 0.0,
                prefillTps = 0.0,
                decodeTps = 0.0,
                errorCode = -1,
                errorMessage = errMsg
            )
        }
    }

    fun nativeCancelGeneration(handle: Long) {
        if (!isLibraryLoaded || handle == 0L) return
        try {
            nativeRequestCancel(handle)
        } catch (_: Throwable) {
        }
    }

    fun nativeFreeModel(handle: Long) {
        handleBackendLabels.remove(handle)
        if (!isLibraryLoaded || handle == 0L) return
        try {
            nativeFreeContext(handle)
        } catch (_: Throwable) {
        }
    }

    @JvmStatic
    external fun nativeInitContext(
        soName: String,
        modelPath: String,
        nCtx: Int,
        nBatch: Int,
        nThreads: Int,
        useMmap: Boolean
    ): Long

    @JvmStatic
    external fun nativeGetModelDesc(handle: Long): String

    @JvmStatic
    external fun nativeGetContextSize(handle: Long): Int

    @JvmStatic
    external fun nativeTokenizeCount(handle: Long, text: String): Int

    @JvmStatic
    external fun nativeApplyChatTemplate(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
        addAssistantPrefix: Boolean
    ): String

    @JvmStatic
    external fun nativeRequestCancel(handle: Long)

    @JvmStatic
    external fun nativeFreeContext(handle: Long)

    @JvmStatic
    external fun nativeComplete(
        handle: Long,
        prompt: String,
        nPredict: Int,
        nThreads: Int,
        temperature: Float,
        topP: Float,
        repeatPenalty: Float,
        stopWords: Array<String>,
        callback: NativeBytePieceReceiver
    ): LongArray?
}
