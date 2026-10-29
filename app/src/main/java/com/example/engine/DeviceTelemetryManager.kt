package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.abs
import kotlin.math.max

enum class InferenceTelemetryPhase(val label: String) {
    IDLE("Idle"),
    MOUNTING("Mounting Weights"),
    PREFILLING("Prefilling KV Cache"),
    DECODING("Streaming Decode"),
    COMPLETED("Completed")
}

data class TpsTelemetrySnapshot(
    val phase: InferenceTelemetryPhase = InferenceTelemetryPhase.IDLE,
    val instantTps: Float = 0f,
    val decodeTps: Float = 0f,
    val sessionAverageTps: Float = 0f,
    val sessionPeakTps: Float = 0f,
    val prefillTps: Float = 0f,
    val lastTtftMs: Int = 0,
    val averageTtftMs: Int = 0,
    val interTokenLatencyMs: Float = 0f,
    val promptTokensEstimate: Int = 0,
    val generatedTokens: Int = 0,
    val totalSessionTokens: Int = 0,
    val energyMicroWh: Float = 0f,
    val energyPerTokenMicroWh: Float = 0f,
    val tokensPerMilliwattHour: Float = 0f,
    val tpsHistory: List<Float> = emptyList(),
    val completedRunsCount: Int = 0
)

data class HardwareTelemetrySnapshot(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val batteryTempCelsius: Float = 0f,
    val batteryVoltageMv: Int = 0,
    val currentNowMicroAmps: Long = 0L,
    val instantaneousMilliwatts: Int = 0,
    val isHardwarePowerMeasured: Boolean = false,
    val totalRamMb: Int = 0,
    val availableRamMb: Int = 0,
    val usedRamMb: Int = 0,
    val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    val savedCarbonGramsSession: Float = 0f
)

/**
 * High-precision monotonic TPS & latency tracker for on-device LLM inference.
 * Separates prompt prefill (TTFT) from token generation (decode TPS), computes
 * sliding-window instantaneous TPS with EMA smoothing, tracks peak throughput,
 * and records a sparkline series for real-time telemetry visualization.
 */
