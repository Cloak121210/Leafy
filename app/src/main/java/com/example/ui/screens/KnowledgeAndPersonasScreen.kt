package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.PersonaPresetEntity
import com.example.data.RagDocumentEntity
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.LeafyUiState

@Composable
fun KnowledgeAndPersonasScreen(
    uiState: LeafyUiState,
    onTogglePinDoc: (RagDocumentEntity) -> Unit,
    onAddRagDoc: (String, String, String) -> Unit,
    onImportRagUri: (Uri?) -> Unit,
    onDeleteRagDoc: (Long) -> Unit,
    onSelectPersona: (PersonaPresetEntity) -> Unit,
    onSaveCustomPersona: (String, String, String, Float, Float) -> Unit,
    onDeleteCustomPersona: (PersonaPresetEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showAddDocDialog by rememberSaveable { mutableStateOf(false) }
    var showAddPersonaDialog by rememberSaveable { mutableStateOf(false) }

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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Local Document RAG & Context Pinning Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "On-Device RAG & Context Pinning",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            ) {
                                Text(
                                    text = "$totalPinnedTokens / ${uiState.powerProfile.contextLimit} tok pinned",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = "Pin local Markdown or text notes directly into Leafy's context window. Passages are ranked offline via BM25 and cited under assistant answers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showAddDocDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("create_rag_doc_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
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
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("import_rag_file_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
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
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "No Local RAG Documents Indexed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Create a Markdown note or import a local .txt/.md file to pin offline knowledge into your conversation context.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { showAddDocDialog = true },
                                modifier = Modifier.testTag("empty_rag_create_first_note_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
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
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (doc.isPinnedToChat) {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
                ),
                border = BorderStroke(
                    1.dp,
                    if (doc.isPinnedToChat) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = doc.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        text = doc.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3. System Prompt Templates & Persona Presets Section
        item {
            Spacer(modifier = Modifier.height(6.dp))
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
                        text = "System Prompt & Persona Presets",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Customize system instructions, temperature, and top-p sampling",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { showAddPersonaDialog = true },
                    modifier = Modifier.testTag("add_custom_persona_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
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
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    }
                ),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonPin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = persona.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
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
                            Button(
                                onClick = { onSelectPersona(persona) },
                                enabled = !isSelected,
                                modifier = Modifier.testTag("activate_persona_${persona.id}")
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Active")
                                } else {
                                    Text("Use Persona")
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
                                        contentDescription = "Delete custom persona",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Text(
                            text = if (persona.systemPrompt.isBlank()) {
                                "(Blank preset — no system prompt instructions prepended)"
                            } else {
                                "\"${persona.systemPrompt}\""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Text(
                        text = "Sampling: Temperature = ${"%.2f".format(persona.temperature)} • Top-P = ${"%.2f".format(persona.topP)} • Repeat Penalty = ${"%.2f".format(persona.repeatPenalty)}",
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
            title = { Text("Add Local RAG Knowledge Document") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    modifier = Modifier.testTag("confirm_save_rag_doc_button")
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

    if (showAddPersonaDialog) {
        var name by rememberSaveable { mutableStateOf("") }
        var tagline by rememberSaveable { mutableStateOf("") }
        var systemPrompt by rememberSaveable { mutableStateOf("You are a concise, privacy-first local AI specialist.") }
        var temperature by rememberSaveable { mutableFloatStateOf(0.65f) }
        var topP by rememberSaveable { mutableFloatStateOf(0.90f) }

        AlertDialog(
            onDismissRequest = { showAddPersonaDialog = false },
            title = { Text("Add Custom Preset") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Preset Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_persona_name_input")
                    )
                    OutlinedTextField(
                        value = tagline,
                        onValueChange = { tagline = it },
                        label = { Text("Short Tagline") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        label = { Text("System Prompt Instructions (leave empty for blank)") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_persona_prompt_input")
                    )
                    Text(
                        text = "Temperature: ${"%.2f".format(temperature)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0.1f..1.2f
                    )
                    Text(
                        text = "Top-P: ${"%.2f".format(topP)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = topP,
                        onValueChange = { topP = it },
                        valueRange = 0.5f..1.0f
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveCustomPersona(name, tagline, systemPrompt, temperature, topP)
                        showAddPersonaDialog = false
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.testTag("confirm_save_persona_button")
                ) {
                    Text("Save & Activate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPersonaDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
