package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AiRuntime
import com.example.data.LocalModelEntity
import com.example.ui.components.RuntimeBadgePill
import com.example.ui.components.importantTextColor
import com.example.ui.components.importantTextStyle
import com.example.ui.components.livingActiveItemBrush
import com.example.ui.components.livingBrightButtonBrush
import com.example.ui.components.livingBrightButtonContentColor
import com.example.ui.components.livingHeroCardBrush
import com.example.ui.components.livingMetricBoxBrush
import com.example.ui.components.livingSecondaryButtonBrush
import com.example.ui.components.livingSecondaryHeroBrush
import com.example.ui.components.livingSurfaceCardBrush
import com.example.ui.components.runtimeAccentColor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.LeafyUiState

/**
 * Dedicated Screen 1: Local Models Vault (Strictly for loading and managing local model files on device storage).
 * Runtimes and Power Modes are in their own separate menus/screens.
 */
@Composable
fun LocalModelsScreen(
    uiState: LeafyUiState,
    onActivateModel: (LocalModelEntity) -> Unit,
    onDeleteLoadedModel: (LocalModelEntity) -> Unit,
    onImportModelUri: (Uri?, Boolean) -> Unit,
    onScanDeviceModels: () -> Unit,
    onLoadModelIntoRam: (LocalModelEntity) -> Unit = {},
    onOffloadModelFromRam: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var pendingSelectedUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingSelectedUri = remember(pendingSelectedUriString) {
        pendingSelectedUriString?.let { Uri.parse(it) }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingSelectedUriString = uri.toString()
        }
    }

    if (pendingSelectedUri != null) {
        val targetUri = pendingSelectedUri
        AlertDialog(
            onDismissRequest = { pendingSelectedUriString = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Model Storage Location",
                    style = importantTextStyle(MaterialTheme.typography.titleLarge),
                    color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "How would you like Leafy to store this model file?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedButton(
                        onClick = {
                            pendingSelectedUriString = null
                            onImportModelUri(targetUri, false)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("keep_in_shared_storage_button")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "Keep in Internal Shared Storage",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Use the model directly from its existing shared storage path without duplicating disk space.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            pendingSelectedUriString = null
                            onImportModelUri(targetUri, true)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = livingBrightButtonContentColor()
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(brush = livingBrightButtonBrush())
                            .testTag("copy_to_app_storage_button")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "Copy to App Storage",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = livingBrightButtonContentColor()
                            )
                            Text(
                                text = "Copy the model file into Leafy's private app storage folder.",
                                style = MaterialTheme.typography.bodySmall,
                                color = livingBrightButtonContentColor().copy(alpha = 0.88f)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { pendingSelectedUriString = null },
                    modifier = Modifier.testTag("cancel_model_storage_dialog_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("local_models_list"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // 1. Local Storage File Loader Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Load Local Model from Device",
                                    style = importantTextStyle(MaterialTheme.typography.titleLarge),
                                    color = importantTextColor(MaterialTheme.colorScheme.onPrimaryContainer)
                                )
                                Text(
                                    text = "Select a real .gguf, .pte, .tmac, or .bitnet weight file (or companion tokenizer.bin / tokenizer.model for ExecuTorch) from device storage.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.90f)
                                )
                            }
                        }

                        AnimatedVisibility(visible = uiState.isImportingModel) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent,
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .background(brush = livingMetricBoxBrush())
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = uiState.importStatusText ?: "Copying local model file into engine storage…",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    LinearProgressIndicator(
                                        progress = { uiState.importProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(50)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        openDocumentLauncher.launch(arrayOf("*/*"))
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "System file picker unavailable (${e.javaClass.simpleName}); scanning device models folder instead.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onScanDeviceModels()
                                    }
                                },
                                enabled = !uiState.isImportingModel,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = livingBrightButtonContentColor()
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(brush = livingBrightButtonBrush())
                                    .testTag("pick_local_model_file_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Select Model File",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = onScanDeviceModels,
                                enabled = !uiState.isImportingModel,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scan_device_models_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Scan Models Folder",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 2. Loaded Local Models Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Loaded Models on Device (${uiState.localModels.size})",
                        style = importantTextStyle(MaterialTheme.typography.titleMedium),
                        color = importantTextColor(MaterialTheme.colorScheme.onBackground),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "RAM Free: ${uiState.telemetry.availableRamMb} MB",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 3. Empty State when no local models are loaded yet
            if (uiState.localModels.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("empty_local_models_card"),
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
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "No Local Models Loaded Yet",
                                style = importantTextStyle(MaterialTheme.typography.titleMedium),
                                color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                            )
                            Text(
                                text = "Tap 'Select Model File' above to load any .gguf, .pte, .tmac, or .bitnet file from your device storage. Only authentic on-disk model files are listed here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 4. Loaded Local Model Cards
            items(
                items = uiState.localModels,
                key = { it.id }
            ) { model ->
                val isActive = uiState.activeModel?.id == model.id
                val isLoadedInRam = uiState.mountedModelId == model.id
                val runtime = AiRuntime.fromId(model.runtimeId)
                val fitsInRam = uiState.telemetry.totalRamMb <= 0 || model.ramRequiredMb <= uiState.telemetry.totalRamMb

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("model_card_${model.id}"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    border = BorderStroke(
                        width = if (isActive) 2.dp else 1.dp,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = if (isActive) {
                                    livingActiveItemBrush(MaterialTheme.colorScheme.primary)
                                } else {
                                    livingSurfaceCardBrush()
                                }
                            )
                            .padding(horizontal = 13.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(end = 6.dp)
                            ) {
                                RuntimeBadgePill(runtime = runtime)
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color.Transparent,
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .background(
                                                brush = if (isLoadedInRam) {
                                                    livingBrightButtonBrush()
                                                } else {
                                                    livingSecondaryButtonBrush()
                                                }
                                            )
                                            .padding(horizontal = 7.dp, vertical = 2.5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Memory,
                                            contentDescription = null,
                                            tint = if (isLoadedInRam) livingBrightButtonContentColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = if (isLoadedInRam) "In RAM" else "Offloaded",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isLoadedInRam) livingBrightButtonContentColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color.Transparent,
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .background(
                                                brush = if (isActive) {
                                                    livingBrightButtonBrush()
                                                } else {
                                                    livingSecondaryButtonBrush()
                                                }
                                            )
                                            .padding(horizontal = 7.dp, vertical = 2.5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = if (isActive) livingBrightButtonContentColor() else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = if (isActive) "Active Model" else "Verified on Disk",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isActive) livingBrightButtonContentColor() else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = model.name,
                            style = importantTextStyle(MaterialTheme.typography.titleMedium),
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                            color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                        )
                        Text(
                            text = "${model.quantization} • ${model.sizeMb} MB" +
                                if (model.sha256Short.isNotBlank()) " • SHA-256: ${model.sha256Short}" else "",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = model.specialty,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Est. RAM: ${model.ramRequiredMb} MB (${if (fitsInRam) "Fits Device" else "High RAM"})",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = if (fitsInRam) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = if (model.expectedTps > 0f) {
                                    "Measured: ${"%.1f".format(model.expectedTps)} t/s"
                                } else {
                                    "Decode TPS: not measured"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val loadLabel = when {
                            uiState.isLoadingModelIntoRam && isActive -> "Loading…"
                            isLoadedInRam -> "Offload"
                            else -> "Load"
                        }
                        val selectLabel = if (isActive) "Selected" else "Select"

                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            if (maxWidth < 420.dp) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                if (isLoadedInRam) {
                                                    onOffloadModelFromRam()
                                                } else {
                                                    onLoadModelIntoRam(model)
                                                }
                                            },
                                            enabled = !uiState.isLoadingModelIntoRam,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.Transparent,
                                                contentColor = livingBrightButtonContentColor()
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(50))
                                                .background(brush = livingBrightButtonBrush())
                                                .testTag("toggle_ram_model_${model.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Memory,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = loadLabel,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = { onActivateModel(model) },
                                            enabled = !isActive,
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("activate_model_${model.id}")
                                        ) {
                                            Icon(
                                                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = selectLabel,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { onDeleteLoadedModel(model) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("delete_model_${model.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Remove model",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Remove",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (isLoadedInRam) {
                                                onOffloadModelFromRam()
                                            } else {
                                                onLoadModelIntoRam(model)
                                            }
                                        },
                                        enabled = !uiState.isLoadingModelIntoRam,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Transparent,
                                            contentColor = livingBrightButtonContentColor()
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(50))
                                            .background(brush = livingBrightButtonBrush())
                                            .testTag("toggle_ram_model_${model.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Memory,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = loadLabel,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { onActivateModel(model) },
                                        enabled = !isActive,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("activate_model_${model.id}")
                                    ) {
                                        Icon(
                                            imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = selectLabel,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { onDeleteLoadedModel(model) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("delete_model_${model.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Remove model",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Remove",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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

/**
 * Dedicated Screen 2: Inference Runtimes Hub (Strictly for inspecting and switching the 4 on-device AI runtimes).
 * Completely separate from Local Models and Power Modes.
 */
@Composable
fun RuntimesScreen(
    uiState: LeafyUiState,
    onSelectRuntime: (AiRuntime) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("runtimes_screen_list"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(brush = livingSecondaryHeroBrush())
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "On-Device Inference Runtimes",
                                style = importantTextStyle(MaterialTheme.typography.titleLarge),
                                color = importantTextColor(MaterialTheme.colorScheme.onSecondaryContainer)
                            )
                        }
                        Text(
                            text = "Select the active native execution backend used to evaluate your loaded local model tensors. All runtime engines execute 100% offline on device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.90f)
                        )
                    }
                }
            }

            items(
                items = AiRuntime.entries,
                key = { it.id }
            ) { runtime ->
                val isSelected = runtime == uiState.activeRuntime
                val accent = runtimeAccentColor(runtime)
                val compatibleModels = uiState.localModels.filter { runtime.isCompatibleWithModel(it) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectRuntime(runtime) }
                        .testTag("runtime_card_${runtime.name.lowercase()}"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = if (isSelected) {
                                    livingActiveItemBrush(accent)
                                } else {
                                    livingSurfaceCardBrush()
                                }
                            )
                            .padding(horizontal = 13.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(end = 6.dp)
                            ) {
                                RuntimeBadgePill(runtime = runtime)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) accent.copy(alpha = 0.16f) else Color.Transparent,
                                border = BorderStroke(0.8.dp, accent.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = when {
                                        isSelected -> "● ACTIVE & SELECTED"
                                        runtime.hasBundledNativeLibrary -> "● BUNDLED JNI"
                                        else -> "● NOT BUNDLED"
                                    },
                                    modifier = Modifier
                                        .then(if (!isSelected) Modifier.background(brush = livingSecondaryButtonBrush()) else Modifier)
                                        .padding(horizontal = 7.dp, vertical = 2.5.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        Text(
                            text = runtime.architectureNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "Primary Quantization: ${runtime.defaultQuantization} • Formats: ${runtime.fileExtensions.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = if (compatibleModels.isNotEmpty()) {
                                "Compatible on device (${compatibleModels.size}): ${compatibleModels.joinToString { it.name }}"
                            } else {
                                "Compatible on device: 0 (Requires ${runtime.fileExtensions.joinToString(" or ")} model file)"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Medium
                            ),
                            color = if (compatibleModels.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = { onSelectRuntime(runtime) },
                            enabled = !isSelected,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = livingBrightButtonContentColor(),
                                disabledContainerColor = Color.Transparent,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .background(
                                    brush = if (isSelected) {
                                        livingSecondaryButtonBrush()
                                    } else {
                                        livingBrightButtonBrush()
                                    }
                                )
                                .testTag("select_runtime_button_${runtime.name.lowercase()}")
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(if (isSelected) "Active Runtime Engine" else "Switch to ${runtime.displayName}")
                        }
                    }
                }
            }
        }
    }
}
