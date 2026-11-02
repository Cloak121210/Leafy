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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.AiRuntime
import com.example.data.LocalModelEntity
import com.example.data.PowerProfile
import com.example.ui.theme.BalancedProfileDark
import com.example.ui.theme.BalancedProfileLight
import com.example.ui.theme.BrightButtonLimeMid
import com.example.ui.theme.BrightButtonMintStart
import com.example.ui.theme.BrightButtonSunEnd
import com.example.ui.theme.BrightCardAquaTint
import com.example.ui.theme.BrightCardMintTint
import com.example.ui.theme.BrightCardSunTint
import com.example.ui.theme.ChlorophyllMintGlow
import com.example.ui.theme.DarkAmberSporeGlow
import com.example.ui.theme.DarkBioluminescentMint
import com.example.ui.theme.DarkCanopySurface
import com.example.ui.theme.DarkCanopyTopGlow
import com.example.ui.theme.DarkCardContainer
import com.example.ui.theme.DarkEmeraldContainer
import com.example.ui.theme.DarkMossBackground
import com.example.ui.theme.DeepForestInk
import com.example.ui.theme.DewdropAquaGlow
import com.example.ui.theme.EcoProfileDark
import com.example.ui.theme.EcoProfileLight
import com.example.ui.theme.ElevatedLeafCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalUseGradientText
import com.example.ui.theme.OnBrightChlorophyll
import com.example.ui.theme.PerformanceProfileDark
import com.example.ui.theme.PerformanceProfileLight
import com.example.ui.theme.RuntimeBitnetDark
import com.example.ui.theme.RuntimeBitnetLight
import com.example.ui.theme.RuntimeExecuTorchDark
import com.example.ui.theme.RuntimeExecuTorchLight
import com.example.ui.theme.RuntimeLlamaCppDark
import com.example.ui.theme.RuntimeLlamaCppLight
import com.example.ui.theme.RuntimeTMacDark
import com.example.ui.theme.RuntimeTMacLight
import com.example.ui.theme.SunbeamMeadowWarm
import com.example.ui.theme.SunbeamSecondaryContainer
import com.example.ui.theme.SunlitCanopyMist
import com.example.ui.theme.SunlitGoldHalo
import com.example.ui.theme.SunlitMorningSky
import com.example.ui.theme.buildImportantTextGradientBrush

fun runtimeAccentColorForTheme(runtime: AiRuntime, isDarkTheme: Boolean): Color = when (runtime) {
    AiRuntime.LLAMA_CPP -> if (isDarkTheme) RuntimeLlamaCppDark else RuntimeLlamaCppLight
    AiRuntime.BITNET_CPP -> if (isDarkTheme) RuntimeBitnetDark else RuntimeBitnetLight
    AiRuntime.T_MAC -> if (isDarkTheme) RuntimeTMacDark else RuntimeTMacLight
    AiRuntime.EXECUTORCH -> if (isDarkTheme) RuntimeExecuTorchDark else RuntimeExecuTorchLight
}

fun powerProfileAccentColorForTheme(profile: PowerProfile, isDarkTheme: Boolean): Color = when (profile) {
    PowerProfile.ECO -> if (isDarkTheme) EcoProfileDark else EcoProfileLight
    PowerProfile.BALANCED -> if (isDarkTheme) BalancedProfileDark else BalancedProfileLight
    PowerProfile.PERFORMANCE -> if (isDarkTheme) PerformanceProfileDark else PerformanceProfileLight
}

@Composable
fun isLeafyDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun runtimeAccentColor(runtime: AiRuntime): Color {
    return runtimeAccentColorForTheme(runtime, isLeafyDarkTheme())
}

@Composable
fun powerProfileAccentColor(profile: PowerProfile): Color {
    return powerProfileAccentColorForTheme(profile, isLeafyDarkTheme())
}

@Composable
fun livingImportantTextBrush(): Brush {
    return buildImportantTextGradientBrush(isLeafyDarkTheme())
}

/**
 * Applies the living botanical multi-stop gradient brush to important headings/titles/values
 * when the user's "Gradient Important Text" toggle is enabled.
 */
@Composable
fun importantTextStyle(baseStyle: TextStyle): TextStyle {
    return if (LocalUseGradientText.current) {
        baseStyle.copy(brush = livingImportantTextBrush())
    } else {
        baseStyle
    }
}

/**
 * Returns Color.Unspecified when Gradient Important Text is enabled so Material3 Text
 * does not overwrite the TextStyle.brush with a flat solid color.
 */
@Composable
fun importantTextColor(fallbackColor: Color): Color {
    return if (LocalUseGradientText.current) {
        Color.Unspecified
    } else {
        fallbackColor
    }
}

