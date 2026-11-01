package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Memory
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.PowerProfile
import com.example.engine.InferenceTelemetryPhase
import com.example.ui.components.LiveTpsSparklineCanvas
import com.example.ui.components.powerProfileAccentColor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.LeafyUiState

@Composable
fun EcoAndSettingsScreen(
    uiState: LeafyUiState,
    onSelectPowerProfile: (PowerProfile) -> Unit,
    onSetComicSans: (Boolean) -> Unit,
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 0. Live TPS & Inference Performance Telemetry Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live_tps_telemetry_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Live TPS & Inference Telemetry",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
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
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                    .height(52.dp)
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
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Live Hardware Telemetry",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = if (telemetry.isHardwarePowerMeasured) "Fuel Gauge: Active" else "Power Sensor: Unmeasured",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                            .height(8.dp)
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
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Configure CPU thread count and context window limit passed to the native inference engine.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PowerProfile.entries.forEach { profile ->
                    val selected = uiState.powerProfile == profile
                    val accent = powerProfileAccentColor(profile)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPowerProfile(profile) }
                            .testTag("power_profile_card_${profile.name.lowercase()}"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) {
                                accent.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            }
                        ),
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = when (profile) {
                                            PowerProfile.ECO -> Icons.Default.EnergySavingsLeaf
                                            PowerProfile.BALANCED -> Icons.Default.Speed
                                            PowerProfile.PERFORMANCE -> Icons.Default.Bolt
                                        },
                                        contentDescription = null,
                                        tint = accent
                                    )
                                    Column {
                                        Text(
                                            text = profile.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = profile.badgeText,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = accent
                                        )
                                    }
                                }

                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected profile",
                                        tint = accent
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

        // 3. Typography & Visual Theme Studio (Rounded Clean Sans-Serif vs Playful Comic Sans)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FontDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Typography & Readability Studio",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Choose between clean rounded sans-serif or playful Comic Sans across all screens",
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
                                containerColor = if (!uiState.useComicSans) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = if (!uiState.useComicSans) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("select_rounded_sans_button")
                        ) {
                            Text("Rounded Sans")
                        }

                        Button(
                            onClick = { onSetComicSans(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.useComicSans) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = if (uiState.useComicSans) {
                                    MaterialTheme.colorScheme.onSecondary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("select_comic_sans_button")
                        ) {
                            Text("Comic Sans Mode")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Column {
                                Text(
                                    text = "Dark Moss Canopy Mode",
                                    style = MaterialTheme.typography.titleSmall
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
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "100% Data Sovereignty & Zero Telemetry",
                                style = MaterialTheme.typography.titleSmall
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wipe_offline_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reset & Wipe Local Chat History",
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
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
                                fontWeight = FontWeight.Bold
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
