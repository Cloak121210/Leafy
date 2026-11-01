package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AiRuntime(
    val id: String,
    val displayName: String,
    val shortBadge: String,
    val defaultQuantization: String,
    val architectureNote: String,
    val fileExtensions: List<String>,
    val hasBundledNativeLibrary: Boolean
) {
    LLAMA_CPP(
        id = "llama.cpp",
        displayName = "llama.cpp",
        shortBadge = "GGUF • v0.13",
        defaultQuantization = "Unknown (read from GGUF)",
        architectureNote = "GGUF native tensor runtime via bundled librnllama_v8_2_dotprod_i8mm.so / libleafy_runtime_jni.so (llama.rn v0.13.0-rc.9 / llama.cpp 0ec4027a2a, supporting LFM3, LFM2, Qwen3.5, Gemma4).",
        fileExtensions = listOf(".gguf"),
        hasBundledNativeLibrary = true
    ),
    BITNET_CPP(
        id = "Bitnet.cpp",
        displayName = "Bitnet.cpp",
        shortBadge = "1.58-bit • I2_S/TL1",
        defaultQuantization = "Unknown (read from GGUF)",
        architectureNote = "1.58-bit ternary weight runtime via bundled libbitnet_llama.so (microsoft/BitNet 0b341e582a + llama.cpp 390c307, ARM NEON I2_S MAD, TL1 LUT & gated squared ReLU).",
        fileExtensions = listOf(".gguf", ".bitnet"),
        hasBundledNativeLibrary = true
    ),
    T_MAC(
        id = "T-MAC",
        displayName = "T-MAC",
        shortBadge = "LUT • INT2/4",
        defaultQuantization = "Unknown (read from header)",
        architectureNote = "Lookup-Table (LUT) low-bit matrix multiplication runtime via bundled libtmac_llama.so (microsoft/T-MAC 7042f8f733 + llama.cpp eb07ecf, AArch64 NEON vqtbl1q LUT kernels).",
        fileExtensions = listOf(".gguf", ".tmac", ".bin"),
        hasBundledNativeLibrary = true
    ),
    EXECUTORCH(
        id = "ExecuTorch",
        displayName = "ExecuTorch",
        shortBadge = "PTE • JNI",
        defaultQuantization = "Unknown (read from PTE)",
        architectureNote = "PyTorch edge runtime loading exported .pte programs via bundled libexecutorch JNI (org.pytorch:executorch-android:1.0.0).",
        fileExtensions = listOf(".pte"),
        hasBundledNativeLibrary = true
    );

    /**
     * Checks whether the given on-disk local model file extension matches this runtime engine.
     */
    fun isCompatibleWithModel(model: LocalModelEntity): Boolean {
        val pathLower = model.localFilePath.lowercase()
        val modelRuntime = entries.find {
            it.id.equals(model.runtimeId, ignoreCase = true) ||
                it.name.equals(model.runtimeId, ignoreCase = true)
        }
        return when (this) {
            LLAMA_CPP -> pathLower.endsWith(".gguf") || modelRuntime == LLAMA_CPP
            BITNET_CPP -> pathLower.endsWith(".bitnet") || pathLower.endsWith(".gguf") || modelRuntime == BITNET_CPP
            T_MAC -> pathLower.endsWith(".tmac") || pathLower.endsWith(".bin") || pathLower.endsWith(".gguf") || modelRuntime == T_MAC
            EXECUTORCH -> pathLower.endsWith(".pte") || modelRuntime == EXECUTORCH
        }
    }

    /**
     * Returns true if the model header or metadata indicates specialized ternary or LUT weights.
     */
    fun isNativeSpecializedModel(model: LocalModelEntity): Boolean {
        val lowerName = (model.name + " " + model.quantization + " " + model.localFilePath).lowercase()
        return when (this) {
            LLAMA_CPP -> lowerName.contains(".gguf")
            BITNET_CPP -> lowerName.contains("1.58") ||
                lowerName.contains("i2_s") ||
                lowerName.contains("bitnet") ||
                lowerName.contains("tq1") ||
                lowerName.contains("tq2") ||
                lowerName.endsWith(".bitnet")
            T_MAC -> lowerName.contains("tmac") ||
                lowerName.contains("lut") ||
                lowerName.contains("int2") ||
                lowerName.contains("int3") ||
                lowerName.endsWith(".tmac")
            EXECUTORCH -> lowerName.endsWith(".pte") || lowerName.contains("pte")
        }
    }

    companion object {
        fun fromId(id: String): AiRuntime =
            entries.find { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
                ?: LLAMA_CPP
    }
}

enum class PowerProfile(
    val id: String,
    val displayName: String,
    val badgeText: String,
    val description: String,
    val cpuThreads: Int,
    val contextLimit: Int
) {
    ECO(
        id = "ECO",
        displayName = "Eco Leaf",
        badgeText = "Eco • 2 Threads",
        description = "Caps native inference thread pool to 2 CPU threads and 2048 context tokens.",
        cpuThreads = 2,
        contextLimit = 2048
    ),
    BALANCED(
        id = "BALANCED",
        displayName = "Balanced Canopy",
        badgeText = "Balanced • 4 Threads",
        description = "Allocates 4 CPU threads and up to 4096 context tokens for native inference.",
        cpuThreads = 4,
        contextLimit = 4096
    ),
    PERFORMANCE(
        id = "PERFORMANCE",
        displayName = "Sunburst Turbo",
        badgeText = "Turbo • 8 Threads",
        description = "Allocates up to 8 CPU threads and up to 8192 context tokens for native inference.",
        cpuThreads = 8,
        contextLimit = 8192
    );

    companion object {
        fun fromId(id: String): PowerProfile =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: ECO
    }
}

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val activeModelId: String,
    val activeRuntimeId: String,
    val activePersonaId: Long,
    val activeLeafMessageId: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "message_nodes")
data class MessageNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val parentId: Long? = null,
    val role: String, // "USER" or "ASSISTANT"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val runtimeId: String = AiRuntime.LLAMA_CPP.id,
    val modelName: String = "",
    val quantization: String = "",
    val powerProfileId: String = PowerProfile.ECO.id,
    val tokensPerSec: Float = 0f,
    val ttftMs: Int = 0,
    val tokenCount: Int = 0,
    val energyMicroWh: Float = 0f,
    val citedRagTitles: String = "", // Pipe-separated document titles used in RAG
    val branchNote: String = ""
)

@Entity(tableName = "local_models")
data class LocalModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val family: String,
    val runtimeId: String,
    val quantization: String,
    val parameters: String,
    val sizeMb: Int,
    val ramRequiredMb: Int,
    val contextWindow: Int,
    val expectedTps: Float,
    val ecoRating: Int, // 1..5 leaves
    val specialty: String,
    val isInstalled: Boolean,
    val isUserImported: Boolean = true,
    val localFilePath: String = "",
    val sha256Short: String = "",
    val fileSizeBytes: Long = 0L
)

@Entity(tableName = "rag_documents")
data class RagDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String,
    val content: String,
    val chunkCount: Int,
    val tokenEstimate: Int,
    val isPinnedToChat: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "persona_presets")
data class PersonaPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconTag: String,
    val tagline: String,
    val systemPrompt: String,
    val temperature: Float,
    val topP: Float,
    val repeatPenalty: Float = 1.1f,
    val isBuiltIn: Boolean = true
)