@Composable
fun livingTopBarBrush(): Brush {
    val isDark = isLeafyDarkTheme()
    return if (isDark) {
        Brush.horizontalGradient(
            colors = listOf(
                DarkMossBackground.copy(alpha = 0.96f),
                DarkCanopyTopGlow.copy(alpha = 0.96f),
                DarkBioluminescentMint.copy(alpha = 0.92f)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                SunlitMorningSky,
                ChlorophyllMintGlow,
                SunbeamMeadowWarm
            )
        )
    }
}

@Composable
fun livingMenuSheetBrush(): Brush {
    val isDark = isLeafyDarkTheme()
    return if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                DarkCanopyTopGlow,
                DarkMossBackground,
                DarkCanopySurface
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                SunlitMorningSky,
                SunlitCanopyMist,
                SunbeamMeadowWarm
            )
        )
    }
}

@Composable
fun livingSurfaceCardBrush(): Brush {
    val isDark = isLeafyDarkTheme()
    return if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                DarkCardContainer,
                DarkCanopyTopGlow,
                DarkCanopySurface
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                ElevatedLeafCard,
                BrightCardMintTint,
                BrightCardSunTint
            )
        )
    }
}

@Composable
fun livingMetricBoxBrush(): Brush {
    val isDark = isLeafyDarkTheme()
    return if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                DarkCanopySurface,
                DarkBioluminescentMint.copy(alpha = 0.65f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                ElevatedLeafCard,
                BrightCardAquaTint,
                BrightCardMintTint
            )
        )
    }
}

@Composable
fun livingBrightButtonBrush(): Brush {
    val isDark = isLeafyDarkTheme()
    return if (isDark) {
        Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                BrightButtonMintStart,
                BrightButtonLimeMid,
                BrightButtonSunEnd
            )
        )
    }
}

@Composable
fun livingBrightButtonContentColor(): Color {
    return if (isLeafyDarkTheme()) OnBrightChlorophyll else DeepForestInk
}

@Composable
fun livingSecondaryButtonBrush(): Brush {
    val isDark = isLeafyDarkTheme()
    return if (isDark) {
        Brush.horizontalGradient(
            colors = listOf(
                DarkBioluminescentMint,
                DarkCardContainer
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                BrightCardMintTint,
                ChlorophyllMintGlow,
                SunbeamMeadowWarm
            )
        )
    }
}

@Composable
fun livingHeroCardBrush(): Brush {
    val isDark = isLeafyDarkTheme()
    return if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                DarkEmeraldContainer.copy(alpha = 0.75f),
                DarkBioluminescentMint.copy(alpha = 0.88f),
                DarkCardContainer.copy(alpha = 0.92f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                ChlorophyllMintGlow,
                SunlitCanopyMist,
                SunbeamMeadowWarm
            )
        )
    }
}

@Composable
fun livingSecondaryHeroBrush(): Brush {
    val isDark = isLeafyDarkTheme()
    return if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                DarkAmberSporeGlow,
                DarkBioluminescentMint.copy(alpha = 0.78f),
                DarkCanopySurface
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                SunbeamSecondaryContainer.copy(alpha = 0.88f),
                SunbeamMeadowWarm,
                SunlitCanopyMist
            )
        )
    }
}

@Composable
fun livingTertiaryHeroBrush(): Brush {
    val isDark = isLeafyDarkTheme()
    return if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                DarkCanopyTopGlow,
                DarkBioluminescentMint.copy(alpha = 0.82f),
                DarkCardContainer
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                DewdropAquaGlow,
                SunlitCanopyMist,
                SunbeamMeadowWarm
            )
        )
    }
}

@Composable
fun livingActiveItemBrush(accentColor: Color): Brush {
    val isDark = isLeafyDarkTheme()
    val baseCard = MaterialTheme.colorScheme.surfaceContainerLowest
    return if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.24f),
                accentColor.copy(alpha = 0.10f),
                baseCard
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                BrightButtonMintStart,
                ChlorophyllMintGlow,
                SunbeamMeadowWarm
            )
        )
    }
}

@Composable
fun livingPrimaryBubbleBrush(): Brush {
    val isDark = isLeafyDarkTheme()
    return if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                DarkEmeraldContainer,
                DarkBioluminescentMint
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                BrightButtonMintStart.copy(alpha = 0.72f),
                BrightButtonLimeMid.copy(alpha = 0.85f),
                BrightButtonSunEnd.copy(alpha = 0.78f)
            )
        )
    }
}

@Composable
fun livingPrimaryBubbleContentColor(): Color {
    return if (isLeafyDarkTheme()) MaterialTheme.colorScheme.onPrimaryContainer else DeepForestInk
}

