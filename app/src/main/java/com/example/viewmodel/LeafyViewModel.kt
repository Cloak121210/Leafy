package com.example.viewmodel

import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.provider.OpenableColumns
import java.io.File

enum class LeafyTab {
    CHAT,
    MODELS,
    RUNTIMES,
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
    val activeRuntime: AiRuntime = AiRuntime.LLAMA_CPP,
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
    val totalSessionTokens: Int = 0,
    val isImportingModel: Boolean = false,
    val importProgress: Float = 0f,
    val importStatusText: String? = null
)

class LeafyViewModel(
    private val appContext: Context,
    private val repository: LeafyRepository,
    private val inferenceEngine: LocalInferenceEngine,
    private val telemetryManager: DeviceTelemetryManager,
    private val voiceHelper: OfflineVoiceHelper,
    private val fileManager: ModelDownloadManager
) : ViewModel() {

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentTab = MutableStateFlow(LeafyTab.CHAT)
    private val _activeConversationId = MutableStateFlow<Long?>(
        prefs.getLong(KEY_ACTIVE_CONV_ID, -1L).takeIf { it > 0L }
    )
    private val _selectedRuntime = MutableStateFlow<AiRuntime?>(
        prefs.getString(KEY_SELECTED_RUNTIME, null)?.takeIf { it.isNotBlank() }?.let { AiRuntime.fromId(it) }
    )
    private val _selectedModelId = MutableStateFlow<String?>(
        prefs.getString(KEY_SELECTED_MODEL_ID, null)?.takeIf { it.isNotBlank() }
    )
    private val _powerProfile = MutableStateFlow(
        prefs.getString(KEY_POWER_PROFILE, null)?.let { PowerProfile.fromId(it) } ?: PowerProfile.ECO
    )
    private val _streamingPreview = MutableStateFlow<StreamingPreviewState?>(null)
    private val _telemetry = MutableStateFlow(HardwareTelemetrySnapshot())
    private val _useComicSans = MutableStateFlow(prefs.getBoolean(KEY_USE_COMIC_SANS, false))
    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean(KEY_IS_DARK_THEME, false))
    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    private val _totalSessionTokens = MutableStateFlow(0)
    private val _isImportingModel = MutableStateFlow(false)
    private val _importProgress = MutableStateFlow(0f)
    private val _importStatusText = MutableStateFlow<String?>(null)

    private var streamingJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeConversationMessagesFlow = combine(
        repository.conversations,
        _activeConversationId
    ) { conversations, requestedId ->
        (conversations.find { it.id == requestedId } ?: conversations.firstOrNull())?.id
    }.distinctUntilChanged().flatMapLatest { activeId ->
        if (activeId != null) {
            repository.observeMessages(activeId)
        } else {
            flowOf(emptyList())
        }
    }

    private val baseDataFlow = combine(
        repository.conversations,
        activeConversationMessagesFlow,
        repository.localModels,
        repository.ragDocuments,
        repository.personas
    ) { conversations, activeMessages, models, ragDocs, personas ->
        quintuple(conversations, activeMessages, models, ragDocs, personas)
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

    private val runtimeSelectionFlow = combine(
        _selectedRuntime,
        _selectedModelId
    ) { selectedRuntime, selectedModelId ->
        selectedRuntime to selectedModelId
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

    private val importFlow = combine(
        _isImportingModel,
        _importProgress,
        _importStatusText
    ) { isImporting, progress, statusText ->
        Triple(isImporting, progress, statusText)
    }

    val uiState: StateFlow<LeafyUiState> = combine(
        baseDataFlow,
        preferencesFlow,
        runtimeSelectionFlow,
        runtimeOverlayFlow,
        importFlow
    ) { base, prefs, selection, overlay, importState ->
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

        val explicitRuntime = selection.first
        val explicitModelId = selection.second

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

        val validLocalModels = models.filter {
            it.isInstalled && it.localFilePath.isNotBlank() && File(it.localFilePath).exists() && File(it.localFilePath).length() > 0L
        }

        val activeRuntime = explicitRuntime
            ?: activeConv?.activeRuntimeId?.takeIf { it.isNotBlank() }?.let { AiRuntime.fromId(it) }
            ?: validLocalModels.firstOrNull()?.runtimeId?.let { AiRuntime.fromId(it) }
            ?: AiRuntime.LLAMA_CPP

        val compatibleModels = validLocalModels.filter { activeRuntime.isCompatibleWithModel(it) }
        val requestedModelId = explicitModelId?.takeIf { it.isNotBlank() } ?: activeConv?.activeModelId

        val activeModel = compatibleModels.find { it.id == requestedModelId }
            ?: compatibleModels.find { AiRuntime.fromId(it.runtimeId) == activeRuntime }
            ?: compatibleModels.firstOrNull()

        val activePersona = personas.find { it.id == activeConv?.activePersonaId }
            ?: personas.firstOrNull()

        LeafyUiState(
            currentTab = currentTab,
            conversations = conversations,
            activeConversation = activeConv,
            activeBranchMessages = branchedPath,
            localModels = validLocalModels,
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
            totalSessionTokens = totalTokens,
            isImportingModel = importState.first,
            importProgress = importState.second,
            importStatusText = importState.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LeafyUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureSeedData()
            val existingConvs = repository.conversations.first()
            if (existingConvs.isEmpty()) {
                val newId = repository.createConversation(
                    title = "Local AI Session",
                    modelId = _selectedModelId.value ?: "",
                    runtimeId = (_selectedRuntime.value ?: AiRuntime.LLAMA_CPP).id,
                    personaId = 1L
                )
                setActiveConversationPref(newId)
            } else {
                val savedId = _activeConversationId.value
                val targetConv = existingConvs.find { it.id == savedId } ?: existingConvs.first()
                setActiveConversationPref(targetConv.id)
            }
            // Automatically discover any existing model files in the app's models directory
            scanDeviceForLocalModels(silent = true)
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

        val leafNode = (activeLeafId?.let { byId[it] }) ?: messages.last()

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

    private fun setActiveConversationPref(id: Long?) {
        _activeConversationId.value = id
        prefs.edit().putLong(KEY_ACTIVE_CONV_ID, id ?: -1L).apply()
    }

    private fun setRuntimeAndModelPrefs(runtime: AiRuntime?, modelId: String?) {
        _selectedRuntime.value = runtime
        _selectedModelId.value = modelId
        prefs.edit()
            .putString(KEY_SELECTED_RUNTIME, runtime?.id ?: "")
            .putString(KEY_SELECTED_MODEL_ID, modelId ?: "")
            .apply()
    }

    fun selectTab(tab: LeafyTab) {
        _currentTab.value = tab
        if (tab == LeafyTab.ECO_SETTINGS) {
            refreshTelemetry()
        }
    }

    fun toggleComicSans() {
        val next = !_useComicSans.value
        _useComicSans.value = next
        prefs.edit().putBoolean(KEY_USE_COMIC_SANS, next).apply()
        showTransientBanner(
            if (next) "Comic Sans Mode enabled."
            else "Rounded Sans-Serif typography active."
        )
    }

    fun setComicSans(enabled: Boolean) {
        _useComicSans.value = enabled
        prefs.edit().putBoolean(KEY_USE_COMIC_SANS, enabled).apply()
    }

    fun toggleDarkTheme() {
        val next = !_isDarkTheme.value
        _isDarkTheme.value = next
        prefs.edit().putBoolean(KEY_IS_DARK_THEME, next).apply()
    }

    fun setPowerProfile(profile: PowerProfile) {
        val changed = _powerProfile.value != profile
        _powerProfile.value = profile
        prefs.edit().putString(KEY_POWER_PROFILE, profile.id).apply()
        if (changed && _streamingPreview.value == null) {
            inferenceEngine.releaseMountedResources()
        }
        refreshTelemetry()
        showTransientBanner("${profile.displayName} activated (${profile.cpuThreads} threads • ${profile.kvCacheBits})")
    }

    fun selectConversation(conversationId: Long) {
        stopStreaming()
        viewModelScope.launch {
            val targetConv = uiState.value.conversations.find { it.id == conversationId }
                ?: repository.conversations.first().find { it.id == conversationId }
            if (targetConv != null) {
                setRuntimeAndModelPrefs(
                    runtime = AiRuntime.fromId(targetConv.activeRuntimeId),
                    modelId = targetConv.activeModelId.takeIf { it.isNotBlank() }
                )
            }
            setActiveConversationPref(conversationId)
            _currentTab.value = LeafyTab.CHAT
        }
    }

    fun createNewConversation() {
        stopStreaming()
        viewModelScope.launch {
            val state = uiState.value
            val modelId = state.activeModel?.id ?: ""
            val runtimeId = state.activeRuntime.id
            val personaId = state.activePersona?.id ?: 1L
            val newId = repository.createConversation(
                title = "New Local Session",
                modelId = modelId,
                runtimeId = runtimeId,
                personaId = personaId
            )
            setActiveConversationPref(newId)
            _currentTab.value = LeafyTab.CHAT
        }
    }

    fun deleteConversation(conversationId: Long) {
        if (_activeConversationId.value == conversationId) {
            stopStreaming()
        }
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
            val remaining = repository.conversations.first()
            if (remaining.isEmpty()) {
                val state = uiState.value
                val modelId = state.activeModel?.id ?: ""
                val runtimeId = state.activeRuntime.id
                val personaId = state.activePersona?.id ?: 1L
                val newId = repository.createConversation(
                    title = "New Local Session",
                    modelId = modelId,
                    runtimeId = runtimeId,
                    personaId = personaId
                )
                setActiveConversationPref(newId)
            } else if (_activeConversationId.value == conversationId) {
                val nextConv = remaining.first()
                setRuntimeAndModelPrefs(
                    runtime = AiRuntime.fromId(nextConv.activeRuntimeId),
                    modelId = nextConv.activeModelId.takeIf { it.isNotBlank() }
                )
                setActiveConversationPref(nextConv.id)
            }
        }
    }

    fun switchModelAndRuntime(model: LocalModelEntity) {
        stopStreaming()
        viewModelScope.launch {
            val file = File(model.localFilePath)
            if (!model.isInstalled || model.localFilePath.isBlank() || !file.exists() || file.length() <= 0L) {
                repository.deleteLocalModel(model.id)
                showTransientBanner("Model file '${model.name}' was not found on disk.")
                return@launch
            }

            val currentRuntime = uiState.value.activeRuntime
            val modelDefaultRuntime = AiRuntime.fromId(model.runtimeId)
            val targetRuntime = if (currentRuntime.isCompatibleWithModel(model) && modelDefaultRuntime == AiRuntime.LLAMA_CPP) {
                currentRuntime
            } else {
                modelDefaultRuntime
            }

            if (uiState.value.activeModel?.id != model.id) {
                inferenceEngine.releaseMountedResources()
            }

            setRuntimeAndModelPrefs(runtime = targetRuntime, modelId = model.id)

            val currentConv = uiState.value.activeConversation
                ?: repository.conversations.first().let { convs ->
                    convs.find { it.id == _activeConversationId.value } ?: convs.firstOrNull()
                }
            if (currentConv != null) {
                repository.updateConversation(
                    currentConv.copy(
                        activeModelId = model.id,
                        activeRuntimeId = targetRuntime.id
                    )
                )
            }
            showTransientBanner("Mounted ${model.name} (${model.quantization}) on ${targetRuntime.displayName}")
        }
    }

    fun switchRuntimeOnly(runtime: AiRuntime) {
        stopStreaming()
        viewModelScope.launch {
            val previousRuntime = uiState.value.activeRuntime
            if (previousRuntime != runtime && runtime == AiRuntime.EXECUTORCH) {
                inferenceEngine.releaseMountedResources()
            }

            val allValidModels = repository.localModels.first().filter {
                it.isInstalled && it.localFilePath.isNotBlank() && File(it.localFilePath).exists() && File(it.localFilePath).length() > 0L
            }
            val compatibleModels = allValidModels.filter { runtime.isCompatibleWithModel(it) }
            val currentModelId = _selectedModelId.value ?: uiState.value.activeModel?.id
            val preferredModel = compatibleModels.find { it.id == currentModelId }
                ?: compatibleModels.find { AiRuntime.fromId(it.runtimeId) == runtime }
                ?: compatibleModels.firstOrNull()

            setRuntimeAndModelPrefs(runtime = runtime, modelId = preferredModel?.id)

            val currentConv = uiState.value.activeConversation
                ?: repository.conversations.first().let { convs ->
                    convs.find { it.id == _activeConversationId.value } ?: convs.firstOrNull()
                }
            if (currentConv != null) {
                repository.updateConversation(
                    currentConv.copy(
                        activeRuntimeId = runtime.id,
                        activeModelId = preferredModel?.id ?: ""
                    )
                )
            }

            if (preferredModel != null) {
                showTransientBanner("Active runtime: ${runtime.displayName} • Mounted ${preferredModel.name}")
            } else {
                showTransientBanner("Active runtime: ${runtime.displayName} (Load a ${runtime.fileExtensions.joinToString("/")} model to run inference)")
            }
        }
    }

    fun selectPersona(persona: PersonaPresetEntity) {
        viewModelScope.launch {
            val currentConv = uiState.value.activeConversation
                ?: repository.conversations.first().firstOrNull()
                ?: return@launch
            repository.updateConversation(currentConv.copy(activePersonaId = persona.id))
            showTransientBanner("Persona active: ${persona.name}")
        }
    }

    fun sendUserMessage(promptText: String) {
        val cleanPrompt = promptText.trim()
        if (cleanPrompt.isEmpty() || _streamingPreview.value != null) return

        viewModelScope.launch {
            val state = uiState.value
            val runtime = state.activeRuntime
            val model = state.activeModel
            if (model == null || !model.isInstalled || model.localFilePath.isBlank()) {
                if (state.localModels.isNotEmpty()) {
                    showTransientBanner("No ${runtime.fileExtensions.joinToString("/")} model loaded for ${runtime.displayName}. Switch runtime or load a ${runtime.fileExtensions.first()} file.")
                } else {
                    showTransientBanner("No local AI model loaded. Please load a model file from the Local Models screen first.")
                }
                return@launch
            }
            val modelFile = File(model.localFilePath)
            if (!modelFile.exists() || modelFile.length() <= 0L) {
                showTransientBanner("Model file not found on disk. Please reload it from the Local Models screen.")
                repository.deleteLocalModel(model.id)
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
            val profile = state.powerProfile
            val persona = state.activePersona ?: PersonaPresetEntity(
                id = 1L,
                name = "Leafy Assistant",
                iconTag = "eco",
                tagline = "Default",
                systemPrompt = "You are Leafy.",
                temperature = 0.65f,
                topP = 0.9f
            )

            if (state.activeBranchMessages.isEmpty() || conv.title == "Local AI Session" || conv.title == "New Local Session") {
                val newTitle = cleanPrompt.take(38).let { if (cleanPrompt.length > 38) "$it…" else it }
                repository.updateConversation(conv.copy(title = newTitle))
            }

            val priorHistory = state.activeBranchMessages.map { it.node }

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
                priorHistory = priorHistory,
                branchAttemptIndex = 1
            )
        }
    }

    /**
     * Edits a previous user message and forks a new conversation branch from its parent.
     */
    fun editUserMessageAndBranch(originalUserNode: MessageNodeEntity, editedPromptText: String) {
        val cleanPrompt = editedPromptText.trim()
        if (cleanPrompt.isEmpty() || _streamingPreview.value != null) return

        viewModelScope.launch {
            val state = uiState.value
            val model = state.activeModel
            if (model == null || !model.isInstalled || model.localFilePath.isBlank()) {
                showTransientBanner("No local AI model loaded. Please load a model file from the Local Models screen first.")
                return@launch
            }
            val modelFile = File(model.localFilePath)
            if (!modelFile.exists() || modelFile.length() <= 0L) {
                showTransientBanner("Model file not found on disk. Please reload it from the Local Models screen.")
                repository.deleteLocalModel(model.id)
                return@launch
            }

            val conv = state.activeConversation ?: return@launch
            val runtime = state.activeRuntime
            val profile = state.powerProfile
            val persona = state.activePersona ?: PersonaPresetEntity(
                id = 1L,
                name = "Leafy Assistant",
                iconTag = "eco",
                tagline = "Default",
                systemPrompt = "You are Leafy.",
                temperature = 0.65f,
                topP = 0.9f
            )

            val priorHistory = state.activeBranchMessages
                .takeWhile { it.node.id != originalUserNode.id }
                .map { it.node }

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
                priorHistory = priorHistory,
                branchAttemptIndex = 2
            )
        }
    }

    /**
     * Regenerates an assistant response under the same user prompt node, creating a new sibling branch.
     */
    fun regenerateAssistantBranch(assistantNode: MessageNodeEntity) {
        if (_streamingPreview.value != null) return
        val parentUserNodeId = assistantNode.parentId ?: return

        viewModelScope.launch {
            val state = uiState.value
            val model = state.activeModel
            if (model == null || !model.isInstalled || model.localFilePath.isBlank()) {
                showTransientBanner("No local AI model loaded. Please load a model file from the Local Models screen first.")
                return@launch
            }
            val modelFile = File(model.localFilePath)
            if (!modelFile.exists() || modelFile.length() <= 0L) {
                showTransientBanner("Model file not found on disk. Please reload it from the Local Models screen.")
                repository.deleteLocalModel(model.id)
                return@launch
            }

            val conv = state.activeConversation ?: return@launch
            val allConvMessages = repository.observeMessages(conv.id).first()
            val parentUserNode = allConvMessages.find { it.id == parentUserNodeId } ?: return@launch
            val existingSiblings = allConvMessages.count { it.parentId == parentUserNodeId }

            val runtime = state.activeRuntime
            val profile = state.powerProfile
            val persona = state.activePersona ?: return@launch

            val priorHistory = state.activeBranchMessages
                .takeWhile { it.node.id != parentUserNodeId }
                .map { it.node }

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
                priorHistory = priorHistory,
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
        priorHistory: List<MessageNodeEntity> = emptyList(),
        branchAttemptIndex: Int
    ) {
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            var finalStepText = ""
            var finalTps = 0f
            var finalTtft = 0
            var finalTokens = 0
            var finalEnergy = 0f
            var finalCitations = emptyList<String>()
            var wasCancelled = false
            var streamError: String? = null

            try {
                inferenceEngine.streamResponse(
                    userPrompt = promptText,
                    model = model,
                    runtime = runtime,
                    powerProfile = powerProfile,
                    persona = persona,
                    pinnedDocs = pinnedDocs,
                    conversationHistory = priorHistory,
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
            } catch (ce: CancellationException) {
                wasCancelled = true
                throw ce
            } catch (t: Throwable) {
                streamError = t.message ?: t.javaClass.simpleName
            } finally {
                withContext(NonCancellable) {
                    _streamingPreview.value = null
                    _totalSessionTokens.value += finalTokens
                    refreshTelemetry()

                    if (finalTps > 0f) {
                        repository.updateLocalModel(model.copy(expectedTps = finalTps))
                    }

                    val contentToPersist = when {
                        finalStepText.isNotBlank() && wasCancelled ->
                            "${finalStepText.trimEnd()}\n\n_[Generation stopped by user]_"
                        finalStepText.isNotBlank() ->
                            finalStepText
                        streamError != null ->
                            "**[Local Inference Error]** Could not complete generation on `${runtime.displayName}`: $streamError"
                        else -> ""
                    }

                    if (contentToPersist.isNotBlank()) {
                        val noteSuffix = when {
                            wasCancelled -> " • Stopped"
                            streamError != null -> " • Error"
                            else -> ""
                        }
                        repository.insertMessageNode(
                            MessageNodeEntity(
                                conversationId = conversationId,
                                parentId = parentUserNodeId,
                                role = "ASSISTANT",
                                content = contentToPersist,
                                runtimeId = runtime.id,
                                modelName = model.name,
                                quantization = model.quantization,
                                powerProfileId = powerProfile.id,
                                tokensPerSec = finalTps,
                                ttftMs = finalTtft,
                                tokenCount = finalTokens,
                                energyMicroWh = finalEnergy,
                                citedRagTitles = finalCitations.joinToString("|"),
                                branchNote = "${runtime.displayName} • ${model.quantization}$noteSuffix"
                            )
                        )
                    }

                    if (streamError != null) {
                        showTransientBanner("Inference error: $streamError")
                    }
                }
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

    fun deleteDownloadedModel(model: LocalModelEntity) {
        viewModelScope.launch {
            if (uiState.value.activeModel?.id == model.id || _selectedModelId.value == model.id) {
                stopStreaming()
                inferenceEngine.releaseMountedResources()
                setRuntimeAndModelPrefs(runtime = _selectedRuntime.value, modelId = null)
            }
            fileManager.deleteModelFile(model.localFilePath)
            repository.deleteLocalModel(model.id)
            showTransientBanner("Removed local model '${model.name}' (${model.sizeMb} MB).")
        }
    }

    /**
     * Loads a local model file from a user-selected Android Uri (Storage Access Framework),
     * copies it into the local engine storage directory, validates its binary header,
     * and activates it for offline inference.
     */
    fun importLocalModelFromUri(uri: Uri?) {
        if (uri == null || _isImportingModel.value) return

        viewModelScope.launch {
            _isImportingModel.value = true
            _importProgress.value = 0.01f
            _importStatusText.value = "Reading local model file…"

            fileManager.importModelFromUri(uri).collect { status ->
                if (status.error != null) {
                    _isImportingModel.value = false
                    _importProgress.value = 0f
                    _importStatusText.value = null
                    showTransientBanner(status.error)
                } else if (!status.isComplete) {
                    _isImportingModel.value = true
                    _importProgress.value = status.progress
                    val copiedMb = status.copiedBytes / (1024L * 1024L)
                    val totalMb = status.totalBytes / (1024L * 1024L)
                    _importStatusText.value = if (totalMb > 0L) {
                        "Loading '${status.displayName}' into engine storage: $copiedMb / $totalMb MB (${(status.progress * 100).toInt()}%)"
                    } else {
                        "Loading '${status.displayName}' into engine storage: $copiedMb MB"
                    }
                } else {
                    _isImportingModel.value = false
                    _importProgress.value = 1.0f
                    _importStatusText.value = null

                    val modelFile = File(status.localFilePath)
                    val registered = registerVerifiedLocalModelFile(
                        file = modelFile,
                        displayName = status.displayName,
                        precomputedSha = status.sha256Short
                    )
                    if (registered != null) {
                        switchModelAndRuntime(registered)
                    }
                }
            }
        }
    }

    /**
     * Scans local app storage and Download folder for .gguf, .pte, .tmac, .bitnet files,
     * verifies their headers, and registers them in the local database.
     */
    fun scanDeviceForLocalModels(silent: Boolean = false) {
        viewModelScope.launch {
            val discoveredFiles = withContext(Dispatchers.IO) {
                fileManager.scanDiskForModelFiles()
            }
            var addedCount = 0
            var firstDiscovered: LocalModelEntity? = null
            val existingPaths = repository.localModels.first().map { it.localFilePath }.toSet()

            for (file in discoveredFiles) {
                if (file.absolutePath !in existingPaths) {
                    val registered = registerVerifiedLocalModelFile(
                        file = file,
                        displayName = file.name,
                        precomputedSha = "",
                        silentInvalid = true
                    )
                    if (registered != null) {
                        if (firstDiscovered == null) firstDiscovered = registered
                        addedCount++
                    }
                }
            }

            if (firstDiscovered != null && uiState.value.activeModel == null) {
                switchModelAndRuntime(firstDiscovered)
            }

            if (!silent) {
                if (addedCount > 0) {
                    showTransientBanner("Discovered and loaded $addedCount local model file(s) from device storage.")
                } else if (uiState.value.localModels.isNotEmpty()) {
                    showTransientBanner("All ${uiState.value.localModels.size} local model file(s) in storage are already loaded.")
                } else {
                    showTransientBanner("No new .gguf, .pte, or .tmac files found in ${fileManager.modelsDir.name}. Use 'Select Model File' to pick from anywhere on device.")
                }
            }
        }
    }

    private suspend fun registerVerifiedLocalModelFile(
        file: File,
        displayName: String,
        precomputedSha: String = "",
        silentInvalid: Boolean = false
    ): LocalModelEntity? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() < 8L) {
            if (!silentInvalid) {
                showTransientBanner("Selected file '$displayName' could not be read or is too small.")
            }
            return@withContext null
        }

        if (fileManager.isCompanionTokenizerFile(displayName) || fileManager.isCompanionTokenizerFile(file.name)) {
            if (!silentInvalid) {
                showTransientBanner("Imported companion tokenizer '${displayName}' (${file.length() / 1024} KB) for ExecuTorch .pte models.")
            }
            return@withContext null
        }

        val ggufHeader = inferenceEngine.readGgufHeader(file)
        val pteHeader = inferenceEngine.readPteHeader(file)
        val tmacHeader = inferenceEngine.readTmacHeader(file)
        val lower = displayName.lowercase()

        val isSupportedExtension = lower.endsWith(".gguf") ||
            lower.endsWith(".pte") ||
            lower.endsWith(".tmac") ||
            lower.endsWith(".bitnet") ||
            lower.endsWith(".bin")

        if (!ggufHeader.isValidGguf && !pteHeader.isValidPte && !tmacHeader.isValidTmac && !isSupportedExtension) {
            fileManager.deleteModelFile(file.absolutePath)
            if (!silentInvalid) {
                showTransientBanner("Invalid model file '$displayName': Not a valid .gguf, .pte, .tmac, or companion tokenizer file.")
            }
            return@withContext null
        }

        val detectedRuntime = when {
            pteHeader.isValidPte || lower.endsWith(".pte") -> AiRuntime.EXECUTORCH
            tmacHeader.isValidTmac || lower.endsWith(".tmac") || lower.contains("tmac") || lower.contains("lut") -> AiRuntime.T_MAC
            lower.contains("bitnet") || lower.contains("1.58") || lower.contains("i2_s") || lower.endsWith(".bitnet") -> AiRuntime.BITNET_CPP
            else -> AiRuntime.LLAMA_CPP
        }

        val detectedQuant = when {
            lower.contains("q8_0") || lower.contains("q8") -> "Q8_0"
            lower.contains("q6_k") -> "Q6_K"
            lower.contains("q5_k_m") || lower.contains("q5") -> "Q5_K_M"
            lower.contains("q4_k_m") -> "Q4_K_M"
            lower.contains("q4_0") -> "Q4_0"
            lower.contains("iq4") -> "IQ4_XS"
            lower.contains("1.58") || lower.contains("i2_s") -> "1.58-bit i2_s"
            tmacHeader.isValidTmac -> tmacHeader.formatType
            pteHeader.isValidPte -> "PTE Flatbuffer"
            ggufHeader.isValidGguf -> "GGUF v${ggufHeader.version}"
            else -> detectedRuntime.defaultQuantization
        }

        val bytes = file.length()
        val sizeMb = (bytes / (1024L * 1024L)).toInt().coerceAtLeast(1)
        val shaHex = precomputedSha.ifBlank { fileManager.computeFileSha256Short(file) }

        val headerSummary = when {
            ggufHeader.isValidGguf -> "Verified GGUF v${ggufHeader.version} • ${ggufHeader.tensorCount} tensors • ${ggufHeader.metadataKvCount} metadata KV pairs"
            pteHeader.isValidPte -> "Verified ExecuTorch PTE binary (${pteHeader.magicString.trim()}) • ${bytes / 1024} KB"
            tmacHeader.isValidTmac -> "Verified T-MAC ${tmacHeader.formatType} binary • ${tmacHeader.tensorCount} LUT tensors"
            else -> "Local model binary (${file.name}) • ${bytes / 1024} KB"
        }

        val cleanTitle = displayName
            .removeSuffix(".gguf")
            .removeSuffix(".GGUF")
            .removeSuffix(".pte")
            .removeSuffix(".PTE")
            .removeSuffix(".tmac")
            .removeSuffix(".bitnet")
            .removeSuffix(".bin")
            .trim()
            .ifBlank { displayName }

        val modelId = "local-${shaHex.ifBlank { file.name.hashCode().toUInt().toString(16) }}"
        val customModel = LocalModelEntity(
            id = modelId,
            name = cleanTitle,
            family = "Local Device Storage",
            runtimeId = detectedRuntime.id,
            quantization = detectedQuant,
            parameters = "${sizeMb} MB",
            sizeMb = sizeMb,
            ramRequiredMb = (sizeMb * 1.25f).toInt().coerceAtLeast(64),
            contextWindow = 4096,
            expectedTps = 0f,
            ecoRating = 5,
            specialty = "$headerSummary • Path: ${file.name}",
            isInstalled = true,
            isUserImported = true,
            localFilePath = file.absolutePath,
            sha256Short = shaHex,
            fileSizeBytes = bytes
        )

        repository.insertLocalModel(customModel)
        customModel
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
            val tokens = (content.split(Regex("\\s+")).size * 1.3f).toInt().coerceAtLeast(1)
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
                var title = "Imported_Note.txt"
                appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIdx >= 0) {
                        title = cursor.getString(nameIdx) ?: title
                    }
                }
                val maxChars = 24_000
                val text = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        val sb = StringBuilder()
                        val buf = CharArray(4096)
                        var read = 0
                        while (sb.length < maxChars && reader.read(buf).also { read = it } != -1) {
                            val toAppend = minOf(read, maxChars - sb.length)
                            sb.append(buf, 0, toAppend)
                        }
                        sb.toString()
                    } ?: ""
                }
                if (text.isNotBlank()) {
                    addRagDocument(title = title, category = "Imported File", content = text)
                } else {
                    showTransientBanner("Selected file '$title' is empty.")
                }
            } catch (e: Exception) {
                showTransientBanner("Could not read file: ${e.message ?: "Read error"}")
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

    fun deleteCustomPersona(persona: PersonaPresetEntity) {
        if (persona.isBuiltIn) return
        viewModelScope.launch {
            val conv = uiState.value.activeConversation
            if (conv != null && conv.activePersonaId == persona.id) {
                repository.updateConversation(conv.copy(activePersonaId = 1L))
            }
            repository.deletePersona(persona.id)
            showTransientBanner("Deleted custom persona '${persona.name}'")
        }
    }

    fun toggleSpeakMessage(messageId: Long, content: String) {
        voiceHelper.toggleSpeak(messageId, content)
    }

    fun wipeAllOfflineData() {
        stopStreaming()
        viewModelScope.launch {
            repository.wipeAllHistory()
            val newId = repository.createConversation(
                title = "Local AI Session",
                modelId = uiState.value.activeModel?.id ?: "",
                runtimeId = uiState.value.activeRuntime.id,
                personaId = 1L
            )
            setActiveConversationPref(newId)
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
        inferenceEngine.releaseMountedResources()
        voiceHelper.shutdown()
    }

    companion object {
        private const val PREFS_NAME = "leafy_studio_prefs"
        private const val KEY_ACTIVE_CONV_ID = "active_conv_id"
        private const val KEY_SELECTED_RUNTIME = "selected_runtime"
        private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
        private const val KEY_POWER_PROFILE = "power_profile"
        private const val KEY_USE_COMIC_SANS = "use_comic_sans"
        private const val KEY_IS_DARK_THEME = "is_dark_theme"

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
                    val fileManager = ModelDownloadManager(appContext)
                    return LeafyViewModel(
                        appContext = appContext,
                        repository = repo,
                        inferenceEngine = engine,
                        telemetryManager = telemetry,
                        voiceHelper = voice,
                        fileManager = fileManager
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