class LiveTpsTelemetryTracker(
    val promptTokensEstimate: Int,
    private val estimatedMilliwatts: Float,
    private val nanoTimeProvider: () -> Long = { System.nanoTime() }
) {
    private val startTimeNs: Long = nanoTimeProvider()
    private var firstTokenTimeNs: Long = 0L
    private var lastTokenTimeNs: Long = 0L
    private var tokenCount: Int = 0

    private val recentTokenTimesNs = ArrayDeque<Long>(WINDOW_MAX_TOKENS)
    private var smoothedInstantTps: Float = 0f
    private var peakTps: Float = 0f
    private var prefillTps: Float = 0f
    private val sparklineSeries = ArrayDeque<Float>(MAX_SPARKLINE_POINTS)

    fun buildPrefillStep(
        citedRagSnippets: List<RetrievedRagSnippet>,
        phase: InferenceTelemetryPhase = InferenceTelemetryPhase.PREFILLING
    ): StreamingStep {
        val nowNs = nanoTimeProvider()
        val elapsedMs = ((nowNs - startTimeNs) / 1_000_000L).toInt().coerceAtLeast(0)
        val elapsedSec = elapsedMs / 1000f
        val liveEnergy = (elapsedSec * estimatedMilliwatts) / 3.6f
        return StreamingStep(
            accumulatedText = "",
            currentTps = 0f,
            ttftMs = elapsedMs,
            generatedTokens = 0,
            energyMicroWh = liveEnergy,
            citedRagSnippets = citedRagSnippets,
            isFinished = false,
            instantTps = 0f,
            peakTps = 0f,
            interTokenLatencyMs = 0f,
            prefillTps = 0f,
            promptTokensEstimate = promptTokensEstimate,
            phase = phase,
            tpsSeries = emptyList()
        )
    }

    fun onTokenReceived(
        cleanAccumulatedText: String,
        citedRagSnippets: List<RetrievedRagSnippet>,
        pieceTokenDelta: Int = 1
    ): StreamingStep {
        val nowNs = nanoTimeProvider()
        val safeDelta = pieceTokenDelta.coerceAtLeast(1)

        if (tokenCount == 0) {
            firstTokenTimeNs = nowNs
            val ttftSec = ((firstTokenTimeNs - startTimeNs) / 1_000_000_000.0).toFloat()
            prefillTps = if (ttftSec > 0.001f && promptTokensEstimate > 0) {
                (promptTokensEstimate / ttftSec).coerceAtMost(5000f)
            } else {
                0f
            }
        }

        tokenCount += safeDelta
        lastTokenTimeNs = nowNs
        recentTokenTimesNs.addLast(nowNs)
        while (recentTokenTimesNs.size > WINDOW_MAX_TOKENS) {
            recentTokenTimesNs.removeFirst()
        }
        // Evict timestamps older than 1.8s when we have at least 3 samples in the sliding window
        while (recentTokenTimesNs.size > 3 && (nowNs - recentTokenTimesNs.first()) > 1_800_000_000L) {
            recentTokenTimesNs.removeFirst()
        }

        val totalElapsedSec = ((nowNs - startTimeNs) / 1_000_000_000.0).toFloat().coerceAtLeast(0.001f)
        val decodeElapsedSec = ((nowNs - firstTokenTimeNs) / 1_000_000_000.0).toFloat()

        // Calculate instantaneous TPS over the sliding window
        val rawWindowTps = if (recentTokenTimesNs.size >= 2) {
            val windowSpanSec = ((recentTokenTimesNs.last() - recentTokenTimesNs.first()) / 1_000_000_000.0).toFloat()
            val windowTokens = (recentTokenTimesNs.size - 1).toFloat()
            if (windowSpanSec >= 0.005f) {
                (windowTokens / windowSpanSec).coerceIn(0.1f, 500f)
            } else {
                smoothedInstantTps.takeIf { it > 0f } ?: (1f / totalElapsedSec).coerceIn(0.1f, 200f)
            }
        } else {
            // First token arrived: estimate initial rate from prefill or total elapsed time
            (1f / totalElapsedSec).coerceIn(0.1f, 200f)
        }

        smoothedInstantTps = if (smoothedInstantTps <= 0f) {
            rawWindowTps
        } else {
            (EMA_ALPHA * rawWindowTps) + ((1f - EMA_ALPHA) * smoothedInstantTps)
        }

        // Calculate decode TPS strictly excluding TTFT prefill delay once decode phase has progressed
        val decodeTps = if (tokenCount >= 2 && decodeElapsedSec >= 0.015f) {
            val pureDecodeRate = ((tokenCount - 1) / decodeElapsedSec).coerceIn(0.1f, 500f)
            if (tokenCount <= 3) {
                // Smoothly blend on very early tokens
                (pureDecodeRate * 0.7f) + (smoothedInstantTps * 0.3f)
            } else {
                pureDecodeRate
            }
        } else {
            smoothedInstantTps
        }

        peakTps = max(peakTps, max(smoothedInstantTps, decodeTps))

        if (sparklineSeries.isEmpty() || tokenCount % 1 == 0) {
            if (sparklineSeries.size >= MAX_SPARKLINE_POINTS) {
                sparklineSeries.removeFirst()
            }
            sparklineSeries.addLast(smoothedInstantTps)
        }

        val ttftMs = ((firstTokenTimeNs - startTimeNs) / 1_000_000L).toInt().coerceAtLeast(1)
        val itlMs = if (tokenCount >= 2 && decodeElapsedSec > 0.001f) {
            (decodeElapsedSec * 1000f) / (tokenCount - 1)
        } else if (decodeTps > 0f) {
            1000f / decodeTps
        } else {
            0f
        }
        val liveEnergy = (totalElapsedSec * estimatedMilliwatts) / 3.6f

        return StreamingStep(
            accumulatedText = cleanAccumulatedText,
            currentTps = decodeTps,
            ttftMs = ttftMs,
            generatedTokens = tokenCount,
            energyMicroWh = liveEnergy,
            citedRagSnippets = citedRagSnippets,
            isFinished = false,
            instantTps = smoothedInstantTps,
            peakTps = peakTps,
            interTokenLatencyMs = itlMs,
            prefillTps = prefillTps,
            promptTokensEstimate = promptTokensEstimate,
            phase = InferenceTelemetryPhase.DECODING,
            tpsSeries = sparklineSeries.toList()
        )
    }

    fun buildFinalStep(
        cleanAccumulatedText: String,
        citedRagSnippets: List<RetrievedRagSnippet>,
        fallbackTokenCount: Int = tokenCount,
        nativeDecodeTpsOverride: Float? = null,
        nativePrefillTpsOverride: Float? = null
    ): StreamingStep {
        val nowNs = nanoTimeProvider()
        val effectiveTokens = if (tokenCount > 0) tokenCount else fallbackTokenCount.coerceAtLeast(0)
        val totalElapsedSec = ((nowNs - startTimeNs) / 1_000_000_000.0).toFloat().coerceAtLeast(0.001f)
        val decodeEndNs = if (lastTokenTimeNs > firstTokenTimeNs) lastTokenTimeNs else nowNs
        val decodeElapsedSec = if (firstTokenTimeNs > 0L) {
            ((decodeEndNs - firstTokenTimeNs) / 1_000_000_000.0).toFloat()
        } else {
            0f
        }

        val computedDecodeTps = when {
            nativeDecodeTpsOverride != null && nativeDecodeTpsOverride > 0f -> nativeDecodeTpsOverride
            effectiveTokens >= 2 && decodeElapsedSec >= 0.010f ->
                ((effectiveTokens - 1) / decodeElapsedSec).coerceIn(0.1f, 500f)
            effectiveTokens > 0 ->
                (effectiveTokens / totalElapsedSec).coerceIn(0.1f, 500f)
            else -> 0f
        }

        val finalPeak = max(peakTps, computedDecodeTps)
        val finalPrefillTps = when {
            nativePrefillTpsOverride != null && nativePrefillTpsOverride > 0f -> nativePrefillTpsOverride
            prefillTps > 0f -> prefillTps
            promptTokensEstimate > 0 && totalElapsedSec > 0f -> (promptTokensEstimate / totalElapsedSec).coerceAtMost(5000f)
            else -> 0f
        }

        val finalTtftMs = if (firstTokenTimeNs > 0L) {
            ((firstTokenTimeNs - startTimeNs) / 1_000_000L).toInt().coerceAtLeast(1)
        } else if (effectiveTokens > 0) {
            (totalElapsedSec * 1000f).toInt().coerceAtLeast(1)
        } else {
            0
        }

        val finalItlMs = if (effectiveTokens >= 2 && decodeElapsedSec > 0.001f) {
            (decodeElapsedSec * 1000f) / (effectiveTokens - 1)
        } else if (computedDecodeTps > 0f) {
            1000f / computedDecodeTps
        } else {
            0f
        }

        if (computedDecodeTps > 0f && sparklineSeries.isEmpty()) {
            sparklineSeries.addLast(computedDecodeTps)
        }

        val finalEnergy = (totalElapsedSec * estimatedMilliwatts) / 3.6f

        return StreamingStep(
            accumulatedText = cleanAccumulatedText,
            currentTps = computedDecodeTps,
            ttftMs = finalTtftMs,
            generatedTokens = effectiveTokens,
            energyMicroWh = finalEnergy,
            citedRagSnippets = citedRagSnippets,
            isFinished = true,
            instantTps = computedDecodeTps,
            peakTps = finalPeak,
            interTokenLatencyMs = finalItlMs,
            prefillTps = finalPrefillTps,
            promptTokensEstimate = promptTokensEstimate,
            phase = InferenceTelemetryPhase.COMPLETED,
            tpsSeries = sparklineSeries.toList()
        )
    }

    companion object {
        private const val WINDOW_MAX_TOKENS = 12
        private const val MAX_SPARKLINE_POINTS = 36
        private const val EMA_ALPHA = 0.42f

        fun estimatePromptTokens(formattedPrompt: String): Int {
            if (formattedPrompt.isBlank()) return 0
            // Standard BPE / SentencePiece approximation (~3.5 chars per token on English + ChatML tags)
            return (formattedPrompt.length / 3.5f).toInt().coerceAtLeast(1)
        }
    }
}

