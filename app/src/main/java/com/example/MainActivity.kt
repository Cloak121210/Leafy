package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AiRuntime
import com.example.data.PowerProfile
import com.example.ui.components.LeafyCleanTopBar
import com.example.ui.components.OrganicLeafBackground
import com.example.ui.components.PowerProfilePill
import com.example.ui.components.RuntimeBadgePill
import com.example.ui.components.importantTextColor
import com.example.ui.components.importantTextStyle
import com.example.ui.components.livingActiveItemBrush
import com.example.ui.components.livingBrightButtonBrush
import com.example.ui.components.livingBrightButtonContentColor
import com.example.ui.components.livingHeroCardBrush
import com.example.ui.components.livingMenuSheetBrush
import com.example.ui.components.livingSecondaryButtonBrush
import com.example.ui.components.livingSecondaryHeroBrush
import com.example.ui.components.livingSurfaceCardBrush
import com.example.ui.components.livingTertiaryHeroBrush
import com.example.ui.components.powerProfileAccentColor
import com.example.ui.components.runtimeAccentColor
import com.example.ui.screens.ChatStudioScreen
import com.example.ui.screens.EcoAndSettingsScreen
import com.example.ui.screens.KnowledgeAndPersonasScreen
import com.example.ui.screens.LocalModelsScreen
import com.example.ui.screens.RuntimesScreen
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LeafyTheme
import com.example.viewmodel.LeafyTab
import com.example.viewmodel.LeafyUiState
import com.example.viewmodel.LeafyViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val viewModel: LeafyViewModel = viewModel(
                factory = LeafyViewModel.provideFactory(context)
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LeafyTheme(
                darkTheme = uiState.isDarkTheme,
                useComicSans = uiState.useComicSans,
                useGradientText = uiState.useGradientText
            ) {
                LeafyAppShell(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeafyAppShell(
    uiState: LeafyUiState,
    viewModel: LeafyViewModel
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showModelSwitcherSheet by rememberSaveable { mutableStateOf(false) }
    var showRuntimeSwitcherSheet by rememberSaveable { mutableStateOf(false) }
    var showSpecialCommandMenu by rememberSaveable { mutableStateOf(false) }
    val saveableStateHolder = rememberSaveableStateHolder()

    BackHandler(enabled = uiState.currentTab != LeafyTab.CHAT) {
        viewModel.selectTab(LeafyTab.CHAT)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            LeafyCanopyNavigationDrawer(
                uiState = uiState,
                onSelectConversation = { convId ->
                    viewModel.selectConversation(convId)
                    scope.launch { drawerState.close() }
                },
                onNewConversation = {
                    viewModel.createNewConversation()
                    scope.launch { drawerState.close() }
                },
                onDeleteConversation = viewModel::deleteConversation,
                onNavigateToScreen = { targetTab ->
                    viewModel.selectTab(targetTab)
                    scope.launch { drawerState.close() }
                },
                onToggleComicSans = viewModel::toggleComicSans,
                onToggleGradientText = viewModel::toggleGradientText,
                onToggleDarkTheme = viewModel::toggleDarkTheme
            )
        }
    ) {
        OrganicLeafBackground(
            modifier = Modifier.fillMaxSize()
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                ),
                topBar = {
                    val secondaryTitle = when (uiState.currentTab) {
                        LeafyTab.CHAT -> "Leafy"
                        LeafyTab.MODELS -> "Local Models"
                        LeafyTab.RUNTIMES -> "Inference Runtimes"
                        LeafyTab.KNOWLEDGE -> "Local RAG & Persona Studio"
                        LeafyTab.ECO_SETTINGS -> "Power Modes & Telemetry"
                    }

                    LeafyCleanTopBar(
                        isChatFrontScreen = uiState.currentTab == LeafyTab.CHAT,
                        secondaryScreenTitle = secondaryTitle,
                        activeModel = uiState.activeModel,
                        activeRuntime = uiState.activeRuntime,
                        powerProfile = uiState.powerProfile,
                        statusBanner = uiState.statusBannerMessage,
                        onOpenCanopyDrawer = {
                            scope.launch { drawerState.open() }
                        },
                        onNavigateBackToChat = {
                            viewModel.selectTab(LeafyTab.CHAT)
                        },
                        onOpenModelSwitcher = { showModelSwitcherSheet = true },
                        onOpenRuntimeSwitcher = { showRuntimeSwitcherSheet = true },
                        onOpenSpecialCommandMenu = { showSpecialCommandMenu = true },
                        onOpenSettingsScreen = {
                            viewModel.selectTab(LeafyTab.ECO_SETTINGS)
                        },
                        isSettingsActive = uiState.currentTab == LeafyTab.ECO_SETTINGS
                    )
                }
            ) { innerPadding ->
                AnimatedContent(
                    targetState = uiState.currentTab,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    label = "screen_transition"
                ) { targetTab ->
                    saveableStateHolder.SaveableStateProvider(targetTab.name) {
                        when (targetTab) {
                            LeafyTab.CHAT -> ChatStudioScreen(
                                uiState = uiState,
                                onSendPrompt = viewModel::sendUserMessage,
                                onEditAndBranchMessage = viewModel::editUserMessageAndBranch,
                                onRegenerateBranch = viewModel::regenerateAssistantBranch,
                                onSwitchSiblingBranch = viewModel::switchSiblingBranch,
                                onToggleSpeak = viewModel::toggleSpeakMessage,
                                onStopStreaming = viewModel::stopStreaming,
                                onOpenSpecialMenu = { showSpecialCommandMenu = true },
                                onNavigateToModels = { viewModel.selectTab(LeafyTab.MODELS) },
                                onOpenRuntimeSwitcher = { showRuntimeSwitcherSheet = true }
                            )

                            LeafyTab.MODELS -> LocalModelsScreen(
                                uiState = uiState,
                                onActivateModel = { model ->
                                    viewModel.switchModelAndRuntime(model)
                                    viewModel.selectTab(LeafyTab.CHAT)
                                },
                                onDeleteLoadedModel = viewModel::deleteDownloadedModel,
                                onImportModelUri = viewModel::importLocalModelFromUri,
                                onScanDeviceModels = { viewModel.scanDeviceForLocalModels(silent = false) },
                                onLoadModelIntoRam = viewModel::loadModelIntoRam,
                                onOffloadModelFromRam = viewModel::offloadModelFromRam
                            )

                            LeafyTab.RUNTIMES -> RuntimesScreen(
                                uiState = uiState,
                                onSelectRuntime = viewModel::switchRuntimeOnly
                            )

                            LeafyTab.KNOWLEDGE -> KnowledgeAndPersonasScreen(
                                uiState = uiState,
                                onTogglePinDoc = viewModel::togglePinRagDocument,
                                onAddRagDoc = viewModel::addRagDocument,
                                onImportRagUri = viewModel::importRagDocumentFromUri,
                                onDeleteRagDoc = viewModel::deleteRagDocument,
                                onSelectPersona = viewModel::selectPersona,
                                onSaveCustomPersona = viewModel::saveCustomPersona,
                                onDeleteCustomPersona = viewModel::deleteCustomPersona
                            )

                            LeafyTab.ECO_SETTINGS -> EcoAndSettingsScreen(
                                uiState = uiState,
                                onSelectPowerProfile = viewModel::setPowerProfile,
                                onSetComicSans = viewModel::setComicSans,
                                onSetGradientText = viewModel::setGradientText,
                                onToggleDarkTheme = viewModel::toggleDarkTheme,
                                onWipeOfflineData = viewModel::wipeAllOfflineData
                            )
                        }
                    }
                }
            }
        }
    }

    // 1. Power Modes & Studio Controls Sheet (Strictly for Power Modes, Persona, RAG & Typography — NO Runtimes!)
    if (showSpecialCommandMenu) {
        ModalBottomSheet(
            onDismissRequest = { showSpecialCommandMenu = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            LeafyPowerModesAndStudioSheet(
                uiState = uiState,
                onSelectPowerProfile = viewModel::setPowerProfile,
                onSelectPersona = viewModel::selectPersona,
                onSaveCustomPersona = viewModel::saveCustomPersona,
                onToggleComicSans = viewModel::toggleComicSans,
                onToggleGradientText = viewModel::toggleGradientText,
                onOpenFullScreen = { tab ->
                    showSpecialCommandMenu = false
                    viewModel.selectTab(tab)
                }
            )
        }
    }

    // 2. Dedicated Local Model Switcher Sheet (Strictly for Local Models — NO Runtimes!)
    if (showModelSwitcherSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModelSwitcherSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = livingMenuSheetBrush())
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "sheet_model_header") {
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
                                text = "Switch Local Model",
                                style = importantTextStyle(MaterialTheme.typography.titleLarge),
                                color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                            )
                            Text(
                                text = "Select a model or Load/Offload it in RAM",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                showModelSwitcherSheet = false
                                viewModel.selectTab(LeafyTab.MODELS)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("sheet_open_model_hub_button")
                        ) {
                            Text("Models Menu")
                        }
                    }
                }

                if (uiState.localModels.isEmpty()) {
                    item(key = "sheet_no_models_empty") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(brush = livingSurfaceCardBrush())
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "No local model files loaded yet",
                                    style = importantTextStyle(MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)),
                                    color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                                )
                                Text(
                                    text = "Open the Local Models screen to select a .gguf, .pte, or .tmac file from your device storage.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        showModelSwitcherSheet = false
                                        viewModel.selectTab(LeafyTab.MODELS)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = livingBrightButtonContentColor()
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(50))
                                        .background(brush = livingBrightButtonBrush())
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Load Local Model File")
                                }
                            }
                        }
                    }
                } else {
                    items(
                        items = uiState.localModels,
                        key = { "sheet_${it.id}" }
                    ) { model ->
                        val isCurrent = uiState.activeModel?.id == model.id
                        val isInRam = uiState.mountedModelId == model.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.switchModelAndRuntime(model)
                                    showModelSwitcherSheet = false
                                }
                                .testTag("sheet_model_item_${model.id}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            ),
                            border = BorderStroke(
                                if (isCurrent) 1.5.dp else 1.dp,
                                if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        brush = if (isCurrent) {
                                            livingActiveItemBrush(MaterialTheme.colorScheme.primary)
                                        } else {
                                            livingSurfaceCardBrush()
                                        }
                                    )
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    val titleStyle = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                    Text(
                                        text = model.name,
                                        style = if (isCurrent) importantTextStyle(titleStyle) else titleStyle,
                                        color = if (isCurrent) importantTextColor(MaterialTheme.colorScheme.onSurface) else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${model.quantization} • ${model.sizeMb} MB • ${if (isInRam) "In RAM" else "Offloaded"}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (isInRam) {
                                                viewModel.offloadModelFromRam()
                                            } else {
                                                viewModel.loadModelIntoRam(model)
                                            }
                                        },
                                        enabled = !uiState.isLoadingModelIntoRam,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("sheet_toggle_ram_${model.id}")
                                    ) {
                                        Text(if (isInRam) "Offload" else "Load")
                                    }
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Active",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "sheet_model_bottom_spacer") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    // 3. Dedicated Inference Runtime Switcher Sheet (Strictly for Runtimes — NO Models or Power Modes!)
    if (showRuntimeSwitcherSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRuntimeSwitcherSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = livingMenuSheetBrush())
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                            text = "Inference Runtimes",
                            style = importantTextStyle(MaterialTheme.typography.titleLarge),
                            color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                        )
                        Text(
                            text = "Select the active on-device execution engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            showRuntimeSwitcherSheet = false
                            viewModel.selectTab(LeafyTab.RUNTIMES)
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("sheet_open_runtimes_screen_button")
                    ) {
                        Text("Runtimes Hub")
                    }
                }

                AiRuntime.entries.forEach { runtime ->
                    val isSelected = uiState.activeRuntime == runtime
                    val accent = runtimeAccentColor(runtime)
                    val compatibleCount = uiState.localModels.count { runtime.isCompatibleWithModel(it) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.switchRuntimeOnly(runtime)
                                showRuntimeSwitcherSheet = false
                            }
                            .testTag("sheet_runtime_${runtime.name.lowercase()}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Transparent
                        ),
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = if (isSelected) {
                                        livingActiveItemBrush(accent)
                                    } else {
                                        livingSurfaceCardBrush()
                                    }
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                RuntimeBadgePill(runtime = runtime)
                                Text(
                                    text = runtime.architectureNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (compatibleCount > 0) {
                                        "$compatibleCount compatible model(s) on device (${runtime.fileExtensions.joinToString(", ")})"
                                    } else {
                                        "0 compatible models loaded — requires ${runtime.fileExtensions.joinToString(" or ")}"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = if (compatibleCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Active runtime",
                                    tint = accent
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Left Botanical Canopy Drawer: Dedicated items for Local Models, Inference Runtimes, RAG & Personas, and Power Modes & Settings.
 * ("Chat Studio" redundant box removed to save vertical space.)
 */
@Composable
private fun LeafyCanopyNavigationDrawer(
    uiState: LeafyUiState,
    onSelectConversation: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onNavigateToScreen: (LeafyTab) -> Unit,
    onToggleComicSans: () -> Unit,
    onToggleGradientText: () -> Unit,
    onToggleDarkTheme: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(310.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .background(brush = livingMenuSheetBrush())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(brush = livingHeroCardBrush())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(34.dp),
                                shape = CircleShape,
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(brush = livingBrightButtonBrush()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EnergySavingsLeaf,
                                        contentDescription = null,
                                        tint = livingBrightButtonContentColor(),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Leafy",
                                        style = importantTextStyle(
                                            MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            )
                                        ),
                                        color = importantTextColor(MaterialTheme.colorScheme.onSurface),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color.Transparent,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                    ) {
                                        Text(
                                            text = BuildConfig.VERSION_NAME,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier
                                                .background(brush = livingSecondaryButtonBrush())
                                                .padding(horizontal = 7.dp, vertical = 1.5.dp)
                                                .testTag("drawer_version_badge")
                                        )
                                    }
                                }
                                Text(
                                    text = "100% Offline • Sovereign AI",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        PowerProfilePill(
                            profile = uiState.powerProfile,
                            onClick = { onNavigateToScreen(LeafyTab.ECO_SETTINGS) }
                        )
                    }
                }

                Button(
                    onClick = onNewConversation,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = livingBrightButtonContentColor()
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(brush = livingBrightButtonBrush())
                        .testTag("drawer_new_thread_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Offline Conversation")
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    item(key = "drawer_workspaces_header") {
                        Text(
                            text = "WORKSPACES & ENGINES",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item(key = "drawer_workspace_models") {
                        DrawerWorkspaceCard(
                            icon = Icons.Default.FolderOpen,
                            title = "Local Models",
                            subtitle = "${uiState.localModels.size} model(s) loaded from device storage",
                            isSelected = uiState.currentTab == LeafyTab.MODELS,
                            onClick = { onNavigateToScreen(LeafyTab.MODELS) },
                            testTag = "drawer_nav_models"
                        )
                    }

                    item(key = "drawer_workspace_runtimes") {
                        DrawerWorkspaceCard(
                            icon = Icons.Default.Memory,
                            title = "Inference Runtimes",
                            subtitle = "Active: ${uiState.activeRuntime.displayName}",
                            isSelected = uiState.currentTab == LeafyTab.RUNTIMES,
                            onClick = { onNavigateToScreen(LeafyTab.RUNTIMES) },
                            testTag = "drawer_nav_runtimes"
                        )
                    }

                    item(key = "drawer_workspace_knowledge") {
                        DrawerWorkspaceCard(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            title = "RAG Knowledge & Personas",
                            subtitle = "${uiState.pinnedRagDocuments.size} docs pinned • ${uiState.activePersona?.name ?: "Leafy Assistant"}",
                            isSelected = uiState.currentTab == LeafyTab.KNOWLEDGE,
                            onClick = { onNavigateToScreen(LeafyTab.KNOWLEDGE) },
                            testTag = "drawer_nav_knowledge"
                        )
                    }

                    item(key = "drawer_workspace_eco") {
                        DrawerWorkspaceCard(
                            icon = Icons.Default.Settings,
                            title = "Power Modes & Telemetry",
                            subtitle = "${uiState.powerProfile.displayName} • Typography & privacy wipe",
                            isSelected = uiState.currentTab == LeafyTab.ECO_SETTINGS,
                            onClick = { onNavigateToScreen(LeafyTab.ECO_SETTINGS) },
                            testTag = "drawer_nav_eco_settings"
                        )
                    }

                    item(key = "drawer_threads_divider") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            Text(
                                text = "RECENT OFFLINE THREADS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (uiState.conversations.isEmpty()) {
                        item(key = "drawer_empty_conversations") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("empty_conversations_card"),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(brush = livingSurfaceCardBrush())
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = "No saved threads yet",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Send a message in the chat screen to create your first offline session.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = uiState.conversations,
                            key = { "drawer_conv_${it.id}" }
                        ) { conv ->
                            val isSelected = uiState.activeConversation?.id == conv.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectConversation(conv.id) },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            brush = if (isSelected) {
                                                livingActiveItemBrush(MaterialTheme.colorScheme.primary)
                                            } else {
                                                livingSurfaceCardBrush()
                                            }
                                        )
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
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
                                            imageVector = Icons.Default.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            val convTitleStyle = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                            )
                                            Text(
                                                text = conv.title,
                                                style = if (isSelected) importantTextStyle(convTitleStyle) else convTitleStyle,
                                                color = if (isSelected) importantTextColor(MaterialTheme.colorScheme.onSurface) else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (isSelected) {
                                                    uiState.activeRuntime.displayName
                                                } else {
                                                    AiRuntime.fromId(conv.activeRuntimeId).displayName
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeleteConversation(conv.id) },
                                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete conversation",
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(brush = livingSecondaryButtonBrush())
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onToggleComicSans)
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                            .testTag("drawer_comic_sans_toggle"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FontDownload,
                            contentDescription = "Toggle Comic Sans",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (uiState.useComicSans) "Comic Sans" else "Rounded Sans",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onToggleGradientText,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("drawer_gradient_text_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Toggle gradient text",
                            tint = if (uiState.useGradientText) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    IconButton(
                        onClick = onToggleDarkTheme,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("drawer_theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle dark theme",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerWorkspaceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = if (isSelected) {
                        livingActiveItemBrush(MaterialTheme.colorScheme.primary)
                    } else {
                        livingSurfaceCardBrush()
                    }
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = RoundedCornerShape(9.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = if (isSelected) {
                                    livingBrightButtonBrush()
                                } else {
                                    livingSecondaryButtonBrush()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) {
                                livingBrightButtonContentColor()
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    val baseTitleStyle = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                    )
                    Text(
                        text = title,
                        style = if (isSelected) importantTextStyle(baseTitleStyle) else baseTitleStyle,
                        color = if (isSelected) importantTextColor(MaterialTheme.colorScheme.onSurface) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isSelected) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Medium
                            ),
                            color = livingBrightButtonContentColor(),
                            modifier = Modifier
                                .background(brush = livingBrightButtonBrush())
                                .padding(horizontal = 6.dp, vertical = 1.5.dp)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * Power Modes & Studio Controls Sheet:
 * Strictly for Power Modes (Eco / Balanced / Turbo), Persona, RAG Context, and Typography.
 * Runtimes and Models have their own separate menus.
 */
@Composable
private fun LeafyPowerModesAndStudioSheet(
    uiState: LeafyUiState,
    onSelectPowerProfile: (PowerProfile) -> Unit,
    onSelectPersona: (com.example.data.PersonaPresetEntity) -> Unit,
    onSaveCustomPersona: (String, String, String, Float, Float) -> Unit,
    onToggleComicSans: () -> Unit,
    onToggleGradientText: () -> Unit,
    onOpenFullScreen: (LeafyTab) -> Unit
) {
    var showAddPresetDialog by rememberSaveable { mutableStateOf(false) }

    if (showAddPresetDialog) {
        var name by rememberSaveable { mutableStateOf("") }
        var tagline by rememberSaveable { mutableStateOf("") }
        var systemPrompt by rememberSaveable { mutableStateOf("") }
        var temperature by rememberSaveable { mutableFloatStateOf(0.65f) }
        var topP by rememberSaveable { mutableFloatStateOf(0.90f) }

        AlertDialog(
            onDismissRequest = { showAddPresetDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Add Custom Preset",
                    style = importantTextStyle(MaterialTheme.typography.titleLarge),
                    color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Preset Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sheet_new_persona_name_input")
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
                        label = { Text("System Prompt (leave empty for blank)") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sheet_new_persona_prompt_input")
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
                        showAddPresetDialog = false
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("sheet_confirm_save_persona_button")
                ) {
                    Text("Save & Activate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPresetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(brush = livingMenuSheetBrush())
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                    text = "Power Modes & Studio Controls",
                    style = importantTextStyle(MaterialTheme.typography.titleLarge),
                    color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                )
                Text(
                    text = "Offline thermal modes, persona & RAG tuning",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .background(brush = livingSecondaryButtonBrush())
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    val sheetTps = when {
                        uiState.tpsTelemetry.instantTps > 0f -> uiState.tpsTelemetry.instantTps
                        uiState.tpsTelemetry.sessionAverageTps > 0f -> uiState.tpsTelemetry.sessionAverageTps
                        else -> 0f
                    }
                    val tpsPrefix = if (sheetTps > 0f) "${"%.1f".format(sheetTps)} t/s • " else "not measured • "
                    Text(
                        text = "$tpsPrefix${uiState.telemetry.batteryPercent}% • ${uiState.telemetry.batteryTempCelsius}°C",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 1. Preset Power Modes Row
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Power & Thermal Mode",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PowerProfile.entries.forEach { profile ->
                    val selected = uiState.powerProfile == profile
                    val accent = powerProfileAccentColor(profile)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectPowerProfile(profile) }
                            .testTag("menu_power_${profile.name.lowercase()}"),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Transparent,
                        border = BorderStroke(
                            if (selected) 1.5.dp else 1.dp,
                            if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .background(
                                    brush = if (selected) {
                                        livingActiveItemBrush(accent)
                                    } else {
                                        livingSurfaceCardBrush()
                                    }
                                )
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = profile.displayName,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (selected) accent else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${profile.cpuThreads}T • ${profile.contextLimit} ctx",
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

        // 2. Persona & Pinned RAG Quick Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        if (uiState.personas.isNotEmpty()) {
                            val idx = uiState.personas.indexOfFirst { it.id == uiState.activePersona?.id }
                            val next = uiState.personas[(idx + 1) % uiState.personas.size]
                            onSelectPersona(next)
                        }
                    }
                    .testTag("menu_cycle_persona_card"),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .background(brush = livingSecondaryHeroBrush())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Active Persona",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        text = uiState.activePersona?.name ?: "Leafy Assistant",
                        style = importantTextStyle(MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)),
                        color = importantTextColor(MaterialTheme.colorScheme.onSecondaryContainer),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap to cycle • T=${uiState.activePersona?.temperature ?: 0.65f}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                    TextButton(
                        onClick = { showAddPresetDialog = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("sheet_add_custom_preset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Add Custom Preset", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenFullScreen(LeafyTab.KNOWLEDGE) }
                    .testTag("menu_open_rag_studio_card"),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .background(brush = livingTertiaryHeroBrush())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Local Document RAG",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Text(
                        text = "${uiState.pinnedRagDocuments.size} of ${uiState.ragDocuments.size} Docs Pinned",
                        style = importantTextStyle(MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)),
                        color = importantTextColor(MaterialTheme.colorScheme.onTertiaryContainer),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Manage notes & personas →",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // 3. Typography & Living Gradient Text Toggles
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = livingSurfaceCardBrush())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
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
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FontDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Playful Comic Sans Font",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (uiState.useComicSans) "Comic Neue active" else "Rounded Sans (Quicksand/Nunito) active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = uiState.useComicSans,
                        onCheckedChange = { onToggleComicSans() },
                        modifier = Modifier.testTag("menu_comic_sans_switch")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

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
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Living Gradient on Important Text",
                                style = importantTextStyle(MaterialTheme.typography.titleSmall),
                                color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                            )
                            Text(
                                text = if (uiState.useGradientText) "Botanical gradient headings active" else "Solid heading colors active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = uiState.useGradientText,
                        onCheckedChange = { onToggleGradientText() },
                        modifier = Modifier.testTag("menu_gradient_text_switch")
                    )
                }
            }
        }

        // 4. Dedicated Screen Launchers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onOpenFullScreen(LeafyTab.KNOWLEDGE) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("RAG & Prompts")
            }

            Button(
                onClick = { onOpenFullScreen(LeafyTab.ECO_SETTINGS) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = livingBrightButtonContentColor()
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(brush = livingBrightButtonBrush())
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Power & Telemetry")
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}
