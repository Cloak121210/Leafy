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
import com.example.engine.InferenceTelemetryPhase
import com.example.engine.LocalInferenceEngine
import com.example.engine.ModelDownloadManager
import com.example.engine.OfflineVoiceHelper
import com.example.engine.TpsTelemetrySnapshot
import com.example.ui.components.stripThinkingTagsForHistory
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.SystemClock
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
    val quantization: String,
    val instantTps: Float = liveTps,
    val peakTps: Float = liveTps,
    val interTokenLatencyMs: Float = if (liveTps > 0f) 1000f / liveTps else 0f,
    val prefillTps: Float = 0f,
    val promptTokensCount: Int = 0,
    val runtimeBackendLabel: String = "",
    val runtimeModelDesc: String = "",
    val phase: InferenceTelemetryPhase = InferenceTelemetryPhase.DECODING,
    val tpsSeries: List<Float> = emptyList()
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
    val tpsTelemetry: TpsTelemetrySnapshot = TpsTelemetrySnapshot(),
    val speakingMessageId: Long? = null,
    val useComicSans: Boolean = false,
    val isDarkTheme: Boolean = false,
    val useGradientText: Boolean = true,
    val showChatStatusBar: Boolean = true,
    val enableThinking: Boolean = false,
    val nPredictUnlimited: Boolean = true,
    val customNPredict: Int = 512,
    val contextSize: Int = 4096,
    val includeThinkingInContext: Boolean = false,
    val kvCacheType: String = "Q8_0",
    val statusBannerMessage: String? = null,
    val totalSessionTokens: Int = 0,
    val isImportingModel: Boolean = false,
    val importProgress: Float = 0f,
    val importStatusText: String? = null,
    val mountedModelId: String? = null,
    val isLoadingModelIntoRam: Boolean = false
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
    private val _sessionTpsSnapshot = MutableStateFlow(TpsTelemetrySnapshot())
    private val _useComicSans = MutableStateFlow(prefs.getBoolean(KEY_USE_COMIC_SANS, false))
    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean(KEY_IS_DARK_THEME, false))
    private val _useGradientText = MutableStateFlow(prefs.getBoolean(KEY_USE_GRADIENT_TEXT, true))
    private val _showChatStatusBar = MutableStateFlow(prefs.getBoolean(KEY_SHOW_CHAT_STATUS_BAR, true))
    private val _enableThinking = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_THINKING, false))
    private val _nPredictUnlimited = MutableStateFlow(prefs.getBoolean(KEY_N_PREDICT_UNLIMITED, true))
    private val _customNPredict = MutableStateFlow(prefs.getInt(KEY_CUSTOM_N_PREDICT, 512).coerceIn(16, 131072))
    private val _contextSize = MutableStateFlow(prefs.getInt(KEY_CONTEXT_SIZE, 4096).coerceIn(2048, 131072))
    private val _includeThinkingInContext = MutableStateFlow(prefs.getBoolean(KEY_INCLUDE_THINKING_IN_CONTEXT, false))
    private val _kvCacheType = MutableStateFlow(prefs.getString(KEY_KV_CACHE_TYPE, "Q8_0") ?: "Q8_0")
    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    private val _totalSessionTokens = MutableStateFlow(0)
    private val _isImportingModel = MutableStateFlow(false)
    private val _importProgress = MutableStateFlow(0f)
    private val _importStatusText = MutableStateFlow<String?>(null)
    private val _mountedModelId = MutableStateFlow<String?>(null)
    private val _isLoadingModelIntoRam = MutableStateFlow(false)

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

    private val verifiedLocalModelsFlow = repository.localModels
        .map { models ->
            models.filter { model ->
                if (!model.isInstalled || model.localFilePath.isBlank()) {
                    false
                } else {
                    fileManager.isModelFileAvailable(File(model.localFilePath))
                }
            }
        }
        .flowOn(Dispatchers.IO)

    private val baseDataFlow = combine(
        repository.conversations,
        activeConversationMessagesFlow,
        verifiedLocalModelsFlow,
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
        _selectedModelId,
        _mountedModelId,
        _isLoadingModelIntoRam
    ) { selectedRuntime, selectedModelId, mountedModelId, isLoadingIntoRam ->
        Quadruple(selectedRuntime, selectedModelId, mountedModelId, isLoadingIntoRam)
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

    private val generationPrefsFlow = combine(
        _nPredictUnlimited,
        _customNPredict,
        _contextSize,
        _includeThinkingInContext,
        _kvCacheType
    ) { nPredictUnl, customNPred, ctxSize, inclThink, kvCache ->
        quintuple(nPredictUnl, customNPred, ctxSize, inclThink, kvCache)
    }

    private val uiFlagsFlow = combine(
        _useGradientText,
        _showChatStatusBar,
        _enableThinking,
        generationPrefsFlow
    ) { useGradientText, showChatStatusBar, enableThinking, genPrefs ->
        Quadruple(useGradientText, showChatStatusBar, enableThinking, genPrefs)
    }

    private val importFlow = combine(
        _isImportingModel,
        _importProgress,
        _importStatusText,
        _sessionTpsSnapshot,
        uiFlagsFlow
    ) { isImporting, progress, statusText, sessionTps, uiFlags ->
        quintuple(isImporting, progress, statusText, sessionTps, uiFlags)
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
        val mountedModelId = selection.third
        val isLoadingIntoRam = selection.fourth

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

        val validLocalModels = models

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

        val computedTpsTelemetry = resolveLiveTpsTelemetry(
            streaming = streaming,
            sessionSnapshot = importState.fourth,
            branchMessages = branchedPath.map { it.node },
            totalSessionTokens = totalTokens
        )

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
            tpsTelemetry = computedTpsTelemetry,
            speakingMessageId = speakingId,
            useComicSans = useComicSans,
            isDarkTheme = isDarkTheme,
            useGradientText = importState.fifth.first,
            showChatStatusBar = importState.fifth.second,
            enableThinking = importState.fifth.third,
            nPredictUnlimited = importState.fifth.fourth.first,
            customNPredict = importState.fifth.fourth.second,
            contextSize = importState.fifth.fourth.third,
            includeThinkingInContext = importState.fifth.fourth.fourth,
            kvCacheType = importState.fifth.fourth.fifth,
            statusBannerMessage = banner,
            totalSessionTokens = computedTpsTelemetry.totalSessionTokens,
            isImportingModel = importState.first,
            importProgress = importState.second,
            importStatusText = importState.third,
            mountedModelId = mountedModelId,
            isLoadingModelIntoRam = isLoadingIntoRam
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

        // Synchronize _mountedModelId with the actual mounted model path in LocalInferenceEngine
        viewModelScope.launch {
            combine(
                inferenceEngine.mountedModelPathFlow,
                verifiedLocalModelsFlow
            ) { mountedPath, models ->
                if (mountedPath.isNullOrBlank()) {
                    null
                } else {
                    models.find {
                        it.localFilePath == mountedPath ||
                            File(it.localFilePath).absolutePath == mountedPath
                    }?.id
                }
            }.collect { resolvedMountedId ->
                _mountedModelId.value = resolvedMountedId
            }
        }

        // Adaptive hardware telemetry sampler (faster cadence during active token streaming)
        viewModelScope.launch {
            while (isActive) {
                refreshTelemetry()
                val intervalMs = if (_streamingPreview.value != null) 450L else 2000L
                delay(intervalMs)
            }
        }
    }

    private fun resolveLiveTpsTelemetry(
        streaming: StreamingPreviewState?,
        sessionSnapshot: TpsTelemetrySnapshot,
        branchMessages: List<MessageNodeEntity>,
        totalSessionTokens: Int
    ): TpsTelemetrySnapshot {
        val assistantNodes = branchMessages.filter {
            it.role.equals("ASSISTANT", ignoreCase = true) && it.tokensPerSec > 0f
        }
        val histNodeTps = assistantNodes.map { it.tokensPerSec }
        val histAvgTps = if (histNodeTps.isNotEmpty()) histNodeTps.average().toFloat() else 0f
        val histPeakTps = histNodeTps.maxOrNull() ?: 0f
        val histAvgTtft = if (assistantNodes.isNotEmpty()) {
            assistantNodes.map { it.ttftMs }.average().toInt()
        } else 0
        val histTotalTokens = assistantNodes.sumOf { it.tokenCount }
        val measuredEnergyNodes = assistantNodes.filter { it.energyMicroWh > 0f }
        val histTotalEnergy = if (measuredEnergyNodes.isNotEmpty()) {
            measuredEnergyNodes.map { it.energyMicroWh.toDouble() }.sum().toFloat()
        } else {
            -1f
        }

        val effectiveTotalTokens = maxOf(totalSessionTokens, histTotalTokens) + (streaming?.generatedTokens ?: 0)
        val combinedHistory = buildList {
            addAll(histNodeTps)
            addAll(sessionSnapshot.tpsHistory)
            if (streaming != null && streaming.tpsSeries.isNotEmpty()) {
                addAll(streaming.tpsSeries)
            }
        }.filter { it > 0f }.takeLast(36)

        if (streaming != null) {
            val peak = maxOf(sessionSnapshot.sessionPeakTps, histPeakTps, streaming.peakTps, streaming.instantTps)
            val avgTps = when {
                sessionSnapshot.sessionAverageTps > 0f && streaming.liveTps > 0f ->
                    (sessionSnapshot.sessionAverageTps + streaming.liveTps) / 2f
                streaming.liveTps > 0f -> streaming.liveTps
                sessionSnapshot.sessionAverageTps > 0f -> sessionSnapshot.sessionAverageTps
                else -> histAvgTps
            }
            val uWhPerTok = if (streaming.generatedTokens > 0 && streaming.energyMicroWh > 0f) {
                streaming.energyMicroWh / streaming.generatedTokens
            } else {
                sessionSnapshot.energyPerTokenMicroWh
            }
            val tokPerMwh = if (uWhPerTok > 0f) 1000f / uWhPerTok else -1f

            return TpsTelemetrySnapshot(
                phase = streaming.phase,
                instantTps = streaming.instantTps,
                decodeTps = streaming.liveTps,
                sessionAverageTps = avgTps,
                sessionPeakTps = peak,
                prefillTps = if (streaming.prefillTps > 0f) streaming.prefillTps else sessionSnapshot.prefillTps,
                lastTtftMs = streaming.ttftMs,
                averageTtftMs = if (sessionSnapshot.averageTtftMs > 0) sessionSnapshot.averageTtftMs else streaming.ttftMs,
                interTokenLatencyMs = streaming.interTokenLatencyMs,
                promptTokensCount = streaming.promptTokensCount,
                generatedTokens = streaming.generatedTokens,
                totalSessionTokens = effectiveTotalTokens,
                energyMicroWh = streaming.energyMicroWh,
                energyPerTokenMicroWh = uWhPerTok,
                tokensPerMilliwattHour = tokPerMwh,
                tpsHistory = combinedHistory,
                completedRunsCount = maxOf(sessionSnapshot.completedRunsCount, assistantNodes.size)
            )
        }

        val lastNode = assistantNodes.lastOrNull()
        val decodeTps = if (sessionSnapshot.decodeTps > 0f) sessionSnapshot.decodeTps else (lastNode?.tokensPerSec ?: 0f)
        val avgTps = if (sessionSnapshot.sessionAverageTps > 0f) sessionSnapshot.sessionAverageTps else histAvgTps
        val peakTps = maxOf(sessionSnapshot.sessionPeakTps, histPeakTps, decodeTps)
        val lastTtft = if (sessionSnapshot.lastTtftMs > 0) sessionSnapshot.lastTtftMs else (lastNode?.ttftMs ?: 0)
        val avgTtft = if (sessionSnapshot.averageTtftMs > 0) sessionSnapshot.averageTtftMs else histAvgTtft
        val itlMs = when {
            sessionSnapshot.interTokenLatencyMs > 0f -> sessionSnapshot.interTokenLatencyMs
            decodeTps > 0f -> 1000f / decodeTps
            else -> 0f
        }
        val uWhPerTok = when {
            sessionSnapshot.energyPerTokenMicroWh > 0f -> sessionSnapshot.energyPerTokenMicroWh
            histTotalTokens > 0 && histTotalEnergy > 0f -> histTotalEnergy / histTotalTokens
            else -> -1f
        }
        val tokPerMwh = if (uWhPerTok > 0f) 1000f / uWhPerTok else -1f
        val finalEnergy = when {
            sessionSnapshot.energyMicroWh > 0f -> sessionSnapshot.energyMicroWh
            lastNode != null && lastNode.energyMicroWh > 0f -> lastNode.energyMicroWh
            else -> -1f
        }

        return TpsTelemetrySnapshot(
            phase = if (decodeTps > 0f) InferenceTelemetryPhase.COMPLETED else InferenceTelemetryPhase.IDLE,
            instantTps = decodeTps,
            decodeTps = decodeTps,
            sessionAverageTps = avgTps,
            sessionPeakTps = peakTps,
            prefillTps = sessionSnapshot.prefillTps,
            lastTtftMs = lastTtft,
            averageTtftMs = avgTtft,
            interTokenLatencyMs = itlMs,
            promptTokensCount = sessionSnapshot.promptTokensCount,
            generatedTokens = if (sessionSnapshot.generatedTokens > 0) sessionSnapshot.generatedTokens else (lastNode?.tokenCount ?: 0),
            totalSessionTokens = effectiveTotalTokens,
            energyMicroWh = finalEnergy,
            energyPerTokenMicroWh = uWhPerTok,
            tokensPerMilliwattHour = tokPerMwh,
            tpsHistory = combinedHistory,
            completedRunsCount = maxOf(sessionSnapshot.completedRunsCount, assistantNodes.size)
        )
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

    fun toggleGradientText() {
        val next = !_useGradientText.value
        _useGradientText.value = next
        prefs.edit().putBoolean(KEY_USE_GRADIENT_TEXT, next).apply()
        showTransientBanner(
            if (next) "Living gradient enabled on important headings & titles."
            else "Solid color restored on headings & titles."
        )
    }

    fun setGradientText(enabled: Boolean) {
        _useGradientText.value = enabled
        prefs.edit().putBoolean(KEY_USE_GRADIENT_TEXT, enabled).apply()
    }

    fun setShowChatStatusBar(enabled: Boolean) {
        _showChatStatusBar.value = enabled
        prefs.edit().putBoolean(KEY_SHOW_CHAT_STATUS_BAR, enabled).apply()
        showTransientBanner(
            if (enabled) "Top chat status bar enabled."
            else "Top chat status bar hidden."
        )
    }

    fun toggleShowChatStatusBar() {
        setShowChatStatusBar(!_showChatStatusBar.value)
    }

    fun setThinkingEnabled(enabled: Boolean) {
        _enableThinking.value = enabled
        prefs.edit().putBoolean(KEY_ENABLE_THINKING, enabled).apply()
    }

    fun toggleThinking() {
        val next = !_enableThinking.value
        setThinkingEnabled(next)
    }

    fun setNPredictUnlimited(unlimited: Boolean) {
        _nPredictUnlimited.value = unlimited
        prefs.edit().putBoolean(KEY_N_PREDICT_UNLIMITED, unlimited).apply()
    }

    fun setCustomNPredict(tokens: Int) {
        val clamped = tokens.coerceIn(16, 131072)
        _customNPredict.value = clamped
        prefs.edit().putInt(KEY_CUSTOM_N_PREDICT, clamped).apply()
    }

    fun setContextSize(tokens: Int) {
        val clamped = tokens.coerceIn(2048, 131072)
        val changed = _contextSize.value != clamped
        _contextSize.value = clamped
        prefs.edit().putInt(KEY_CONTEXT_SIZE, clamped).apply()
        if (changed && _streamingPreview.value == null) {
            viewModelScope.launch {
                inferenceEngine.offloadModelFromRam()
                _mountedModelId.value = null
            }
        }
    }

    fun setIncludeThinkingInContext(enabled: Boolean) {
        _includeThinkingInContext.value = enabled
        prefs.edit().putBoolean(KEY_INCLUDE_THINKING_IN_CONTEXT, enabled).apply()
    }

    fun setKvCacheType(cacheType: String) {
        val normalized = when (cacheType.uppercase()) {
            "F16", "Q8_0", "Q8", "Q4_0", "Q4", "F32" -> if (cacheType.uppercase() == "Q8") "Q8_0" else if (cacheType.uppercase() == "Q4") "Q4_0" else cacheType.uppercase()
            else -> "Q8_0"
        }
        val changed = _kvCacheType.value != normalized
        _kvCacheType.value = normalized
        prefs.edit().putString(KEY_KV_CACHE_TYPE, normalized).apply()
        if (changed && _streamingPreview.value == null) {
            viewModelScope.launch {
                inferenceEngine.offloadModelFromRam()
                _mountedModelId.value = null
            }
        }
        showTransientBanner("KV Cache Type set to $normalized")
    }

    fun setPowerProfile(profile: PowerProfile) {
        val changed = _powerProfile.value != profile
        _powerProfile.value = profile
        prefs.edit().putString(KEY_POWER_PROFILE, profile.id).apply()
        if (changed && _streamingPreview.value == null) {
            viewModelScope.launch {
                inferenceEngine.offloadModelFromRam()
                _mountedModelId.value = null
                refreshTelemetry()
            }
        } else {
            refreshTelemetry()
        }
        showTransientBanner("${profile.displayName} activated (${profile.description})")
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
            val fileExists = withContext(Dispatchers.IO) {
                model.isInstalled && model.localFilePath.isNotBlank() &&
                    fileManager.isModelFileAvailable(File(model.localFilePath))
            }
            if (!fileExists) {
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
                _mountedModelId.value = null
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
            showTransientBanner("Selected ${model.name} (${model.quantization}) on ${targetRuntime.displayName}")
        }
    }

    /**
     * Explicitly loads a local model into RAM (PocketPal-style Load button).
     */
    fun loadModelIntoRam(model: LocalModelEntity) {
        if (_isLoadingModelIntoRam.value) return
        stopStreaming()
        viewModelScope.launch {
            _isLoadingModelIntoRam.value = true
            try {
                val currentRuntime = uiState.value.activeRuntime
                val modelDefaultRuntime = AiRuntime.fromId(model.runtimeId)
                val targetRuntime = if (currentRuntime.isCompatibleWithModel(model) && modelDefaultRuntime == AiRuntime.LLAMA_CPP) {
                    currentRuntime
                } else {
                    modelDefaultRuntime
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

                val result = inferenceEngine.loadModelIntoRam(
                    model = model,
                    runtime = targetRuntime,
                    powerProfile = uiState.value.powerProfile,
                    customContextSize = uiState.value.contextSize,
                    kvCacheType = uiState.value.kvCacheType
                )
                result.fold(
                    onSuccess = { msg ->
                        _mountedModelId.value = model.id
                        refreshTelemetry()
                        showTransientBanner(msg)
                    },
                    onFailure = { err ->
                        _mountedModelId.value = null
                        refreshTelemetry()
                        showTransientBanner(err.message ?: "Failed to load '${model.name}' into RAM.")
                    }
                )
            } finally {
                _isLoadingModelIntoRam.value = false
            }
        }
    }

    /**
     * Explicitly offloads any loaded model weights and KV cache from RAM (PocketPal-style Offload button).
     */
    fun offloadModelFromRam() {
        stopStreaming()
        viewModelScope.launch {
            inferenceEngine.offloadModelFromRam()
            _mountedModelId.value = null
            refreshTelemetry()
            showTransientBanner("Offloaded model weights and KV cache from RAM.")
        }
    }

    fun switchRuntimeOnly(runtime: AiRuntime) {
        stopStreaming()
        viewModelScope.launch {
            val previousRuntime = uiState.value.activeRuntime
            if (previousRuntime != runtime && runtime == AiRuntime.EXECUTORCH) {
                inferenceEngine.releaseMountedResources()
            }

            val allValidModels = withContext(Dispatchers.IO) {
                repository.localModels.first().filter {
                    if (!it.isInstalled || it.localFilePath.isBlank()) {
                        false
                    } else {
                        fileManager.isModelFileAvailable(File(it.localFilePath))
                    }
                }
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
            showTransientBanner("Preset active: ${persona.name}")
        }
    }

    fun sendUserMessage(promptText: String) {
        val rawTrimmed = promptText.trim()
        if (rawTrimmed.isEmpty() || _streamingPreview.value != null) return
        val cleanPrompt = if (rawTrimmed.length > MAX_PROMPT_CHARS) {
            showTransientBanner("Prompt truncated to ${MAX_PROMPT_CHARS} characters to fit local database & context limits.")
            rawTrimmed.take(MAX_PROMPT_CHARS)
        } else {
            rawTrimmed
        }

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
            val modelFileValid = withContext(Dispatchers.IO) {
                fileManager.isModelFileAvailable(File(model.localFilePath))
            }
            if (!modelFileValid) {
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
                name = "llama.cpp Default",
                iconTag = "code",
                tagline = "Standard llama.cpp sampling defaults",
                systemPrompt = "You are a helpful, accurate, and concise assistant.",
                temperature = 0.80f,
                topK = 40,
                topP = 0.95f,
                minP = 0.05f,
                xtcThreshold = 0.10f,
                xtcProbability = 0.0f,
                typicalP = 1.0f,
                penaltyLastN = 64,
                repeatPenalty = 1.0f,
                penaltyFreq = 0.0f,
                penaltyPresent = 0.0f,
                mirostat = 0,
                mirostatTau = 5.0f,
                mirostatEta = 0.10f,
                seed = -1,
                jinjaEnabled = true
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
        val rawTrimmed = editedPromptText.trim()
        if (rawTrimmed.isEmpty() || _streamingPreview.value != null) return
        val cleanPrompt = if (rawTrimmed.length > MAX_PROMPT_CHARS) {
            showTransientBanner("Edited prompt truncated to ${MAX_PROMPT_CHARS} characters.")
            rawTrimmed.take(MAX_PROMPT_CHARS)
        } else {
            rawTrimmed
        }

        viewModelScope.launch {
            val state = uiState.value
            val model = state.activeModel
            if (model == null || !model.isInstalled || model.localFilePath.isBlank()) {
                showTransientBanner("No local AI model loaded. Please load a model file from the Local Models screen first.")
                return@launch
            }
            val modelFileValid = withContext(Dispatchers.IO) {
                fileManager.isModelFileAvailable(File(model.localFilePath))
            }
            if (!modelFileValid) {
                showTransientBanner("Model file not found on disk. Please reload it from the Local Models screen.")
                repository.deleteLocalModel(model.id)
                return@launch
            }

            val conv = state.activeConversation ?: return@launch
            val runtime = state.activeRuntime
            val profile = state.powerProfile
            val persona = state.activePersona ?: PersonaPresetEntity(
                id = 1L,
                name = "llama.cpp Default",
                iconTag = "code",
                tagline = "Standard llama.cpp sampling defaults",
                systemPrompt = "You are a helpful, accurate, and concise assistant.",
                temperature = 0.80f,
                topK = 40,
                topP = 0.95f,
                minP = 0.05f,
                xtcThreshold = 0.10f,
                xtcProbability = 0.0f,
                typicalP = 1.0f,
                penaltyLastN = 64,
                repeatPenalty = 1.0f,
                penaltyFreq = 0.0f,
                penaltyPresent = 0.0f,
                mirostat = 0,
                mirostatTau = 5.0f,
                mirostatEta = 0.10f,
                seed = -1,
                jinjaEnabled = true
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
            val modelFileValid = withContext(Dispatchers.IO) {
                fileManager.isModelFileAvailable(File(model.localFilePath))
            }
            if (!modelFileValid) {
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
            var finalInstantTps = 0f
            var finalPeakTps = 0f
            var finalItlMs = 0f
            var finalPrefillTps = 0f
            var finalPromptTokens = 0
            var finalTtft = 0
            var finalTokens = 0
            var finalEnergy = -1f
            var finalRuntimeQuant = model.quantization
            var finalRuntimeBackend = runtime.displayName
            var finalRuntimeDesc = model.name
            var finalTpsSeries = emptyList<Float>()
            var finalCitations = emptyList<String>()
            var wasCancelled = false
            var streamError: String? = null

            var lastPreviewEmitMs = 0L
            var lastPreviewPhase: InferenceTelemetryPhase? = null

            try {
                val initialHw = _telemetry.value
                val initialHwMw = if (initialHw.isHardwarePowerMeasured && initialHw.instantaneousMilliwatts > 0) {
                    initialHw.instantaneousMilliwatts.toFloat()
                } else {
                    -1f
                }

                val currentState = uiState.value
                val effectiveNPredict = if (currentState.nPredictUnlimited) -1 else currentState.customNPredict
                inferenceEngine.streamResponse(
                    userPrompt = promptText,
                    model = model,
                    runtime = runtime,
                    powerProfile = powerProfile,
                    persona = persona,
                    pinnedDocs = pinnedDocs,
                    conversationHistory = priorHistory,
                    branchAttemptIndex = branchAttemptIndex,
                    hardwareMeasuredMilliwatts = initialHwMw,
                    enableThinking = currentState.enableThinking,
                    customContextSize = currentState.contextSize,
                    nPredictLimit = effectiveNPredict,
                    includeThinkingInContext = currentState.includeThinkingInContext,
                    kvCacheType = currentState.kvCacheType
                ).collect { step ->
                    finalStepText = step.accumulatedText
                    finalTps = step.currentTps
                    finalInstantTps = step.instantTps
                    finalPeakTps = maxOf(finalPeakTps, step.peakTps)
                    finalItlMs = step.interTokenLatencyMs
                    if (step.prefillTps > 0f) finalPrefillTps = step.prefillTps
                    if (step.promptTokensCount > 0) finalPromptTokens = step.promptTokensCount
                    finalTtft = step.ttftMs
                    finalTokens = step.generatedTokens
                    finalEnergy = step.energyMicroWh
                    if (step.runtimeQuantizationLabel.isNotBlank()) {
                        finalRuntimeQuant = step.runtimeQuantizationLabel
                    }
                    if (step.runtimeBackendLabel.isNotBlank()) {
                        finalRuntimeBackend = step.runtimeBackendLabel
                    }
                    if (step.runtimeModelDesc.isNotBlank()) {
                        finalRuntimeDesc = step.runtimeModelDesc
                    }
                    if (step.tpsSeries.isNotEmpty()) {
                        finalTpsSeries = step.tpsSeries
                    }
                    finalCitations = step.citedRagSnippets.map { it.documentTitle }

                    val nowMs = SystemClock.elapsedRealtime()
                    val shouldEmitPreview = step.isFinished ||
                        step.generatedTokens <= 1 ||
                        step.phase != lastPreviewPhase ||
                        (nowMs - lastPreviewEmitMs) >= STREAM_PREVIEW_MIN_INTERVAL_MS

                    if (shouldEmitPreview) {
                        lastPreviewEmitMs = nowMs
                        lastPreviewPhase = step.phase
                        _streamingPreview.value = StreamingStepToPreview(
                            parentNodeId = parentUserNodeId,
                            stepText = step.accumulatedText,
                            tps = step.currentTps,
                            ttft = step.ttftMs,
                            tokens = step.generatedTokens,
                            energy = step.energyMicroWh,
                            citations = finalCitations,
                            runtime = runtime,
                            modelName = finalRuntimeDesc,
                            quantization = finalRuntimeQuant,
                            instantTps = step.instantTps,
                            peakTps = finalPeakTps,
                            interTokenLatencyMs = step.interTokenLatencyMs,
                            prefillTps = finalPrefillTps,
                            promptTokensCount = finalPromptTokens,
                            runtimeBackendLabel = finalRuntimeBackend,
                            runtimeModelDesc = finalRuntimeDesc,
                            phase = step.phase,
                            tpsSeries = finalTpsSeries
                        )
                    }
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

                    if (finalTps > 0f || finalTokens > 0) {
                        val prev = _sessionTpsSnapshot.value
                        val prevRuns = prev.completedRunsCount
                        val nextRuns = prevRuns + 1
                        val nextAvgTps = if (prevRuns > 0 && prev.sessionAverageTps > 0f && finalTps > 0f) {
                            ((prev.sessionAverageTps * prevRuns) + finalTps) / nextRuns
                        } else if (finalTps > 0f) {
                            finalTps
                        } else {
                            prev.sessionAverageTps
                        }
                        val nextAvgTtft = if (prevRuns > 0 && prev.averageTtftMs > 0 && finalTtft > 0) {
                            ((prev.averageTtftMs * prevRuns) + finalTtft) / nextRuns
                        } else if (finalTtft > 0) {
                            finalTtft
                        } else {
                            prev.averageTtftMs
                        }
                        val nextHistory = (prev.tpsHistory + (if (finalTpsSeries.isNotEmpty()) finalTpsSeries else listOfNotNull(finalTps.takeIf { it > 0f })))
                            .takeLast(36)
                        val uWhPerTok = if (finalTokens > 0 && finalEnergy > 0f) finalEnergy / finalTokens else prev.energyPerTokenMicroWh
                        val tokPerMwh = if (uWhPerTok > 0f) 1000f / uWhPerTok else prev.tokensPerMilliwattHour

                        _sessionTpsSnapshot.value = TpsTelemetrySnapshot(
                            phase = InferenceTelemetryPhase.COMPLETED,
                            instantTps = finalInstantTps.takeIf { it > 0f } ?: finalTps,
                            decodeTps = finalTps,
                            sessionAverageTps = nextAvgTps,
                            sessionPeakTps = maxOf(prev.sessionPeakTps, finalPeakTps, finalTps),
                            prefillTps = if (finalPrefillTps > 0f) finalPrefillTps else prev.prefillTps,
                            lastTtftMs = finalTtft,
                            averageTtftMs = nextAvgTtft,
                            interTokenLatencyMs = if (finalItlMs > 0f) finalItlMs else if (finalTps > 0f) 1000f / finalTps else prev.interTokenLatencyMs,
                            promptTokensCount = finalPromptTokens,
                            generatedTokens = finalTokens,
                            totalSessionTokens = _totalSessionTokens.value,
                            energyMicroWh = finalEnergy,
                            energyPerTokenMicroWh = uWhPerTok,
                            tokensPerMilliwattHour = tokPerMwh,
                            tpsHistory = nextHistory,
                            completedRunsCount = nextRuns
                        )
                    }

                    val mountedPath = inferenceEngine.getMountedModelPath()
                    if (mountedPath != null && mountedPath == File(model.localFilePath).absolutePath) {
                        _mountedModelId.value = model.id
                    }

                    refreshTelemetry()

                    if (finalTps > 0f || finalRuntimeQuant != model.quantization) {
                        repository.updateLocalModel(
                            model.copy(
                                expectedTps = if (finalTps > 0f) finalTps else model.expectedTps,
                                quantization = finalRuntimeQuant
                            )
                        )
                    }

                    val contentToPersist = when {
                        finalStepText.isNotBlank() && wasCancelled ->
                            "${finalStepText.trimEnd().take(MAX_ASSISTANT_REPLY_CHARS)}\n\n_[Generation stopped by user]_"
                        finalStepText.isNotBlank() ->
                            finalStepText.take(MAX_ASSISTANT_REPLY_CHARS)
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
                                modelName = finalRuntimeDesc,
                                quantization = finalRuntimeQuant,
                                powerProfileId = powerProfile.id,
                                tokensPerSec = finalTps,
                                ttftMs = finalTtft,
                                tokenCount = finalTokens,
                                energyMicroWh = finalEnergy,
                                citedRagTitles = finalCitations.joinToString("|"),
                                branchNote = "${runtime.displayName} • $finalRuntimeQuant$noteSuffix"
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
        quantization: String,
        instantTps: Float = tps,
        peakTps: Float = tps,
        interTokenLatencyMs: Float = if (tps > 0f) 1000f / tps else 0f,
        prefillTps: Float = 0f,
        promptTokensCount: Int = 0,
        runtimeBackendLabel: String = "",
        runtimeModelDesc: String = "",
        phase: InferenceTelemetryPhase = InferenceTelemetryPhase.DECODING,
        tpsSeries: List<Float> = emptyList()
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
        quantization = quantization,
        instantTps = instantTps,
        peakTps = peakTps,
        interTokenLatencyMs = interTokenLatencyMs,
        prefillTps = prefillTps,
        promptTokensCount = promptTokensCount,
        runtimeBackendLabel = runtimeBackendLabel,
        runtimeModelDesc = runtimeModelDesc,
        phase = phase,
        tpsSeries = tpsSeries
    )

    fun stopStreaming() {
        streamingJob?.cancel()
        inferenceEngine.stopInference()
        _streamingPreview.value = null
    }

    fun deleteDownloadedModel(model: LocalModelEntity) {
        viewModelScope.launch {
            if (uiState.value.activeModel?.id == model.id || _selectedModelId.value == model.id || _mountedModelId.value == model.id) {
                stopStreaming()
                inferenceEngine.releaseMountedResources()
                _mountedModelId.value = null
                setRuntimeAndModelPrefs(runtime = _selectedRuntime.value, modelId = null)
            }
            fileManager.deleteModelFile(model.localFilePath)
            repository.deleteLocalModel(model.id)
            showTransientBanner("Removed local model '${model.name}' (${model.sizeMb} MB).")
        }
    }

    /**
     * Loads a local model file from a user-selected Android Uri (Storage Access Framework),
     * either keeping it in internal shared storage or copying it into app storage,
     * validates its binary header, and registers it for offline inference.
     */
    fun importLocalModelFromUri(uri: Uri?, copyToAppStorage: Boolean = true) {
        if (uri == null || _isImportingModel.value) return

        viewModelScope.launch {
            _isImportingModel.value = true
            _importProgress.value = 0.01f
            _importStatusText.value = if (copyToAppStorage) {
                "Copying model file to app storage…"
            } else {
                "Verifying model file in internal shared storage…"
            }

            fileManager.importModelFromUri(uri, copyToAppStorage = copyToAppStorage).collect { status ->
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
        val accessibleFile = fileManager.ensureAccessibleFile(file)
        val fileBytes = fileManager.getModelFileLength(accessibleFile)
        if (!fileManager.isModelFileAvailable(accessibleFile) || fileBytes < 8L) {
            if (!silentInvalid) {
                showTransientBanner("Selected file '$displayName' could not be read or is too small.")
            }
            return@withContext null
        }

        if (fileManager.isCompanionTokenizerFile(displayName) || fileManager.isCompanionTokenizerFile(accessibleFile.name)) {
            if (!silentInvalid) {
                showTransientBanner("Imported companion tokenizer '${displayName}' (${fileBytes / 1024} KB) for ExecuTorch .pte models.")
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

        // Route by real binary header inspection, not filename guessing
        val detectedRuntime = when {
            pteHeader.isValidPte || lower.endsWith(".pte") -> AiRuntime.EXECUTORCH
            tmacHeader.isValidTmac || lower.endsWith(".tmac") -> AiRuntime.T_MAC
            lower.endsWith(".bitnet") -> AiRuntime.BITNET_CPP
            else -> AiRuntime.LLAMA_CPP
        }

        // Read quantization directly from the parsed file header metadata
        val detectedQuant = when {
            ggufHeader.isValidGguf -> ggufHeader.quantizationLabel
            tmacHeader.isValidTmac -> tmacHeader.formatType
            pteHeader.isValidPte -> "PTE (${pteHeader.magicString.trim()})"
            else -> "Unknown"
        }

        val detectedCtxWindow = ggufHeader.contextLength?.takeIf { it in 512..131072 } ?: 4096

        val bytes = fileBytes
        val sizeMb = (bytes / (1024L * 1024L)).toInt().coerceAtLeast(1)
        val shaHex = precomputedSha.ifBlank { fileManager.computeFileSha256Short(accessibleFile) }

        val headerSummary = when {
            ggufHeader.isValidGguf -> buildString {
                append("Verified GGUF v${ggufHeader.version}")
                ggufHeader.architecture?.let { append(" • arch=$it") }
                append(" • quant=${ggufHeader.quantizationLabel}")
                append(" • ${ggufHeader.tensorCount} tensors • ${ggufHeader.metadataKvCount} KV pairs")
            }
            pteHeader.isValidPte -> "Verified ExecuTorch PTE binary (${pteHeader.magicString.trim()}) • ${bytes / 1024} KB"
            tmacHeader.isValidTmac -> "Verified T-MAC ${tmacHeader.formatType} binary • ${tmacHeader.tensorCount} LUT tensors"
            else -> "Local model binary ($displayName) • ${bytes / 1024} KB"
        }

        val cleanTitle = (ggufHeader.modelName?.takeIf { it.isNotBlank() } ?: displayName)
            .removeSuffix(".gguf")
            .removeSuffix(".GGUF")
            .removeSuffix(".pte")
            .removeSuffix(".PTE")
            .removeSuffix(".tmac")
            .removeSuffix(".bitnet")
            .removeSuffix(".bin")
            .trim()
            .ifBlank { displayName }

        val modelId = "local-${shaHex.ifBlank { displayName.hashCode().toUInt().toString(16) }}"
        val pathLabel = if (accessibleFile.path.startsWith("/proc/self/fd/")) {
            "$displayName (Shared Storage FD)"
        } else {
            accessibleFile.name
        }
        val customModel = LocalModelEntity(
            id = modelId,
            name = cleanTitle,
            family = ggufHeader.architecture ?: "Local Device Storage",
            runtimeId = detectedRuntime.id,
            quantization = detectedQuant,
            parameters = "${sizeMb} MB",
            sizeMb = sizeMb,
            ramRequiredMb = (sizeMb * 1.25f).toInt().coerceAtLeast(64),
            contextWindow = detectedCtxWindow,
            expectedTps = 0f,
            ecoRating = 5,
            specialty = "$headerSummary • Path: $pathLabel",
            isInstalled = true,
            isUserImported = true,
            localFilePath = accessibleFile.path,
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
        val trimmedTitle = title.trim().take(MAX_RAG_TITLE_CHARS)
        val rawContent = content.trim()
        if (trimmedTitle.isBlank() || rawContent.isBlank()) return
        val wasTruncated = rawContent.length > MAX_RAG_CONTENT_CHARS
        val boundedContent = rawContent.take(MAX_RAG_CONTENT_CHARS)

        viewModelScope.launch {
            val paragraphs = boundedContent.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }.size.coerceAtLeast(1)
            val tokens = (boundedContent.split(Regex("\\s+")).size * 1.3f).toInt().coerceAtLeast(1)
            repository.insertRagDocument(
                RagDocumentEntity(
                    title = trimmedTitle,
                    category = category.trim().ifBlank { "Local Note" }.take(64),
                    content = boundedContent,
                    chunkCount = paragraphs,
                    tokenEstimate = tokens,
                    isPinnedToChat = true
                )
            )
            val truncNotice = if (wasTruncated) " (capped at ${MAX_RAG_CONTENT_CHARS} chars)" else ""
            showTransientBanner("Indexed '$trimmedTitle'$truncNotice ($paragraphs chunks, ~$tokens tokens) locally.")
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

    // Preset Actions
    fun saveCustomPersona(
        name: String,
        tagline: String,
        systemPrompt: String,
        temperature: Float,
        topP: Float
    ) {
        saveOrUpdatePreset(
            PersonaPresetEntity(
                id = 0L,
                name = name,
                iconTag = "psychology",
                tagline = tagline,
                systemPrompt = systemPrompt,
                temperature = temperature,
                topP = topP,
                isBuiltIn = false
            )
        )
    }

    fun saveOrUpdatePreset(preset: PersonaPresetEntity) {
        val cleanName = preset.name.trim().take(80)
        val cleanSystemPrompt = preset.systemPrompt.trim().take(MAX_PERSONA_PROMPT_CHARS)
        if (cleanName.isBlank()) return
        val normalized = preset.copy(
            name = cleanName,
            tagline = preset.tagline.trim().ifBlank { "Custom llama.cpp preset" }.take(120),
            systemPrompt = cleanSystemPrompt,
            temperature = preset.temperature.coerceIn(0f, 2f),
            topK = preset.topK.coerceIn(0, 500),
            topP = preset.topP.coerceIn(0.01f, 1.0f),
            minP = preset.minP.coerceIn(0.0f, 1.0f),
            xtcThreshold = preset.xtcThreshold.coerceIn(0.0f, 1.0f),
            xtcProbability = preset.xtcProbability.coerceIn(0.0f, 1.0f),
            typicalP = preset.typicalP.coerceIn(0.0f, 1.0f),
            penaltyLastN = preset.penaltyLastN.coerceIn(-1, 2048),
            repeatPenalty = preset.repeatPenalty.coerceIn(0.5f, 2.5f),
            penaltyFreq = preset.penaltyFreq.coerceIn(0.0f, 2.0f),
            penaltyPresent = preset.penaltyPresent.coerceIn(0.0f, 2.0f),
            mirostat = preset.mirostat.coerceIn(0, 2),
            mirostatTau = preset.mirostatTau.coerceIn(0.1f, 10.0f),
            mirostatEta = preset.mirostatEta.coerceIn(0.01f, 1.0f)
        )
        viewModelScope.launch {
            val existing = uiState.value.personas.any { it.id == normalized.id && normalized.id > 0L }
            val activeId = if (existing) {
                repository.updatePersona(normalized)
                normalized.id
            } else {
                repository.insertPersona(normalized.copy(id = 0L, isBuiltIn = false))
            }
            val conv = uiState.value.activeConversation
            if (conv != null) {
                repository.updateConversation(conv.copy(activePersonaId = activeId))
            }
            showTransientBanner(
                if (existing) "Updated preset '$cleanName'" else "Saved & activated preset '$cleanName'"
            )
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
            showTransientBanner("Deleted custom preset '${persona.name}'")
        }
    }

    fun toggleSpeakMessage(messageId: Long, content: String) {
        val cleanSpokenContent = stripThinkingTagsForHistory(content).ifBlank { content }
        voiceHelper.toggleSpeak(messageId, cleanSpokenContent)
    }

    fun wipeAllOfflineData() {
        stopStreaming()
        viewModelScope.launch {
            repository.wipeAllHistory()
            _sessionTpsSnapshot.value = TpsTelemetrySnapshot()
            _totalSessionTokens.value = 0
            val newId = repository.createConversation(
                title = "Local AI Session",
                modelId = uiState.value.activeModel?.id ?: "",
                runtimeId = uiState.value.activeRuntime.id,
                personaId = 1L
            )
            setActiveConversationPref(newId)
            refreshTelemetry()
            showTransientBanner("Local chat database wiped & reset to clean offline state.")
        }
    }

    fun refreshTelemetry() {
        viewModelScope.launch(Dispatchers.IO) {
            _telemetry.value = telemetryManager.sampleTelemetry()
        }
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
        private const val KEY_USE_GRADIENT_TEXT = "use_gradient_text"
        private const val KEY_SHOW_CHAT_STATUS_BAR = "show_chat_status_bar"
        private const val KEY_ENABLE_THINKING = "enable_thinking"
        private const val KEY_N_PREDICT_UNLIMITED = "n_predict_unlimited"
        private const val KEY_CUSTOM_N_PREDICT = "custom_n_predict"
        private const val KEY_CONTEXT_SIZE = "context_size"
        private const val KEY_INCLUDE_THINKING_IN_CONTEXT = "include_thinking_in_context"
        private const val KEY_KV_CACHE_TYPE = "kv_cache_type"
        private const val MAX_PROMPT_CHARS = 16_000
        private const val MAX_ASSISTANT_REPLY_CHARS = 32_000
        private const val MAX_RAG_CONTENT_CHARS = 24_000
        private const val MAX_RAG_TITLE_CHARS = 160
        private const val MAX_PERSONA_PROMPT_CHARS = 4_000
        private const val STREAM_PREVIEW_MIN_INTERVAL_MS = 32L

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

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

private fun <A, B, C, D, E> quintuple(a: A, b: B, c: C, d: D, e: E) =
    Quintuple(a, b, c, d, e)
