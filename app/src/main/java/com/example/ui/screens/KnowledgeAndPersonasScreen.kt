package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.example.data.PersonaPresetEntity
import com.example.data.RagDocumentEntity
import com.example.ui.components.importantTextColor
import com.example.ui.components.importantTextStyle
import com.example.ui.components.livingActiveItemBrush
import com.example.ui.components.livingBrightButtonBrush
import com.example.ui.components.livingBrightButtonContentColor
import com.example.ui.components.livingMetricBoxBrush
import com.example.ui.components.livingSecondaryButtonBrush
import com.example.ui.components.livingSurfaceCardBrush
import com.example.ui.components.livingTertiaryHeroBrush
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.LeafyUiState

import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.mutableIntStateOf

@Composable
fun KnowledgeAndPersonasScreen(
    uiState: LeafyUiState,
    onTogglePinDoc: (RagDocumentEntity) -> Unit,
    onAddRagDoc: (String, String, String) -> Unit,
    onImportRagUri: (Uri?) -> Unit,
    onDeleteRagDoc: (Long) -> Unit,
    onSelectPersona: (PersonaPresetEntity) -> Unit,
    onSaveCustomPersona: (String, String, String, Float, Float) -> Unit,
    onSaveOrUpdatePreset: (PersonaPresetEntity) -> Unit = { preset ->
        onSaveCustomPersona(preset.name, preset.tagline, preset.systemPrompt, preset.temperature, preset.topP)
    },
    onDeleteCustomPersona: (PersonaPresetEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showAddDocDialog by rememberSaveable { mutableStateOf(false) }
    var editingPreset by rememberSaveable { mutableStateOf<Long?>(null) }
    var showNewPresetDialog by rememberSaveable { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportRagUri(uri)
        }
    }

    val totalPinnedTokens = uiState.pinnedRagDocuments.sumOf { it.tokenEstimate }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("knowledge_personas_list"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // 1. Local Document RAG & Context Pinning Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(brush = livingTertiaryHeroBrush())
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(19.dp)
                                )
                                Text(
                                    text = "On-Device RAG & Context Pinning",
                                    style = importantTextStyle(MaterialTheme.typography.titleLarge),
                                    color = importantTextColor(MaterialTheme.colorScheme.onTertiaryContainer)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.Transparent,
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = "$totalPinnedTokens / ${uiState.powerProfile.contextLimit} tok pinned",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier
                                        .background(brush = livingMetricBoxBrush())
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = "Pin local Markdown or text notes directly into Leafy's context window. Passages are ranked offline via BM25 and cited under assistant answers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.90f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showAddDocDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = livingBrightButtonContentColor()
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(brush = livingBrightButtonBrush())
                                    .testTag("create_rag_doc_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "New RAG Note",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    try {
                                        filePickerLauncher.launch(arrayOf("text/*", "*/*"))
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "System file picker unavailable (${e.javaClass.simpleName}); opening manual RAG note editor.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        showAddDocDialog = true
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("import_rag_file_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Import File",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 2. RAG Documents List (or Empty State Card)
            if (uiState.ragDocuments.isEmpty()) {
                item(key = "empty_rag_documents") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("empty_rag_documents_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Transparent
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(brush = livingSurfaceCardBrush())
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = "No Local RAG Documents Indexed",
                                style = importantTextStyle(MaterialTheme.typography.titleMedium),
                                color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                            )
                            Text(
                                text = "Create a Markdown note or import a local .txt/.md file to pin offline knowledge into your conversation context.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { showAddDocDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("empty_rag_create_first_note_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Create First Note")
                            }
                        }
                    }
                }
            }

            items(
                items = uiState.ragDocuments,
                key = { "rag_${it.id}" }
            ) { doc ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rag_doc_card_${doc.id}"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    border = BorderStroke(
                        if (doc.isPinnedToChat) 1.5.dp else 1.dp,
                        if (doc.isPinnedToChat) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = if (doc.isPinnedToChat) {
                                    livingActiveItemBrush(MaterialTheme.colorScheme.tertiary)
                                } else {
                                    livingSurfaceCardBrush()
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = doc.title,
                                        style = importantTextStyle(MaterialTheme.typography.titleSmall),
                                        fontWeight = if (doc.isPinnedToChat) FontWeight.SemiBold else FontWeight.Medium,
                                        color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                                    )
                                    Text(
                                        text = "${doc.category} • ${doc.chunkCount} chunks • ~${doc.tokenEstimate} tokens",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = doc.isPinnedToChat,
                                    onCheckedChange = { onTogglePinDoc(doc) },
                                    modifier = Modifier.testTag("pin_switch_doc_${doc.id}")
                                )
                                IconButton(
                                    onClick = { onDeleteRagDoc(doc.id) },
                                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete document",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = doc.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 3. Chat Generation Settings (Presets) Section
            item {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                    ) {
                        Text(
                            text = "Chat Generation Presets",
                            style = importantTextStyle(MaterialTheme.typography.titleMedium),
                            color = importantTextColor(MaterialTheme.colorScheme.onBackground)
                        )
                        Text(
                            text = "Bundled llama.cpp sampler parameters & system prompts (llama.cpp Default active by default)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { showNewPresetDialog = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_custom_persona_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Custom Preset")
                    }
                }
            }

            items(
                items = uiState.personas,
                key = { "persona_${it.id}" }
            ) { persona ->
                val isSelected = uiState.activePersona?.id == persona.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("persona_card_${persona.id}"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = if (isSelected) {
                                    livingActiveItemBrush(MaterialTheme.colorScheme.primary)
                                } else {
                                    livingSurfaceCardBrush()
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonPin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(19.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = persona.name,
                                        style = importantTextStyle(MaterialTheme.typography.titleSmall),
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                                    )
                                    Text(
                                        text = persona.tagline,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { editingPreset = persona.id },
                                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("customize_preset_${persona.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Customize preset",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Customize")
                                }
                                Button(
                                    onClick = { onSelectPersona(persona) },
                                    enabled = !isSelected,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = livingBrightButtonContentColor(),
                                        disabledContainerColor = Color.Transparent,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            brush = if (isSelected) {
                                                livingSecondaryButtonBrush()
                                            } else {
                                                livingBrightButtonBrush()
                                            }
                                        )
                                        .testTag("activate_persona_${persona.id}")
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Active")
                                    } else {
                                        Text("Use Preset")
                                    }
                                }
                                if (!persona.isBuiltIn) {
                                    IconButton(
                                        onClick = { onDeleteCustomPersona(persona) },
                                        modifier = Modifier
                                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                            .testTag("delete_persona_${persona.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete custom preset",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Transparent,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                        ) {
                            Text(
                                text = if (persona.systemPrompt.isBlank()) {
                                    "(Blank preset — no system prompt instructions prepended)"
                                } else {
                                    "\"${persona.systemPrompt}\""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(brush = livingMetricBoxBrush())
                                    .padding(horizontal = 9.dp, vertical = 7.dp)
                            )
                        }

                        val mirostatLabel = when (persona.mirostat) {
                            1 -> "v1"
                            2 -> "v2"
                            else -> "Off"
                        }
                        Text(
                            text = "Temp=${"%.2f".format(persona.temperature)} • Top-K=${persona.topK} • Top-P=${"%.2f".format(persona.topP)} • Min-P=${"%.2f".format(persona.minP)} • Repeat=${"%.2f".format(persona.repeatPenalty)} (LastN=${persona.penaltyLastN}) • Mirostat=$mirostatLabel • Seed=${persona.seed} • Jinja=${if (persona.jinjaEnabled) "On" else "Off"}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (showAddDocDialog) {
        var docTitle by rememberSaveable { mutableStateOf("") }
        var docCategory by rememberSaveable { mutableStateOf("Research Note") }
        var docContent by rememberSaveable { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDocDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Add Local RAG Knowledge Document",
                    style = importantTextStyle(MaterialTheme.typography.titleMedium),
                    color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = docTitle,
                        onValueChange = { docTitle = it },
                        label = { Text("Document Title (e.g. Garden_Sensor_Spec.md)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_rag_title_input")
                    )
                    OutlinedTextField(
                        value = docCategory,
                        onValueChange = { docCategory = it },
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = docContent,
                        onValueChange = { docContent = it },
                        label = { Text("Markdown or Plaintext Knowledge") },
                        minLines = 4,
                        maxLines = 7,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_rag_content_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddRagDoc(docTitle, docCategory, docContent)
                        showAddDocDialog = false
                    },
                    enabled = docTitle.isNotBlank() && docContent.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = livingBrightButtonContentColor()
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(brush = livingBrightButtonBrush())
                        .testTag("confirm_save_rag_doc_button")
                ) {
                    Text("Index & Pin Locally")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDocDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val activeEditingPreset = editingPreset?.let { targetId ->
        uiState.personas.find { it.id == targetId }
    }
    if (showNewPresetDialog || activeEditingPreset != null) {
        val initialPreset = activeEditingPreset ?: PersonaPresetEntity(
            id = 0L,
            name = "",
            iconTag = "code",
            tagline = "Custom llama.cpp sampler preset",
            systemPrompt = "You are a concise, privacy-first local AI specialist.",
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
            jinjaEnabled = true,
            isBuiltIn = false
        )
        ChatGenerationPresetEditorDialog(
            initialPreset = initialPreset,
            isEditingExisting = activeEditingPreset != null,
            onDismiss = {
                showNewPresetDialog = false
                editingPreset = null
            },
            onSave = { updatedPreset ->
                onSaveOrUpdatePreset(updatedPreset)
                showNewPresetDialog = false
                editingPreset = null
            }
        )
    }
}

@Composable
private fun ChatGenerationPresetEditorDialog(
    initialPreset: PersonaPresetEntity,
    isEditingExisting: Boolean,
    onDismiss: () -> Unit,
    onSave: (PersonaPresetEntity) -> Unit
) {
    var name by rememberSaveable(initialPreset.id) { mutableStateOf(initialPreset.name) }
    var tagline by rememberSaveable(initialPreset.id) { mutableStateOf(initialPreset.tagline) }
    var systemPrompt by rememberSaveable(initialPreset.id) { mutableStateOf(initialPreset.systemPrompt) }

    var temperature by rememberSaveable(initialPreset.id) { mutableFloatStateOf(initialPreset.temperature) }
    var topK by rememberSaveable(initialPreset.id) { mutableIntStateOf(initialPreset.topK) }
    var topP by rememberSaveable(initialPreset.id) { mutableFloatStateOf(initialPreset.topP) }
    var minP by rememberSaveable(initialPreset.id) { mutableFloatStateOf(initialPreset.minP) }
    var xtcThreshold by rememberSaveable(initialPreset.id) { mutableFloatStateOf(initialPreset.xtcThreshold) }
    var xtcProbability by rememberSaveable(initialPreset.id) { mutableFloatStateOf(initialPreset.xtcProbability) }
    var typicalP by rememberSaveable(initialPreset.id) { mutableFloatStateOf(initialPreset.typicalP) }
    var penaltyLastN by rememberSaveable(initialPreset.id) { mutableIntStateOf(initialPreset.penaltyLastN) }
    var repeatPenalty by rememberSaveable(initialPreset.id) { mutableFloatStateOf(initialPreset.repeatPenalty) }
    var penaltyFreq by rememberSaveable(initialPreset.id) { mutableFloatStateOf(initialPreset.penaltyFreq) }
    var penaltyPresent by rememberSaveable(initialPreset.id) { mutableFloatStateOf(initialPreset.penaltyPresent) }
    var mirostat by rememberSaveable(initialPreset.id) { mutableIntStateOf(initialPreset.mirostat) }
    var mirostatTau by rememberSaveable(initialPreset.id) { mutableFloatStateOf(initialPreset.mirostatTau) }
    var mirostatEta by rememberSaveable(initialPreset.id) { mutableFloatStateOf(initialPreset.mirostatEta) }
    var seedText by rememberSaveable(initialPreset.id) { mutableStateOf(initialPreset.seed.toString()) }
    var jinjaEnabled by rememberSaveable(initialPreset.id) { mutableStateOf(initialPreset.jinjaEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = if (isEditingExisting) "Chat Generation Settings (${initialPreset.name})" else "Chat Generation Settings (New Preset)",
                style = importantTextStyle(MaterialTheme.typography.titleMedium),
                color = importantTextColor(MaterialTheme.colorScheme.onSurface)
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_persona_name_input")
                )
                OutlinedTextField(
                    value = tagline,
                    onValueChange = { tagline = it },
                    label = { Text("Short Tagline") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Prompt Instructions (leave empty for blank)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_persona_prompt_input")
                )

                // TEMPERATURE
                PresetFloatSliderBlock(
                    title = "TEMPERATURE",
                    subtitle = "Control creativity vs predictability. Higher values make responses more creative but less focused.",
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0.0f..2.0f
                )

                // TOP K
                PresetIntSliderBlock(
                    title = "TOP K",
                    subtitle = "Control creativity by limiting word choices to the K most likely options. Lower values make responses more focused.",
                    value = topK,
                    onValueChange = { topK = it },
                    valueRange = 0f..200f
                )

                // TOP P
                PresetFloatSliderBlock(
                    title = "TOP P",
                    subtitle = "Balance creativity and coherence. Higher values (near 1.0) allow more creative but potentially less focused responses.",
                    value = topP,
                    onValueChange = { topP = it },
                    valueRange = 0.05f..1.0f
                )

                // MIN P
                PresetFloatSliderBlock(
                    title = "MIN P",
                    subtitle = "The minimum probability for a token to be considered. Filter out unlikely words to reduce nonsensical or out-of-context responses.",
                    value = minP,
                    onValueChange = { minP = it },
                    valueRange = 0.0f..0.5f
                )

                // XTC THRESHOLD
                PresetFloatSliderBlock(
                    title = "XTC THRESHOLD",
                    subtitle = "Set a minimum probability threshold for tokens to be removed via XTC sampler. (> 0.5 disables XTC)",
                    value = xtcThreshold,
                    onValueChange = { xtcThreshold = it },
                    valueRange = 0.0f..1.0f
                )

                // XTC PROBABILITY
                PresetFloatSliderBlock(
                    title = "XTC PROBABILITY",
                    subtitle = "Set the chance for token removal via XTC sampler. 0 is disabled.",
                    value = xtcProbability,
                    onValueChange = { xtcProbability = it },
                    valueRange = 0.0f..1.0f
                )

                // TYPICAL P
                PresetFloatSliderBlock(
                    title = "TYPICAL P",
                    subtitle = "Enable locally typical sampling with parameter p. 1.0 is disabled.",
                    value = typicalP,
                    onValueChange = { typicalP = it },
                    valueRange = 0.1f..1.0f
                )

                // PENALTY LAST N
                PresetIntSliderBlock(
                    title = "PENALTY LAST N",
                    subtitle = "How far back to check for repetition. Larger values help prevent long-term repetition.",
                    value = penaltyLastN,
                    onValueChange = { penaltyLastN = it },
                    valueRange = 0f..512f
                )

                // PENALTY REPEAT
                PresetFloatSliderBlock(
                    title = "PENALTY REPEAT",
                    subtitle = "Discourage word repetition. Higher values make responses use more diverse language.",
                    value = repeatPenalty,
                    onValueChange = { repeatPenalty = it },
                    valueRange = 0.8f..2.0f
                )

                // PENALTY FREQ
                PresetFloatSliderBlock(
                    title = "PENALTY FREQ",
                    subtitle = "Penalize overused words. Higher values encourage using a broader vocabulary.",
                    value = penaltyFreq,
                    onValueChange = { penaltyFreq = it },
                    valueRange = 0.0f..2.0f
                )

                // PENALTY PRESENT
                PresetFloatSliderBlock(
                    title = "PENALTY PRESENT",
                    subtitle = "Reduce repetition of themes and ideas. Higher values encourage more diverse content.",
                    value = penaltyPresent,
                    onValueChange = { penaltyPresent = it },
                    valueRange = 0.0f..2.0f
                )

                // Mirostat (Off | v1 | v2)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Mirostat",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Enable advanced control over response creativity. Set to 1 or 2 (smoother) for smart, real-time adjustments to randomness and coherence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0 to "Off", 1 to "v1", 2 to "v2").forEach { (modeVal, label) ->
                            val selected = mirostat == modeVal
                            Button(
                                onClick = { mirostat = modeVal },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preset_mirostat_$label")
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                // MIROSTAT TAU
                PresetFloatSliderBlock(
                    title = "MIROSTAT TAU",
                    subtitle = "Set the target creativity level for Mirostat. Higher values allow for more diverse and imaginative responses.",
                    value = mirostatTau,
                    onValueChange = { mirostatTau = it },
                    valueRange = 0.5f..10.0f
                )

                // MIROSTAT ETA
                PresetFloatSliderBlock(
                    title = "MIROSTAT ETA",
                    subtitle = "How quickly Mirostat adjusts creativity. Higher values mean faster adjustments.",
                    value = mirostatEta,
                    onValueChange = { mirostatEta = it },
                    valueRange = 0.01f..1.0f
                )

                // SEED
                OutlinedTextField(
                    value = seedText,
                    onValueChange = { seedText = it },
                    label = { Text("SEED (-1 for random)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preset_seed_input")
                )

                // JINJA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "JINJA",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Enable Jinja templating for chat formatting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = jinjaEnabled,
                        onCheckedChange = { jinjaEnabled = it },
                        modifier = Modifier.testTag("preset_jinja_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedSeed = seedText.trim().toIntOrNull() ?: -1
                    onSave(
                        initialPreset.copy(
                            name = name,
                            tagline = tagline,
                            systemPrompt = systemPrompt,
                            temperature = temperature,
                            topK = topK,
                            topP = topP,
                            minP = minP,
                            xtcThreshold = xtcThreshold,
                            xtcProbability = xtcProbability,
                            typicalP = typicalP,
                            penaltyLastN = penaltyLastN,
                            repeatPenalty = repeatPenalty,
                            penaltyFreq = penaltyFreq,
                            penaltyPresent = penaltyPresent,
                            mirostat = mirostat,
                            mirostatTau = mirostatTau,
                            mirostatEta = mirostatEta,
                            seed = parsedSeed,
                            jinjaEnabled = jinjaEnabled
                        )
                    )
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = livingBrightButtonContentColor()
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(brush = livingBrightButtonBrush())
                    .testTag("confirm_save_persona_button")
            ) {
                Text(if (isEditingExisting) "Save Preset" else "Save & Activate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PresetFloatSliderBlock(
    title: String,
    subtitle: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = "%.2f".format(value),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

@Composable
private fun PresetIntSliderBlock(
    title: String,
    subtitle: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange
        )
    }
}
