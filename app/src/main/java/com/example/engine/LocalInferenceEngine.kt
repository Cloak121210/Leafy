package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import com.example.data.AiRuntime
import com.example.data.LocalModelEntity
import com.example.data.MessageNodeEntity
import com.example.data.PersonaPresetEntity
import com.example.data.PowerProfile
import com.example.data.RagDocumentEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.nehuatl.llamacpp.LlamaAndroid
import org.nehuatl.llamacpp.LlamaContext
import org.pytorch.executorch.Module
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.random.Random

data class RetrievedRagSnippet(
    val documentId: Long,
    val documentTitle: String,
    val matchedPassage: String,
    val relevanceScore: Float
)

data class StreamingStep(
    val accumulatedText: String,
    val currentTps: Float,
    val ttftMs: Int,
    val generatedTokens: Int,
    val energyMicroWh: Float,
    val citedRagSnippets: List<RetrievedRagSnippet>,
    val isFinished: Boolean,
    val instantTps: Float = currentTps,
    val peakTps: Float = currentTps,
    val interTokenLatencyMs: Float = if (currentTps > 0f) 1000f / currentTps else 0f,
    val prefillTps: Float = 0f,
    val promptTokensCount: Int = 0,
    val runtimeBackendLabel: String = "",
    val runtimeModelDesc: String = "",
    val runtimeQuantizationLabel: String = "",
    val phase: InferenceTelemetryPhase = if (isFinished) InferenceTelemetryPhase.COMPLETED else InferenceTelemetryPhase.DECODING,
    val tpsSeries: List<Float> = emptyList()
) {
    val promptTokensEstimate: Int
        get() = promptTokensCount
}

data class GgufHeaderInfo(
    val isValidGguf: Boolean,
    val magicString: String,
    val version: Int,
    val tensorCount: Long,
    val metadataKvCount: Long,
    val architecture: String? = null,
    val modelName: String? = null,
    val fileType: Int? = null,
    val contextLength: Int? = null,
    val chatTemplate: String? = null,
    val quantizationLabel: String = "Unknown"
)

data class PteHeaderInfo(
    val isValidPte: Boolean,
    val magicString: String,
    val version: Int,
    val fileSize: Long
)

data class TmacHeaderInfo(
    val isValidTmac: Boolean,
    val formatType: String,
    val lutBits: Int,
    val tensorCount: Long
)

