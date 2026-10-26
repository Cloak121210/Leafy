package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AiRuntime
import com.example.data.ConversationEntity
import com.example.data.LeafyDatabase
import com.example.data.LeafyRepository
import com.example.data.LocalModelEntity
import com.example.data.MessageNodeEntity
import com.example.data.PersonaPresetEntity
import com.example.data.PowerProfile
import com.example.data.RagDocumentEntity
import com.example.engine.DeviceTelemetryManager
import com.example.engine.HardwareTelemetrySnapshot
import com.example.engine.LocalInferenceEngine
import com.example.engine.ModelDownloadManager
import com.example.engine.OfflineVoiceHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class LeafyTab {
    CHAT,
    MODELS,
    KNOWLEDGE,
    ECO_SETTINGS
}

data class BranchedMessageItem(
    val node: MessageNodeEntity,
    val siblingIds: List<Long>,
    val currentSiblingIndex: Int, // 0-based
    val totalSiblings: Int
)

data class StreamingPreviewState(
    val parentNodeId: Long?,
    val partialText: String,
    val liveTps: Float,
    val ttftMs: Int,
    val generatedTokens: Int,
    val energyMicroWh: Float,
    val citedRagTitles: List<String>,
    val runtime: AiRuntime,
    val modelName: String,
    val quantization: String
)

data class LeafyUiState(
    val currentTab: LeafyTab = LeafyTab.CHAT,
    val conversations: List<ConversationEntity> = emptyList(),
    val activeConversation: ConversationEntity? = null,
    val activeBranchMessages: List<BranchedMessageItem> = emptyList(),
    val localModels: List<LocalModelEntity> = emptyList(),
    val activeModel: LocalModelEntity? = null,
    val activeRuntime: AiRuntime = AiRuntime.BITNET_CPP,
    val powerProfile: PowerProfile = PowerProfile.ECO,
    val ragDocuments: List<RagDocumentEntity> = emptyList(),
    val pinnedRagDocuments: List<RagDocumentEntity> = emptyList(),
    val personas: List<PersonaPresetEntity> = emptyList(),
    val activePersona: PersonaPresetEntity? = null,
    val streamingPreview: StreamingPreviewState? = null,
    val telemetry: HardwareTelemetrySnapshot = HardwareTelemetrySnapshot(),
    val speakingMessageId: Long? = null,
    val useComicSans: Boolean = false,
    val isDarkTheme: Boolean = false,
    val statusBannerMessage: String? = null,
    val totalSessionTokens: Int = 142
)