/**
 * Luminous, living sunlit botanical conservatory background with multi-stop gradients,
 * warm sunbeam halos, and organic leaf-vein artistry drawn on Canvas behind screens.
 */
@Composable
fun OrganicLeafBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isDark = isLeafyDarkTheme()
    val primaryTint = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.08f else 0.09f)
    val secondaryTint = MaterialTheme.colorScheme.secondary.copy(alpha = if (isDark) 0.06f else 0.10f)
    val tertiaryTint = MaterialTheme.colorScheme.tertiary.copy(alpha = if (isDark) 0.05f else 0.08f)

    val baseBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                DarkMossBackground,
                DarkCanopyTopGlow,
                DarkMossBackground
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                SunlitMorningSky,
                SunlitCanopyMist,
                SunbeamMeadowWarm
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = baseBrush)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Top-left warm golden sunbeam halo in light mode / emerald canopy aura in dark mode
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        if (isDark) primaryTint.copy(alpha = 0.13f) else SunlitGoldHalo.copy(alpha = 0.42f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.12f, h * 0.06f),
                    radius = w * 0.75f
                ),
                center = Offset(w * 0.12f, h * 0.06f),
                radius = w * 0.75f
            )

            // Top-right fresh chlorophyll glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        if (isDark) secondaryTint.copy(alpha = 0.10f) else ChlorophyllMintGlow.copy(alpha = 0.65f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.90f, h * 0.12f),
                    radius = w * 0.68f
                ),
                center = Offset(w * 0.90f, h * 0.12f),
                radius = w * 0.68f
            )

            // Bottom-left dewy botanical mist
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        if (isDark) primaryTint.copy(alpha = 0.11f) else DewdropAquaGlow.copy(alpha = 0.58f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.15f, h * 0.86f),
                    radius = w * 0.72f
                ),
                center = Offset(w * 0.15f, h * 0.86f),
                radius = w * 0.72f
            )

            // Organic upper botanical leaf contour & central vein
            val upperLeafContour = Path().apply {
                moveTo(w * 0.95f, h * 0.01f)
                cubicTo(
                    w * 0.68f, h * 0.08f,
                    w * 0.56f, h * 0.26f,
                    w * 0.62f, h * 0.44f
                )
                cubicTo(
                    w * 0.82f, h * 0.36f,
                    w * 0.92f, h * 0.18f,
                    w * 0.95f, h * 0.01f
                )
            }
            drawPath(
                path = upperLeafContour,
                brush = Brush.linearGradient(
                    colors = listOf(primaryTint.copy(alpha = 0.08f), Color.Transparent),
                    start = Offset(w * 0.95f, 0f),
                    end = Offset(w * 0.60f, h * 0.45f)
                )
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
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
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
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
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
        color = Color.Transparent,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f))
    ) {
        Row(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.14f),
                            accent.copy(alpha = 0.06f)
                        )
                    )
                )
                .padding(horizontal = 8.dp, vertical = 2.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = "${runtime.displayName} • ${runtime.shortBadge}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Medium
                ),
                color = accent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
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
    Box(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
            border = BorderStroke(1.dp, color.copy(alpha = 0.42f))
        ) {
            Row(
                modifier = Modifier
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                color.copy(alpha = 0.14f),
                                color.copy(alpha = 0.06f)
                            )
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
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
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = color,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Provides an outer >= 48dp x 48dp accessible hit area while preserving a compact visual circular Surface inside.
 */
@Composable
private fun TopBarCircularButton(
    onClick: () -> Unit,
    visualSize: Dp,
    containerBrush: Brush,
    border: BorderStroke?,
    testTag: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(visualSize),
            shape = CircleShape,
            color = Color.Transparent,
            border = border
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush = containerBrush),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
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
    onOpenQuickSettingsMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(brush = livingTopBarBrush())
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (isChatFrontScreen) {
                // Left: Organic Canopy Drawer button (48dp touch target, 36dp visual circle)
                TopBarCircularButton(
                    onClick = onOpenCanopyDrawer,
                    visualSize = 36.dp,
                    containerBrush = livingSecondaryButtonBrush(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    testTag = "open_canopy_drawer_button"
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Leafy menu drawer",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Center: Dedicated Local Model Switcher Pill (Strictly for Local Models)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .sizeIn(minHeight = 48.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClick = onOpenModelSwitcher)
                        .testTag("top_model_switcher_pill"),
                    contentAlignment = Alignment.Center
                ) {
                    val hasActiveModel = activeModel != null && activeModel.isInstalled
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = Color.Transparent,
                        shadowElevation = 1.dp,
                        border = BorderStroke(
                            1.dp,
                            if (hasActiveModel) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = if (hasActiveModel) {
                                        livingActiveItemBrush(MaterialTheme.colorScheme.primary)
                                    } else {
                                        livingSurfaceCardBrush()
                                    }
                                )
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            val dotColor = if (hasActiveModel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            val titleText = if (hasActiveModel) activeModel.name else "No ${activeRuntime.fileExtensions.first()} Model"
                            val subtitleText = if (hasActiveModel) {
                                "${activeModel.quantization} • ${activeModel.sizeMb} MB • ${activeRuntime.displayName}"
                            } else {
                                "Load model for ${activeRuntime.displayName}"
                            }

                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start
                            ) {
                                val modelTitleStyle = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (hasActiveModel) FontWeight.SemiBold else FontWeight.Medium
                                )
                                Text(
                                    text = titleText,
                                    style = if (hasActiveModel) importantTextStyle(modelTitleStyle) else modelTitleStyle,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (hasActiveModel) {
                                        importantTextColor(MaterialTheme.colorScheme.onSurface)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
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
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Right: Single "Three Dots" Button for Quick Settings (48dp touch target, 36dp visual circle)
                TopBarCircularButton(
                    onClick = onOpenQuickSettingsMenu,
                    visualSize = 36.dp,
                    containerBrush = livingSecondaryButtonBrush(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)),
                    testTag = "open_quick_settings_button"
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Quick Settings",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                // Secondary Screen Header with clean Back to Chat button (48dp touch target, 38dp visual circle)
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TopBarCircularButton(
                            onClick = onNavigateBackToChat,
                            visualSize = 38.dp,
                            containerBrush = livingSecondaryButtonBrush(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
                            testTag = "back_to_chat_button"
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Chat",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = secondaryScreenTitle,
                                style = importantTextStyle(MaterialTheme.typography.titleLarge),
                                color = importantTextColor(MaterialTheme.colorScheme.onBackground),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Leafy Offline Studio",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    PowerProfilePill(
                        profile = powerProfile,
                        onClick = null
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
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Transparent,
                    shadowElevation = 1.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .background(brush = livingTertiaryHeroBrush())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EnergySavingsLeaf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(13.dp)
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

/**
 * Real-time Canvas sparkline waveform visualizing live and historical TPS throughput.
 */
@Composable
fun LiveTpsSparklineCanvas(
    tpsSeries: List<Float>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Reference horizontal guide lines (25%, 50%, 75%)
        listOf(0.25f, 0.5f, 0.75f).forEach { ratio ->
            val y = h * ratio
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val cleanPoints = tpsSeries.filter { it.isFinite() && it >= 0f }
        if (cleanPoints.isEmpty()) {
            val midY = h * 0.65f
            drawLine(
                color = accentColor.copy(alpha = 0.35f),
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            return@Canvas
        }

        val displayPoints = if (cleanPoints.size == 1) {
            listOf(cleanPoints.first(), cleanPoints.first())
        } else {
            cleanPoints
        }

        val maxVal = (displayPoints.maxOrNull() ?: 10f).coerceAtLeast(5f) * 1.15f
        val minVal = 0f
        val range = (maxVal - minVal).coerceAtLeast(1f)
        val verticalPad = h * 0.12f
        val usableH = (h - verticalPad * 2f).coerceAtLeast(1f)

        val coordinates = displayPoints.mapIndexed { idx, value ->
            val x = (idx.toFloat() / (displayPoints.size - 1).coerceAtLeast(1)) * w
            val normalized = ((value - minVal) / range).coerceIn(0f, 1f)
            val y = (h - verticalPad) - (normalized * usableH)
            Offset(x, y)
        }

        val strokePath = Path().apply {
            moveTo(coordinates.first().x, coordinates.first().y)
            for (i in 1 until coordinates.size) {
                val prev = coordinates[i - 1]
                val curr = coordinates[i]
                val controlX = (prev.x + curr.x) / 2f
                cubicTo(controlX, prev.y, controlX, curr.y, curr.x, curr.y)
            }
        }

        val fillPath = Path().apply {
            addPath(strokePath)
            lineTo(coordinates.last().x, h)
            lineTo(coordinates.first().x, h)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.30f),
                    accentColor.copy(alpha = 0.02f)
                ),
                startY = 0f,
                endY = h
            )
        )

        drawPath(
            path = strokePath,
            color = accentColor,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        )

        val lastPt = coordinates.last()
        drawCircle(
            color = accentColor.copy(alpha = 0.28f),
            radius = 6.dp.toPx(),
            center = lastPt
        )
        drawCircle(
            color = accentColor,
            radius = 3.2.dp.toPx(),
            center = lastPt
        )
    }
}
