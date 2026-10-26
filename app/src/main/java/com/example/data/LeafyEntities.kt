package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AiRuntime(
    val id: String,
    val displayName: String,
    val shortBadge: String,
    val defaultQuantization: String,
    val architectureNote: String,
    val energyEfficiencyMultiplier: Float,
    val speedMultiplier: Float,
    val fileExtensions: List<String>
) {
    LLAMA_CPP(
        id = "llama.cpp",
        displayName = "llama.cpp",
        shortBadge = "GGUF • NEON",
        defaultQuantization = "Q4_K_M",
        architectureNote = "Universal GGUF quantized tensor engine with ARM NEON & Vulkan offload.",
        energyEfficiencyMultiplier = 1.0f,
        speedMultiplier = 1.15f,
        fileExtensions = listOf(".gguf")
    ),
    BITNET_CPP(
        id = "Bitnet.cpp",
        displayName = "Bitnet.cpp",
        shortBadge = "1.58-bit • i2_s",
        defaultQuantization = "1.58-bit Ternary",
        architectureNote = "1-bit / 1.58-bit ternary weights {-1, 0, +1} eliminating floating-point multiplication for up to 71% lower battery drain.",
        energyEfficiencyMultiplier = 1.65f,
        speedMultiplier = 1.45f,
        fileExtensions = listOf(".gguf", ".bitnet")
    ),
    T_MAC(
        id = "T-MAC",
        displayName = "T-MAC",
        shortBadge = "LUT • INT2/4",
        defaultQuantization = "INT3-LUT",
        architectureNote = "Lookup-Table (LUT) low-bit matrix multiplication on mobile CPUs without dequantization overhead.",
        energyEfficiencyMultiplier = 1.50f,
        speedMultiplier = 1.55f,
        fileExtensions = listOf(".tmac", ".bin", ".gguf")
    ),
    EXECUTORCH(
        id = "ExecuTorch",
        displayName = "ExecuTorch",
        shortBadge = "PTE • XNNPACK",
        defaultQuantization = "INT4-XNNPACK",
        architectureNote = "PyTorch edge runtime exporting static .pte graphs to XNNPACK, Qualcomm HTP, and MediaTek NeuroPilot.",
        energyEfficiencyMultiplier = 1.35f,
        speedMultiplier = 1.38f,
        fileExtensions = listOf(".pte")
    );

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
    val contextLimit: Int,
    val kvCacheBits: String,
    val estimatedMilliwatts: Int
) {
    ECO(
        id = "ECO",
        displayName = "Eco Leaf",
        badgeText = "Eco • 2 Threads",
        description = "Pins execution to efficiency cores, uses INT4 KV-cache quantization, and caps thermal envelope.",
        cpuThreads = 2,
        contextLimit = 2048,
        kvCacheBits = "Q4_0 KV",
        estimatedMilliwatts = 680
    ),
    BALANCED(
        id = "BALANCED",
        displayName = "Balanced Canopy",
        badgeText = "Balanced • 4 Threads",
        description = "Dynamically scales 4 big.LITTLE threads with Q8_0 KV-cache for responsive sustained local chat.",
        cpuThreads = 4,
        contextLimit = 4096,
        kvCacheBits = "Q8_0 KV",
        estimatedMilliwatts = 1240
    ),
    PERFORMANCE(
        id = "PERFORMANCE",
        displayName = "Sunburst Turbo",
        badgeText = "Turbo • 8 Threads",
        description = "Unlocks 8 performance threads, SIMD/NPU acceleration, and full 8K context window for heavy RAG & coding.",
        cpuThreads = 8,
        contextLimit = 8192,
        kvCacheBits = "FP16 KV",
        estimatedMilliwatts = 2350
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
