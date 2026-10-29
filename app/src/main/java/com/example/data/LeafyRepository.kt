package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.io.File

class LeafyRepository(private val dao: LeafyDao) {

    val conversations: Flow<List<ConversationEntity>> = dao.observeConversations()
    val localModels: Flow<List<LocalModelEntity>> = dao.observeLocalModels()
    val ragDocuments: Flow<List<RagDocumentEntity>> = dao.observeRagDocuments()
    val personas: Flow<List<PersonaPresetEntity>> = dao.observePersonas()

    fun observeMessages(conversationId: Long): Flow<List<MessageNodeEntity>> =
        dao.observeMessagesForConversation(conversationId)

    suspend fun ensureSeedData() {
        if (dao.countPersonas() == 0) {
            dao.insertPersonas(defaultPersonas())
        }
        // Purge any model entry that does not correspond to a real physical file on disk
        verifyModelFilesOnDisk()
    }

    suspend fun verifyModelFilesOnDisk() {
        dao.deleteUninstalledModels()
        val models = dao.observeLocalModels().firstOrNull() ?: emptyList()
        for (model in models) {
            val file = File(model.localFilePath)
            if (!model.isInstalled || model.localFilePath.isBlank() || !file.exists() || file.length() <= 0L) {
                dao.deleteLocalModel(model.id)
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
        /**
         * Returns empty list: no fake or pre-populated download catalog items.
         * All local models in Leafy are loaded from real files on device storage.
         */
        fun defaultLocalModels(): List<LocalModelEntity> = emptyList()

        fun defaultPersonas(): List<PersonaPresetEntity> = listOf(
            PersonaPresetEntity(
                id = 1L,
                name = "Leafy Assistant",
                iconTag = "eco",
                tagline = "Direct, privacy-first local AI assistant",
                systemPrompt = "You are Leafy, a privacy-preserving on-device AI assistant. Provide clear, accurate answers grounded in any pinned local RAG documents.",
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
                systemPrompt = "You are an expert systems engineer specializing in on-device LLM inference (llama.cpp, Bitnet.cpp, T-MAC lookup tables, ExecuTorch) and clean Kotlin/C++ architecture.",
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
                systemPrompt = "You are a patient Socratic tutor. Break complex ideas into clear steps, verify assumptions, and end with one follow-up question.",
                temperature = 0.70f,
                topP = 0.92f,
                repeatPenalty = 1.08f,
                isBuiltIn = true
            )
        )

        fun defaultRagDocuments(): List<RagDocumentEntity> = emptyList()
    }
}
