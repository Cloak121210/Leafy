package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class LeafyRepository(private val dao: LeafyDao) {

    val conversations: Flow<List<ConversationEntity>> = dao.observeConversations()
    val allMessages: Flow<List<MessageNodeEntity>> = dao.observeAllMessages()
    val localModels: Flow<List<LocalModelEntity>> = dao.observeLocalModels()
    val ragDocuments: Flow<List<RagDocumentEntity>> = dao.observeRagDocuments()
    val personas: Flow<List<PersonaPresetEntity>> = dao.observePersonas()

    fun observeMessages(conversationId: Long): Flow<List<MessageNodeEntity>> =
        dao.observeMessagesForConversation(conversationId)

    suspend fun ensureSeedData() {
        if (dao.countLocalModels() == 0) {
            dao.insertLocalModels(defaultLocalModels())
        }
        if (dao.countPersonas() == 0) {
            dao.insertPersonas(defaultPersonas())
        }
        if (dao.countRagDocuments() == 0) {
            dao.insertRagDocuments(defaultRagDocuments())
        }

        // Verify on-disk file presence: reset any model marked installed whose file does not exist
        verifyModelFilesOnDisk()
    }

    suspend fun verifyModelFilesOnDisk() {
        val models = dao.observeLocalModels().firstOrNull() ?: emptyList()
        for (model in models) {
            if (model.isInstalled) {
                val file = java.io.File(model.localFilePath)
                if (model.localFilePath.isBlank() || !file.exists() || file.length() <= 0L) {
                    dao.updateLocalModel(
                        model.copy(
                            isInstalled = false,
                            localFilePath = "",
                            isDownloading = false,
                            downloadProgress = 0f,
                            downloadedBytes = 0L
                        )
                    )
                }
            }
        }
    }

    suspend fun createConversation(
        title: String,
        modelId: String,
        runtimeId: String,
        personaId: Long
    ): Long {
        return dao.insertConversation(
            ConversationEntity(
                title = title,
                activeModelId = modelId,
                activeRuntimeId = runtimeId,
                activePersonaId = personaId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateConversation(conversation: ConversationEntity) {
        dao.updateConversation(conversation.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteConversation(conversationId: Long) {
        dao.deleteMessagesForConversation(conversationId)
        dao.deleteConversation(conversationId)
    }

    suspend fun insertMessageNode(node: MessageNodeEntity): Long {
        val nodeId = dao.insertMessageNode(node)
        val conv = dao.getConversationById(node.conversationId)
        if (conv != null) {
            dao.updateConversation(
                conv.copy(
                    activeLeafMessageId = nodeId,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        return nodeId
    }

    suspend fun setActiveBranchLeaf(conversationId: Long, leafNodeId: Long) {
        val conv = dao.getConversationById(conversationId) ?: return
        dao.updateConversation(
            conv.copy(
                activeLeafMessageId = leafNodeId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateLocalModel(model: LocalModelEntity) = dao.updateLocalModel(model)
    suspend fun insertLocalModel(model: LocalModelEntity) = dao.insertLocalModel(model)
    suspend fun deleteLocalModel(id: String) = dao.deleteLocalModel(id)

    suspend fun insertRagDocument(doc: RagDocumentEntity): Long = dao.insertRagDocument(doc)
    suspend fun updateRagDocument(doc: RagDocumentEntity) = dao.updateRagDocument(doc)
    suspend fun deleteRagDocument(id: Long) = dao.deleteRagDocument(id)

    suspend fun insertPersona(persona: PersonaPresetEntity): Long = dao.insertPersona(persona)
    suspend fun updatePersona(persona: PersonaPresetEntity) = dao.updatePersona(persona)
    suspend fun deletePersona(id: Long) = dao.deletePersona(id)

    suspend fun wipeAllHistory() {
        dao.wipeAllMessages()
        dao.wipeAllConversations()
    }

    companion object {
        fun defaultLocalModels(): List<LocalModelEntity> = listOf(
            LocalModelEntity(
                id = "smollm2-135m-q4km",
                name = "SmolLM2 135M Leaf-Nano",
                family = "HuggingFace SmolLM2",
                runtimeId = AiRuntime.LLAMA_CPP.id,
                quantization = "Q4_K_M",
                parameters = "135M",
                sizeMb = 95,
                ramRequiredMb = 180,
                contextWindow = 2048,
                expectedTps = 88.5f,
                ecoRating = 5,
                specialty = "Ultra-compact mobile model for instant testing, minimal battery drain and quick download.",
                isInstalled = false,
                localFilePath = "",
                sha256Short = "c31a7b42",
                downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-135M-Instruct-GGUF/resolve/main/smollm2-135m-instruct-q4_k_m.gguf"
            ),
            LocalModelEntity(
                id = "smollm2-360m-bitnet",
                name = "SmolLM2 360M Leaf-Micro",
                family = "HuggingFace SmolLM2",
                runtimeId = AiRuntime.BITNET_CPP.id,
                quantization = "1.58-bit i2_s",
                parameters = "360M",
                sizeMb = 115,
                ramRequiredMb = 240,
                contextWindow = 2048,
                expectedTps = 78.5f,
                ecoRating = 5,
                specialty = "Sub-watt ternary quantized model for battery-conscious local inference.",
                isInstalled = false,
                localFilePath = "",
                sha256Short = "d4901bc2",
                downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q4_k_m.gguf"
            ),
            LocalModelEntity(
                id = "bitnet-b158-2b-4t",
                name = "BitNet b1.58 2B4T",
                family = "Microsoft / Leafy Ternary",
                runtimeId = AiRuntime.BITNET_CPP.id,
                quantization = "1.58-bit i2_s",
                parameters = "2.0B",
                sizeMb = 410,
                ramRequiredMb = 620,
                contextWindow = 4096,
                expectedTps = 46.8f,
                ecoRating = 5,
                specialty = "Ultra-low milliwatt daily assistant using ternary {-1, 0, +1} weights.",
                isInstalled = false,
                localFilePath = "",
                sha256Short = "9f4a2e1c",
                downloadUrl = "https://huggingface.co/microsoft/BiTNet-b1.58-2B-4T-GGUF/resolve/main/bitnet_b1_58-2B-4T-i2_s.gguf"
            ),
            LocalModelEntity(
                id = "llama-3.2-3b-q4km",
                name = "Llama 3.2 3B Instruct",
                family = "Meta Llama 3.2",
                runtimeId = AiRuntime.LLAMA_CPP.id,
                quantization = "Q4_K_M",
                parameters = "3.2B",
                sizeMb = 1920,
                ramRequiredMb = 2450,
                contextWindow = 8192,
                expectedTps = 28.4f,
                ecoRating = 4,
                specialty = "High-precision reasoning, multi-turn conversation branching & local RAG synthesis.",
                isInstalled = false,
                localFilePath = "",
                sha256Short = "3b8e71d0",
                downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf"
            ),
            LocalModelEntity(
                id = "qwen2.5-1.5b-tmac-lut",
                name = "Qwen 2.5 1.5B T-MAC LUT",
                family = "Qwen 2.5 Edge",
                runtimeId = AiRuntime.T_MAC.id,
                quantization = "INT3-LUT",
                parameters = "1.5B",
                sizeMb = 580,
                ramRequiredMb = 840,
                contextWindow = 8192,
                expectedTps = 52.1f,
                ecoRating = 5,
                specialty = "Zero-dequantization table lookup inference for rapid coding & math on ARM CPUs.",
                isInstalled = false,
                localFilePath = "",
                sha256Short = "7c11e9a4",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
            ),
            LocalModelEntity(
                id = "phi-3.5-mini-executorch",
                name = "Phi-3.5 Mini XNNPACK",
                family = "Phi-3.5 Edge",
                runtimeId = AiRuntime.EXECUTORCH.id,
                quantization = "INT4-PTE",
                parameters = "3.8B",
                sizeMb = 2140,
                ramRequiredMb = 2680,
                contextWindow = 4096,
                expectedTps = 33.6f,
                ecoRating = 4,
                specialty = "Compiled ExecuTorch .pte graph optimized for XNNPACK & NPU delegation.",
                isInstalled = false,
                localFilePath = "",
                sha256Short = "e509b32f",
                downloadUrl = "https://huggingface.co/microsoft/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf"
            ),
            LocalModelEntity(
                id = "gemma-2-2b-q4km",
                name = "Gemma 2 2B Instruct",
                family = "Google Gemma 2",
                runtimeId = AiRuntime.LLAMA_CPP.id,
                quantization = "Q4_K_M",
                parameters = "2.6B",
                sizeMb = 1630,
                ramRequiredMb = 2100,
                contextWindow = 8192,
                expectedTps = 31.2f,
                ecoRating = 4,
                specialty = "Creative writing, summarization, and structured note analysis.",
                isInstalled = false,
                localFilePath = "",
                sha256Short = "a12f88c3",
                downloadUrl = "https://huggingface.co/google/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf"
            ),
            LocalModelEntity(
                id = "smollm2-360m-bitnet",
                name = "SmolLM2 360M Leaf-Nano",
                family = "HuggingFace SmolLM2",
                runtimeId = AiRuntime.BITNET_CPP.id,
                quantization = "1.58-bit i2_s",
                parameters = "360M",
                sizeMb = 115,
                ramRequiredMb = 240,
                contextWindow = 2048,
                expectedTps = 84.5f,
                ecoRating = 5,
                specialty = "Instantaneous sub-watt responses for extreme battery saving.",
                isInstalled = false,
                localFilePath = "",
                sha256Short = "d4901bc2",
                downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q4_k_m.gguf"
            ),
            LocalModelEntity(
                id = "deepseek-r1-1.5b-tmac",
                name = "DeepSeek-R1 Distill 1.5B",
                family = "DeepSeek Reasoning",
                runtimeId = AiRuntime.T_MAC.id,
                quantization = "INT4-LUT",
                parameters = "1.5B",
                sizeMb = 790,
                ramRequiredMb = 1100,
                contextWindow = 8192,
                expectedTps = 44.0f,
                ecoRating = 5,
                specialty = "Step-by-step chain-of-thought reasoning with LUT acceleration.",
                isInstalled = false,
                localFilePath = "",
                sha256Short = "68fa9012",
                downloadUrl = "https://huggingface.co/deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf"
            ),
            LocalModelEntity(
                id = "llama-3.2-1b-executorch",
                name = "Llama 3.2 1B SpinQuant",
                family = "Meta ExecuTorch",
                runtimeId = AiRuntime.EXECUTORCH.id,
                quantization = "INT4-SpinQuant",
                parameters = "1.2B",
                sizeMb = 740,
                ramRequiredMb = 980,
                contextWindow = 4096,
                expectedTps = 58.2f,
                ecoRating = 5,
                specialty = "SpinQuant outlier-smoothed weights for ultra-fast mobile NPU/CPU inference.",
                isInstalled = false,
                localFilePath = "",
                sha256Short = "c9214ef8",
                downloadUrl = "https://huggingface.co/meta-llama/Llama-3.2-1B-Instruct/resolve/main/llama-3.2-1b.pte"
            )
        )

        fun defaultPersonas(): List<PersonaPresetEntity> = listOf(
            PersonaPresetEntity(
                id = 1L,
                name = "Leafy Eco Guide",
                iconTag = "eco",
                tagline = "Thoughtful, privacy-first local AI assistant",
                systemPrompt = "You are Leafy, a privacy-preserving, energy-conscious on-device AI assistant. Provide clear, structured, accurate answers grounded in any pinned local RAG documents.",
                temperature = 0.65f,
                topP = 0.90f,
                repeatPenalty = 1.10f,
                isBuiltIn = true
            ),
            PersonaPresetEntity(
                id = 2L,
                name = "Edge Systems Architect",
                iconTag = "code",
                tagline = "Quantization, C++/Rust/Kotlin & embedded AI specialist",
                systemPrompt = "You are an expert systems engineer specializing in on-device LLM inference (llama.cpp, Bitnet.cpp, T-MAC lookup tables, ExecuTorch) and clean Kotlin/C++ architecture. Give concrete benchmarks and code.",
                temperature = 0.35f,
                topP = 0.85f,
                repeatPenalty = 1.15f,
                isBuiltIn = true
            ),
            PersonaPresetEntity(
                id = 3L,
                name = "Socratic Study Partner",
                iconTag = "school",
                tagline = "Guides understanding with step-by-step questions",
                systemPrompt = "You are a patient Socratic tutor. Break complex ideas into intuitive analogies, verify assumptions, and end with one insightful follow-up question.",
                temperature = 0.70f,
                topP = 0.92f,
                repeatPenalty = 1.08f,
                isBuiltIn = true
            ),
            PersonaPresetEntity(
                id = 4L,
                name = "Botanical Storyteller",
                iconTag = "auto_stories",
                tagline = "Warm, imaginative prose & creative brainstorming",
                systemPrompt = "You are a vivid, imaginative writer with a warm, organic voice. Craft memorable metaphors, rich sensory detail, and uplifting ideas.",
                temperature = 0.88f,
                topP = 0.95f,
                repeatPenalty = 1.12f,
                isBuiltIn = true
            )
        )

        fun defaultRagDocuments(): List<RagDocumentEntity> = listOf(
            RagDocumentEntity(
                id = 1L,
                title = "On-Device AI Runtimes Field Guide.md",
                category = "Technical Reference",
                content = """
                    # Leafy Multi-Runtime Architecture Reference
                    1. **llama.cpp (GGUF)**: Uses k-quants (Q4_K_M, Q5_K_M, Q8_0) with ARM NEON dot-product intrinsics (`SDOT`/`UDOT`) and optional KV-cache quantization (`Q4_0`/`Q8_0`). Ideal for broad model compatibility.
                    2. **Bitnet.cpp (1.58-bit Ternary)**: Encodes weights strictly as ternary values {-1, 0, +1} (1.58 bits per weight). Replaces costly floating-point fused multiply-accumulate (FMA) with pure integer additions, cutting mobile CPU energy consumption by 55%–71%.
                    3. **T-MAC (Table-Lookup MAC)**: Precomputes activations into fast on-chip SRAM Lookup Tables (LUTs), enabling arbitrary low-bit (INT2/INT3/INT4) matrix multiplication without runtime dequantization overhead.
                    4. **ExecuTorch (.pte)**: Compiles PyTorch models Ahead-of-Time (AoT) into portable `.pte` flatbuffers delegated to XNNPACK, Vulkan, or mobile NPU accelerators with near-zero runtime memory overhead.
                """.trimIndent(),
                chunkCount = 4,
                tokenEstimate = 215,
                isPinnedToChat = true
            ),
            RagDocumentEntity(
                id = 2L,
                title = "Off-Grid Solar & Permaculture Notes.txt",
                category = "Personal Notes",
                content = """
                    # Off-Grid Homestead & Battery Budget Notes
                    - LiFePO4 battery bank: 48V 100Ah (4.8 kWh usable capacity).
                    - Companion planting guild: Fruit trees surrounded by comfrey (dynamic accumulator), clover (nitrogen fixer), yarrow, and nasturtium (pest trap).
                    - Rainwater harvesting cistern: 2,500 gallons gravity-fed through a first-flush diverter and biochar-sand slow filter.
                    - Mobile device power budget: Running Leafy in Eco Leaf mode (~0.68W) consumes less than 0.7 Wh per hour of continuous local AI research.
                """.trimIndent(),
                chunkCount = 3,
                tokenEstimate = 165,
                isPinnedToChat = true
            ),
            RagDocumentEntity(
                id = 3L,
                title = "Privacy & Sovereign Data Manifesto.md",
                category = "Privacy Policy",
                content = """
                    # Zero-Telemetry Data Sovereignty
                    - All prompts, conversation branches, voice transcriptions, and RAG embeddings remain 100% inside the device's local SQLite storage (`leafy_offline_ai.db`).
                    - Zero external API calls, zero cloud analytics, and zero third-party SDK trackers.
                    - Users can export their full conversation tree as portable JSON/Markdown or cryptographic wipe local tables in one tap.
                """.trimIndent(),
                chunkCount = 2,
                tokenEstimate = 120,
                isPinnedToChat = false
            )
        )
    }
}