class LeafyViewModel(
    private val appContext: Context,
    private val repository: LeafyRepository,
    private val inferenceEngine: LocalInferenceEngine,
    private val telemetryManager: DeviceTelemetryManager,
    private val voiceHelper: OfflineVoiceHelper,
    private val downloadManager: ModelDownloadManager
) : ViewModel() {

    private val _currentTab = MutableStateFlow(LeafyTab.CHAT)
    private val _activeConversationId = MutableStateFlow<Long?>(null)
    private val _powerProfile = MutableStateFlow(PowerProfile.ECO)
    private val _streamingPreview = MutableStateFlow<StreamingPreviewState?>(null)
    private val _telemetry = MutableStateFlow(HardwareTelemetrySnapshot())
    private val _useComicSans = MutableStateFlow(false)
    private val _isDarkTheme = MutableStateFlow(false)
    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    private val _totalSessionTokens = MutableStateFlow(142)

    private var streamingJob: Job? = null

    private val baseDataFlow = combine(
        repository.conversations,
        repository.allMessages,
        repository.localModels,
        repository.ragDocuments,
        repository.personas
    ) { conversations, allMessages, models, ragDocs, personas ->
         quintuple(conversations, allMessages, models, ragDocs, personas)
    }

    private val preferencesFlow = combine(
        _currentTab,
        _activeConversationId,
        _powerProfile,
        _useComicSans,
        _isDarkTheme
    ) { tab, activeConvId, powerProfile, comicSans, darkTheme ->
        quintuple(tab, activeConvId, powerProfile, comicSans, darkTheme)
    }

    private val runtimeOverlayFlow = combine(
        _streamingPreview,
        _telemetry,
        voiceHelper.speakingMessageId,
        _statusBannerMessage,
        _totalSessionTokens
    ) { streaming, telemetry, speakingId, banner, totalTokens ->
        quintuple(streaming, telemetry, speakingId, banner, totalTokens)
    }

    val uiState: StateFlow<LeafyUiState> = combine(
        baseDataFlow,
        preferencesFlow,
        runtimeOverlayFlow
    ) { base, prefs, overlay ->
        val conversations = base.first
        val allMessages = base.second
        val models = base.third
        val ragDocs = base.fourth
        val personas = base.fifth

        val currentTab = prefs.first
        val requestedConvId = prefs.second
        val powerProfile = prefs.third
        val useComicSans = prefs.fourth
        val isDarkTheme = prefs.fifth

        val streaming = overlay.first
        val telemetry = overlay.second
        val speakingId = overlay.third
        val banner = overlay.fourth
        val totalTokens = overlay.fifth

        val activeConv = conversations.find { it.id == requestedConvId } ?: conversations.firstOrNull()
        val convMessages = if (activeConv != null) {
            allMessages.filter { it.conversationId == activeConv.id }
        } else {
            emptyList()
        }

        val branchedPath = resolveActiveBranchPath(
            messages = convMessages,
            activeLeafId = activeConv?.activeLeafMessageId
        )

        val installedModels = models.filter {
            it.isInstalled && it.localFilePath.isNotBlank() && java.io.File(it.localFilePath).exists()
        }
        val activeModel = installedModels.find { it.id == activeConv?.activeModelId }
            ?: installedModels.firstOrNull()

        val activeRuntime = activeConv?.activeRuntimeId?.let { AiRuntime.fromId(it) }
            ?: activeModel?.runtimeId?.let { AiRuntime.fromId(it) }
            ?: AiRuntime.BITNET_CPP

        val activePersona = personas.find { it.id == activeConv?.activePersonaId }
            ?: personas.firstOrNull()

        LeafyUiState(
            currentTab = currentTab,
            conversations = conversations,
            activeConversation = activeConv,
            activeBranchMessages = branchedPath,
            localModels = models,
            activeModel = activeModel,
            activeRuntime = activeRuntime,
            powerProfile = powerProfile,
            ragDocuments = ragDocs,
            pinnedRagDocuments = ragDocs.filter { it.isPinnedToChat },
            personas = personas,
            activePersona = activePersona,
            streamingPreview = streaming,
            telemetry = telemetry,
            speakingMessageId = speakingId,
            useComicSans = useComicSans,
            isDarkTheme = isDarkTheme,
            statusBannerMessage = banner,
            totalSessionTokens = totalTokens
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LeafyUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureSeedData()
            val existingConvs = repository.conversations.first()
            if (existingConvs.isEmpty()) {
                val newId = repository.createConversation(
                    title = "Local AI Session",
                    modelId = "",
                    runtimeId = AiRuntime.LLAMA_CPP.id,
                    personaId = 1L
                )
                _activeConversationId.value = newId
            } else {
                _activeConversationId.value = existingConvs.first().id
            }
        }

        // Background hardware telemetry sampler
        viewModelScope.launch {
            while (isActive) {
                refreshTelemetry()
                delay(2500L)
            }
        }
    }


    private fun resolveActiveBranchPath(
        messages: List<MessageNodeEntity>,
        activeLeafId: Long?
    ): List<BranchedMessageItem> {
        if (messages.isEmpty()) return emptyList()

        val byId = messages.associateBy { it.id }
        val childrenByParent = messages.groupBy { it.parentId }

        // Find target leaf node
        val leafNode = (activeLeafId?.let { byId[it] }) ?: messages.last()

        // Walk up from leafNode to root
        val pathReversed = mutableListOf<MessageNodeEntity>()
        var cursor: MessageNodeEntity? = leafNode
        val visited = mutableSetOf<Long>()
        while (cursor != null && visited.add(cursor.id)) {
            pathReversed.add(cursor)
            cursor = cursor.parentId?.let { byId[it] }
        }

        val linearPath = pathReversed.reversed()
        return linearPath.map { node ->
            val siblings = childrenByParent[node.parentId]
                ?.sortedWith(compareBy<MessageNodeEntity> { it.timestamp }.thenBy { it.id })
                ?: listOf(node)
            val siblingIds = siblings.map { it.id }
            val index = siblingIds.indexOf(node.id).coerceAtLeast(0)
            BranchedMessageItem(
                node = node,
                siblingIds = siblingIds,
                currentSiblingIndex = index,
                totalSiblings = siblingIds.size
            )
        }
    }

    fun selectTab(tab: LeafyTab) {
        _currentTab.value = tab
    }

    fun toggleComicSans() {
        val next = !_useComicSans.value
        _useComicSans.value = next
        showTransientBanner(
            if (next) "Comic Sans Mode enabled — playful & informal typography active!"
            else "Rounded Sans-Serif (Quicksand + Nunito) typography active."
        )
    }

    fun setComicSans(enabled: Boolean) {
        _useComicSans.value = enabled
    }

    fun toggleDarkTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun setPowerProfile(profile: PowerProfile) {
        _powerProfile.value = profile
        refreshTelemetry()
        showTransientBanner("${profile.displayName} activated (${profile.cpuThreads} threads • ${profile.kvCacheBits})")
    }

    fun selectConversation(conversationId: Long) {
        _activeConversationId.value = conversationId
        _currentTab.value = LeafyTab.CHAT
    }

    fun createNewConversation() {
        viewModelScope.launch {
            val state = uiState.value
            val modelId = state.activeModel?.id ?: "bitnet-b158-2b-4t"
            val runtimeId = state.activeRuntime.id
            val personaId = state.activePersona?.id ?: 1L
            val newId = repository.createConversation(
                title = "New Local Session",
                modelId = modelId,
                runtimeId = runtimeId,
                personaId = personaId
            )
            _activeConversationId.value = newId
            _currentTab.value = LeafyTab.CHAT
        }
    }

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
            val remaining = repository.conversations.first()
            if (remaining.isEmpty()) {
                val state = uiState.value
                val modelId = state.activeModel?.id ?: "bitnet-b158-2b-4t"
                val runtimeId = state.activeRuntime.id
                val personaId = state.activePersona?.id ?: 1L
                val newId = repository.createConversation(
                    title = "New Local Session",
                    modelId = modelId,
                    runtimeId = runtimeId,
                    personaId = personaId
                )
                _activeConversationId.value = newId
            } else if (_activeConversationId.value == conversationId) {
                _activeConversationId.value = remaining.first().id
            }
        }
    }

    fun switchModelAndRuntime(model: LocalModelEntity) {
        viewModelScope.launch {
            val file = java.io.File(model.localFilePath)
            if (!model.isInstalled || model.localFilePath.isBlank() || !file.exists() || file.length() <= 0L) {
                showTransientBanner("Model '${model.name}' is not downloaded yet. Please download it from the Models tab first.")
                return@launch
            }
            val currentConv = uiState.value.activeConversation
            if (currentConv != null) {
                repository.updateConversation(
                    currentConv.copy(
                        activeModelId = model.id,
                        activeRuntimeId = model.runtimeId
                    )
                )
            }
            showTransientBanner("Active model: ${model.name} (${model.quantization})")
        }
    }

    fun switchRuntimeOnly(runtime: AiRuntime) {
        viewModelScope.launch {
            val modelsForRuntime = uiState.value.localModels.filter {
                AiRuntime.fromId(it.runtimeId) == runtime && it.isInstalled
            }
            val preferredModel = modelsForRuntime.firstOrNull()

            val currentConv = uiState.value.activeConversation
            if (currentConv != null) {
                repository.updateConversation(
                    currentConv.copy(
                        activeRuntimeId = runtime.id,
                        activeModelId = preferredModel?.id ?: currentConv.activeModelId
                    )
                )
            }
            showTransientBanner("Runtime switched to ${runtime.displayName} (${runtime.shortBadge})")
        }
    }

    fun selectPersona(persona: PersonaPresetEntity) {
        viewModelScope.launch {
            val currentConv = uiState.value.activeConversation ?: return@launch
            repository.updateConversation(currentConv.copy(activePersonaId = persona.id))
            showTransientBanner("Persona active: ${persona.name}")
        }
    }

    fun sendUserMessage(promptText: String) {
        val cleanPrompt = promptText.trim()
        if (cleanPrompt.isEmpty() || _streamingPreview.value != null) return

        viewModelScope.launch {
            val state = uiState.value
            val model = state.activeModel
            if (model == null || !model.isInstalled || model.localFilePath.isBlank()) {
                showTransientBanner("No local AI model downloaded yet! Please download a model from the Models tab first.")
                return@launch
            }
            val modelFile = java.io.File(model.localFilePath)
            if (!modelFile.exists() || modelFile.length() <= 0L) {
                showTransientBanner("Model file not found on disk. Please download it from the Models tab.")
                repository.updateLocalModel(model.copy(isInstalled = false, localFilePath = ""))
                return@launch
            }

            val conv = state.activeConversation ?: run {
                val newId = repository.createConversation(
                    title = cleanPrompt.take(38),
                    modelId = model.id,
                    runtimeId = state.activeRuntime.id,
                    personaId = 1L
                )
                _activeConversationId.value = newId
                repository.conversations.first().find { it.id == newId }
            } ?: return@launch

            val currentLeafId = state.activeBranchMessages.lastOrNull()?.node?.id ?: conv.activeLeafMessageId
            val runtime = state.activeRuntime
            val profile = state.powerProfile
            val persona = state.activePersona ?: PersonaPresetEntity(
                id = 1L,
                name = "Leafy Eco Guide",
                iconTag = "eco",
                tagline = "Default",
                systemPrompt = "You are Leafy.",
                temperature = 0.65f,
                topP = 0.9f
            )

            // Update conversation title if it's a fresh conversation
            if (state.activeBranchMessages.isEmpty() || conv.title == "Local AI Session" || conv.title == "New Local Session") {
                val newTitle = cleanPrompt.take(38).let { if (cleanPrompt.length > 38) "$it…" else it }
                repository.updateConversation(conv.copy(title = newTitle))
            }

            val userNodeId = repository.insertMessageNode(
                MessageNodeEntity(
                    conversationId = conv.id,
                    parentId = currentLeafId,
                    role = "USER",
                    content = cleanPrompt,
                    runtimeId = runtime.id,
                    modelName = model.name,
                    quantization = model.quantization,
                    powerProfileId = profile.id
                )
            )

            streamAssistantReplyForParent(
                conversationId = conv.id,
                parentUserNodeId = userNodeId,
                promptText = cleanPrompt,
                model = model,
                runtime = runtime,
                powerProfile = profile,
                persona = persona,
                pinnedDocs = state.pinnedRagDocuments,
                branchAttemptIndex = 1
            )
        }
    }

    /**
     * Edits a previous user message and forks a new conversation branch from its parent!
     */
    fun editUserMessageAndBranch(originalUserNode: MessageNodeEntity, editedPromptText: String) {
        val cleanPrompt = editedPromptText.trim()
        if (cleanPrompt.isEmpty() || _streamingPreview.value != null) return

        viewModelScope.launch {
            val state = uiState.value
            val model = state.activeModel
            if (model == null || !model.isInstalled || model.localFilePath.isBlank()) {
                showTransientBanner("No local AI model downloaded yet! Please download a model from the Models tab first.")
                return@launch
            }
            val modelFile = java.io.File(model.localFilePath)
            if (!modelFile.exists() || modelFile.length() <= 0L) {
                showTransientBanner("Model file not found on disk. Please download it from the Models tab.")
                repository.updateLocalModel(model.copy(isInstalled = false, localFilePath = ""))
                return@launch
            }

            val conv = state.activeConversation ?: return@launch
            val runtime = state.activeRuntime
            val profile = state.powerProfile
            val persona = state.activePersona ?: PersonaPresetEntity(
                id = 1L,
                name = "Leafy Eco Guide",
                iconTag = "eco",
                tagline = "Default",
                systemPrompt = "You are Leafy.",
                temperature = 0.65f,
                topP = 0.9f
            )

            val newBranchUserNodeId = repository.insertMessageNode(
                MessageNodeEntity(
                    conversationId = conv.id,
                    parentId = originalUserNode.parentId,
                    role = "USER",
                    content = cleanPrompt,
                    runtimeId = runtime.id,
                    modelName = model.name,
                    quantization = model.quantization,
                    powerProfileId = profile.id,
                    branchNote = "Edited fork"
                )
            )

            streamAssistantReplyForParent(
                conversationId = conv.id,
                parentUserNodeId = newBranchUserNodeId,
                promptText = cleanPrompt,
                model = model,
                runtime = runtime,
                powerProfile = profile,
                persona = persona,
                pinnedDocs = state.pinnedRagDocuments,
                branchAttemptIndex = 2
            )
        }
    }

    /**
     * Regenerates an assistant response under the same user prompt node, creating a new sibling branch
     * (e.g., to compare runtimes or sampling on the exact same prompt).
     */
    fun regenerateAssistantBranch(assistantNode: MessageNodeEntity) {
        if (_streamingPreview.value != null) return
        val parentUserNodeId = assistantNode.parentId ?: return

        viewModelScope.launch {
            val state = uiState.value
            val model = state.activeModel
            if (model == null || !model.isInstalled || model.localFilePath.isBlank()) {
                showTransientBanner("No local AI model downloaded yet! Please download a model from the Models tab first.")
                return@launch
            }
            val modelFile = java.io.File(model.localFilePath)
            if (!modelFile.exists() || modelFile.length() <= 0L) {
                showTransientBanner("Model file not found on disk. Please download it from the Models tab.")
                repository.updateLocalModel(model.copy(isInstalled = false, localFilePath = ""))
                return@launch
            }

            val conv = state.activeConversation ?: return@launch
            val allConvMessages = repository.observeMessages(conv.id).first()
            val parentUserNode = allConvMessages.find { it.id == parentUserNodeId } ?: return@launch
            val existingSiblings = allConvMessages.count { it.parentId == parentUserNodeId }

            val runtime = state.activeRuntime
            val profile = state.powerProfile
            val persona = state.activePersona ?: return@launch

            // Set active leaf to the user prompt while streaming the new branch
            repository.setActiveBranchLeaf(conv.id, parentUserNodeId)

            streamAssistantReplyForParent(
                conversationId = conv.id,
                parentUserNodeId = parentUserNodeId,
                promptText = parentUserNode.content,
                model = model,
                runtime = runtime,
                powerProfile = profile,
                persona = persona,
                pinnedDocs = state.pinnedRagDocuments,
                branchAttemptIndex = existingSiblings + 1
            )
        }
    }

    /**
     * Switches between sibling branches (`< 1/2 >`) on any message node in the conversation tree.
     */
    fun switchSiblingBranch(targetSiblingNodeId: Long) {
        viewModelScope.launch {
            val conv = uiState.value.activeConversation ?: return@launch
            val allConvMessages = repository.observeMessages(conv.id).first()
            val childrenByParent = allConvMessages.groupBy { it.parentId }

            // Walk down from targetSiblingNodeId along the most recent child chain to find its leaf
            var cursorId = targetSiblingNodeId
            val visited = mutableSetOf<Long>()
            while (visited.add(cursorId)) {
                val children = childrenByParent[cursorId]
                if (children.isNullOrEmpty()) break
                cursorId = children.maxByOrNull { it.timestamp }?.id ?: break
            }

            repository.setActiveBranchLeaf(conv.id, cursorId)
        }
    }

    private fun streamAssistantReplyForParent(
        conversationId: Long,
        parentUserNodeId: Long,
        promptText: String,
        model: LocalModelEntity,
        runtime: AiRuntime,
        powerProfile: PowerProfile,
        persona: PersonaPresetEntity,
        pinnedDocs: List<RagDocumentEntity>,
        branchAttemptIndex: Int
    ) {
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            var finalStepText = ""
            var finalTps = model.expectedTps
            var finalTtft = 45
            var finalTokens = 32
            var finalEnergy = 240f
            var finalCitations = emptyList<String>()

            inferenceEngine.streamResponse(
                userPrompt = promptText,
                model = model,
                runtime = runtime,
                powerProfile = powerProfile,
                persona = persona,
                pinnedDocs = pinnedDocs,
                branchAttemptIndex = branchAttemptIndex
            ).collect { step ->
                finalStepText = step.accumulatedText
                finalTps = step.currentTps
                finalTtft = step.ttftMs
                finalTokens = step.generatedTokens
                finalEnergy = step.energyMicroWh
                finalCitations = step.citedRagSnippets.map { it.documentTitle }

                _streamingPreview.value = StreamingStepToPreview(
                    parentNodeId = parentUserNodeId,
                    stepText = step.accumulatedText,
                    tps = step.currentTps,
                    ttft = step.ttftMs,
                    tokens = step.generatedTokens,
                    energy = step.energyMicroWh,
                    citations = finalCitations,
                    runtime = runtime,
                    modelName = model.name,
                    quantization = model.quantization
                )
            }

            _streamingPreview.value = null
            _totalSessionTokens.value += finalTokens
            refreshTelemetry()

            if (finalStepText.isNotBlank()) {
                repository.insertMessageNode(
                    MessageNodeEntity(
                        conversationId = conversationId,
                        parentId = parentUserNodeId,
                        role = "ASSISTANT",
                        content = finalStepText,
                        runtimeId = runtime.id,
                        modelName = model.name,
                        quantization = model.quantization,
                        powerProfileId = powerProfile.id,
                        tokensPerSec = finalTps,
                        ttftMs = finalTtft,
                        tokenCount = finalTokens,
                        energyMicroWh = finalEnergy,
                        citedRagTitles = finalCitations.joinToString("|"),
                        branchNote = "${runtime.displayName} • ${model.quantization}"
                    )
                )
            }
        }
    }

    private fun StreamingStepToPreview(
        parentNodeId: Long?,
        stepText: String,
        tps: Float,
        ttft: Int,
        tokens: Int,
        energy: Float,
        citations: List<String>,
        runtime: AiRuntime,
        modelName: String,
        quantization: String
    ) = StreamingPreviewState(
        parentNodeId = parentNodeId,
        partialText = stepText,
        liveTps = tps,
        ttftMs = ttft,
        generatedTokens = tokens,
        energyMicroWh = energy,
        citedRagTitles = citations,
        runtime = runtime,
        modelName = modelName,
        quantization = quantization
    )

    fun stopStreaming() {
        streamingJob?.cancel()
        inferenceEngine.stopInference()
        _streamingPreview.value = null
    }

    fun startModelDownload(model: LocalModelEntity) {
        if (model.isDownloading) return

        viewModelScope.launch {
            repository.updateLocalModel(
                model.copy(
                    isDownloading = true,
                    downloadProgress = 0.01f,
                    downloadSpeedMb = 0f
                )
            )
            showTransientBanner("Started downloading ${model.name} weights…")

            val targetFileName = "${model.id}.${AiRuntime.fromId(model.runtimeId).fileExtensions.first().removePrefix(".")}"
            val fallbackBytes = (model.sizeMb * 1024L * 1024L)

            downloadManager.downloadModel(
                modelId = model.id,
                url = model.downloadUrl,
                targetFileName = targetFileName,
                fallbackTotalBytes = fallbackBytes
            ).collect { status ->
                if (status.isComplete) {
                    repository.updateLocalModel(
                        model.copy(
                            isInstalled = true,
                            isDownloading = false,
                            downloadProgress = 1.0f,
                            localFilePath = status.localFilePath,
                            downloadedBytes = status.totalBytes,
                            totalBytes = status.totalBytes,
                            downloadSpeedMb = 0f
                        )
                    )
                    val conv = uiState.value.activeConversation
                    if (conv != null) {
                        repository.updateConversation(
                            conv.copy(
                                activeModelId = model.id,
                                activeRuntimeId = model.runtimeId
                            )
                        )
                    }
                    showTransientBanner("Downloaded & verified ${model.name}! Ready for offline inference.")
                } else if (status.error != null) {
                    repository.updateLocalModel(
                        model.copy(
                            isDownloading = false,
                            downloadProgress = 0f,
                            downloadSpeedMb = 0f
                        )
                    )
                    showTransientBanner(status.error)
                } else {
                    repository.updateLocalModel(
                        model.copy(
                            isDownloading = true,
                            downloadProgress = status.progress,
                            downloadedBytes = status.downloadedBytes,
                            totalBytes = status.totalBytes,
                            downloadSpeedMb = status.speedMbPerSec,
                            localFilePath = status.localFilePath
                        )
                    )
                }
            }
        }
    }

    fun cancelModelDownload(modelId: String) {
        downloadManager.cancelDownload(modelId)
        viewModelScope.launch {
            val model = uiState.value.localModels.find { it.id == modelId } ?: return@launch
            repository.updateLocalModel(
                model.copy(
                    isDownloading = false,
                    downloadProgress = 0f,
                    downloadSpeedMb = 0f
                )
            )
            showTransientBanner("Cancelled download for ${model.name}.")
        }
    }

    fun deleteDownloadedModel(model: LocalModelEntity) {
        viewModelScope.launch {
            downloadManager.deleteModelFile(model.localFilePath)
            repository.updateLocalModel(
                model.copy(
                    isInstalled = false,
                    isDownloading = false,
                    downloadProgress = 0f,
                    downloadedBytes = 0L,
                    localFilePath = ""
                )
            )
            showTransientBanner("Deleted local weights for ${model.name} (${model.sizeMb} MB storage freed).")
        }
    }

    fun downloadCustomModelFromUrl(
        url: String,
        name: String,
        runtime: AiRuntime,
        quant: String,
        sizeMb: Int
    ) {
        viewModelScope.launch {
            val cleanName = name.ifBlank { url.substringAfterLast("/").take(28) }
            val customModel = LocalModelEntity(
                id = "dl-${System.currentTimeMillis()}",
                name = cleanName,
                family = "Hugging Face Model Hub",
                runtimeId = runtime.id,
                quantization = quant.ifBlank { runtime.defaultQuantization },
                parameters = "${(sizeMb / 580f).coerceIn(0.4f, 14.0f).let { "%.1fB".format(it) }}",
                sizeMb = sizeMb.coerceAtLeast(64),
                ramRequiredMb = (sizeMb * 1.25f).toInt(),
                contextWindow = 4096,
                expectedTps = 38.0f,
                ecoRating = 5,
                specialty = "Downloaded directly from $url for offline inference.",
                isInstalled = false,
                isUserImported = true,
                downloadUrl = url,
                sha256Short = Integer.toHexString(url.hashCode()).take(8)
            )
            repository.insertLocalModel(customModel)
            startModelDownload(customModel)
        }
    }

    fun importLocalModelFromUri(uri: Uri?, fallbackName: String = "Custom-Edge-Model-Q4_K_M.gguf") {
        viewModelScope.launch {
            var displayName = fallbackName
            var sizeMb = 1180

            if (uri != null) {
                try {
                    appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIdx >= 0) {
                                displayName = cursor.getString(nameIdx) ?: fallbackName
                            }
                            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                                val bytes = cursor.getLong(sizeIdx)
                                sizeMb = (bytes / (1024L * 1024L)).toInt().coerceAtLeast(85)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }

            val lower = displayName.lowercase()
            val detectedRuntime = when {
                lower.endsWith(".pte") -> AiRuntime.EXECUTORCH
                lower.contains("bitnet") || lower.contains("1.58") || lower.contains("i2_s") -> AiRuntime.BITNET_CPP
                lower.endsWith(".tmac") || lower.contains("lut") -> AiRuntime.T_MAC
                else -> AiRuntime.LLAMA_CPP
            }

            val detectedQuant = when {
                lower.contains("q8") -> "Q8_0"
                lower.contains("q5") -> "Q5_K_M"
                lower.contains("1.58") || lower.contains("i2_s") -> "1.58-bit i2_s"
                lower.contains("lut") -> "INT4-LUT"
                lower.endsWith(".pte") -> "INT4-XNNPACK"
                else -> "Q4_K_M"
            }

            val cleanTitle = displayName
                .removeSuffix(".gguf")
                .removeSuffix(".pte")
                .removeSuffix(".tmac")
                .removeSuffix(".bin")
                .replace("_", " ")
                .replace("-", " ")

            val customModel = LocalModelEntity(
                id = "custom-${System.currentTimeMillis()}",
                name = cleanTitle,
                family = "User Imported Local Weights",
                runtimeId = detectedRuntime.id,
                quantization = detectedQuant,
                parameters = "${(sizeMb / 550f).coerceIn(0.5f, 8.0f).let { "%.1fB".format(it) }}",
                sizeMb = sizeMb,
                ramRequiredMb = (sizeMb * 1.28f).toInt(),
                contextWindow = 4096,
                expectedTps = 36.5f,
                ecoRating = 5,
                specialty = "Imported directly from device storage (${displayName}) with zero cloud upload.",
                isInstalled = true,
                isUserImported = true,
                localFilePath = uri?.toString() ?: "/sdcard/Download/$displayName",
                sha256Short = Integer.toHexString(displayName.hashCode()).take(8)
            )

            repository.insertLocalModel(customModel)
            switchModelAndRuntime(customModel)
        }
    }

    fun importCustomModelManual(
        name: String,
        runtime: AiRuntime,
        quantization: String,
        sizeMb: Int
    ) {
        viewModelScope.launch {
            val customModel = LocalModelEntity(
                id = "custom-${System.currentTimeMillis()}",
                name = name.ifBlank { "Custom Local Model" },
                family = "User Local Weight Import",
                runtimeId = runtime.id,
                quantization = quantization.ifBlank { runtime.defaultQuantization },
                parameters = "${(sizeMb / 580f).coerceIn(0.4f, 13.0f).let { "%.1fB".format(it) }}",
                sizeMb = sizeMb.coerceAtLeast(64),
                ramRequiredMb = (sizeMb * 1.25f).toInt().coerceAtLeast(128),
                contextWindow = 4096,
                expectedTps = 39.0f,
                ecoRating = 5,
                specialty = "Custom quantized local weight file mapped into ${runtime.displayName}.",
                isInstalled = true,
                isUserImported = true,
                localFilePath = "/storage/emulated/0/LeafyModels/${name.lowercase().replace(" ", "_")}${runtime.fileExtensions.first()}",
                sha256Short = Integer.toHexString(name.hashCode()).take(8)
            )
            repository.insertLocalModel(customModel)
            switchModelAndRuntime(customModel)
        }
    }

    // RAG Document Actions
    fun togglePinRagDocument(doc: RagDocumentEntity) {
        viewModelScope.launch {
            val updated = doc.copy(isPinnedToChat = !doc.isPinnedToChat)
            repository.updateRagDocument(updated)
            showTransientBanner(
                if (updated.isPinnedToChat) "Pinned '${doc.title}' to active RAG context window."
                else "Unpinned '${doc.title}' from active context."
            )
        }
    }

    fun addRagDocument(title: String, category: String, content: String) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            val paragraphs = content.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }.size.coerceAtLeast(1)
            val tokens = (content.split(Regex("\\s+")).size * 1.3f).toInt().coerceAtLeast(12)
            repository.insertRagDocument(
                RagDocumentEntity(
                    title = title.trim(),
                    category = category.ifBlank { "Local Note" },
                    content = content.trim(),
                    chunkCount = paragraphs,
                    tokenEstimate = tokens,
                    isPinnedToChat = true
                )
            )
            showTransientBanner("Indexed '${title.trim()}' ($paragraphs chunks, ~$tokens tokens) locally.")
        }
    }

    fun importRagDocumentFromUri(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            try {
                var title = "Imported_Local_Note.txt"
                appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIdx >= 0) {
                        title = cursor.getString(nameIdx) ?: title
                    }
                }
                val text = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                    it.readText().take(8000)
                } ?: ""
                if (text.isNotBlank()) {
                    addRagDocument(title = title, category = "Imported File", content = text)
                }
            } catch (_: Exception) {
                showTransientBanner("Could not read file content.")
            }
        }
    }

    fun deleteRagDocument(docId: Long) {
        viewModelScope.launch {
            repository.deleteRagDocument(docId)
        }
    }

    // Persona Actions
    fun saveCustomPersona(
        name: String,
        tagline: String,
        systemPrompt: String,
        temperature: Float,
        topP: Float
    ) {
        if (name.isBlank() || systemPrompt.isBlank()) return
        viewModelScope.launch {
            val id = repository.insertPersona(
                PersonaPresetEntity(
                    name = name.trim(),
                    iconTag = "psychology",
                    tagline = tagline.ifBlank { "Custom local persona" },
                    systemPrompt = systemPrompt.trim(),
                    temperature = temperature,
                    topP = topP,
                    isBuiltIn = false
                )
            )
            val conv = uiState.value.activeConversation
            if (conv != null) {
                repository.updateConversation(conv.copy(activePersonaId = id))
            }
            showTransientBanner("Saved & activated persona '${name.trim()}'")
        }
    }

    fun toggleSpeakMessage(messageId: Long, content: String) {
        voiceHelper.toggleSpeak(messageId, content)
    }

    fun wipeAllOfflineData() {
        viewModelScope.launch {
            repository.wipeAllHistory()
            val newId = repository.createConversation(
                title = "Local AI Session",
                modelId = "",
                runtimeId = AiRuntime.LLAMA_CPP.id,
                personaId = 1L
            )
            _activeConversationId.value = newId
            showTransientBanner("Local chat database wiped & reset to clean offline state.")
        }
    }

    fun refreshTelemetry() {
        val profile = _powerProfile.value
        val isGen = _streamingPreview.value != null
        _telemetry.value = telemetryManager.sampleTelemetry(
            activeProfileMilliwatts = profile.estimatedMilliwatts,
            isGenerating = isGen,
            totalSessionTokens = _totalSessionTokens.value
        )
    }

    fun dismissBanner() {
        _statusBannerMessage.value = null
    }

    private fun showTransientBanner(message: String) {
        viewModelScope.launch {
            _statusBannerMessage.value = message
            delay(3200L)
            if (_statusBannerMessage.value == message) {
                _statusBannerMessage.value = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceHelper.shutdown()
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appContext = context.applicationContext
                    val db = LeafyDatabase.getInstance(appContext)
                    val repo = LeafyRepository(db.leafyDao())
                    val engine = LocalInferenceEngine(appContext)
                    val telemetry = DeviceTelemetryManager(appContext)
                    val voice = OfflineVoiceHelper(appContext)
                    val downloadManager = ModelDownloadManager(appContext)
                    return LeafyViewModel(
                        appContext = appContext,
                        repository = repo,
                        inferenceEngine = engine,
                        telemetryManager = telemetry,
                        voiceHelper = voice,
                        downloadManager = downloadManager
                    ) as T
                }
            }
    }
}

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

private fun <A, B, C, D, E> quintuple(a: A, b: B, c: C, d: D, e: E) =
    Quintuple(a, b, c, d, e)
