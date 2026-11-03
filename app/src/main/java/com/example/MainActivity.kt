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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.rememberDrawerState
import com.example.data.PowerProfile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    var showQuickSettingsMenu by rememberSaveable { mutableStateOf(false) }
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
                }
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
                        LeafyTab.KNOWLEDGE -> "Local RAG & Presets Studio"
                        LeafyTab.ECO_SETTINGS -> "Settings & Telemetry"
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
                        onOpenQuickSettingsMenu = { showQuickSettingsMenu = true }
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
                                onNavigateToModels = { viewModel.selectTab(LeafyTab.MODELS) },
                                onOpenRuntimeSwitcher = { showRuntimeSwitcherSheet = true },
                                onToggleThinking = viewModel::toggleThinking
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
                                onSaveOrUpdatePreset = viewModel::saveOrUpdatePreset,
                                onDeleteCustomPersona = viewModel::deleteCustomPersona
                            )

                            LeafyTab.ECO_SETTINGS -> EcoAndSettingsScreen(
                                uiState = uiState,
                                onSelectPowerProfile = viewModel::setPowerProfile,
                                onSetComicSans = viewModel::setComicSans,
                                onSetGradientText = viewModel::setGradientText,
                                onSetShowChatStatusBar = viewModel::setShowChatStatusBar,
                                onWipeOfflineData = viewModel::wipeAllOfflineData
                            )
                        }
                    }
                }
            }
        }
    }

    // 1. Quick Settings Right-Slide Menu (Opened via the Three-Dots button on the Chat Screen)
    BackHandler(enabled = showQuickSettingsMenu) {
        showQuickSettingsMenu = false
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showQuickSettingsMenu,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { showQuickSettingsMenu = false }
                    .testTag("quick_settings_scrim")
            )
        }

        AnimatedVisibility(
            visible = showQuickSettingsMenu,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            LeafyQuickSettingsSideMenu(
                uiState = uiState,
                onDismiss = { showQuickSettingsMenu = false },
                onSelectPowerProfile = viewModel::setPowerProfile,
                onSetNPredictUnlimited = viewModel::setNPredictUnlimited,
                onSetCustomNPredict = viewModel::setCustomNPredict,
                onSelectPreset = viewModel::selectPersona,
                onSetContextSize = viewModel::setContextSize,
                onSetIncludeThinkingInContext = viewModel::setIncludeThinkingInContext,
                onSetKvCacheType = viewModel::setKvCacheType,
                onToggleDarkTheme = viewModel::toggleDarkTheme
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
    onNavigateToScreen: (LeafyTab) -> Unit
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
                            onClick = null
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
                            title = "RAG Knowledge & Presets",
                            subtitle = "${uiState.pinnedRagDocuments.size} docs pinned • ${uiState.activePersona?.name ?: "llama.cpp Default"}",
                            isSelected = uiState.currentTab == LeafyTab.KNOWLEDGE,
                            onClick = { onNavigateToScreen(LeafyTab.KNOWLEDGE) },
                            testTag = "drawer_nav_knowledge"
                        )
                    }

                    item(key = "drawer_workspace_eco") {
                        DrawerWorkspaceCard(
                            icon = Icons.Default.Settings,
                            title = "Settings & Telemetry",
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
 * Quick Settings Left-Slide Menu (Opened via the Three-Dots button on the Chat Screen):
 * Contains strictly the 7 requested items:
 * 1. Power Modes (Eco Leaf, Balanced Canopy, Sunlit Turbo)
 * 2. N Predict (Maximum token generation limit: Unlimited vs Custom)
 * 3. Active Preset (renamed from Active Persona)
 * 4. Context Size Slider (Min = 2k, Max = 128k)
 * 5. Include Thinking in Context Toggle
 * 6. KV Cache Type (default Q8)
 * 7. Light Mode / Dark Mode Toggle
 * (Local Document RAG Box, RAG & Personas button, and All Settings button removed.)
 */
@Composable
private fun LeafyQuickSettingsSideMenu(
    uiState: LeafyUiState,
    onDismiss: () -> Unit,
    onSelectPowerProfile: (PowerProfile) -> Unit,
    onSetNPredictUnlimited: (Boolean) -> Unit,
    onSetCustomNPredict: (Int) -> Unit,
    onSelectPreset: (com.example.data.PersonaPresetEntity) -> Unit,
    onSetContextSize: (Int) -> Unit,
    onSetIncludeThinkingInContext: (Boolean) -> Unit,
    onSetKvCacheType: (String) -> Unit,
    onToggleDarkTheme: () -> Unit
) {
    var customNPredictText by rememberSaveable(uiState.customNPredict) {
        mutableStateOf(uiState.customNPredict.toString())
    }
    val contextStepsK = rememberSaveable(uiState.contextSize) {
        (uiState.contextSize / 1024).coerceIn(2, 128)
    }

    Surface(
        modifier = Modifier
            .width(330.dp)
            .fillMaxHeight()
            .testTag("quick_settings_right_side_menu"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = livingMenuSheetBrush())
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quick Generation Menu",
                        style = importantTextStyle(MaterialTheme.typography.titleLarge),
                        color = importantTextColor(MaterialTheme.colorScheme.onSurface)
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
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("quick_settings_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close quick settings menu",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            HorizontalDivider()

            // 1. Power Modes: Eco Leaf, Balanced Canopy, Sunlit Turbo
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "POWER MODES",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                PowerProfile.entries.forEach { profile ->
                    val isSelected = uiState.powerProfile == profile
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectPowerProfile(profile) }
                            .testTag("quick_menu_power_${profile.id}"),
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
                                .padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = profile.displayName,
                                    style = importantTextStyle(
                                        MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                        )
                                    ),
                                    color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                                )
                                Text(
                                    text = profile.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected power mode",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. N Predict (Maximum Token Generation Limit)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(brush = livingSurfaceCardBrush())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "N PREDICT",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Maximum number of tokens to generate. Set to Unlimited for no limit, or Custom to specify a value.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val unlSelected = uiState.nPredictUnlimited
                        Button(
                            onClick = { onSetNPredictUnlimited(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (unlSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (unlSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_menu_n_predict_unlimited")
                        ) {
                            Text("Unlimited")
                        }
                        Button(
                            onClick = { onSetNPredictUnlimited(false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!unlSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (!unlSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_menu_n_predict_custom")
                        ) {
                            Text("Custom")
                        }
                    }
                    if (!uiState.nPredictUnlimited) {
                        OutlinedTextField(
                            value = customNPredictText,
                            onValueChange = { raw ->
                                customNPredictText = raw
                                raw.trim().toIntOrNull()?.let { parsed ->
                                    onSetCustomNPredict(parsed)
                                }
                            },
                            label = { Text("Max Tokens (n_predict)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_menu_n_predict_input")
                        )
                    }
                }
            }

            // 3. Active Preset (renamed from Active Persona)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        if (uiState.personas.isNotEmpty()) {
                            val idx = uiState.personas.indexOfFirst { it.id == uiState.activePersona?.id }
                            val next = uiState.personas[(idx + 1) % uiState.personas.size]
                            onSelectPreset(next)
                        }
                    }
                    .testTag("menu_cycle_persona_card"),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(brush = livingSecondaryHeroBrush())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Active Preset",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        text = uiState.activePersona?.name ?: "llama.cpp Default",
                        style = importantTextStyle(MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)),
                        color = importantTextColor(MaterialTheme.colorScheme.onSecondaryContainer)
                    )
                    val activePreset = uiState.activePersona
                    Text(
                        text = "Tap to cycle preset • T=${"%.2f".format(activePreset?.temperature ?: 0.80f)} • Top-K=${activePreset?.topK ?: 40} • Top-P=${"%.2f".format(activePreset?.topP ?: 0.95f)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            // 4. Context Size Slider: Min = 2k, Max = 128k
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(brush = livingSurfaceCardBrush())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CONTEXT SIZE",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${contextStepsK}k (${uiState.contextSize} tok)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Min = 2k (2048), Max = 128k (131072)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = contextStepsK.toFloat(),
                        onValueChange = { kVal ->
                            val snappedK = kVal.toInt().coerceIn(2, 128)
                            onSetContextSize(snappedK * 1024)
                        },
                        valueRange = 2f..128f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_menu_context_size_slider")
                    )
                }
            }

            // 5. Include Thinking in Context Toggle
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(brush = livingSurfaceCardBrush())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "INCLUDE THINKING IN CONTEXT",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Include AI thinking/reasoning parts in the context sent to the model.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.includeThinkingInContext,
                        onCheckedChange = onSetIncludeThinkingInContext,
                        modifier = Modifier.testTag("quick_menu_include_thinking_switch")
                    )
                }
            }

            // 6. KV Cache Type (default Q8)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(brush = livingSurfaceCardBrush())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "KV CACHE TYPE",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Default: Q8",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    val kvOptions = listOf(
                        "Q8_0" to "Q8",
                        "F16" to "F16",
                        "Q4_0" to "Q4",
                        "F32" to "F32"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        kvOptions.forEach { (typeCode, label) ->
                            val selected = uiState.kvCacheType.equals(typeCode, ignoreCase = true)
                            Button(
                                onClick = { onSetKvCacheType(typeCode) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_menu_kv_${label.lowercase()}")
                            ) {
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // 7. Light Mode / Dark Mode Toggle as usual
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(brush = livingSurfaceCardBrush())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
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
                            imageVector = if (uiState.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Light / Dark Mode",
                                style = importantTextStyle(MaterialTheme.typography.titleSmall),
                                color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                            )
                            Text(
                                text = if (uiState.isDarkTheme) {
                                    "Dark Moss Canopy active"
                                } else {
                                    "Light Sunlit Botanical active"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = uiState.isDarkTheme,
                        onCheckedChange = { onToggleDarkTheme() },
                        modifier = Modifier.testTag("quick_settings_theme_switch")
                    )
                }
            }
        }
    }
}
