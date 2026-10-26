package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AiRuntime
import com.example.data.LocalModelEntity
import com.example.ui.components.BotanicalNeuralHeroCard
import com.example.ui.components.RuntimeBadgePill
import com.example.ui.components.runtimeAccentColor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.LeafyUiState

@Composable
fun ModelsAndRuntimesScreen(
    uiState: LeafyUiState,
    onActivateModel: (LocalModelEntity) -> Unit,
    onStartDownload: (LocalModelEntity) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteDownloadedModel: (LocalModelEntity) -> Unit,
    onSelectRuntime: (AiRuntime) -> Unit,
    onImportModelUri: (Uri?) -> Unit,
    onImportCustomModelManual: (String, AiRuntime, String, Int) -> Unit,
    onDownloadCustomUrl: (String, String, AiRuntime, String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var runtimeFilter by rememberSaveable { mutableStateOf<String?>("ALL") }
    var showCustomImportDialog by remember { mutableStateOf(false) }
    var showHuggingFaceUrlDialog by remember { mutableStateOf(false) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportModelUri(uri)
        } else {
            showCustomImportDialog = true
        }
    }

    val filteredModels = remember(uiState.localModels, runtimeFilter) {
        if (runtimeFilter == null || runtimeFilter == "ALL") {
            uiState.localModels
        } else {
            uiState.localModels.filter { it.runtimeId.equals(runtimeFilter, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("models_runtimes_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Botanical Hero Overview Card
        item {
            BotanicalNeuralHeroCard(
                title = "On-Device Model Vault & Internet Hub",
                subtitle = "Download quantized weights directly from Hugging Face or import local files. Once downloaded, models run 100% offline.",
                activeRuntime = uiState.activeRuntime,
                powerProfile = uiState.powerProfile,
                telemetry = uiState.telemetry
            )
        }

        // 2. Four Edge Inference Runtimes Architecture Strip
        item {
            Text(
                text = "Supported Edge Runtimes",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiRuntime.entries.forEach { runtime ->
                    val isSelected = runtime == uiState.activeRuntime
                    val accent = runtimeAccentColor(runtime)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectRuntime(runtime) }
                            .testTag("runtime_card_${runtime.name.lowercase()}"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                accent.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            }
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RuntimeBadgePill(runtime = runtime)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Text(
                                        text = if (isSelected) "● ACTIVE & SELECTED" else "● READY / ACTIVE",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = runtime.architectureNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Default Quant: ${runtime.defaultQuantization} • Efficiency: ${"%.2f".format(runtime.energyEfficiencyMultiplier)}x • Formats: ${runtime.fileExtensions.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 3. Internet Download & Local Weight Importers
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Column {
                            Text(
                                text = "Download or Mount Model Weights",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Download via URL from Hugging Face or import existing files from device storage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showHuggingFaceUrlDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("download_hf_url_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hugging Face URL")
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    openDocumentLauncher.launch(arrayOf("*/*"))
                                } catch (_: Exception) {
                                    showCustomImportDialog = true
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_local_model_file_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Storage File")
                        }
                    }
                }
            }
        }

        // 4. Runtime Filter Strip for Model Catalog
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = runtimeFilter == "ALL",
                    onClick = { runtimeFilter = "ALL" },
                    label = { Text("All Edge Models (${uiState.localModels.size})") }
                )
                AiRuntime.entries.forEach { rt ->
                    FilterChip(
                        selected = runtimeFilter == rt.id,
                        onClick = { runtimeFilter = rt.id },
                        label = { Text(rt.displayName) }
                    )
                }
            }
        }

        // 5. Model Cards with Download & Local Management
        items(
            items = filteredModels,
            key = { it.id }
        ) { model ->
            val isActive = uiState.activeModel?.id == model.id
            val runtime = AiRuntime.fromId(model.runtimeId)
            val fitsInRam = model.ramRequiredMb <= uiState.telemetry.totalRamMb

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("model_card_${model.id}"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    }
                ),
                border = BorderStroke(
                    width = if (isActive) 2.dp else 1.dp,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RuntimeBadgePill(runtime = runtime)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            repeat(model.ecoRating) {
                                Icon(
                                    imageVector = Icons.Default.EnergySavingsLeaf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = model.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${model.family} • ${model.parameters} • ${model.quantization}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (model.isInstalled) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (isActive) "Loaded in RAM" else "On Device",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = "Internet Download",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = model.specialty,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Technical Specs Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Download Size: ${model.sizeMb} MB",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                        )
                        Text(
                            text = "RAM: ${model.ramRequiredMb} MB (${if (fitsInRam) "Fits Device" else "Heavy"})",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = if (fitsInRam) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "~${"%.1f".format(model.expectedTps)} t/s",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                        )
                    }

                    // Live Download Progress Bar (when downloading over Internet)
                    AnimatedVisibility(visible = model.isDownloading) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Downloading weights: ${(model.downloadProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${model.downloadedBytes / (1024 * 1024)} / ${model.sizeMb} MB @ ${"%.1f".format(model.downloadSpeedMb)} MB/s",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { model.downloadProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { onCancelDownload(model.id) },
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Cancel", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (model.isInstalled) {
                            Button(
                                onClick = { onActivateModel(model) },
                                enabled = !isActive,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("activate_model_${model.id}")
                            ) {
                                Icon(
                                    imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isActive) "Active in Chat" else "Switch to Model")
                            }

                            if (!isActive) {
                                OutlinedButton(
                                    onClick = { onDeleteDownloadedModel(model) },
                                    modifier = Modifier.testTag("delete_model_${model.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete weights",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete")
                                }
                            }
                        } else {
                            Button(
                                onClick = { onStartDownload(model) },
                                enabled = !model.isDownloading,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("start_download_${model.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download from Hugging Face")
                            }
                        }
                    }
                }
            }
        }
    }

    // Hugging Face Direct Download Dialog
    if (showHuggingFaceUrlDialog) {
        var hfUrl by remember { mutableStateOf("https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf") }
        var modelName by remember { mutableStateOf("Llama 3.2 1B Instruct") }
        var selectedRuntime by remember { mutableStateOf(AiRuntime.LLAMA_CPP) }
        var quantFormat by remember { mutableStateOf("Q4_K_M") }
        var estSizeMb by remember { mutableStateOf("780") }

        AlertDialog(
            onDismissRequest = { showHuggingFaceUrlDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Download from Hugging Face")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = hfUrl,
                        onValueChange = { hfUrl = it },
                        label = { Text("Direct Model URL (.gguf, .pte, .tmac)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text("Model Display Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Target Runtime Engine:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AiRuntime.entries.forEach { rt ->
                            FilterChip(
                                selected = selectedRuntime == rt,
                                onClick = { selectedRuntime = rt },
                                label = { Text(rt.displayName) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantFormat,
                            onValueChange = { quantFormat = it },
                            label = { Text("Quantization") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = estSizeMb,
                            onValueChange = { estSizeMb = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Size (MB)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDownloadCustomUrl(
                            hfUrl,
                            modelName,
                            selectedRuntime,
                            quantFormat,
                            estSizeMb.toIntOrNull() ?: 800
                        )
                        showHuggingFaceUrlDialog = false
                    },
                    enabled = hfUrl.isNotBlank()
                ) {
                    Text("Start Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHuggingFaceUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCustomImportDialog) {
        var customName by remember { mutableStateOf("Mistral-7B-Instruct-v0.3-Q4_K_M") }
        var selectedRuntime by remember { mutableStateOf(AiRuntime.LLAMA_CPP) }
        var customQuant by remember { mutableStateOf("Q4_K_M") }
        var customSizeMb by remember { mutableStateOf("1450") }

        AlertDialog(
            onDismissRequest = { showCustomImportDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Import Local Storage File")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Model Name / Filename") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_model_name_input")
                    )

                    Text(
                        text = "Target Edge Runtime:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AiRuntime.entries.forEach { rt ->
                            FilterChip(
                                selected = selectedRuntime == rt,
                                onClick = {
                                    selectedRuntime = rt
                                    customQuant = rt.defaultQuantization
                                },
                                label = { Text(rt.displayName) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customQuant,
                            onValueChange = { customQuant = it },
                            label = { Text("Quantization") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customSizeMb,
                            onValueChange = { customSizeMb = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Size (MB)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportCustomModelManual(
                            customName,
                            selectedRuntime,
                            customQuant,
                            customSizeMb.toIntOrNull() ?: 1200
                        )
                        showCustomImportDialog = false
                    },
                    modifier = Modifier.testTag("confirm_custom_model_import_button")
                ) {
                    Text("Mount & Activate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
