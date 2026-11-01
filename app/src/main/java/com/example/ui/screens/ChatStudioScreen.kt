package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.AiRuntime
import com.example.data.MessageNodeEntity
import com.example.engine.InferenceTelemetryPhase
import com.example.ui.components.MarkdownText
import com.example.ui.components.importantTextColor
import com.example.ui.components.importantTextStyle
import com.example.ui.components.livingBrightButtonBrush
import com.example.ui.components.livingBrightButtonContentColor
import com.example.ui.components.livingHeroCardBrush
import com.example.ui.components.livingMetricBoxBrush
import com.example.ui.components.livingPrimaryBubbleBrush
import com.example.ui.components.livingPrimaryBubbleContentColor
import com.example.ui.components.livingSecondaryButtonBrush
import com.example.ui.components.livingSecondaryHeroBrush
import com.example.ui.components.livingSurfaceCardBrush
import com.example.ui.components.runtimeAccentColor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.BranchedMessageItem
import com.example.viewmodel.LeafyUiState
import com.example.viewmodel.StreamingPreviewState

internal fun shouldAutoFollowStreamingTail(
    totalItemsCount: Int,
    lastVisibleItemIndex: Int,
    lastVisibleItemOffset: Int,
    lastVisibleItemSize: Int,
    viewportEndOffset: Int,
    slackPx: Int = 140
): Boolean {
    if (totalItemsCount <= 0 || lastVisibleItemIndex < 0) return true
    if (lastVisibleItemIndex < totalItemsCount - 2) return false
    if (lastVisibleItemIndex == totalItemsCount - 2) return true
    val itemBottom = lastVisibleItemOffset + lastVisibleItemSize
    val distanceToBottom = itemBottom - viewportEndOffset
    return distanceToBottom <= slackPx
}

