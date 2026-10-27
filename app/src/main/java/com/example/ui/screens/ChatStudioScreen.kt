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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.ui.components.MarkdownText
import com.example.ui.components.runtimeAccentColor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.BranchedMessageItem
import com.example.viewmodel.LeafyUiState
import com.example.viewmodel.StreamingPreviewState

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
    var editingMessageNode by remember { mutableStateOf<MessageNodeEntity?>(null) }
    var editDraftText by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    LaunchedEffect(uiState.activeBranchMessages.size, uiState.streamingPreview?.partialText) {
        val totalItems = uiState.activeBranchMessages.size + (if (uiState.streamingPreview != null) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    // Real Android SpeechRecognizer Launcher (no fake fallback popup)
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

    Column(modifier = modifier.fillMaxSize()) {
        // Status bar showing active runtime, persona, pinned RAG count & battery telemetry
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onOpenSpecialMenu)
                    .testTag("chat_context_whisper_pill"),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EnergySavingsLeaf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${uiState.activeRuntime.displayName} • ${uiState.activePersona?.name ?: "Leafy Assistant"} • ${uiState.powerProfile.displayName} • ${uiState.pinnedRagDocuments.size} RAG pinned • ${uiState.telemetry.batteryPercent}%",
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.activeModel == null) {
                item(key = "no_model_warning_banner") {
                    val hasOtherLoadedModels = uiState.localModels.isNotEmpty()
                    val requiredExt = uiState.activeRuntime.fileExtensions.joinToString(" or ")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("no_model_banner"),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (hasOtherLoadedModels) {
                                            "No ${uiState.activeRuntime.fileExtensions.first()} Model Loaded for ${uiState.activeRuntime.displayName}"
                                        } else {
                                            "No Local AI Model Loaded"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = if (hasOtherLoadedModels) {
                                            "${uiState.activeRuntime.displayName} requires $requiredExt weights, while your loaded model '${uiState.localModels.first().name}' is ${uiState.localModels.first().quantization}. Load a $requiredExt file or switch back to a compatible runtime."
                                        } else {
                                            "Load a local model weight file (.gguf, .pte, .tmac, or .bitnet) from your device storage to run 100% offline inference."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onNavigateToModels,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("load_model_cta_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (hasOtherLoadedModels) "Load ${uiState.activeRuntime.fileExtensions.first()} File" else "Load Local Model from Device")
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
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "${uiState.activeModel.name} Loaded",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
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
                        editingMessageNode = branchedItem.node
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

        // Floating Organic Capsule Composer Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onOpenSpecialMenu,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
                            .testTag("composer_special_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Open power mode & persona controls",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
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
                            .size(42.dp)
                            .clip(CircleShape)
                            .testTag("voice_dictation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Offline voice input",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            if (promptInput.isNotBlank()) {
                                onSendPrompt(promptInput)
                                promptInput = ""
                            }
                        },
                        enabled = promptInput.isNotBlank() && uiState.streamingPreview == null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (promptInput.isNotBlank() && uiState.streamingPreview == null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            )
                            .testTag("send_prompt_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send prompt",
                            tint = if (promptInput.isNotBlank() && uiState.streamingPreview == null) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    editingMessageNode?.let { targetNode ->
        AlertDialog(
            onDismissRequest = { editingMessageNode = null },
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
                    Text("Edit & Fork Conversation Branch")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        editingMessageNode = null
                    },
                    modifier = Modifier.testTag("confirm_fork_branch_button")
                ) {
                    Text("Fork & Generate")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessageNode = null }) {
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
    var showTelemetryAndRagDetails by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (item.totalSiblings > 1) {
            Surface(
                modifier = Modifier.padding(bottom = 5.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            val prevIdx = (item.currentSiblingIndex - 1 + item.totalSiblings) % item.totalSiblings
                            onSwitchBranch(item.siblingIds[prevIdx])
                        },
                        modifier = Modifier
                            .size(22.dp)
                            .testTag("branch_prev_${node.id}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous branch",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = "${item.currentSiblingIndex + 1}/${item.totalSiblings}" +
                            if (node.branchNote.isNotBlank()) " • ${node.branchNote}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = {
                            val nextIdx = (item.currentSiblingIndex + 1) % item.totalSiblings
                            onSwitchBranch(item.siblingIds[nextIdx])
                        },
                        modifier = Modifier
                            .size(22.dp)
                            .testTag("branch_next_${node.id}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next branch",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.widthIn(max = 540.dp),
            shape = RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 24.dp,
                bottomStart = if (isUser) 24.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 24.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 1.dp else 2.dp),
            border = if (!isUser) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
            } else null
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MarkdownText(
                    markdown = node.content,
                    isUser = isUser
                )

                AnimatedVisibility(visible = !isUser && showTelemetryAndRagDetails) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Engine: ${runtime.displayName} (${node.quantization}) • Model: ${node.modelName}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = runtimeAccentColor(runtime)
                            )
                            Text(
                                text = "Speed: ${"%.1f".format(node.tokensPerSec)} t/s • TTFT: ${node.ttftMs} ms • Energy: ${"%.0f".format(node.energyMicroWh)} µWh",
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
                                        modifier = Modifier.size(12.dp)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onEditClick)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("edit_branch_user_${node.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit and branch",
                                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Edit & Branch",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
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
                                .clip(RoundedCornerShape(50))
                                .clickable { showTelemetryAndRagDetails = !showTelemetryAndRagDetails }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(runtimeAccentColor(runtime))
                            )
                            Text(
                                text = "${runtime.displayName} • ${"%.1f".format(node.tokensPerSec)} t/s",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (citedTitles.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "RAG cited",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Toggle telemetry details",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable(onClick = onSpeakClick)
                                    .testTag("tts_speak_button_${node.id}"),
                                shape = RoundedCornerShape(50),
                                color = if (isSpeaking) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Speak response offline",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    if (isSpeaking) {
                                        Text(
                                            text = "Stop",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable(onClick = onRegenerateBranch)
                                    .testTag("fork_assistant_branch_${node.id}"),
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Fork branch response",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
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

@Composable
private fun StreamingAssistantCard(
    preview: StreamingPreviewState,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(1.5.dp, runtimeAccentColor(preview.runtime))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EnergySavingsLeaf,
                        contentDescription = null,
                        tint = runtimeAccentColor(preview.runtime),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${preview.runtime.displayName} • ${"%.1f".format(preview.liveTps)} t/s",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = runtimeAccentColor(preview.runtime)
                    )
                }

                TextButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop generation",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop")
                }
            }

            MarkdownText(
                markdown = preview.partialText,
                isUser = false
            )
        }
    }
}
