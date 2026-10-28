package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AiRuntime
import com.example.data.LocalModelEntity
import com.example.data.PowerProfile
import com.example.engine.HardwareTelemetrySnapshot
import com.example.ui.theme.BalancedProfileAmber
import com.example.ui.theme.EcoProfileGreen
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PerformanceProfileCoral
import com.example.ui.theme.RuntimeBitnetColor
import com.example.ui.theme.RuntimeExecuTorchColor
import com.example.ui.theme.RuntimeLlamaCppColor
import com.example.ui.theme.RuntimeTMacColor

fun runtimeAccentColor(runtime: AiRuntime): Color = when (runtime) {
    AiRuntime.LLAMA_CPP -> RuntimeLlamaCppColor
    AiRuntime.BITNET_CPP -> RuntimeBitnetColor
    AiRuntime.T_MAC -> RuntimeTMacColor
    AiRuntime.EXECUTORCH -> RuntimeExecuTorchColor
}

fun powerProfileAccentColor(profile: PowerProfile): Color = when (profile) {
    PowerProfile.ECO -> EcoProfileGreen
    PowerProfile.BALANCED -> BalancedProfileAmber
    PowerProfile.PERFORMANCE -> PerformanceProfileCoral
}

/**
 * Subtle organic leaf-vein and sunlit contour watermark drawn on Canvas behind screens.
 */
@Composable
fun OrganicLeafBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val primaryTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.055f)
    val secondaryTint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.045f)
    val tertiaryTint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.04f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondaryTint.copy(alpha = 0.09f), Color.Transparent),
                    center = Offset(w * 0.88f, h * 0.08f),
                    radius = w * 0.65f
                ),
                center = Offset(w * 0.88f, h * 0.08f),
                radius = w * 0.65f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryTint.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(w * 0.12f, h * 0.85f),
                    radius = w * 0.7f
                ),
                center = Offset(w * 0.12f, h * 0.85f),
                radius = w * 0.7f
            )

            val leafStem = Path().apply {
                moveTo(w * 0.92f, h * 0.02f)
                cubicTo(
                    w * 0.72f, h * 0.14f,
                    w * 0.84f, h * 0.32f,
                    w * 0.62f, h * 0.44f
                )
            }
            drawPath(
                path = leafStem,
                color = primaryTint,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            val lowerVein = Path().apply {
                moveTo(w * 0.04f, h * 0.72f)
                cubicTo(
                    w * 0.25f, h * 0.65f,
                    w * 0.35f, h * 0.82f,
                    w * 0.58f, h * 0.76f
                )
            }
            drawPath(
                path = lowerVein,
                color = tertiaryTint,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        content()
    }
}

@Composable
fun RuntimeBadgePill(
    runtime: AiRuntime,
    modifier: Modifier = Modifier
) {
    val accent = runtimeAccentColor(runtime)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = "${runtime.displayName} • ${runtime.shortBadge}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = accent
            )
        }
    }
}

@Composable
fun PowerProfilePill(
    profile: PowerProfile,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val color = powerProfileAccentColor(profile)
    Surface(
        modifier = modifier
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = when (profile) {
                    PowerProfile.ECO -> Icons.Default.EnergySavingsLeaf
                    PowerProfile.BALANCED -> Icons.Default.Speed
                    PowerProfile.PERFORMANCE -> Icons.Default.Bolt
                },
                contentDescription = profile.displayName,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = profile.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Clean floating Top Bar for the front-and-center Chat interface.
 * Keeps Model selection, Runtime selection, and Power Mode / Studio Controls in completely separate menus.
 */
@Composable
fun LeafyCleanTopBar(
    isChatFrontScreen: Boolean,
    secondaryScreenTitle: String,
    activeModel: LocalModelEntity?,
    activeRuntime: AiRuntime,
    powerProfile: PowerProfile,
    statusBanner: String?,
    onOpenCanopyDrawer: () -> Unit,
    onNavigateBackToChat: () -> Unit,
    onOpenModelSwitcher: () -> Unit,
    onOpenRuntimeSwitcher: () -> Unit,
    onOpenSpecialCommandMenu: () -> Unit,
    onOpenSettingsScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isChatFrontScreen) {
                // Left: Organic Canopy Drawer button
                Surface(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenCanopyDrawer)
                        .testTag("open_canopy_drawer_button"),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Leafy menu drawer",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // Center: Dedicated Local Model Switcher Pill (Strictly for Local Models)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onOpenModelSwitcher)
                        .testTag("top_model_switcher_pill"),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val hasActiveModel = activeModel != null && activeModel.isInstalled
                        val dotColor = if (hasActiveModel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        val titleText = if (hasActiveModel) activeModel.name else "No ${activeRuntime.fileExtensions.first()} Model"
                        val subtitleText = if (hasActiveModel) {
                            "${activeModel.quantization} • ${activeModel.sizeMb} MB • ${activeRuntime.displayName}"
                        } else {
                            "Load model for ${activeRuntime.displayName}"
                        }

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (hasActiveModel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "Switch local model",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Right: Dedicated Runtime Switcher Button + Power Mode Controls + Settings
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Dedicated Runtime Engine Menu Button (Separate from Models & Modes)
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenRuntimeSwitcher)
                            .testTag("top_runtime_switcher_button"),
                        shape = CircleShape,
                        color = runtimeAccentColor(activeRuntime).copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, runtimeAccentColor(activeRuntime).copy(alpha = 0.45f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "Switch Inference Runtime (${activeRuntime.displayName})",
                                tint = runtimeAccentColor(activeRuntime),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Power Modes & Studio Controls Button
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenSpecialCommandMenu)
                            .testTag("open_special_menu_button"),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Power Modes & Studio Controls",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenSettingsScreen)
                            .testTag("open_settings_screen_button"),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings & Eco Studio",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                // Secondary Screen Header with clean Back to Chat button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onNavigateBackToChat)
                                .testTag("back_to_chat_button"),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Chat",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Column {
                            Text(
                                text = secondaryScreenTitle,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Leafy Offline Studio",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    PowerProfilePill(
                        profile = powerProfile,
                        onClick = onOpenSpecialCommandMenu
                    )
                }
            }
        }

        // Transient status feedback pill
        AnimatedVisibility(
            visible = statusBanner != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EnergySavingsLeaf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = statusBanner.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}