@Composable
fun ChatStudioScreen(
    uiState: LeafyUiState,
    onSendPrompt: (String) -> Unit,
    onEditAndBranchMessage: (MessageNodeEntity, String) -> Unit,
    onRegenerateBranch: (MessageNodeEntity) -> Unit,
    onSwitchSiblingBranch: (Long) -> Unit,
    onToggleSpeak: (Long, String) -> Unit,
    onStopStreaming: () -> Unit,
    onOpenSpecialMenu: () -> Unit,
    onNavigateToModels: () -> Unit,
    onOpenRuntimeSwitcher: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var promptInput by rememberSaveable { mutableStateOf("") }
    var editingMessageId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editDraftText by rememberSaveable { mutableStateOf("") }
    val editingMessageNode = remember(editingMessageId, uiState.activeBranchMessages) {
        editingMessageId?.let { targetId ->
            uiState.activeBranchMessages.find { it.node.id == targetId }?.node
        }
    }

    val listState = rememberLazyListState()
    var userAnchoredToTail by rememberSaveable { mutableStateOf(true) }
    var previousMessageCount by rememberSaveable { mutableIntStateOf(uiState.activeBranchMessages.size) }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
            Triple(
                listState.isScrollInProgress,
                layoutInfo.totalItemsCount,
                if (lastVisible != null) {
                    shouldAutoFollowStreamingTail(
                        totalItemsCount = layoutInfo.totalItemsCount,
                        lastVisibleItemIndex = lastVisible.index,
                        lastVisibleItemOffset = lastVisible.offset,
                        lastVisibleItemSize = lastVisible.size,
                        viewportEndOffset = layoutInfo.viewportEndOffset
                    )
                } else {
                    true
                }
            )
        }.collect { (isUserScrolling, _, nearBottom) ->
            if (isUserScrolling) {
                userAnchoredToTail = nearBottom
            }
        }
    }

    val isStreaming = uiState.streamingPreview != null
    LaunchedEffect(uiState.activeBranchMessages.size, isStreaming) {
        val currentCount = uiState.activeBranchMessages.size
        val messageAdded = currentCount > previousMessageCount
        previousMessageCount = currentCount
        if (messageAdded && !isStreaming) {
            val lastRole = uiState.activeBranchMessages.lastOrNull()?.node?.role
            if (lastRole == "USER") {
                userAnchoredToTail = true
            }
        } else if (isStreaming && messageAdded) {
            userAnchoredToTail = true
        }

        val totalItems = listState.layoutInfo.totalItemsCount.takeIf { it > 0 }
            ?: (currentCount + (if (isStreaming) 1 else 0))
        if (totalItems > 0 && userAnchoredToTail && !listState.isScrollInProgress) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    LaunchedEffect(
        uiState.streamingPreview?.partialText?.length,
        uiState.streamingPreview?.generatedTokens,
        uiState.streamingPreview?.phase
    ) {
        if (uiState.streamingPreview != null && userAnchoredToTail && !listState.isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
            if (lastVisible != null && totalItems > 0) {
                if (lastVisible.index == totalItems - 1) {
                    val bottomOverflow = (lastVisible.offset + lastVisible.size) - layoutInfo.viewportEndOffset
                    if (bottomOverflow > 0) {
                        listState.scrollBy(bottomOverflow.toFloat())
                    }
                } else {
                    listState.scrollToItem(totalItems - 1)
                }
            }
        }
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenList = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val bestTranscript = spokenList?.firstOrNull()
            if (!bestTranscript.isNullOrBlank()) {
                promptInput = if (promptInput.isBlank()) bestTranscript else "$promptInput $bestTranscript"
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Leafy (Offline)…")
                }
                speechRecognizerLauncher.launch(intent)
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
        ) {
            // Compact status pill showing active runtime, live TPS readout, persona, pinned RAG count & battery telemetry
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenSpecialMenu)
                        .testTag("chat_context_whisper_pill"),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                ) {
                    Row(
                        modifier = Modifier
                            .background(brush = livingSecondaryButtonBrush())
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EnergySavingsLeaf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        val tpsSuffix = when {
                            uiState.streamingPreview != null && uiState.streamingPreview.phase == InferenceTelemetryPhase.PREFILLING ->
                                " • PREFILL (${if (uiState.streamingPreview.ttftMs > 0) "${uiState.streamingPreview.ttftMs}ms" else "not measured"})"
                            uiState.streamingPreview != null && uiState.streamingPreview.instantTps > 0f ->
                                " • ${"%.1f".format(uiState.streamingPreview.instantTps)} t/s"
                            uiState.tpsTelemetry.decodeTps > 0f ->
                                " • ${"%.1f".format(uiState.tpsTelemetry.decodeTps)} t/s"
                            else -> " • TPS: not measured"
                        }
                        val battLabel = if (uiState.telemetry.batteryPercent in 0..100) "${uiState.telemetry.batteryPercent}%" else "not measured"
                        Text(
                            text = "${uiState.activeRuntime.displayName}$tpsSuffix • ${uiState.activePersona?.name ?: "Leafy Assistant"} • ${uiState.powerProfile.displayName} • ${uiState.pinnedRagDocuments.size} RAG • $battLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Main Chat Canvas
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("chat_messages_list"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.conversations.isEmpty()) {
                    item(key = "empty_session_history_banner") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("empty_session_history_banner"),
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(brush = livingSurfaceCardBrush())
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EnergySavingsLeaf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "No offline conversation threads saved yet. Send a message below to start your first local session.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (uiState.activeModel == null) {
                    item(key = "no_model_warning_banner") {
                        val hasOtherLoadedModels = uiState.localModels.isNotEmpty()
                        val requiredExt = uiState.activeRuntime.fileExtensions.joinToString(" or ")
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("no_model_banner"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(brush = livingSecondaryHeroBrush())
                                    .padding(horizontal = 13.dp, vertical = 11.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (hasOtherLoadedModels) {
                                                "No ${uiState.activeRuntime.fileExtensions.first()} Model Loaded for ${uiState.activeRuntime.displayName}"
                                            } else {
                                                "No Local AI Model Loaded"
                                            },
                                            style = importantTextStyle(MaterialTheme.typography.titleMedium),
                                            color = importantTextColor(MaterialTheme.colorScheme.onSecondaryContainer)
                                        )
                                        Text(
                                            text = if (hasOtherLoadedModels) {
                                                "${uiState.activeRuntime.displayName} requires $requiredExt weights, while your loaded model '${uiState.localModels.first().name}' is ${uiState.localModels.first().quantization}. Load a $requiredExt file or switch back to a compatible runtime."
                                            } else {
                                                "Load a local model weight file (.gguf, .pte, .tmac, or .bitnet) from your device storage to run 100% offline inference."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.90f)
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = onNavigateToModels,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Transparent,
                                            contentColor = livingBrightButtonContentColor()
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(50))
                                            .background(brush = livingBrightButtonBrush())
                                            .testTag("load_model_cta_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = if (hasOtherLoadedModels) "Load ${uiState.activeRuntime.fileExtensions.first()} File" else "Load Local Model from Device",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                    if (hasOtherLoadedModels) {
                                        TextButton(
                                            onClick = onOpenRuntimeSwitcher,
                                            modifier = Modifier.testTag("switch_compatible_runtime_button")
                                        ) {
                                            Text("Switch Runtime")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (uiState.activeBranchMessages.isEmpty()) {
                    item(key = "model_ready_welcome_card") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("model_ready_card"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(brush = livingHeroCardBrush())
                                    .padding(horizontal = 13.dp, vertical = 11.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "${uiState.activeModel.name} Loaded",
                                        style = importantTextStyle(MaterialTheme.typography.titleMedium),
                                        color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                                    )
                                }
                                Text(
                                    text = "Local weights (${uiState.activeModel.quantization}, ${uiState.activeModel.sizeMb} MB) are mounted for ${uiState.activeRuntime.displayName}. Type a message below to start offline inference.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                items(
                    items = uiState.activeBranchMessages,
                    key = { it.node.id }
                ) { branchedItem ->
                    BranchedMessageBubble(
                        item = branchedItem,
                        isSpeaking = uiState.speakingMessageId == branchedItem.node.id,
                        onEditClick = {
                            editingMessageId = branchedItem.node.id
                            editDraftText = branchedItem.node.content
                        },
                        onRegenerateBranch = { onRegenerateBranch(branchedItem.node) },
                        onSwitchBranch = onSwitchSiblingBranch,
                        onSpeakClick = {
                            onToggleSpeak(branchedItem.node.id, branchedItem.node.content)
                        }
                    )
                }

                uiState.streamingPreview?.let { preview ->
                    item(key = "streaming_preview") {
                        StreamingAssistantCard(
                            preview = preview,
                            onStop = onStopStreaming
                        )
                    }
                }
            }

            // Floating Organic Capsule Composer Bar (with Living Gradient Surface)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    color = Color.Transparent,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(brush = livingSurfaceCardBrush())
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        IconButton(
                            onClick = onOpenSpecialMenu,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("composer_special_menu_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(brush = livingSecondaryButtonBrush()),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Open power mode & persona controls",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = promptInput,
                            onValueChange = { promptInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_prompt_input"),
                            placeholder = {
                                Text(
                                    text = "Message ${uiState.activeModel?.name ?: "Leafy"} offline…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )

                        IconButton(
                            onClick = {
                                val hasAudioPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasAudioPerm) {
                                    try {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(
                                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                            )
                                            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                                        }
                                        speechRecognizerLauncher.launch(intent)
                                    } catch (_: Exception) {}
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .testTag("voice_dictation_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Offline voice input",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        val canSend = promptInput.isNotBlank() && uiState.streamingPreview == null
                        IconButton(
                            onClick = {
                                if (promptInput.isNotBlank()) {
                                    onSendPrompt(promptInput)
                                    promptInput = ""
                                }
                            },
                            enabled = canSend,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("send_prompt_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (canSend) {
                                            Modifier.background(brush = livingBrightButtonBrush())
                                        } else {
                                            Modifier.background(brush = livingSecondaryButtonBrush())
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send prompt",
                                    tint = if (canSend) {
                                        livingBrightButtonContentColor()
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                    },
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editingMessageNode?.let { targetNode ->
        AlertDialog(
            onDismissRequest = { editingMessageId = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CallSplit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Edit & Fork Conversation Branch",
                        style = importantTextStyle(MaterialTheme.typography.titleMedium),
                        color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Editing this prompt forks a non-destructive branch using ${uiState.activeRuntime.displayName} (${uiState.activeModel?.name ?: "No model"}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editDraftText,
                        onValueChange = { editDraftText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("branch_edit_text_field"),
                        minLines = 3,
                        maxLines = 6,
                        label = { Text("Prompt for new branch") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEditAndBranchMessage(targetNode, editDraftText)
                        editingMessageId = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = livingBrightButtonContentColor()
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(brush = livingBrightButtonBrush())
                        .testTag("confirm_fork_branch_button")
                ) {
                    Text("Fork & Generate")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessageId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun BranchedMessageBubble(
    item: BranchedMessageItem,
    isSpeaking: Boolean,
    onEditClick: () -> Unit,
    onRegenerateBranch: () -> Unit,
    onSwitchBranch: (Long) -> Unit,
    onSpeakClick: () -> Unit
) {
    val node = item.node
    val isUser = node.role == "USER"
    val runtime = AiRuntime.fromId(node.runtimeId)
    val citedTitles = remember(node.citedRagTitles) {
        node.citedRagTitles.split("|").filter { it.isNotBlank() }
    }
    var showTelemetryAndRagDetails by rememberSaveable(node.id) { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val bubbleMaxWidth = minOf(maxWidth * 0.86f, 560.dp).coerceAtLeast(240.dp)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (item.totalSiblings > 1) {
                Surface(
                    modifier = Modifier.padding(bottom = 3.dp),
                    shape = RoundedCornerShape(50),
                    color = Color.Transparent,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .background(brush = livingSecondaryButtonBrush())
                            .padding(horizontal = 4.dp, vertical = 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val prevIdx = (item.currentSiblingIndex - 1 + item.totalSiblings) % item.totalSiblings
                                onSwitchBranch(item.siblingIds[prevIdx])
                            },
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("branch_prev_${node.id}")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous branch",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = "${item.currentSiblingIndex + 1}/${item.totalSiblings}" +
                                if (node.branchNote.isNotBlank()) " • ${node.branchNote}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = {
                                val nextIdx = (item.currentSiblingIndex + 1) % item.totalSiblings
                                onSwitchBranch(item.siblingIds[nextIdx])
                            },
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("branch_next_${node.id}")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next branch",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.widthIn(max = bubbleMaxWidth),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (isUser) 20.dp else 5.dp,
                    bottomEnd = if (isUser) 5.dp else 20.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(
                    1.dp,
                    if (isUser) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            brush = if (isUser) {
                                livingPrimaryBubbleBrush()
                            } else {
                                livingSurfaceCardBrush()
                            }
                        )
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    MarkdownText(
                        markdown = node.content,
                        isUser = isUser
                    )

                    AnimatedVisibility(visible = !isUser && showTelemetryAndRagDetails) {
                        val decodeLabel = if (node.tokensPerSec > 0f) "${"%.1f".format(node.tokensPerSec)} t/s" else "not measured"
                        val itlLabel = if (node.tokensPerSec > 0f) "${"%.1f".format(1000f / node.tokensPerSec)} ms/tok" else "not measured"
                        val ttftLabel = if (node.ttftMs > 0) "${node.ttftMs} ms" else "not measured"
                        val tokensLabel = if (node.tokenCount > 0) "${node.tokenCount} tok" else "not measured"
                        val energyLabel = if (node.energyMicroWh > 0f) {
                            val uWhPerTok = if (node.tokenCount > 0) node.energyMicroWh / node.tokenCount else 0f
                            "${"%.1f".format(node.energyMicroWh)} µWh (${"%.2f".format(uWhPerTok)} µWh/tok)"
                        } else {
                            "not measured"
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Transparent,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(brush = livingMetricBoxBrush())
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "Engine: ${runtime.displayName} (${node.quantization.ifBlank { "not measured" }}) • Model: ${node.modelName.ifBlank { "not measured" }}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = runtimeAccentColor(runtime)
                                )
                                Text(
                                    text = "Decode: $decodeLabel • ITL: $itlLabel • TTFT: $ttftLabel",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tokens: $tokensLabel • Energy: $energyLabel • Mode: ${node.powerProfileId}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (citedTitles.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "RAG Sources: ${citedTitles.joinToString(", ")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isUser) {
                        val userBubbleTextColor = livingPrimaryBubbleContentColor()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Row(
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 32.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable(onClick = onEditClick)
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                                    .testTag("edit_branch_user_${node.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit and branch",
                                    tint = userBubbleTextColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "Edit & Branch",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = userBubbleTextColor.copy(alpha = 0.88f)
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(end = 4.dp)
                                    .sizeIn(minWidth = 48.dp, minHeight = 32.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable { showTelemetryAndRagDetails = !showTelemetryAndRagDetails }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(runtimeAccentColor(runtime))
                                )
                                val nodeTpsText = if (node.tokensPerSec > 0f) {
                                    "${"%.1f".format(node.tokensPerSec)} t/s"
                                } else {
                                    "not measured"
                                }
                                Text(
                                    text = "${runtime.displayName} • $nodeTpsText" +
                                        if (node.tokenCount > 0) " • ${node.tokenCount} tok" else "",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (citedTitles.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "RAG cited",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Toggle telemetry details",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .sizeIn(minWidth = 48.dp, minHeight = 32.dp)
                                        .clip(RoundedCornerShape(50))
                                        .clickable(onClick = onSpeakClick)
                                        .testTag("tts_speak_button_${node.id}"),
                                    shape = RoundedCornerShape(50),
                                    color = Color.Transparent,
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .background(
                                                brush = if (isSpeaking) {
                                                    livingBrightButtonBrush()
                                                } else {
                                                    livingSecondaryButtonBrush()
                                                }
                                            )
                                            .padding(horizontal = 9.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Speak response offline",
                                            tint = if (isSpeaking) livingBrightButtonContentColor() else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        if (isSpeaking) {
                                            Text(
                                                text = "Stop",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = livingBrightButtonContentColor()
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .sizeIn(minWidth = 48.dp, minHeight = 32.dp)
                                        .clip(RoundedCornerShape(50))
                                        .clickable(onClick = onRegenerateBranch)
                                        .testTag("fork_assistant_branch_${node.id}"),
                                    shape = RoundedCornerShape(50),
                                    color = Color.Transparent,
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .background(brush = livingSecondaryButtonBrush())
                                            .padding(horizontal = 9.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Fork branch response",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Branch",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingAssistantCard(
    preview: StreamingPreviewState,
    onStop: () -> Unit
) {
    val accent = runtimeAccentColor(preview.runtime)
    val isPrefilling = preview.phase == InferenceTelemetryPhase.PREFILLING

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_streaming_telemetry_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = BorderStroke(1.5.dp, accent)
    ) {
        Column(
            modifier = Modifier
                .background(brush = livingSurfaceCardBrush())
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Top Header: Runtime, Phase Pill, Live Instant/Decode TPS & Stop Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accent.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isPrefilling) Icons.Default.Bolt else Icons.Default.Speed,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (isPrefilling) "PREFILL" else "LIVE TPS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = accent
                            )
                        }
                    }

                    val backendTitle = preview.runtimeBackendLabel.ifBlank { preview.runtime.displayName }
                    val promptTokText = if (preview.promptTokensCount > 0) "${preview.promptTokensCount} prompt tok" else "prompt tok: not measured"
                    val decodeHeaderText = if (preview.instantTps > 0f || preview.liveTps > 0f) {
                        val inst = if (preview.instantTps > 0f) "${"%.1f".format(preview.instantTps)} t/s" else "not measured"
                        val avg = if (preview.liveTps > 0f) "${"%.1f".format(preview.liveTps)} t/s" else "not measured"
                        "$backendTitle • $inst (Decode: $avg)"
                    } else {
                        "$backendTitle • Decode: not measured"
                    }
                    Text(
                        text = if (isPrefilling) {
                            "$backendTitle • $promptTokText"
                        } else {
                            decodeHeaderText
                        },
                        style = importantTextStyle(
                            MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                        ),
                        color = importantTextColor(accent),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextButton(
                    onClick = onStop,
                    modifier = Modifier.testTag("stop_streaming_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop generation",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Stop")
                }
            }

            // Live Telemetry HUD Strip
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
                color = Color.Transparent,
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(brush = livingMetricBoxBrush())
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TTFT: ${if (preview.ttftMs > 0) "${preview.ttftMs} ms" else "not measured"}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "ITL: ${if (preview.interTokenLatencyMs > 0f) "${"%.1f".format(preview.interTokenLatencyMs)} ms/tok" else "not measured"}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Out: ${preview.generatedTokens} tok",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (preview.energyMicroWh > 0f) "${"%.1f".format(preview.energyMicroWh)} µWh" else "Energy: not measured",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (preview.partialText.isBlank() && isPrefilling) {
                val promptCountLabel = if (preview.promptTokensCount > 0) "${preview.promptTokensCount} native tokens" else "not measured"
                Text(
                    text = "Evaluating prompt context ($promptCountLabel) into ${preview.runtimeBackendLabel.ifBlank { preview.runtime.displayName }} (${preview.quantization}) KV cache…",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                MarkdownText(
                    markdown = preview.partialText,
                    isUser = false
                )
            }
        }
    }
}
