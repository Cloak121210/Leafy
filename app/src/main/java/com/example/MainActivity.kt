package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                useComicSans = uiState.useComicSans
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
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                style = MaterialTheme.typography.titleLarge
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
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "No local model files loaded yet",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
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
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
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
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = model.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
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
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    Spacer(modifier = Modifier.height(12.dp))
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
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                            text = "Inference Runtimes",
                            style = MaterialTheme.typography.titleLarge
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
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                RuntimeBadgePill(runtime = runtime)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = runtime.architectureNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
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

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/**
 * Left Botanical Canopy Drawer: Separate dedicated items for Local Models, Inference Runtimes, RAG & Personas, and Power Modes & Settings.
 */
@Composable
private fun LeafyCanopyNavigationDrawer(
    uiState: LeafyUiState,
    onSelectConversation: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onNavigateToScreen: (LeafyTab) -> Unit,
    onToggleComicSans: () -> Unit,
    onToggleDarkTheme: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.EnergySavingsLeaf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
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
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = BuildConfig.VERSION_NAME,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
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

                Button(
                    onClick = onNewConversation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("drawer_new_thread_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Offline Conversation")
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "drawer_workspaces_header") {
                        Text(
                            text = "WORKSPACES & ENGINES",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item(key = "drawer_workspace_chat") {
                        DrawerWorkspaceCard(
                            icon = Icons.Default.ChatBubbleOutline,
                            title = "Chat Studio",
                            subtitle = uiState.activeConversation?.title ?: "Active local conversation",
                            isSelected = uiState.currentTab == LeafyTab.CHAT,
                            onClick = { onNavigateToScreen(LeafyTab.CHAT) },
                            testTag = "drawer_nav_chat"
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
                            subtitle = "Active: ${uiState.activeRuntime.displayName} (llama.cpp, Bitnet, T-MAC, ExecuTorch)",
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
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
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
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "No saved threads yet",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Start a prompt in Chat Studio to create your first offline session.",
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
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSelectConversation(conv.id) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
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
                                            imageVector = Icons.Default.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = conv.title,
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
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
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onToggleComicSans)
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                            .testTag("drawer_comic_sans_toggle"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FontDownload,
                            contentDescription = "Toggle Comic Sans",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (uiState.useComicSans) "Comic Sans: ON" else "Rounded Sans",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                            contentDescription = "Toggle dark theme"
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
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
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
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Offline thermal modes, persona & RAG tuning",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp)
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
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // 1. Preset Power Modes Row
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelectPowerProfile(profile) }
                            .testTag("menu_power_${profile.name.lowercase()}"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = profile.displayName,
                                style = MaterialTheme.typography.labelLarge,
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
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        if (uiState.personas.isNotEmpty()) {
                            val idx = uiState.personas.indexOfFirst { it.id == uiState.activePersona?.id }
                            val next = uiState.personas[(idx + 1) % uiState.personas.size]
                            onSelectPersona(next)
                        }
                    }
                    .testTag("menu_cycle_persona_card"),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
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
                            text = "Active Persona",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        text = uiState.activePersona?.name ?: "Leafy Assistant",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap to cycle • T=${uiState.activePersona?.temperature ?: 0.65f}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    TextButton(
                        onClick = { showAddPresetDialog = true },
                        modifier = Modifier.testTag("sheet_add_custom_preset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Custom Preset", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onOpenFullScreen(LeafyTab.KNOWLEDGE) }
                    .testTag("menu_open_rag_studio_card"),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Local Document RAG",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Text(
                        text = "${uiState.pinnedRagDocuments.size} of ${uiState.ragDocuments.size} Docs Pinned",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Manage notes & personas →",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // 3. Typography Toggle
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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
                        tint = MaterialTheme.colorScheme.primary
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
        }

        // 4. Dedicated Screen Launchers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onOpenFullScreen(LeafyTab.KNOWLEDGE) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("RAG & Prompts")
            }

            Button(
                onClick = { onOpenFullScreen(LeafyTab.ECO_SETTINGS) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Power & Telemetry")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}