class LocalInferenceEngine(
    private val context: Context? = null
) {
    private val inferenceMutex = Mutex()
    private val nativeHandleLock = Any()
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var mountedLlamaContext: LlamaContext? = null

    @Volatile
    private var mountedModelPath: String? = null

    private val _mountedModelPathFlow = MutableStateFlow<String?>(null)
    val mountedModelPathFlow: StateFlow<String?> = _mountedModelPathFlow.asStateFlow()

    @Volatile
    private var mountedCtxSize: Int = 0

    @Volatile
    private var mountedThreads: Int = 0

    @Volatile
    private var mountedFallbackUsed: Boolean = false

    @Volatile
    private var mountedModelDesc: String = ""

    @Volatile
    private var mountedNativeBridgeHandle: Long = 0L

    @Volatile
    private var mountedNativeEngineId: String = ""

    @Volatile
    private var activeNativeBridgeHandle: Long = 0L

    @Volatile
    private var activeLlamaContext: LlamaContext? = null

    @Volatile
    private var activeLlamaEngine: LlamaAndroid? = null

    @Volatile
    private var activeLlamaContextId: Int? = null

    @Volatile
    private var activeExecuTorchLlmModule: LlmModule? = null

    @Volatile
    private var isCancelled: Boolean = false

    /**
     * Halts active model generation across any active native engine.
     * Synchronized with nativeHandleLock so stopCompletion()/stop() never races with native release()/close().
     */
    fun stopInference() {
        isCancelled = true
        synchronized(nativeHandleLock) {
            val bridgeHandle = activeNativeBridgeHandle.takeIf { it != 0L } ?: mountedNativeBridgeHandle
            if (bridgeHandle != 0L && LeafyNativeBridge.isLibraryLoaded) {
                try {
                    LeafyNativeBridge.nativeCancelGeneration(bridgeHandle)
                } catch (_: Throwable) {}
            }
            val directCtx = activeLlamaContext ?: mountedLlamaContext
            if (directCtx != null && directCtx.context != 0L) {
                try {
                    directCtx.stopCompletion()
                } catch (_: Throwable) {}
            }
            val execLlm = activeExecuTorchLlmModule
            if (execLlm != null) {
                try {
                    execLlm.stop()
                } catch (_: Throwable) {}
            }
        }
        val engine = activeLlamaEngine
        val ctxId = activeLlamaContextId
        if (engine != null && ctxId != null) {
            engineScope.launch {
                try {
                    engine.stopCompletion(ctxId)
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Stops any ongoing generation and frees the mounted native context from RAM.
     */
    fun releaseMountedResources() {
        stopInference()
        engineScope.launch {
            inferenceMutex.withLock {
                unmountLlamaContextLocked()
            }
        }
    }

    /**
     * Returns the absolute file path of the model currently loaded in native RAM, or null if offloaded.
     */
    fun getMountedModelPath(): String? = synchronized(nativeHandleLock) {
        val hasBridge = mountedNativeBridgeHandle != 0L
        val hasLlamaCtx = mountedLlamaContext != null && mountedLlamaContext!!.context != 0L
        if (hasBridge || hasLlamaCtx || mountedModelPath != null) mountedModelPath else null
    }

    /**
     * Explicitly offloads any mounted model and KV cache from RAM.
     */
    suspend fun offloadModelFromRam() = withContext(Dispatchers.IO) {
        stopInference()
        inferenceMutex.withLock {
            unmountLlamaContextLocked()
        }
    }

    /**
     * Explicitly loads a local model file into native RAM (`LeafyNativeBridge` or `LlamaContext`)
     * for the given runtime and power profile.
     */
    suspend fun loadModelIntoRam(
        model: LocalModelEntity,
        runtime: AiRuntime,
        powerProfile: PowerProfile
    ): Result<String> = withContext(Dispatchers.IO) {
        inferenceMutex.withLock {
            try {
                val modelFile = File(model.localFilePath)
                if (!modelFile.exists() || modelFile.length() <= 0L) {
                    return@withLock Result.failure(
                        IllegalStateException("Model file '${model.name}' does not exist on disk.")
                    )
                }
                if (runtime == AiRuntime.EXECUTORCH) {
                    val pteHeader = readPteHeader(modelFile)
                    if (!pteHeader.isValidPte) {
                        return@withLock Result.failure(
                            IllegalStateException("File '${modelFile.name}' is not a valid ExecuTorch .pte model.")
                        )
                    }
                    unmountLlamaContextLocked()
                    synchronized(nativeHandleLock) {
                        mountedModelPath = modelFile.absolutePath
                        mountedCtxSize = powerProfile.contextLimit
                        mountedThreads = powerProfile.cpuThreads
                        _mountedModelPathFlow.value = mountedModelPath
                    }
                    return@withLock Result.success(
                        "Verified '${model.name}' for ${runtime.displayName}."
                    )
                }

                val headerInfo = readGgufHeader(modelFile)
                if (!headerInfo.isValidGguf) {
                    return@withLock Result.failure(
                        IllegalStateException("File '${modelFile.name}' is not a valid GGUF model (found '${headerInfo.magicString}').")
                    )
                }

                val memAssessment = assessDeviceMemoryForModel(modelFile)
                if (memAssessment.isCriticallyLowMemory) {
                    return@withLock Result.failure(
                        IllegalStateException("Insufficient free device RAM (${memAssessment.availMemMb} MB available) to load '${model.name}' into RAM.")
                    )
                }

                val modelTrainedCtx = headerInfo.contextLength?.takeIf { it >= 512 } ?: model.contextWindow.coerceAtLeast(2048)
                val requestedCtx = powerProfile.contextLimit.coerceAtMost(modelTrainedCtx).coerceIn(512, 4096)
                val effectivePrimaryCtx = if (memAssessment.availMemMb < 1024L) {
                    requestedCtx.coerceAtMost(1024)
                } else {
                    requestedCtx.coerceAtMost(2048)
                }
                val clampedThreads = powerProfile.cpuThreads.coerceIn(1, 8)
                val canonicalPath = modelFile.absolutePath
                val engineId = runtime.id

                if (LeafyNativeBridge.ensureLoaded()) {
                    synchronized(nativeHandleLock) {
                        if (mountedNativeBridgeHandle != 0L &&
                            mountedNativeEngineId == engineId &&
                            mountedModelPath == canonicalPath &&
                            mountedCtxSize in 512..4096
                        ) {
                            return@withLock Result.success(
                                "'${model.name}' is already loaded in RAM on ${runtime.displayName} (${mountedCtxSize} ctx)."
                            )
                        }
                    }

                    unmountLlamaContextLocked()
                    val attempts = listOf(
                        Triple(effectivePrimaryCtx, 512, true),
                        Triple(1024, 256, true),
                        Triple(512, 256, true),
                        Triple(512, 256, false)
                    )
                    var handle = 0L
                    for ((tryCtx, tryBatch, tryMmap) in attempts) {
                        val loaded = LeafyNativeBridge.nativeLoadModel(
                            engineId = engineId,
                            modelPath = canonicalPath,
                            nCtx = tryCtx,
                            nBatch = tryBatch,
                            nThreads = clampedThreads,
                            useMmap = tryMmap
                        )
                        if (loaded != 0L) {
                            handle = loaded
                            val actualCtx = LeafyNativeBridge.nativeGetContextSize(loaded).takeIf { it > 0 } ?: tryCtx
                            val nativeDesc = LeafyNativeBridge.nativeGetModelDescription(loaded).trim()
                            synchronized(nativeHandleLock) {
                                mountedNativeBridgeHandle = loaded
                                mountedNativeEngineId = engineId
                                mountedModelPath = canonicalPath
                                mountedCtxSize = actualCtx
                                mountedThreads = clampedThreads
                                mountedModelDesc = nativeDesc
                                _mountedModelPathFlow.value = canonicalPath
                            }
                            break
                        }
                    }

                    if (handle == 0L) {
                        val nativeErr = LeafyNativeBridge.nativeGetLastError().trim()
                        return@withLock Result.failure(
                            IllegalStateException("Native $engineId failed to load '${model.name}' into RAM (${nativeErr.ifBlank { "nativeLoadModel returned 0L" }}).")
                        )
                    }

                    return@withLock Result.success(
                        "Loaded '${model.name}' into RAM on ${runtime.displayName} (${mountedCtxSize} ctx)."
                    )
                } else {
                    val llamaCtx = getOrMountNativeLlamaContextLocked(
                        modelFile = modelFile,
                        requestedCtx = requestedCtx,
                        requestedThreads = clampedThreads,
                        preferredBatchSize = 512
                    )
                    if (llamaCtx.context != 0L) {
                        return@withLock Result.success(
                            "Loaded '${model.name}' into RAM on ${runtime.displayName} (${mountedCtxSize} ctx)."
                        )
                    } else {
                        return@withLock Result.failure(
                            IllegalStateException("Failed to initialize native context for '${model.name}'.")
                        )
                    }
                }
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }
    }

    private fun unmountLlamaContextLocked() {
        val existing: LlamaContext?
        val existingBridgeHandle: Long
        synchronized(nativeHandleLock) {
            existing = mountedLlamaContext
            mountedLlamaContext = null
            if (activeLlamaContext === existing) {
                activeLlamaContext = null
            }
            existingBridgeHandle = mountedNativeBridgeHandle
            mountedNativeBridgeHandle = 0L
            mountedNativeEngineId = ""
            if (activeNativeBridgeHandle == existingBridgeHandle) {
                activeNativeBridgeHandle = 0L
            }
            mountedModelPath = null
            mountedCtxSize = 0
            mountedThreads = 0
            mountedFallbackUsed = false
            mountedModelDesc = ""
            if (existingBridgeHandle != 0L && LeafyNativeBridge.isLibraryLoaded) {
                try {
                    LeafyNativeBridge.nativeCancelGeneration(existingBridgeHandle)
                } catch (_: Throwable) {}
            }
            if (existing != null && existing.context != 0L) {
                try {
                    existing.stopCompletion()
                } catch (_: Throwable) {}
            }
        }
        if (existingBridgeHandle != 0L && LeafyNativeBridge.isLibraryLoaded) {
            synchronized(nativeHandleLock) {
                try {
                    LeafyNativeBridge.nativeFreeModel(existingBridgeHandle)
                } catch (_: Throwable) {}
            }
        }
        if (existing != null) {
            synchronized(nativeHandleLock) {
                try {
                    existing.release()
                } catch (_: Throwable) {}
            }
        }
        synchronized(nativeHandleLock) {
            if (mountedModelPath == null) {
                _mountedModelPathFlow.value = null
            }
        }
    }

    /**
     * Performs on-device keyword and phrase chunk retrieval over pinned RAG documents.
     */
    fun retrieveRagContext(
        query: String,
        pinnedDocs: List<RagDocumentEntity>
    ): List<RetrievedRagSnippet> {
        if (pinnedDocs.isEmpty() || query.isBlank()) return emptyList()

        val queryTerms = query.lowercase()
            .split(Regex("[^a-z0-9._-]+"))
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .distinct()

        if (queryTerms.isEmpty()) return emptyList()

        val results = mutableListOf<RetrievedRagSnippet>()

        for (doc in pinnedDocs) {
            val paragraphs = doc.content
                .split(Regex("\\n\\s*\\n|\\n(?=#|\\d+\\.|-)"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            var bestPassage: String? = null
            var bestScore = 0f

            for (para in paragraphs) {
                val lowerPara = para.lowercase()
                var score = 0f
                for (term in queryTerms) {
                    if (lowerPara.contains(term)) {
                        score += 2.5f
                    }
                    if (doc.title.lowercase().contains(term)) {
                        score += 1.8f
                    }
                }
                if (score > bestScore) {
                    bestScore = score
                    bestPassage = para
                }
            }

            if (bestScore >= 2.0f && bestPassage != null) {
                results.add(
                    RetrievedRagSnippet(
                        documentId = doc.id,
                        documentTitle = doc.title,
                        matchedPassage = bestPassage.take(300),
                        relevanceScore = bestScore
                    )
                )
            }
        }

        return results.sortedByDescending { it.relevanceScore }.take(2)
    }

    /**
     * Streams real native token generation directly from the model weights on disk.
     * No simulated, fallback, or placeholder text generation paths exist.
     */
    fun streamResponse(
        userPrompt: String,
        model: LocalModelEntity,
        runtime: AiRuntime,
        powerProfile: PowerProfile,
        persona: PersonaPresetEntity,
        pinnedDocs: List<RagDocumentEntity>,
        @Suppress("UNUSED_PARAMETER") branchAttemptIndex: Int = 1,
        conversationHistory: List<MessageNodeEntity> = emptyList(),
        hardwareMeasuredMilliwatts: Float = -1f
    ): Flow<StreamingStep> = flow {
        isCancelled = false

        val modelFile = File(model.localFilePath)
        if (!model.isInstalled || model.localFilePath.isBlank() || !modelFile.exists() || modelFile.length() <= 0L) {
            emit(
                StreamingStep(
                    accumulatedText = "Error: Local AI model weights not found on device ('${model.name}').\n\nPlease load a valid local model file from your device storage in the Local Models screen.",
                    currentTps = 0f,
                    ttftMs = 0,
                    generatedTokens = 0,
                    energyMicroWh = -1f,
                    citedRagSnippets = emptyList(),
                    isFinished = true
                )
            )
            return@flow
        }

        if (!runtime.isCompatibleWithModel(model)) {
            emit(
                StreamingStep(
                    accumulatedText = "Runtime compatibility error: Active model '${model.name}' (${model.quantization}) is not compatible with ${runtime.displayName} (requires ${runtime.fileExtensions.joinToString(" or ")} format).\n\nPlease switch to a compatible runtime or load a ${runtime.fileExtensions.first()} model file.",
                    currentTps = 0f,
                    ttftMs = 0,
                    generatedTokens = 0,
                    energyMicroWh = -1f,
                    citedRagSnippets = emptyList(),
                    isFinished = true
                )
            )
            return@flow
        }

        val retrievedRag = retrieveRagContext(userPrompt, pinnedDocs)
        val formattedPrompt = buildFormattedPrompt(userPrompt, persona, retrievedRag, conversationHistory)

        when (runtime) {
            AiRuntime.LLAMA_CPP -> {
                emitAll(
                    streamLlamaCppEngine(
                        modelFile = modelFile,
                        rawUserPrompt = userPrompt,
                        formattedPrompt = formattedPrompt,
                        model = model,
                        powerProfile = powerProfile,
                        persona = persona,
                        retrievedRag = retrievedRag,
                        conversationHistory = conversationHistory,
                        hardwareMeasuredMilliwatts = hardwareMeasuredMilliwatts
                    )
                )
            }
            AiRuntime.BITNET_CPP -> {
                emitAll(
                    streamBitNetEngine(
                        modelFile = modelFile,
                        rawUserPrompt = userPrompt,
                        formattedPrompt = formattedPrompt,
                        model = model,
                        powerProfile = powerProfile,
                        persona = persona,
                        retrievedRag = retrievedRag,
                        conversationHistory = conversationHistory,
                        hardwareMeasuredMilliwatts = hardwareMeasuredMilliwatts
                    )
                )
            }
            AiRuntime.EXECUTORCH -> {
                emitAll(
                    streamExecuTorchEngine(
                        modelFile = modelFile,
                        rawUserPrompt = userPrompt,
                        model = model,
                        powerProfile = powerProfile,
                        persona = persona,
                        retrievedRag = retrievedRag,
                        conversationHistory = conversationHistory,
                        hardwareMeasuredMilliwatts = hardwareMeasuredMilliwatts
                    )
                )
            }
            AiRuntime.T_MAC -> {
                emitAll(
                    streamTMacLutEngine(
                        modelFile = modelFile,
                        rawUserPrompt = userPrompt,
                        formattedPrompt = formattedPrompt,
                        model = model,
                        powerProfile = powerProfile,
                        persona = persona,
                        retrievedRag = retrievedRag,
                        conversationHistory = conversationHistory,
                        hardwareMeasuredMilliwatts = hardwareMeasuredMilliwatts
                    )
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    private data class DeviceMemoryAssessment(
        val availMemMb: Long,
        val totalMemMb: Long,
        val isSystemLowMemory: Boolean,
        val canAllocateOnHeap: Boolean,
        val isCriticallyLowMemory: Boolean
    )

    private fun assessDeviceMemoryForModel(modelFile: File): DeviceMemoryAssessment {
        val modelSizeMb = (modelFile.length() / (1024L * 1024L)).coerceAtLeast(1L)
        val activityManager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (activityManager == null) {
            return DeviceMemoryAssessment(
                availMemMb = 2048L,
                totalMemMb = 4096L,
                isSystemLowMemory = false,
                canAllocateOnHeap = modelSizeMb <= 1024L,
                isCriticallyLowMemory = false
            )
        }
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024L * 1024L)
        val totalMb = memInfo.totalMem / (1024L * 1024L)
        val thresholdMb = memInfo.threshold / (1024L * 1024L)
        val canHeapLoad = !memInfo.lowMemory && availMb > (modelSizeMb + thresholdMb + 384L)
        val criticallyLow = availMb in 1L..127L
        return DeviceMemoryAssessment(
            availMemMb = availMb,
            totalMemMb = totalMb,
            isSystemLowMemory = memInfo.lowMemory,
            canAllocateOnHeap = canHeapLoad,
            isCriticallyLowMemory = criticallyLow
        )
    }

    internal fun openDetachedModelFd(modelFile: File): Int {
        val pfd = ParcelFileDescriptor.open(modelFile, ParcelFileDescriptor.MODE_READ_ONLY)
            ?: throw IllegalStateException("Unable to open read-only file descriptor for '${modelFile.name}'.")
        val fd = try {
            pfd.detachFd()
        } catch (_: NoSuchMethodError) {
            val rawFd = pfd.fd.coerceAtLeast(0)
            try { pfd.close() } catch (_: Throwable) {}
            rawFd
        }
        if (fd < 0) {
            throw IllegalStateException("Invalid detached file descriptor ($fd) for '${modelFile.name}'.")
        }
        return fd
    }

    internal fun buildLlamaContextParams(
        canonicalPath: String,
        modelFd: Int,
        nCtx: Int,
        nBatch: Int,
        nThreads: Int,
        useMmap: Boolean
    ): Map<String, Any> {
        require(modelFd >= 0) { "model_fd must be a non-negative file descriptor (was $modelFd)" }
        return mapOf(
            "model" to canonicalPath,
            "model_fd" to modelFd,
            "n_ctx" to nCtx,
            "n_batch" to nBatch,
            "n_threads" to nThreads,
            "n_gpu_layers" to 0,
            "use_mmap" to useMmap,
            "use_mlock" to false,
            "embedding" to false,
            "vocab_only" to false,
            "lora" to "",
            "lora_scaled" to 1.0,
            "rope_freq_base" to 0.0,
            "rope_freq_scale" to 0.0
        )
    }

    /**
     * Captures the app process's own recent native logcat lines from `RNLlama` / `llama.cpp`
     * when `initContextWithFd` returns 0L so the exact native C++ failure is surfaced to the UI.
     */
    private fun captureRecentNativeLlamaLogs(): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "80"))
            val matchingLines = BufferedReader(InputStreamReader(proc.inputStream)).useLines { lines ->
                lines.filter { line ->
                    line.contains("RNLlama", ignoreCase = true) ||
                        line.contains("llama_model_load", ignoreCase = true) ||
                        line.contains("gguf_init", ignoreCase = true) ||
                        line.contains("failed to", ignoreCase = true) ||
                        line.contains("unknown model architecture", ignoreCase = true) ||
                        line.contains("not implemented", ignoreCase = true)
                }.toList()
            }
            matchingLines.takeLast(4).joinToString(" | ").trim()
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Mounts or reuses a native LlamaContext from `org.nehuatl:llamacpp-kotlin:0.4.0`.
     */
    private fun getOrMountNativeLlamaContextLocked(
        modelFile: File,
        requestedCtx: Int,
        requestedThreads: Int,
        preferredBatchSize: Int = 512
    ): LlamaContext {
        val canonicalPath = modelFile.absolutePath
        val header = readGgufHeader(modelFile)

        // Check if GGUF uses Microsoft's BitNet-b1.58 / bitnet-25 I2_S / gated-squared-ReLU architecture
        // which is not implemented in mainline llama.cpp (librnllama.so)
        val archLower = header.architecture?.lowercase() ?: ""
        if (archLower == "bitnet-b1.58" || archLower == "bitnet-25" || archLower == "bitnet-b1.58-2b") {
            throw NotImplementedError(
                "GGUF model '${modelFile.name}' specifies general.architecture='${header.architecture}' (${header.quantizationLabel}), " +
                    "which requires Microsoft bitnet.cpp's gated-squared-ReLU / I2_S kernel. " +
                    "The bundled librnllama.so (org.nehuatl:llamacpp-kotlin:0.4.0) only supports mainline llama.cpp architectures " +
                    "(including general.architecture='bitnet' with TQ1_0/TQ2_0)."
            )
        }

        val primaryCtxSize = requestedCtx.coerceIn(512, 4096)
        val clampedThreads = requestedThreads.coerceIn(1, 8)

        val memAssessment = assessDeviceMemoryForModel(modelFile)
        if (memAssessment.isCriticallyLowMemory) {
            throw IllegalStateException(
                "Insufficient free device RAM (${memAssessment.availMemMb} MB available) to mount '${modelFile.name}'. Close background apps or switch to Eco Leaf profile."
            )
        }

        val effectivePrimaryCtx = if (memAssessment.availMemMb < 1024L) {
            primaryCtxSize.coerceAtMost(1024)
        } else {
            primaryCtxSize.coerceAtMost(2048)
        }
        val primaryBatch = minOf(preferredBatchSize, 512)

        synchronized(nativeHandleLock) {
            val existing = mountedLlamaContext
            val ctxMatches = mountedCtxSize == effectivePrimaryCtx || (mountedFallbackUsed && mountedCtxSize in 512..2048)
            if (existing != null &&
                existing.context != 0L &&
                mountedModelPath == canonicalPath &&
                ctxMatches
            ) {
                return existing
            }
        }

        unmountLlamaContextLocked()

        val supported64Bit = Build.SUPPORTED_64_BIT_ABIS?.any { it == "arm64-v8a" || it == "x86_64" } ?: true
        if (!supported64Bit) {
            throw IllegalStateException("Only 64-bit architectures (arm64-v8a / x86_64) are supported by native llama.cpp.")
        }

        data class InitAttempt(
            val nCtx: Int,
            val nBatch: Int,
            val nThreads: Int,
            val useMmap: Boolean,
            val isFallback: Boolean
        )

        // Try useMmap = true first (maps weights directly from OS page cache without 1GB+ heap malloc
        // and without advancing the fd offset across llama_params_fit + llama_model_load_from_file),
        // then test LlamaContext's native default n_ctx = 512, then non-mmap fallbacks.
        val attempts = listOf(
            InitAttempt(
                nCtx = effectivePrimaryCtx,
                nBatch = primaryBatch,
                nThreads = clampedThreads,
                useMmap = true,
                isFallback = false
            ),
            InitAttempt(
                nCtx = 512,
                nBatch = 512,
                nThreads = 0,
                useMmap = true,
                isFallback = true
            ),
            InitAttempt(
                nCtx = effectivePrimaryCtx,
                nBatch = primaryBatch,
                nThreads = 0,
                useMmap = false,
                isFallback = false
            ),
            InitAttempt(
                nCtx = 1024,
                nBatch = 256,
                nThreads = clampedThreads,
                useMmap = true,
                isFallback = true
            ),
            InitAttempt(
                nCtx = 512,
                nBatch = 256,
                nThreads = 0,
                useMmap = false,
                isFallback = true
            )
        )

        var lastError: Throwable? = null
        for (attempt in attempts) {
            try {
                val detachedFd = openDetachedModelFd(modelFile)
                val params = buildLlamaContextParams(
                    canonicalPath = canonicalPath,
                    modelFd = detachedFd,
                    nCtx = attempt.nCtx,
                    nBatch = attempt.nBatch,
                    nThreads = attempt.nThreads,
                    useMmap = attempt.useMmap
                )
                val ctxId = abs(Random.nextInt(1, Int.MAX_VALUE))
                val llamaCtx = LlamaContext(ctxId, params)
                if (llamaCtx.context != 0L) {
                    val detailsMap = try { llamaCtx.modelDetails } catch (_: Throwable) { emptyMap<String, Any>() }
                    val nativeDesc = (detailsMap["desc"] as? String)?.trim().orEmpty()
                    synchronized(nativeHandleLock) {
                        mountedLlamaContext = llamaCtx
                        mountedModelPath = canonicalPath
                        mountedCtxSize = attempt.nCtx
                        mountedThreads = if (attempt.nThreads > 0) attempt.nThreads else clampedThreads
                        mountedFallbackUsed = attempt.isFallback
                        mountedModelDesc = nativeDesc
                        _mountedModelPathFlow.value = canonicalPath
                    }
                    return llamaCtx
                }
                try { llamaCtx.release() } catch (_: Throwable) {}
            } catch (err: Throwable) {
                lastError = err
            }
        }

        val nativeLogSnippet = captureRecentNativeLlamaLogs()
        val archDetail = header.architecture?.let { "arch='$it', " } ?: ""
        val quantDetail = "quant='${header.quantizationLabel}', "
        val headerDetail = "${archDetail}${quantDetail}GGUF v${header.version}, tensors=${header.tensorCount}, size=${modelFile.length() / (1024 * 1024)} MB, availRam=${memAssessment.availMemMb} MB"
        val logSuffix = if (nativeLogSnippet.isNotBlank()) " | Native log: $nativeLogSnippet" else ""
        throw IllegalStateException(
            "Native context initialization failed (${lastError?.message ?: "initContextWithFd returned 0L"}; $headerDetail$logSuffix).",
            lastError
        )
    }

    private fun computeGenerationTokenBudget(effectiveCtxSize: Int): Int {
        val safeCtx = effectiveCtxSize.coerceIn(512, 4096)
        return (safeCtx / 6).coerceIn(64, 384)
    }

    private fun computeMaxPromptChars(effectiveCtxSize: Int, nPredict: Int): Int {
        val safeCtx = effectiveCtxSize.coerceIn(512, 4096)
        val availablePromptTokens = ((safeCtx / 2) - nPredict - 24).coerceIn(64, 1024)
        return (availablePromptTokens * 3).coerceIn(256, 3072)
    }

    private fun filterRecentHistory(
        history: List<MessageNodeEntity>,
        maxTurns: Int = 6,
        maxCharsPerTurn: Int = 500
    ): List<MessageNodeEntity> {
        if (history.isEmpty() || maxTurns <= 0) return emptyList()
        return history
            .filter { node ->
                node.content.isNotBlank() &&
                    !node.content.startsWith("Error:") &&
                    !node.content.startsWith("**[Local Inference Error]**") &&
                    !node.content.startsWith("Runtime compatibility error:") &&
                    !node.content.startsWith("Inference execution error:") &&
                    !node.content.startsWith("BitNet") &&
                    !node.content.startsWith("ExecuTorch") &&
                    !node.content.startsWith("T-MAC") &&
                    !node.content.startsWith("Native runtime notice:") &&
                    !node.content.contains("[Inference interrupted:")
            }
            .takeLast(maxTurns)
            .map { it.copy(content = it.content.trim().take(maxCharsPerTurn)) }
    }

    private fun resolveModelChatPrompt(
        @Suppress("UNUSED_PARAMETER") llamaCtx: LlamaContext,
        userPrompt: String,
        persona: PersonaPresetEntity,
        retrievedRag: List<RetrievedRagSnippet>,
        conversationHistory: List<MessageNodeEntity>,
        fallbackFormattedPrompt: String,
        effectiveCtxSize: Int = 2048
    ): String {
        val nPredict = computeGenerationTokenBudget(effectiveCtxSize)
        val maxPromptChars = computeMaxPromptChars(effectiveCtxSize, nPredict)
        val maxTurns = if (effectiveCtxSize <= 1024) 2 else 4
        val maxTurnChars = if (effectiveCtxSize <= 1024) 220 else 400
        val boundedUserPrompt = userPrompt.trim().take((maxPromptChars / 2).coerceAtLeast(200))

        var turnsToKeep = maxTurns
        while (turnsToKeep >= 0) {
            val boundedHistory = filterRecentHistory(conversationHistory, turnsToKeep, maxTurnChars)
            val candidate = buildFormattedPrompt(
                userPrompt = boundedUserPrompt,
                persona = persona,
                ragSnippets = retrievedRag,
                conversationHistory = boundedHistory,
                effectiveCtxSize = effectiveCtxSize
            )

            if (candidate.length <= maxPromptChars || turnsToKeep == 0) {
                return if (candidate.length > maxPromptChars) {
                    candidate.takeLast(maxPromptChars)
                } else {
                    candidate
                }
            }
            turnsToKeep--
        }

        return fallbackFormattedPrompt.takeLast(maxPromptChars)
    }

    /**
     * Engine 1: llama.cpp Native GGUF Execution (`librnllama_v8_2_dotprod_i8mm.so` / `librnllama_v8.so` from `mybigday/llama.rn` v0.13.0-rc.9, commit `27783c86ce` / `ggml-org/llama.cpp` `0ec4027a2a`).
     */
    private fun streamLlamaCppEngine(
        modelFile: File,
        rawUserPrompt: String,
        formattedPrompt: String,
        model: LocalModelEntity,
        powerProfile: PowerProfile,
        persona: PersonaPresetEntity,
        retrievedRag: List<RetrievedRagSnippet>,
        conversationHistory: List<MessageNodeEntity>,
        hardwareMeasuredMilliwatts: Float
    ): Flow<StreamingStep> = flow {
        val headerInfo = readGgufHeader(modelFile)
        if (!headerInfo.isValidGguf) {
            emit(
                StreamingStep(
                    accumulatedText = "Error: Loaded file '${modelFile.name}' (${modelFile.length() / (1024 * 1024)} MB) is not a valid GGUF model file.\n\nFile validation failed: Magic header is '${headerInfo.magicString}', expected 'GGUF'.",
                    currentTps = 0f,
                    ttftMs = 0,
                    generatedTokens = 0,
                    energyMicroWh = -1f,
                    citedRagSnippets = emptyList(),
                    isFinished = true
                )
            )
            return@flow
        }

        if (context == null) {
            emit(
                StreamingStep(
                    accumulatedText = "Error: Android context is required for llama.cpp native engine initialization.",
                    currentTps = 0f,
                    ttftMs = 0,
                    generatedTokens = 0,
                    energyMicroWh = -1f,
                    citedRagSnippets = emptyList(),
                    isFinished = true
                )
            )
            return@flow
        }

        if (LeafyNativeBridge.ensureLoaded()) {
            emitAll(
                executeNativeBridgeCompletion(
                    engineId = AiRuntime.LLAMA_CPP.id,
                    modelFile = modelFile,
                    headerInfo = headerInfo,
                    rawUserPrompt = rawUserPrompt,
                    formattedPrompt = formattedPrompt,
                    model = model,
                    powerProfile = powerProfile,
                    persona = persona,
                    retrievedRag = retrievedRag,
                    conversationHistory = conversationHistory,
                    hardwareMeasuredMilliwatts = hardwareMeasuredMilliwatts
                )
            )
        } else {
            emitAll(
                executeNativeLibrnllamaCompletion(
                    modelFile = modelFile,
                    headerInfo = headerInfo,
                    rawUserPrompt = rawUserPrompt,
                    formattedPrompt = formattedPrompt,
                    model = model,
                    powerProfile = powerProfile,
                    persona = persona,
                    retrievedRag = retrievedRag,
                    conversationHistory = conversationHistory,
                    hardwareMeasuredMilliwatts = hardwareMeasuredMilliwatts,
                    backendLabel = "librnllama JNI (llama.cpp)"
                )
            )
        }
    }

    /**
     * Engine 2: Bitnet.cpp Native Runtime (`libbitnet_llama.so` built from `microsoft/BitNet` commit `0b341e582a` + `isHuangXin/llama.cpp` `390c307`,
     * supporting 1.58-bit `I2_S` ARM NEON MAD, `TL1` LUT kernels, `TQ1_0`/`TQ2_0`, and `bitnet-b1.58`/`bitnet-25` gated squared ReLU).
     */
    private fun streamBitNetEngine(
        modelFile: File,
        rawUserPrompt: String,
        formattedPrompt: String,
        model: LocalModelEntity,
        powerProfile: PowerProfile,
        persona: PersonaPresetEntity,
        retrievedRag: List<RetrievedRagSnippet>,
        conversationHistory: List<MessageNodeEntity>,
        hardwareMeasuredMilliwatts: Float
    ): Flow<StreamingStep> = flow {
        val headerInfo = readGgufHeader(modelFile)
        if (!headerInfo.isValidGguf) {
            emit(
                StreamingStep(
                    accumulatedText = "Bitnet.cpp runtime error: File '${modelFile.name}' is not a valid GGUF model file (found header '${headerInfo.magicString}', expected 'GGUF').",
                    currentTps = 0f,
                    ttftMs = 0,
                    generatedTokens = 0,
                    energyMicroWh = -1f,
                    citedRagSnippets = emptyList(),
                    isFinished = true
                )
            )
            return@flow
        }

        if (context == null) {
            emit(
                StreamingStep(
                    accumulatedText = "Error: Android context is required for native Bitnet.cpp initialization.",
                    currentTps = 0f,
                    ttftMs = 0,
                    generatedTokens = 0,
                    energyMicroWh = -1f,
                    citedRagSnippets = emptyList(),
                    isFinished = true
                )
            )
            return@flow
        }

        if (LeafyNativeBridge.ensureLoaded()) {
            emitAll(
                executeNativeBridgeCompletion(
                    engineId = AiRuntime.BITNET_CPP.id,
                    modelFile = modelFile,
                    headerInfo = headerInfo,
                    rawUserPrompt = rawUserPrompt,
                    formattedPrompt = formattedPrompt,
                    model = model,
                    powerProfile = powerProfile,
                    persona = persona,
                    retrievedRag = retrievedRag,
                    conversationHistory = conversationHistory,
                    hardwareMeasuredMilliwatts = hardwareMeasuredMilliwatts
                )
            )
        } else {
            emit(
                StreamingStep(
                    accumulatedText = "Bitnet.cpp native bridge error: Failed to load 'libleafy_runtime_jni.so' / 'libbitnet_llama.so' (${LeafyNativeBridge.loadError ?: "JNI library unavailable on this ABI"}).",
                    currentTps = 0f,
                    ttftMs = 0,
                    generatedTokens = 0,
                    energyMicroWh = -1f,
                    citedRagSnippets = emptyList(),
                    isFinished = true
                )
            )
        }
    }

    /**
     * Executes genuine native completion via `LeafyNativeBridge` (`libleafy_runtime_jni.so`), which dynamically links:
     * - `LLAMA_CPP`: `librnllama_v8_2_dotprod_i8mm.so` / `librnllama_v8.so` (`mybigday/llama.rn` v0.13.0-rc.9, commit `27783c86ce` / `ggml-org/llama.cpp` `0ec4027a2a`)
     * - `BITNET_CPP`: `libbitnet_llama.so` (`microsoft/BitNet` `0b341e582a` + `isHuangXin/llama.cpp` `390c307`)
     * - `T_MAC`: `libtmac_llama.so` (`microsoft/T-MAC` `7042f8f733` + `kaleid-liner/llama.cpp` `eb07ecf`)
     */
    private fun executeNativeBridgeCompletion(
        engineId: String,
        modelFile: File,
        headerInfo: GgufHeaderInfo,
        rawUserPrompt: String,
        formattedPrompt: String,
        model: LocalModelEntity,
        powerProfile: PowerProfile,
        persona: PersonaPresetEntity,
        retrievedRag: List<RetrievedRagSnippet>,
        conversationHistory: List<MessageNodeEntity>,
        hardwareMeasuredMilliwatts: Float
    ): Flow<StreamingStep> = flow {
        inferenceMutex.withLock {
            var handle = 0L
            var genJob: Job? = null
            var invalidateOnError = false
            try {
                val memAssessment = assessDeviceMemoryForModel(modelFile)
                if (memAssessment.isCriticallyLowMemory) {
                    throw IllegalStateException(
                        "Insufficient free device RAM (${memAssessment.availMemMb} MB available) to mount '${modelFile.name}'. Close background apps or switch to Eco Leaf profile."
                    )
                }

                val modelTrainedCtx = headerInfo.contextLength?.takeIf { it >= 512 } ?: model.contextWindow.coerceAtLeast(2048)
                val requestedCtx = powerProfile.contextLimit.coerceAtMost(modelTrainedCtx).coerceIn(512, 4096)
                val effectivePrimaryCtx = if (memAssessment.availMemMb < 1024L) {
                    requestedCtx.coerceAtMost(1024)
                } else {
                    requestedCtx.coerceAtMost(2048)
                }
                val clampedThreads = powerProfile.cpuThreads.coerceIn(1, 8)
                val canonicalPath = modelFile.absolutePath

                synchronized(nativeHandleLock) {
                    if (mountedNativeBridgeHandle != 0L &&
                        mountedNativeEngineId == engineId &&
                        mountedModelPath == canonicalPath &&
                        mountedCtxSize in 512..4096
                    ) {
                        handle = mountedNativeBridgeHandle
                    }
                }

                if (handle == 0L) {
                    unmountLlamaContextLocked()
                    val attempts = listOf(
                        Triple(effectivePrimaryCtx, 512, true),
                        Triple(1024, 256, true),
                        Triple(512, 256, true),
                        Triple(512, 256, false)
                    )
                    for ((tryCtx, tryBatch, tryMmap) in attempts) {
                        val loaded = LeafyNativeBridge.nativeLoadModel(
                            engineId = engineId,
                            modelPath = canonicalPath,
                            nCtx = tryCtx,
                            nBatch = tryBatch,
                            nThreads = clampedThreads,
                            useMmap = tryMmap
                        )
                        if (loaded != 0L) {
                            handle = loaded
                            val actualCtx = LeafyNativeBridge.nativeGetContextSize(loaded).takeIf { it > 0 } ?: tryCtx
                            val nativeDesc = LeafyNativeBridge.nativeGetModelDescription(loaded).trim()
                            synchronized(nativeHandleLock) {
                                mountedNativeBridgeHandle = loaded
                                mountedNativeEngineId = engineId
                                mountedModelPath = canonicalPath
                                mountedCtxSize = actualCtx
                                mountedThreads = clampedThreads
                                mountedModelDesc = nativeDesc
                                _mountedModelPathFlow.value = canonicalPath
                            }
                            break
                        }
                    }
                }

                if (handle == 0L) {
                    val nativeErr = LeafyNativeBridge.nativeGetLastError().trim()
                    val archDetail = headerInfo.architecture?.let { "arch='$it', " } ?: ""
                    val quantDetail = "quant='${headerInfo.quantizationLabel}', "
                    val headerDetail = "${archDetail}${quantDetail}GGUF v${headerInfo.version}, tensors=${headerInfo.tensorCount}, size=${modelFile.length() / (1024 * 1024)} MB, availRam=${memAssessment.availMemMb} MB"
                    throw IllegalStateException(
                        "Native $engineId initialization failed (${nativeErr.ifBlank { "nativeLoadModel returned 0L" }}; $headerDetail)."
                    )
                }

                synchronized(nativeHandleLock) {
                    activeNativeBridgeHandle = handle
                }

                val effectiveCtx = mountedCtxSize.takeIf { it > 0 } ?: effectivePrimaryCtx
                val nPredict = computeGenerationTokenBudget(effectiveCtx)
                val runtimeBackend = LeafyNativeBridge.nativeGetBackendDescription(handle).ifBlank { engineId }
                val runtimeDesc = LeafyNativeBridge.nativeGetModelDescription(handle).ifBlank {
                    headerInfo.modelName ?: headerInfo.architecture ?: model.name
                }
                val runtimeQuant = headerInfo.quantizationLabel

                val effectivePrompt = buildNativeTemplatedPrompt(
                    handle = handle,
                    userPrompt = rawUserPrompt,
                    persona = persona,
                    retrievedRag = retrievedRag,
                    conversationHistory = conversationHistory,
                    fallbackFormattedPrompt = formattedPrompt,
                    effectiveCtxSize = effectiveCtx,
                    ggufChatTemplate = headerInfo.chatTemplate
                )

                val exactPromptTokens = LeafyNativeBridge.nativeTokenizeCount(handle, effectivePrompt).coerceAtLeast(0)
                val tpsTracker = LiveTpsTelemetryTracker(
                    promptTokensCount = exactPromptTokens,
                    hardwareMeasuredMilliwatts = hardwareMeasuredMilliwatts
                )
                emit(
                    tpsTracker.buildPrefillStep(retrievedRag, InferenceTelemetryPhase.PREFILLING).copy(
                        runtimeBackendLabel = runtimeBackend,
                        runtimeModelDesc = runtimeDesc,
                        runtimeQuantizationLabel = runtimeQuant
                    )
                )

                val tokenChannel = Channel<String>(Channel.UNLIMITED)
                var nativeTimings: NativeGenTimings? = null
                var genError: Throwable? = null
                val targetHandle = handle

                genJob = engineScope.launch {
                    try {
                        nativeTimings = LeafyNativeBridge.nativeGenerateStream(
                            handle = targetHandle,
                            prompt = effectivePrompt,
                            nPredict = nPredict,
                            nThreads = clampedThreads,
                            temperature = persona.temperature,
                            topP = persona.topP,
                            repeatPenalty = persona.repeatPenalty,
                            stopSequences = STOP_SEQUENCES.toTypedArray(),
                            callback = TokenStreamCallback { piece ->
                                if (isCancelled) {
                                    false
                                } else {
                                    tokenChannel.trySend(piece)
                                    true
                                }
                            }
                        )
                    } catch (t: Throwable) {
                        invalidateOnError = true
                        genError = t
                    } finally {
                        tokenChannel.close()
                    }
                }

                val accumulated = StringBuilder()
                for (piece in tokenChannel) {
                    accumulated.append(piece)
                    val cleanText = cleanStopTokens(accumulated.toString(), STOP_SEQUENCES)
                    emit(
                        tpsTracker.onTokenReceived(cleanText, retrievedRag, pieceTokenDelta = 1).copy(
                            runtimeBackendLabel = runtimeBackend,
                            runtimeModelDesc = runtimeDesc,
                            runtimeQuantizationLabel = runtimeQuant
                        )
                    )
                }

                genJob.join()

                if (genError != null && accumulated.isEmpty()) {
                    throw genError!!
                }

                val timings = nativeTimings
                if (timings != null && timings.errorCode != 0 && accumulated.isEmpty()) {
                    invalidateOnError = true
                    throw IllegalStateException(
                        timings.errorMessage.ifBlank { "Native generation error code ${timings.errorCode}" }
                    )
                }

                emit(
                    tpsTracker.buildFinalStep(
                        cleanAccumulatedText = cleanStopTokens(accumulated.toString(), STOP_SEQUENCES),
                        citedRagSnippets = retrievedRag,
                        nativeGeneratedTokenCount = timings?.generatedTokens?.takeIf { it > 0 },
                        nativePromptTokenCount = timings?.promptTokens?.takeIf { it > 0 },
                        nativeDecodeTpsOverride = timings?.decodeTps?.toFloat()?.takeIf { it > 0f && it.isFinite() },
                        nativePrefillTpsOverride = timings?.prefillTps?.toFloat()?.takeIf { it > 0f && it.isFinite() },
                        nativePromptMsOverride = timings?.prefillMs?.toFloat()?.takeIf { it > 0f && it.isFinite() },
                        nativeDecodeMsOverride = timings?.decodeMs?.toFloat()?.takeIf { it > 0f && it.isFinite() }
                    ).copy(
                        runtimeBackendLabel = runtimeBackend,
                        runtimeModelDesc = runtimeDesc,
                        runtimeQuantizationLabel = runtimeQuant
                    )
                )
            } catch (e: Throwable) {
                invalidateOnError = true
                if (e is kotlinx.coroutines.CancellationException) throw e
                emit(
                    StreamingStep(
                        accumulatedText = "$engineId execution error: ${e.message ?: e.javaClass.simpleName}",
                        currentTps = 0f,
                        ttftMs = 0,
                        generatedTokens = 0,
                        energyMicroWh = -1f,
                        citedRagSnippets = retrievedRag,
                        isFinished = true
                    )
                )
            } finally {
                withContext(NonCancellable) {
                    synchronized(nativeHandleLock) {
                        if (handle != 0L && LeafyNativeBridge.isLibraryLoaded) {
                            try { LeafyNativeBridge.nativeCancelGeneration(handle) } catch (_: Throwable) {}
                        }
                    }
                    try { genJob?.join() } catch (_: Throwable) {}
                    synchronized(nativeHandleLock) {
                        activeNativeBridgeHandle = 0L
                    }
                    if (invalidateOnError) {
                        unmountLlamaContextLocked()
                    }
                }
            }
        }
    }

    private fun buildNativeTemplatedPrompt(
        handle: Long,
        userPrompt: String,
        persona: PersonaPresetEntity,
        retrievedRag: List<RetrievedRagSnippet>,
        conversationHistory: List<MessageNodeEntity>,
        fallbackFormattedPrompt: String,
        effectiveCtxSize: Int,
        ggufChatTemplate: String? = null
    ): String {
        val nPredict = computeGenerationTokenBudget(effectiveCtxSize)
        val maxPromptChars = computeMaxPromptChars(effectiveCtxSize, nPredict)
        val maxTurns = if (effectiveCtxSize <= 1024) 2 else 4
        val maxTurnChars = if (effectiveCtxSize <= 1024) 220 else 400
        val maxRagChars = if (effectiveCtxSize <= 512) 180 else 300

        val ragContextText = if (retrievedRag.isNotEmpty()) {
            "\n\nContext from device knowledge:\n" + retrievedRag.joinToString("\n\n") {
                "Document: ${it.documentTitle}\nPassage: ${it.matchedPassage.take(maxRagChars)}"
            }
        } else ""
        val systemInstruction = "${persona.systemPrompt.take(400)}$ragContextText".trim()
        val boundedHistory = filterRecentHistory(conversationHistory, maxTurns, maxTurnChars)

        val roles = mutableListOf<String>()
        val contents = mutableListOf<String>()
        if (systemInstruction.isNotBlank()) {
            roles.add("system")
            contents.add(systemInstruction)
        }
        for (hist in boundedHistory) {
            roles.add(if (hist.role.equals("USER", ignoreCase = true)) "user" else "assistant")
            contents.add(hist.content.trim())
        }
        roles.add("user")
        contents.add(userPrompt.trim().take((maxPromptChars / 2).coerceAtLeast(200)))

        val templated = try {
            LeafyNativeBridge.nativeApplyChatTemplate(
                handle = handle,
                roles = roles.toTypedArray(),
                contents = contents.toTypedArray(),
                addAssistantPrefix = true
            )
        } catch (_: Throwable) {
            ""
        }
        if (templated.isNotBlank()) {
            return if (templated.length > maxPromptChars) templated.takeLast(maxPromptChars) else templated
        }
        val ggufFormatted = buildFormattedPrompt(
            userPrompt = userPrompt.trim().take((maxPromptChars / 2).coerceAtLeast(200)),
            persona = persona,
            ragSnippets = retrievedRag,
            conversationHistory = boundedHistory,
            effectiveCtxSize = effectiveCtxSize,
            chatTemplateHint = ggufChatTemplate
        )
        return if (ggufFormatted.isNotBlank()) {
            ggufFormatted.takeLast(maxPromptChars)
        } else {
            fallbackFormattedPrompt.takeLast(maxPromptChars)
        }
    }

    /**
     * Executes genuine native completion via `org.nehuatl.llamacpp.LlamaContext` (`librnllama.so`).
     * Counts prompt tokens via native `llamaCtx.tokenize()`, counts generated tokens strictly per
     * sampled token callback, and reads backend/model metadata from `llamaCtx.modelDetails` and the GGUF header.
     */
    private fun executeNativeLibrnllamaCompletion(
        modelFile: File,
        headerInfo: GgufHeaderInfo,
        rawUserPrompt: String,
        formattedPrompt: String,
        model: LocalModelEntity,
        powerProfile: PowerProfile,
        persona: PersonaPresetEntity,
        retrievedRag: List<RetrievedRagSnippet>,
        conversationHistory: List<MessageNodeEntity>,
        hardwareMeasuredMilliwatts: Float,
        backendLabel: String
    ): Flow<StreamingStep> = flow {
        inferenceMutex.withLock {
            var llamaCtx: LlamaContext? = null
            var completionJob: Job? = null
            var invalidateMountedOnError = false
            try {
                val modelTrainedCtx = headerInfo.contextLength?.takeIf { it >= 512 } ?: model.contextWindow.coerceAtLeast(2048)
                val targetCtx = powerProfile.contextLimit.coerceAtMost(modelTrainedCtx).coerceIn(512, 4096)
                llamaCtx = getOrMountNativeLlamaContextLocked(
                    modelFile = modelFile,
                    requestedCtx = targetCtx,
                    requestedThreads = powerProfile.cpuThreads,
                    preferredBatchSize = 512
                )
                synchronized(nativeHandleLock) {
                    activeLlamaContext = llamaCtx
                }
                val effectiveCtx = mountedCtxSize.takeIf { it > 0 } ?: targetCtx
                val nPredict = computeGenerationTokenBudget(effectiveCtx)

                val effectivePrompt = resolveModelChatPrompt(
                    llamaCtx = llamaCtx,
                    userPrompt = rawUserPrompt,
                    persona = persona,
                    retrievedRag = retrievedRag,
                    conversationHistory = conversationHistory,
                    fallbackFormattedPrompt = formattedPrompt,
                    effectiveCtxSize = effectiveCtx
                )

                // Tokenize prompt using the loaded native model's vocabulary (never estimate from character length)
                val exactPromptTokens = try {
                    llamaCtx.tokenize(effectivePrompt)?.size ?: 0
                } catch (_: Throwable) {
                    0
                }

                val runtimeDesc = mountedModelDesc.ifBlank {
                    headerInfo.modelName ?: headerInfo.architecture ?: model.name
                }
                val runtimeQuant = headerInfo.quantizationLabel

                val tpsTracker = LiveTpsTelemetryTracker(
                    promptTokensCount = exactPromptTokens,
                    hardwareMeasuredMilliwatts = hardwareMeasuredMilliwatts
                )
                emit(
                    tpsTracker.buildPrefillStep(retrievedRag, InferenceTelemetryPhase.PREFILLING).copy(
                        runtimeBackendLabel = backendLabel,
                        runtimeModelDesc = runtimeDesc,
                        runtimeQuantizationLabel = runtimeQuant
                    )
                )

                val tokenChannel = Channel<String>(Channel.UNLIMITED)
                llamaCtx.setTokenCallback { partialToken ->
                    if (!isCancelled) {
                        tokenChannel.trySend(partialToken)
                    }
                }

                val effectiveThreads = mountedThreads.takeIf { it > 0 }?.coerceAtMost(powerProfile.cpuThreads)
                    ?: powerProfile.cpuThreads.coerceIn(1, 8)
                val completionParams = mapOf<String, Any>(
                    "prompt" to effectivePrompt,
                    "n_predict" to nPredict,
                    "temperature" to persona.temperature.toDouble(),
                    "top_p" to persona.topP.toDouble(),
                    "penalty_repeat" to persona.repeatPenalty.toDouble(),
                    "emit_partial_completion" to true,
                    "n_threads" to effectiveThreads,
                    "stop" to STOP_SEQUENCES
                )

                var nativeMetrics = NativeCompletionMetrics()
                var completionError: Throwable? = null
                completionJob = engineScope.launch {
                    try {
                        val result = llamaCtx.completion(completionParams)
                        nativeMetrics = extractNativeCompletionMetrics(result)
                    } catch (e: Throwable) {
                        invalidateMountedOnError = true
                        completionError = e
                    } finally {
                        tokenChannel.close()
                    }
                }

                val accumulated = StringBuilder()
                var tokenCount = 0

                for (token in tokenChannel) {
                    tokenCount++
                    accumulated.append(token)
                    val cleanText = cleanStopTokens(accumulated.toString(), STOP_SEQUENCES)
                    emit(
                        tpsTracker.onTokenReceived(cleanText, retrievedRag, pieceTokenDelta = 1).copy(
                            runtimeBackendLabel = backendLabel,
                            runtimeModelDesc = runtimeDesc,
                            runtimeQuantizationLabel = runtimeQuant
                        )
                    )
                }

                completionJob.join()

                if (completionError != null && accumulated.isEmpty()) {
                    throw completionError!!
                }

                if (accumulated.isEmpty() && !nativeMetrics.text.isNullOrBlank()) {
                    accumulated.append(nativeMetrics.text)
                }

                emit(
                    tpsTracker.buildFinalStep(
                        cleanAccumulatedText = cleanStopTokens(accumulated.toString(), STOP_SEQUENCES),
                        citedRagSnippets = retrievedRag,
                        nativeGeneratedTokenCount = nativeMetrics.predictedTokensCount,
                        nativePromptTokenCount = nativeMetrics.promptTokensCount,
                        nativeDecodeTpsOverride = nativeMetrics.nativeDecodeTps,
                        nativePrefillTpsOverride = nativeMetrics.nativePrefillTps,
                        nativePromptMsOverride = nativeMetrics.promptElapsedMs,
                        nativeDecodeMsOverride = nativeMetrics.predictedElapsedMs
                    ).copy(
                        runtimeBackendLabel = backendLabel,
                        runtimeModelDesc = runtimeDesc,
                        runtimeQuantizationLabel = runtimeQuant
                    )
                )
            } catch (e: UnsatisfiedLinkError) {
                invalidateMountedOnError = true
                emit(
                    StreamingStep(
                        accumulatedText = "Native runtime error: 'librnllama.so' failed to link on this device ABI.\n\nDetails: ${e.message}",
                        currentTps = 0f,
                        ttftMs = 0,
                        generatedTokens = 0,
                        energyMicroWh = -1f,
                        citedRagSnippets = retrievedRag,
                        isFinished = true
                    )
                )
            } catch (e: Throwable) {
                invalidateMountedOnError = true
                if (e is kotlinx.coroutines.CancellationException) throw e
                emit(
                    StreamingStep(
                        accumulatedText = "Inference execution error: ${e.message ?: e.javaClass.simpleName}",
                        currentTps = 0f,
                        ttftMs = 0,
                        generatedTokens = 0,
                        energyMicroWh = -1f,
                        citedRagSnippets = retrievedRag,
                        isFinished = true
                    )
                )
            } finally {
                withContext(NonCancellable) {
                    synchronized(nativeHandleLock) {
                        if (llamaCtx != null && llamaCtx.context != 0L) {
                            try { llamaCtx.stopCompletion() } catch (_: Throwable) {}
                        }
                    }
                    try { completionJob?.join() } catch (_: Throwable) {}
                    synchronized(nativeHandleLock) {
                        activeLlamaContext = null
                    }
                    if (invalidateMountedOnError) {
                        unmountLlamaContextLocked()
                    }
                }
            }
        }
    }

    private data class NativeCompletionMetrics(
        val text: String? = null,
        val nativeDecodeTps: Float? = null,
        val nativePrefillTps: Float? = null,
        val predictedTokensCount: Int? = null,
        val promptTokensCount: Int? = null,
        val promptElapsedMs: Float? = null,
        val predictedElapsedMs: Float? = null
    )

    private fun extractNativeCompletionMetrics(result: Map<*, *>?): NativeCompletionMetrics {
        if (result == null) return NativeCompletionMetrics()
        val text = result["text"] as? String
        val timings = result["timings"] as? Map<*, *>
        val decodeTps = ((timings?.get("predicted_per_second") as? Number)
            ?: (result["tokens_per_second"] as? Number))
            ?.toFloat()?.takeIf { it > 0f && it.isFinite() }
        val prefillTps = (timings?.get("prompt_per_second") as? Number)
            ?.toFloat()?.takeIf { it > 0f && it.isFinite() }
        val predictedCount = ((result["tokens_predicted"] as? Number)
            ?: (timings?.get("predicted_n") as? Number))
            ?.toInt()?.takeIf { it >= 0 }
        val promptCount = ((result["tokens_evaluated"] as? Number)
            ?: (timings?.get("prompt_n") as? Number))
            ?.toInt()?.takeIf { it >= 0 }
        val promptMs = (timings?.get("prompt_ms") as? Number)
            ?.toFloat()?.takeIf { it > 0f && it.isFinite() }
        val predictedMs = (timings?.get("predicted_ms") as? Number)
            ?.toFloat()?.takeIf { it > 0f && it.isFinite() }
        return NativeCompletionMetrics(
            text = text,
            nativeDecodeTps = decodeTps,
            nativePrefillTps = prefillTps,
            predictedTokensCount = predictedCount,
            promptTokensCount = promptCount,
            promptElapsedMs = promptMs,
            predictedElapsedMs = predictedMs
        )
    }

    private fun cleanStopTokens(raw: String, stopWords: List<String>): String {
        var result = raw
        for (stop in stopWords) {
            if (result.endsWith(stop)) {
                result = result.removeSuffix(stop)
            }
        }
        return result.trimEnd()
    }

    /**
     * Engine 3: PyTorch ExecuTorch Native Edge Runtime (`libexecutorch.so` from `org.pytorch:executorch-android:1.0.0`).
     */
    private fun streamExecuTorchEngine(
        modelFile: File,
        rawUserPrompt: String,
        model: LocalModelEntity,
        powerProfile: PowerProfile,
        persona: PersonaPresetEntity,
        retrievedRag: List<RetrievedRagSnippet>,
        conversationHistory: List<MessageNodeEntity>,
        hardwareMeasuredMilliwatts: Float
    ): Flow<StreamingStep> = flow {
        val pteHeader = readPteHeader(modelFile)
        val ggufHeader = readGgufHeader(modelFile)

        if (ggufHeader.isValidGguf && !pteHeader.isValidPte) {
            emit(
                StreamingStep(
                    accumulatedText = "ExecuTorch runtime error: '${modelFile.name}' is a GGUF weight file (${ggufHeader.quantizationLabel}), not a compiled PyTorch ExecuTorch (.pte) flatbuffer graph.\n\nSwitch the active runtime to llama.cpp to run this GGUF model, or load a .pte model file in Local Models.",
                    currentTps = 0f,
                    ttftMs = 0,
                    generatedTokens = 0,
                    energyMicroWh = -1f,
                    citedRagSnippets = emptyList(),
                    isFinished = true
                )
            )
            return@flow
        }

        if (!pteHeader.isValidPte) {
            emit(
                StreamingStep(
                    accumulatedText = "ExecuTorch error: '${modelFile.name}' is not a valid ExecuTorch .pte flatbuffer binary.\n\nHeader verification found '${pteHeader.magicString}', expected ExecuTorch PTE format.",
                    currentTps = 0f,
                    ttftMs = 0,
                    generatedTokens = 0,
                    energyMicroWh = -1f,
                    citedRagSnippets = emptyList(),
                    isFinished = true
                )
            )
            return@flow
        }

        inferenceMutex.withLock {
            unmountLlamaContextLocked()

            val companionTokenizer = findCompanionTokenizerFile(modelFile)
            if (companionTokenizer != null) {
                var llmModule: LlmModule? = null
                var genJob: Job? = null
                try {
                    val targetCtx = powerProfile.contextLimit.coerceAtMost(model.contextWindow.coerceAtLeast(512))
                    val nPredict = computeGenerationTokenBudget(targetCtx)
                    val boundedPrompt = buildFormattedPrompt(
                        userPrompt = rawUserPrompt,
                        persona = persona,
                        ragSnippets = retrievedRag,
                        conversationHistory = conversationHistory,
                        effectiveCtxSize = targetCtx
                    )
                    val tpsTracker = LiveTpsTelemetryTracker(
                        promptTokensCount = 0,
                        hardwareMeasuredMilliwatts = hardwareMeasuredMilliwatts
                    )
                    emit(
                        tpsTracker.buildPrefillStep(retrievedRag, InferenceTelemetryPhase.PREFILLING).copy(
                            runtimeBackendLabel = "libexecutorch JNI",
                            runtimeModelDesc = modelFile.name,
                            runtimeQuantizationLabel = "PTE (${pteHeader.magicString.trim()})"
                        )
                    )

                    val tokenChannel = Channel<String>(Channel.UNLIMITED)

                    val memAssessment = assessDeviceMemoryForModel(modelFile)
                    if (memAssessment.isCriticallyLowMemory) {
                        throw IllegalStateException(
                            "Insufficient free device RAM (${memAssessment.availMemMb} MB available) to load ExecuTorch model '${modelFile.name}'."
                        )
                    }

                    val createdModule = LlmModule(
                        modelFile.absolutePath,
                        companionTokenizer.absolutePath,
                        persona.temperature
                    )
                    llmModule = createdModule
                    synchronized(nativeHandleLock) {
                        activeExecuTorchLlmModule = createdModule
                    }
                    createdModule.load()

                    var genError: Throwable? = null
                    genJob = engineScope.launch {
                        try {
                            llmModule.generate(
                                boundedPrompt,
                                nPredict,
                                object : LlmCallback {
                                    override fun onResult(result: String) {
                                        if (!isCancelled && result.isNotEmpty()) {
                                            tokenChannel.trySend(result)
                                        }
                                    }
                                },
                                false
                            )
                        } catch (e: Throwable) {
                            genError = e
                        } finally {
                            tokenChannel.close()
                        }
                    }

                    val accumulated = StringBuilder()
                    var tokenCount = 0

                    for (token in tokenChannel) {
                        tokenCount++
                        accumulated.append(token)
                        val cleanText = cleanStopTokens(accumulated.toString(), STOP_SEQUENCES)
                        emit(
                            tpsTracker.onTokenReceived(cleanText, retrievedRag, pieceTokenDelta = 1).copy(
                                runtimeBackendLabel = "libexecutorch JNI",
                                runtimeModelDesc = modelFile.name,
                                runtimeQuantizationLabel = "PTE (${pteHeader.magicString.trim()})"
                            )
                        )
                    }

                    genJob.join()

                    if (genError != null && accumulated.isEmpty()) {
                        throw genError!!
                    }

                    emit(
                        tpsTracker.buildFinalStep(
                            cleanAccumulatedText = cleanStopTokens(accumulated.toString(), STOP_SEQUENCES),
                            citedRagSnippets = retrievedRag,
                            nativeGeneratedTokenCount = tokenCount
                        ).copy(
                            runtimeBackendLabel = "libexecutorch JNI",
                            runtimeModelDesc = modelFile.name,
                            runtimeQuantizationLabel = "PTE (${pteHeader.magicString.trim()})"
                        )
                    )
                } catch (e: UnsatisfiedLinkError) {
                    emit(
                        StreamingStep(
                            accumulatedText = "ExecuTorch notice: Native library 'libexecutorch.so' failed to link on this device ABI.\n\nDetails: ${e.message}",
                            currentTps = 0f,
                            ttftMs = 0,
                            generatedTokens = 0,
                            energyMicroWh = -1f,
                            citedRagSnippets = retrievedRag,
                            isFinished = true
                        )
                    )
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emit(
                        StreamingStep(
                            accumulatedText = "ExecuTorch LLM execution error: ${e.message ?: e.javaClass.simpleName}",
                            currentTps = 0f,
                            ttftMs = 0,
                            generatedTokens = 0,
                            energyMicroWh = -1f,
                            citedRagSnippets = retrievedRag,
                            isFinished = true
                        )
                    )
                } finally {
                    withContext(NonCancellable) {
                        synchronized(nativeHandleLock) {
                            try { llmModule?.stop() } catch (_: Throwable) {}
                        }
                        try { genJob?.join() } catch (_: Throwable) {}
                        synchronized(nativeHandleLock) {
                            activeExecuTorchLlmModule = null
                            try { llmModule?.close() } catch (_: Throwable) {}
                        }
                    }
                }
                return@withLock
            }

            var module: Module? = null
            try {
                val startTimeNs = System.nanoTime()
                module = Module.load(modelFile.absolutePath, Module.LOAD_MODE_MMAP)
                val loadElapsedMs = ((System.nanoTime() - startTimeNs) / 1_000_000L).toInt().coerceAtLeast(1)

                emit(
                    StreamingStep(
                        accumulatedText = "ExecuTorch PTE graph '${modelFile.name}' (${model.sizeMb} MB) verified and mounted via MMAP in ${loadElapsedMs} ms, but no companion tokenizer file was found.\n\nTo run generative text decoding with ExecuTorch LlmModule, open Local Models → tap 'Select Model File' and import your companion 'tokenizer.bin' or 'tokenizer.model' file.",
                        currentTps = 0f,
                        ttftMs = loadElapsedMs,
                        generatedTokens = 0,
                        energyMicroWh = -1f,
                        citedRagSnippets = retrievedRag,
                        isFinished = true
                    )
                )
            } catch (e: UnsatisfiedLinkError) {
                emit(
                    StreamingStep(
                        accumulatedText = "ExecuTorch notice: Native library 'libexecutorch.so' requires physical Android device runtime.\n\nDetails: ${e.message}",
                        currentTps = 0f,
                        ttftMs = 0,
                        generatedTokens = 0,
                        energyMicroWh = -1f,
                        citedRagSnippets = retrievedRag,
                        isFinished = true
                    )
                )
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                emit(
                    StreamingStep(
                        accumulatedText = "ExecuTorch execution error: ${e.message ?: e.javaClass.simpleName}",
                        currentTps = 0f,
                        ttftMs = 0,
                        generatedTokens = 0,
                        energyMicroWh = -1f,
                        citedRagSnippets = retrievedRag,
                        isFinished = true
                    )
                )
            } finally {
                withContext(NonCancellable) {
                    try { module?.destroy() } catch (_: Throwable) {}
                }
            }
        }
    }

    private fun isValidCompanionTokenizerFile(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 64L) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(128)
                val read = raf.read(header)
                if (read < 4) return@use false
                val magic4 = String(header, 0, 4, Charsets.US_ASCII)
                if (magic4 == "GGUF") return@use false
                val firstNonWs = header.take(read)
                    .map { (it.toInt() and 0xFF).toChar() }
                    .firstOrNull { !it.isWhitespace() }
                if (file.name.endsWith(".json", ignoreCase = true)) {
                    firstNonWs == '{'
                } else {
                    firstNonWs != '{' && firstNonWs != '[' && firstNonWs != '<'
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun findCompanionTokenizerFile(pteFile: File): File? {
        val searchDirs = mutableListOf<File>()
        pteFile.parentFile?.let { searchDirs.add(it) }
        context?.getExternalFilesDir("models")?.let { if (it !in searchDirs) searchDirs.add(it) }
        context?.filesDir?.let {
            val internalModels = File(it, "models")
            if (internalModels !in searchDirs) searchDirs.add(internalModels)
        }

        val candidates = listOf(
            "${pteFile.nameWithoutExtension}.tokenizer.bin",
            "${pteFile.nameWithoutExtension}.tokenizer.model",
            "${pteFile.nameWithoutExtension}.tokenizer.json",
            "tokenizer.bin",
            "tokenizer.model",
            "tokenizer.json"
        )

        for (dir in searchDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            for (name in candidates) {
                val f = File(dir, name)
                if (isValidCompanionTokenizerFile(f)) {
                    return f
                }
            }
            val anyTok = dir.listFiles()?.firstOrNull { f ->
                val lower = f.name.lowercase()
                (lower.endsWith("tokenizer.bin") || lower.endsWith("tokenizer.model") || lower.endsWith("tokenizer.json")) &&
                    isValidCompanionTokenizerFile(f)
            }
            if (anyTok != null) {
                return anyTok
            }
        }
        return null
    }

    /**
     * Engine 4: T-MAC Native Runtime (`libtmac_llama.so` built from `microsoft/T-MAC` commit `7042f8f733` + `kaleid-liner/llama.cpp` `eb07ecf`
     * with AArch64 NEON `vqtbl1q` LUT kernels for `TQ1_0`, `TQ2_0`, `Q4_0`, `I1`, `I2`, `I3`, `I4` GGUF tensors).
     */
    private fun streamTMacLutEngine(
        modelFile: File,
        rawUserPrompt: String,
        formattedPrompt: String,
        model: LocalModelEntity,
        powerProfile: PowerProfile,
        persona: PersonaPresetEntity,
        retrievedRag: List<RetrievedRagSnippet>,
        conversationHistory: List<MessageNodeEntity>,
        hardwareMeasuredMilliwatts: Float
    ): Flow<StreamingStep> = flow {
        val headerInfo = readGgufHeader(modelFile)
        if (!headerInfo.isValidGguf) {
            val tmacHeader = readTmacHeader(modelFile)
            val err = NotImplementedError(
                "T-MAC runtime error (NotImplementedError): File '${modelFile.name}' has header '${tmacHeader.formatType}' instead of a GGUF model container. " +
                    "Bundled 'libtmac_llama.so' (microsoft/T-MAC 7042f8f733 + llama.cpp eb07ecf) executes AArch64 LUT kernels on GGUF models (TQ1_0, TQ2_0, Q4_0, INT2/INT4); " +
                    "standalone raw '.tmac' tensor dumps without GGUF tokenizer and hyperparameter metadata cannot be executed directly."
            )
            emit(
                StreamingStep(
                    accumulatedText = err.message ?: "T-MAC raw tensor format is not supported without a GGUF container.",
                    currentTps = 0f,
                    ttftMs = 0,
                    generatedTokens = 0,
                    energyMicroWh = -1f,
                    citedRagSnippets = emptyList(),
                    isFinished = true
                )
            )
            return@flow
        }

        if (LeafyNativeBridge.ensureLoaded()) {
            emitAll(
                executeNativeBridgeCompletion(
                    engineId = AiRuntime.T_MAC.id,
                    modelFile = modelFile,
                    headerInfo = headerInfo,
                    rawUserPrompt = rawUserPrompt,
                    formattedPrompt = formattedPrompt,
                    model = model,
                    powerProfile = powerProfile,
                    persona = persona,
                    retrievedRag = retrievedRag,
                    conversationHistory = conversationHistory,
                    hardwareMeasuredMilliwatts = hardwareMeasuredMilliwatts
                )
            )
        } else {
            emit(
                StreamingStep(
                    accumulatedText = "T-MAC native bridge error: Failed to load 'libleafy_runtime_jni.so' / 'libtmac_llama.so' (${LeafyNativeBridge.loadError ?: "JNI library unavailable on this ABI"}).",
                    currentTps = 0f,
                    ttftMs = 0,
                    generatedTokens = 0,
                    energyMicroWh = -1f,
                    citedRagSnippets = emptyList(),
                    isFinished = true
                )
            )
        }
    }

    /**
     * Formats user prompt with Persona system instructions, recent conversation history, and retrieved RAG context.
     */
    fun buildFormattedPrompt(
        userPrompt: String,
        persona: PersonaPresetEntity,
        ragSnippets: List<RetrievedRagSnippet>,
        conversationHistory: List<MessageNodeEntity> = emptyList(),
        effectiveCtxSize: Int = 2048,
        chatTemplateHint: String? = null
    ): String {
        val maxTurns = if (effectiveCtxSize <= 512) 2 else if (effectiveCtxSize <= 1024) 4 else 6
        val maxTurnChars = if (effectiveCtxSize <= 512) 220 else 500
        val maxRagChars = if (effectiveCtxSize <= 512) 180 else 300

        val ragContextText = if (ragSnippets.isNotEmpty()) {
            "\n\nContext from device knowledge:\n" + ragSnippets.joinToString("\n\n") {
                "Document: ${it.documentTitle}\nPassage: ${it.matchedPassage.take(maxRagChars)}"
            }
        } else ""

        val systemInstruction = "${persona.systemPrompt.take(400)}$ragContextText".trim()
        val recentHistory = filterRecentHistory(conversationHistory, maxTurns, maxTurnChars)
        val tmpl = chatTemplateHint.orEmpty()

        return when {
            tmpl.contains("<|start_header_id|>") -> buildString {
                append("<|begin_of_text|>")
                if (systemInstruction.isNotBlank()) {
                    append("<|start_header_id|>system<|end_header_id|>\n\n")
                    append(systemInstruction)
                    append("<|eot_id|>")
                }
                for (hist in recentHistory) {
                    val role = if (hist.role.equals("USER", ignoreCase = true)) "user" else "assistant"
                    append("<|start_header_id|>").append(role).append("<|end_header_id|>\n\n")
                    append(hist.content.trim())
                    append("<|eot_id|>")
                }
                append("<|start_header_id|>user<|end_header_id|>\n\n")
                append(userPrompt.trim())
                append("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n")
            }
            tmpl.contains("<start_of_turn>") -> buildString {
                for (hist in recentHistory) {
                    val role = if (hist.role.equals("USER", ignoreCase = true)) "user" else "model"
                    append("<start_of_turn>").append(role).append("\n")
                    append(hist.content.trim())
                    append("<end_of_turn>\n")
                }
                append("<start_of_turn>user\n")
                if (systemInstruction.isNotBlank()) {
                    append(systemInstruction).append("\n\n")
                }
                append(userPrompt.trim())
                append("<end_of_turn>\n<start_of_turn>model\n")
            }
            tmpl.contains("<|user|>") && tmpl.contains("<|end|>") -> buildString {
                if (systemInstruction.isNotBlank()) {
                    append("<|system|>\n").append(systemInstruction).append("<|end|>\n")
                }
                for (hist in recentHistory) {
                    val tag = if (hist.role.equals("USER", ignoreCase = true)) "<|user|>" else "<|assistant|>"
                    append(tag).append("\n").append(hist.content.trim()).append("<|end|>\n")
                }
                append("<|user|>\n").append(userPrompt.trim()).append("<|end|>\n<|assistant|>\n")
            }
            else -> buildString {
                if (systemInstruction.isNotBlank()) {
                    append("<|im_start|>system\n")
                    append(systemInstruction)
                    append("<|im_end|>\n")
                }
                for (hist in recentHistory) {
                    val role = if (hist.role.equals("USER", ignoreCase = true)) "user" else "assistant"
                    append("<|im_start|>").append(role).append("\n")
                    append(hist.content.trim())
                    append("<|im_end|>\n")
                }
                append("<|im_start|>user\n")
                append(userPrompt.trim())
                append("<|im_end|>\n<|im_start|>assistant\n")
            }
        }
    }

    /**
     * Reads the binary GGUF header and KV metadata table to extract real model metadata
     * (`general.architecture`, `general.name`, `general.file_type`, `<arch>.context_length`).
     */
    fun readGgufHeader(file: File): GgufHeaderInfo {
        if (!file.exists() || file.length() < 24) {
            return GgufHeaderInfo(isValidGguf = false, magicString = "EMPTY", version = 0, tensorCount = 0L, metadataKvCount = 0L)
        }
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magicBytes = ByteArray(4)
                raf.readFully(magicBytes)
                val magicStr = String(magicBytes, Charsets.US_ASCII)
                val isGguf = (magicStr == "GGUF")

                if (!isGguf) {
                    GgufHeaderInfo(isValidGguf = false, magicString = magicStr, version = 0, tensorCount = 0L, metadataKvCount = 0L)
                } else {
                    val version = readIntLittleEndian(raf)
                    val tensorCount = readLongLittleEndian(raf)
                    val metadataKvCount = readLongLittleEndian(raf)
                    val parsedMeta = readGgufMetadataKeys(raf, file.length(), metadataKvCount)
                    val quantLabel = mapGgufFileTypeToQuantization(parsedMeta.fileType)
                        ?: parsedMeta.architecture?.let { "GGUF v$version ($it)" }
                        ?: "GGUF v$version"
                    GgufHeaderInfo(
                        isValidGguf = true,
                        magicString = magicStr,
                        version = version,
                        tensorCount = tensorCount,
                        metadataKvCount = metadataKvCount,
                        architecture = parsedMeta.architecture,
                        modelName = parsedMeta.modelName,
                        fileType = parsedMeta.fileType,
                        contextLength = parsedMeta.contextLength,
                        chatTemplate = parsedMeta.chatTemplate,
                        quantizationLabel = quantLabel
                    )
                }
            }
        } catch (e: Exception) {
            GgufHeaderInfo(isValidGguf = false, magicString = "ERROR: ${e.message}", version = 0, tensorCount = 0L, metadataKvCount = 0L)
        }
    }

    fun readPteHeader(file: File): PteHeaderInfo {
        if (!file.exists() || file.length() < 8) {
            return PteHeaderInfo(isValidPte = false, magicString = "EMPTY", version = 0, fileSize = 0L)
        }
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val headerBytes = ByteArray(8)
                raf.readFully(headerBytes)
                val magic0to3 = String(headerBytes.sliceArray(0..3), Charsets.US_ASCII)
                if (magic0to3 == "GGUF") {
                    return PteHeaderInfo(isValidPte = false, magicString = "GGUF", version = 0, fileSize = file.length())
                }
                val ident4to7 = String(headerBytes.sliceArray(4..7), Charsets.US_ASCII)
                val isPte = ident4to7.startsWith("ET") || ident4to7.startsWith("PT") || file.name.endsWith(".pte", ignoreCase = true)
                PteHeaderInfo(
                    isValidPte = isPte,
                    magicString = ident4to7,
                    version = 1,
                    fileSize = file.length()
                )
            }
        } catch (e: Exception) {
            PteHeaderInfo(isValidPte = false, magicString = "ERROR: ${e.message}", version = 0, fileSize = 0L)
        }
    }

    fun readTmacHeader(file: File): TmacHeaderInfo {
        if (!file.exists() || file.length() < 16) {
            return TmacHeaderInfo(isValidTmac = false, formatType = "EMPTY", lutBits = 0, tensorCount = 0L)
        }
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magicBytes = ByteArray(4)
                raf.readFully(magicBytes)
                val magicStr = String(magicBytes, Charsets.US_ASCII)
                val isTmac = magicStr == "TMAC" || file.name.endsWith(".tmac", ignoreCase = true)
                val bits = if (raf.length() >= 8) {
                    val b = raf.read()
                    if (b in 2..4) b else 3
                } else 3
                val tensors = if (raf.length() >= 16) {
                    raf.seek(8)
                    readLongLittleEndian(raf).coerceAtLeast(1L)
                } else 1L
                TmacHeaderInfo(
                    isValidTmac = isTmac,
                    formatType = if (isTmac) "INT${bits}-LUT" else magicStr,
                    lutBits = bits,
                    tensorCount = tensors
                )
            }
        } catch (e: Exception) {
            TmacHeaderInfo(isValidTmac = false, formatType = "ERROR", lutBits = 0, tensorCount = 0L)
        }
    }

    private data class ParsedGgufMetadata(
        val architecture: String? = null,
        val modelName: String? = null,
        val fileType: Int? = null,
        val contextLength: Int? = null,
        val chatTemplate: String? = null
    )

    private fun readGgufMetadataKeys(
        raf: RandomAccessFile,
        fileLen: Long,
        kvCount: Long
    ): ParsedGgufMetadata {
        var arch: String? = null
        var name: String? = null
        var fileType: Int? = null
        var ctxLen: Int? = null
        var chatTemplate: String? = null
        try {
            val limit = kvCount.coerceIn(0L, 96L).toInt()
            for (i in 0 until limit) {
                if (raf.filePointer + 12L > fileLen) break
                val keyLen = readLongLittleEndian(raf)
                if (keyLen <= 0L || keyLen > 256L || raf.filePointer + keyLen + 4L > fileLen) break
                val keyBytes = ByteArray(keyLen.toInt())
                raf.readFully(keyBytes)
                val key = String(keyBytes, Charsets.UTF_8)
                val valType = readIntLittleEndian(raf)

                when {
                    (key == "general.architecture" || key == "general.name" || key == "tokenizer.chat_template") && valType == 8 -> {
                        if (raf.filePointer + 8L > fileLen) break
                        val strLen = readLongLittleEndian(raf)
                        val maxAllowedLen = if (key == "tokenizer.chat_template") 16384L else 512L
                        if (strLen < 0L || strLen > maxAllowedLen || raf.filePointer + strLen > fileLen) break
                        val strBytes = ByteArray(strLen.toInt())
                        raf.readFully(strBytes)
                        val strVal = String(strBytes, Charsets.UTF_8)
                        when (key) {
                            "general.architecture" -> arch = strVal
                            "general.name" -> name = strVal
                            "tokenizer.chat_template" -> chatTemplate = strVal
                        }
                    }
                    key == "general.file_type" && (valType == 4 || valType == 5) -> {
                        if (raf.filePointer + 4L > fileLen) break
                        fileType = readIntLittleEndian(raf)
                    }
                    key.endsWith(".context_length") && (valType == 4 || valType == 5) -> {
                        if (raf.filePointer + 4L > fileLen) break
                        ctxLen = readIntLittleEndian(raf)
                    }
                    key.endsWith(".context_length") && (valType == 10 || valType == 11) -> {
                        if (raf.filePointer + 8L > fileLen) break
                        ctxLen = readLongLittleEndian(raf).toInt()
                    }
                    else -> {
                        val skip = computeGgufValueSkipBytes(raf, fileLen, valType) ?: break
                        if (raf.filePointer + skip > fileLen) break
                        raf.seek(raf.filePointer + skip)
                    }
                }

                if (arch != null && name != null && fileType != null && ctxLen != null && chatTemplate != null) {
                    break
                }
            }
        } catch (_: Throwable) {
        }
        return ParsedGgufMetadata(
            architecture = arch,
            modelName = name,
            fileType = fileType,
            contextLength = ctxLen,
            chatTemplate = chatTemplate
        )
    }

    private fun computeGgufValueSkipBytes(raf: RandomAccessFile, fileLen: Long, valType: Int): Long? {
        return when (valType) {
            0, 1, 7 -> 1L
            2, 3 -> 2L
            4, 5, 6 -> 4L
            10, 11, 12 -> 8L
            8 -> {
                if (raf.filePointer + 8L > fileLen) return null
                val sLen = readLongLittleEndian(raf)
                if (sLen < 0L || sLen > 1_048_576L) return null
                sLen
            }
            9 -> {
                // GGUF_TYPE_ARRAY: uint32 elem_type + uint64 count
                if (raf.filePointer + 12L > fileLen) return null
                val elemType = readIntLittleEndian(raf)
                val count = readLongLittleEndian(raf)
                if (count < 0L || count > 262_144L) return null
                val fixedElemSize: Long? = when (elemType) {
                    0, 1, 7 -> 1L
                    2, 3 -> 2L
                    4, 5, 6 -> 4L
                    10, 11, 12 -> 8L
                    else -> null
                }
                if (fixedElemSize != null) {
                    fixedElemSize * count
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun readIntLittleEndian(raf: RandomAccessFile): Int {
        val b1 = raf.read()
        val b2 = raf.read()
        val b3 = raf.read()
        val b4 = raf.read()
        return (b1 and 0xFF) or ((b2 and 0xFF) shl 8) or ((b3 and 0xFF) shl 16) or ((b4 and 0xFF) shl 24)
    }

    private fun readLongLittleEndian(raf: RandomAccessFile): Long {
        var result = 0L
        for (i in 0..7) {
            val b = raf.read().toLong() and 0xFF
            result = result or (b shl (i * 8))
        }
        return result
    }

    companion object {
        private val STOP_WORDS = setOf(
            "the", "and", "for", "with", "that", "this", "from", "what", "how",
            "can", "you", "about", "into", "your", "are", "was", "were", "have"
        )

        private val STOP_SEQUENCES = listOf(
            "<|im_end|>",
            "<|eot_id|>",
            "<|end_of_text|>",
            "</s>",
            "<|endoftext|>",
            "<end_of_turn>",
            "<|user|>",
            "<|im_start|>user"
        )

        /**
         * Maps official GGUF `general.file_type` (`enum llama_ftype` from `llama.h`) to its quantization name.
         */
        fun mapGgufFileTypeToQuantization(fileType: Int?): String? = when (fileType) {
            0 -> "F32"
            1 -> "F16"
            2 -> "Q4_0"
            3 -> "Q4_1"
            7 -> "Q8_0"
            8 -> "Q5_0"
            9 -> "Q5_1"
            10 -> "Q2_K"
            11 -> "Q3_K_S"
            12 -> "Q3_K_M"
            13 -> "Q3_K_L"
            14 -> "Q4_K_S"
            15 -> "Q4_K_M"
            16 -> "Q5_K_S"
            17 -> "Q5_K_M"
            18 -> "Q6_K"
            19 -> "IQ2_XXS"
            20 -> "IQ2_XS"
            21 -> "Q2_K_S"
            22 -> "IQ3_XS"
            23 -> "IQ3_XXS"
            24 -> "IQ1_S"
            25 -> "IQ4_NL"
            26 -> "IQ3_S"
            27 -> "IQ3_M"
            28 -> "IQ2_S"
            29 -> "IQ2_M"
            30 -> "IQ4_XS"
            31 -> "IQ1_M"
            32 -> "BF16"
            36 -> "TQ1_0"
            37 -> "TQ2_0"
            38 -> "I2_S (1.58-bit)"
            null -> null
            else -> "ftype=$fileType"
        }
    }
}
