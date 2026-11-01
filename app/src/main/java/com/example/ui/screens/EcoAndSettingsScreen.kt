package com.example.ui.screens

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.PowerProfile
import com.example.engine.InferenceTelemetryPhase
import com.example.ui.components.LiveTpsSparklineCanvas
import com.example.ui.components.importantTextColor
import com.example.ui.components.importantTextStyle
import com.example.ui.components.livingActiveItemBrush
import com.example.ui.components.livingBrightButtonBrush
import com.example.ui.components.livingBrightButtonContentColor
import com.example.ui.components.livingHeroCardBrush
import com.example.ui.components.livingMetricBoxBrush
import com.example.ui.components.livingSecondaryButtonBrush
import com.example.ui.components.livingSurfaceCardBrush
import com.example.ui.components.livingTertiaryHeroBrush
import com.example.ui.components.powerProfileAccentColor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.LeafyUiState

@Composable
fun EcoAndSettingsScreen(
    uiState: LeafyUiState,
    onSelectPowerProfile: (PowerProfile) -> Unit,
    onSetComicSans: (Boolean) -> Unit,
    onSetGradientText: (Boolean) -> Unit = {},
    onToggleDarkTheme: () -> Unit,
    onWipeOfflineData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry = uiState.telemetry
    val tpsTelemetry = uiState.tpsTelemetry
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
                .testTag("eco_settings_list"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // 0. Live TPS & Inference Performance Telemetry Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live_tps_telemetry_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(brush = livingTertiaryHeroBrush())
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
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
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(19.dp)
                                )
                                Text(
                                    text = "Live TPS & Inference Telemetry",
                                    style = importantTextStyle(MaterialTheme.typography.titleLarge),
                                    color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                                )
                            }
                            val statusBadgeText = when (tpsTelemetry.phase) {
                                InferenceTelemetryPhase.PREFILLING -> "PREFILLING"
                                InferenceTelemetryPhase.DECODING -> "LIVE STREAM"
                                InferenceTelemetryPhase.MOUNTING -> "MOUNTING"
                                InferenceTelemetryPhase.COMPLETED -> "MEASURED"
                                InferenceTelemetryPhase.IDLE -> "STANDBY"
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = statusBadgeText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        val primaryTps = when {
                            tpsTelemetry.instantTps > 0f -> tpsTelemetry.instantTps
                            tpsTelemetry.decodeTps > 0f -> tpsTelemetry.decodeTps
                            else -> tpsTelemetry.sessionAverageTps
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            val peakLabel = if (tpsTelemetry.sessionPeakTps > 0f) {
                                "${"%.1f".format(tpsTelemetry.sessionPeakTps)} t/s"
                            } else {
                                "not measured"
                            }
                            val avgLabel = if (tpsTelemetry.sessionAverageTps > 0f) {
                                "${"%.1f".format(tpsTelemetry.sessionAverageTps)} t/s"
                            } else {
                                "not measured"
                            }
                            TelemetryMetricBox(
                                title = "Decode Velocity",
                                value = if (primaryTps > 0f) "${"%.1f".format(primaryTps)} tok/s" else "not measured",
                                subtitle = "Peak $peakLabel • Avg $avgLabel",
                                modifier = Modifier.weight(1f)
                            )
                            val lastTtftLabel = if (tpsTelemetry.lastTtftMs > 0) "${tpsTelemetry.lastTtftMs} ms" else "not measured"
                            val avgTtftLabel = if (tpsTelemetry.averageTtftMs > 0) "${tpsTelemetry.averageTtftMs} ms" else "not measured"
                            TelemetryMetricBox(
                                title = "Latency & TTFT",
                                value = if (tpsTelemetry.interTokenLatencyMs > 0f) {
                                    "${"%.1f".format(tpsTelemetry.interTokenLatencyMs)} ms/tok"
                                } else {
                                    "not measured"
                                },
                                subtitle = "TTFT: $lastTtftLabel (Avg $avgTtftLabel)",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            val promptTokLabel = if (tpsTelemetry.promptTokensCount > 0) {
                                "${tpsTelemetry.promptTokensCount} prompt"
                            } else {
                                "prompt not measured"
                            }
                            TelemetryMetricBox(
                                title = "Prefill & Volume",
                                value = if (tpsTelemetry.prefillTps > 0f) {
                                    "${"%.1f".format(tpsTelemetry.prefillTps)} tok/s"
                                } else {
                                    "not measured"
                                },
                                subtitle = "$promptTokLabel • ${tpsTelemetry.totalSessionTokens} session tok",
                                modifier = Modifier.weight(1f)
                            )
                            val tokPerMwhLabel = if (tpsTelemetry.tokensPerMilliwattHour > 0f) {
                                "${"%.0f".format(tpsTelemetry.tokensPerMilliwattHour)} tok/mWh"
                            } else {
                                "not measured"
                            }
                            TelemetryMetricBox(
                                title = "Token Efficiency",
                                value = when {
                                    tpsTelemetry.energyPerTokenMicroWh > 0f ->
                                        "${"%.1f".format(tpsTelemetry.energyPerTokenMicroWh)} µWh/tok"
                                    tpsTelemetry.energyMicroWh > 0L ->
                                        "${tpsTelemetry.energyMicroWh} µWh"
                                    else -> "not measured"
                                },
                                subtitle = "$tokPerMwhLabel • ${tpsTelemetry.completedRunsCount} runs",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (tpsTelemetry.tpsHistory.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Live Token Velocity Waveform",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Max ${"%.1f".format((tpsTelemetry.tpsHistory.maxOrNull() ?: 0f).coerceAtLeast(tpsTelemetry.sessionPeakTps))} tok/s",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                LiveTpsSparklineCanvas(
                                    tpsSeries = tpsTelemetry.tpsHistory,
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 1. Live Battery & Hardware Telemetry Dashboard
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
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
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
                                    imageVector = Icons.Default.BatteryChargingFull,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(19.dp)
                                )
                                Text(
                                    text = "Live Hardware Telemetry",
                                    style = importantTextStyle(MaterialTheme.typography.titleLarge),
                                    color = importantTextColor(MaterialTheme.colorScheme.onPrimaryContainer)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.Transparent,
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
                            ) {
                                Text(
                                    text = if (telemetry.isHardwarePowerMeasured) "Fuel Gauge: Active" else "Power Sensor: Unmeasured",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(brush = livingMetricBoxBrush())
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            TelemetryMetricBox(
                                title = "Battery Level",
                                value = if (telemetry.batteryPercent > 0) "${telemetry.batteryPercent}%" else "not measured",
                                subtitle = if (telemetry.batteryVoltageMv > 0) {
                                    "${telemetry.batteryVoltageMv} mV • ${if (telemetry.isCharging) "Charging" else "Discharging"}"
                                } else {
                                    "Voltage: not measured"
                                },
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryMetricBox(
                                title = "Thermal & Power",
                                value = if (telemetry.batteryTempCelsius > 0f) "${telemetry.batteryTempCelsius}°C" else "not measured",
                                subtitle = if (telemetry.isHardwarePowerMeasured && telemetry.instantaneousMilliwatts > 0) {
                                    "${telemetry.instantaneousMilliwatts} mW draw (hardware)"
                                } else {
                                    "Power draw: not measured"
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            TelemetryMetricBox(
                                title = "Device Memory",
                                value = if (telemetry.availableRamMb > 0) "${telemetry.availableRamMb} MB Free" else "not measured",
                                subtitle = if (telemetry.totalRamMb > 0) {
                                    "${telemetry.usedRamMb} / ${telemetry.totalRamMb} MB used"
                                } else {
                                    "RAM: not measured"
                                },
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryMetricBox(
                                title = "CPU Topology",
                                value = "${telemetry.cpuCores} Cores",
                                subtitle = "${uiState.powerProfile.cpuThreads} active threads",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        val ramRatio = if (telemetry.totalRamMb > 0) {
                            (telemetry.usedRamMb.toFloat() / telemetry.totalRamMb.toFloat()).coerceIn(0.05f, 0.98f)
                        } else {
                            0.05f
                        }
                        LinearProgressIndicator(
                            progress = { ramRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // 2. Preset Power Profiles (Eco, Balanced, Performance)
            item {
                Text(
                    text = "Preset Thread & Context Profiles",
                    style = importantTextStyle(MaterialTheme.typography.titleMedium),
                    color = importantTextColor(MaterialTheme.colorScheme.onBackground)
                )
                Text(
                    text = "Configure CPU thread count and context window limit passed to the native inference engine.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    PowerProfile.entries.forEach { profile ->
                        val selected = uiState.powerProfile == profile
                        val accent = powerProfileAccentColor(profile)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPowerProfile(profile) }
                                .testTag("power_profile_card_${profile.name.lowercase()}"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            ),
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        brush = if (selected) {
                                            livingActiveItemBrush(accent)
                                        } else {
                                            livingSurfaceCardBrush()
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (profile) {
                                                PowerProfile.ECO -> Icons.Default.EnergySavingsLeaf
                                                PowerProfile.BALANCED -> Icons.Default.Speed
                                                PowerProfile.PERFORMANCE -> Icons.Default.Bolt
                                            },
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = profile.displayName,
                                                style = importantTextStyle(MaterialTheme.typography.titleSmall),
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                                color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                                            )
                                            Text(
                                                text = profile.badgeText,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                color = accent
                                            )
                                        }
                                    }

                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected profile",
                                            tint = accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = profile.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = "Threads: ${profile.cpuThreads} • Context Cap: ${profile.contextLimit} tok",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 3. Typography & Visual Theme Studio (Rounded Clean Sans-Serif vs Playful Comic Sans + Gradient Text Toggle)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(brush = livingSurfaceCardBrush())
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FontDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Typography & Readability Studio",
                                    style = importantTextStyle(MaterialTheme.typography.titleMedium),
                                    color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                                )
                                Text(
                                    text = "Choose between clean rounded sans-serif or playful Comic Sans, and toggle gradient text",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onSetComicSans(false) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = if (!uiState.useComicSans) {
                                        livingBrightButtonContentColor()
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        brush = if (!uiState.useComicSans) {
                                            livingBrightButtonBrush()
                                        } else {
                                            livingSecondaryButtonBrush()
                                        }
                                    )
                                    .testTag("select_rounded_sans_button")
                            ) {
                                Text("Rounded Sans")
                            }

                            Button(
                                onClick = { onSetComicSans(true) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = if (uiState.useComicSans) {
                                        livingBrightButtonContentColor()
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        brush = if (uiState.useComicSans) {
                                            livingBrightButtonBrush()
                                        } else {
                                            livingSecondaryButtonBrush()
                                        }
                                    )
                                    .testTag("select_comic_sans_button")
                            ) {
                                Text("Comic Sans Mode")
                            }
                        }

                        // Living Gradient on Important Text Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Living Gradient on Important Text",
                                        style = importantTextStyle(MaterialTheme.typography.titleSmall),
                                        color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                                    )
                                    Text(
                                        text = if (uiState.useGradientText) {
                                            "Botanical multi-stop gradient enabled on key headings & titles"
                                        } else {
                                            "Solid theme ink colors on headings & titles"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = uiState.useGradientText,
                                onCheckedChange = { onSetGradientText(it) },
                                modifier = Modifier.testTag("gradient_text_switch")
                            )
                        }

                        // Dark Moss Canopy Mode Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Dark Moss Canopy Mode",
                                        style = importantTextStyle(MaterialTheme.typography.titleSmall),
                                        color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                                    )
                                    Text(
                                        text = if (uiState.isDarkTheme) "OLED Dark Canopy active" else "Bright Sunlit Botanical active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = uiState.isDarkTheme,
                                onCheckedChange = { onToggleDarkTheme() },
                                modifier = Modifier.testTag("dark_theme_switch")
                            )
                        }
                    }
                }
            }

            // 4. Complete Data Sovereignty & Offline Privacy Controls
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(brush = livingSurfaceCardBrush())
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "100% Data Sovereignty & Zero Telemetry",
                                    style = importantTextStyle(MaterialTheme.typography.titleMedium),
                                    color = importantTextColor(MaterialTheme.colorScheme.onSurface)
                                )
                                Text(
                                    text = "All conversations, branches, and RAG documents live strictly inside your device's encrypted Room SQLite sandbox.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onWipeOfflineData,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wipe_offline_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Reset & Wipe Local Chat History",
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Leafy Offline Studio",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Version ${com.example.BuildConfig.VERSION_NAME} (Build ${com.example.BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("settings_version_label")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryMetricBox(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = Color.Transparent,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier
                .background(brush = livingMetricBoxBrush())
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(1.5.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = importantTextStyle(
                    MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                ),
                color = importantTextColor(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