class DeviceTelemetryManager(private val context: Context) {

    fun sampleTelemetry(
        activeProfileMilliwatts: Int,
        isGenerating: Boolean,
        totalSessionTokens: Int
    ): HardwareTelemetrySnapshot {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val stickyIntent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        }

        val level = stickyIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = stickyIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) {
            ((level.toFloat() / scale.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } else {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 0
        }

        val status = stickyIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val rawTempTenths = stickyIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = if (rawTempTenths > 0) rawTempTenths / 10f else 0f

        val voltageMv = stickyIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            ?.takeIf { it > 0 } ?: 0

        val rawCurrentNow = batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0L
        val isValidCurrent = rawCurrentNow != Long.MIN_VALUE &&
            rawCurrentNow != 0L &&
            rawCurrentNow > -50_000_000L &&
            rawCurrentNow < 50_000_000L

        val safeCurrentNow = if (isValidCurrent) rawCurrentNow else 0L
        val hardwareMeasured = isValidCurrent && voltageMv > 0
        val measuredMw = if (hardwareMeasured) {
            val currentMa = abs(safeCurrentNow) / 1000f
            ((currentMa * (voltageMv / 1000f))).toInt()
        } else if (isGenerating) {
            activeProfileMilliwatts
        } else {
            0
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        val totalRamMb = if (memInfo.totalMem > 0) {
            (memInfo.totalMem / (1024L * 1024L)).toInt()
        } else {
            (Runtime.getRuntime().maxMemory() / (1024L * 1024L)).toInt()
        }
        val availRamMb = if (memInfo.availMem > 0) {
            (memInfo.availMem / (1024L * 1024L)).toInt()
        } else {
            (Runtime.getRuntime().freeMemory() / (1024L * 1024L)).toInt()
        }
        val usedRamMb = (totalRamMb - availRamMb).coerceAtLeast(0)
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val savedCarbonGrams = if (totalSessionTokens > 0) {
            (totalSessionTokens / 100f) * 0.29f
        } else {
            0f
        }

        return HardwareTelemetrySnapshot(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            batteryTempCelsius = tempC,
            batteryVoltageMv = voltageMv,
            currentNowMicroAmps = safeCurrentNow,
            instantaneousMilliwatts = measuredMw,
            isHardwarePowerMeasured = hardwareMeasured,
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            usedRamMb = usedRamMb,
            cpuCores = cores,
            savedCarbonGramsSession = savedCarbonGrams
        )
    }
}
