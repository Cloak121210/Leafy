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
    val promptTokensCount: Int = 0,
    val generatedTokens: Int = 0,
    val totalSessionTokens: Int = 0,
    val energyMicroWh: Float = -1f,
    val energyPerTokenMicroWh: Float = -1f,
    val tokensPerMilliwattHour: Float = -1f,
    val tpsHistory: List<Float> = emptyList(),
    val completedRunsCount: Int = 0
)

data class HardwareTelemetrySnapshot(
    val batteryPercent: Int = -1,
    val isCharging: Boolean = false,
    val batteryTempCelsius: Float = -1f,
    val batteryVoltageMv: Int = 0,
    val currentNowMicroAmps: Long = 0L,
    val instantaneousMilliwatts: Int = -1,
    val isHardwarePowerMeasured: Boolean = false,
    val totalRamMb: Int = 0,
    val availableRamMb: Int = 0,
    val usedRamMb: Int = 0,
    val cpuCores: Int = Runtime.getRuntime().availableProcessors()
)

/**
 * Monotonic clock telemetry tracker for on-device LLM inference.
 * Strictly separates prompt prefill (TTFT & prefill TPS) from token generation (decode TPS),
 * counts tokens strictly in the generation loop (never from character length),
 * and reports energy only when hardware battery current & voltage are measured.
 */
class LiveTpsTelemetryTracker(
    private var promptTokensCount: Int = 0,
    private val hardwareMeasuredMilliwatts: Float = -1f,
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

    fun updatePromptTokenCount(exactTokenCount: Int) {
        if (exactTokenCount >= 0) {
            promptTokensCount = exactTokenCount
        }
    }

    private fun computeHardwareEnergyMicroWh(elapsedSec: Float): Float {
        return if (hardwareMeasuredMilliwatts > 0f && elapsedSec > 0f) {
            (elapsedSec * hardwareMeasuredMilliwatts) / 3.6f
        } else {
            -1f
        }
    }

    fun buildPrefillStep(
        citedRagSnippets: List<RetrievedRagSnippet>,
        phase: InferenceTelemetryPhase = InferenceTelemetryPhase.PREFILLING
    ): StreamingStep {
        val nowNs = nanoTimeProvider()
        val elapsedMs = ((nowNs - startTimeNs) / 1_000_000L).toInt().coerceAtLeast(0)
        val elapsedSec = elapsedMs / 1000f
        return StreamingStep(
            accumulatedText = "",
            currentTps = 0f,
            ttftMs = elapsedMs,
            generatedTokens = 0,
            energyMicroWh = computeHardwareEnergyMicroWh(elapsedSec),
            citedRagSnippets = citedRagSnippets,
            isFinished = false,
            instantTps = 0f,
            peakTps = 0f,
            interTokenLatencyMs = 0f,
            prefillTps = 0f,
            promptTokensCount = promptTokensCount,
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
            prefillTps = if (ttftSec > 0.0005f && promptTokensCount > 0) {
                promptTokensCount / ttftSec
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
        while (recentTokenTimesNs.size > 3 && (nowNs - recentTokenTimesNs.first()) > 1_800_000_000L) {
            recentTokenTimesNs.removeFirst()
        }

        val totalElapsedSec = ((nowNs - startTimeNs) / 1_000_000_000.0).toFloat().coerceAtLeast(0.001f)
        val decodeElapsedSec = ((nowNs - firstTokenTimeNs) / 1_000_000_000.0).toFloat()

        // Calculate instantaneous decode TPS strictly over inter-token timestamps (never mixing prefill time)
        val rawWindowTps = if (recentTokenTimesNs.size >= 2) {
            val windowSpanSec = ((recentTokenTimesNs.last() - recentTokenTimesNs.first()) / 1_000_000_000.0).toFloat()
            val windowTokens = (recentTokenTimesNs.size - 1).toFloat()
            if (windowSpanSec > 0.0005f) {
                windowTokens / windowSpanSec
            } else {
                smoothedInstantTps
            }
        } else {
            0f
        }

        if (rawWindowTps > 0f) {
            smoothedInstantTps = if (smoothedInstantTps <= 0f) {
                rawWindowTps
            } else {
                (EMA_ALPHA * rawWindowTps) + ((1f - EMA_ALPHA) * smoothedInstantTps)
            }
        }

        // Pure decode TPS strictly excludes prefill TTFT
        val decodeTps = if (tokenCount >= 2 && decodeElapsedSec > 0.0005f) {
            (tokenCount - 1) / decodeElapsedSec
        } else {
            0f
        }

        peakTps = max(peakTps, max(smoothedInstantTps, decodeTps))

        if (smoothedInstantTps > 0f) {
            if (sparklineSeries.size >= MAX_SPARKLINE_POINTS) {
                sparklineSeries.removeFirst()
            }
            sparklineSeries.addLast(smoothedInstantTps)
        }

        val ttftMs = ((firstTokenTimeNs - startTimeNs) / 1_000_000L).toInt().coerceAtLeast(1)
        val itlMs = if (tokenCount >= 2 && decodeElapsedSec > 0.0005f) {
            (decodeElapsedSec * 1000f) / (tokenCount - 1)
        } else {
            0f
        }

        return StreamingStep(
            accumulatedText = cleanAccumulatedText,
            currentTps = decodeTps,
            ttftMs = ttftMs,
            generatedTokens = tokenCount,
            energyMicroWh = computeHardwareEnergyMicroWh(totalElapsedSec),
            citedRagSnippets = citedRagSnippets,
            isFinished = false,
            instantTps = smoothedInstantTps,
            peakTps = peakTps,
            interTokenLatencyMs = itlMs,
            prefillTps = prefillTps,
            promptTokensCount = promptTokensCount,
            phase = InferenceTelemetryPhase.DECODING,
            tpsSeries = sparklineSeries.toList()
        )
    }

    fun buildFinalStep(
        cleanAccumulatedText: String,
        citedRagSnippets: List<RetrievedRagSnippet>,
        nativeGeneratedTokenCount: Int? = null,
        nativePromptTokenCount: Int? = null,
        nativeDecodeTpsOverride: Float? = null,
        nativePrefillTpsOverride: Float? = null,
        nativePromptMsOverride: Float? = null,
        nativeDecodeMsOverride: Float? = null
    ): StreamingStep {
        val nowNs = nanoTimeProvider()
        if (nativePromptTokenCount != null && nativePromptTokenCount > 0) {
            promptTokensCount = nativePromptTokenCount
        }
        val effectiveTokens = when {
            tokenCount > 0 -> tokenCount
            nativeGeneratedTokenCount != null && nativeGeneratedTokenCount > 0 -> nativeGeneratedTokenCount
            else -> 0
        }
        val totalElapsedSec = ((nowNs - startTimeNs) / 1_000_000_000.0).toFloat().coerceAtLeast(0.001f)
        val decodeEndNs = if (lastTokenTimeNs > firstTokenTimeNs) lastTokenTimeNs else nowNs
        val decodeElapsedSec = if (firstTokenTimeNs > 0L) {
            ((decodeEndNs - firstTokenTimeNs) / 1_000_000_000.0).toFloat()
        } else if (nativeDecodeMsOverride != null && nativeDecodeMsOverride > 0f) {
            nativeDecodeMsOverride / 1000f
        } else {
            0f
        }

        // Decode TPS must ONLY reflect token generation, never prefill + decode combined
        val computedDecodeTps = when {
            nativeDecodeTpsOverride != null && nativeDecodeTpsOverride > 0f -> nativeDecodeTpsOverride
            effectiveTokens >= 2 && decodeElapsedSec > 0.0005f -> (effectiveTokens - 1) / decodeElapsedSec
            else -> 0f
        }

        val finalPeak = max(peakTps, computedDecodeTps)
        val finalPrefillTps = when {
            nativePrefillTpsOverride != null && nativePrefillTpsOverride > 0f -> nativePrefillTpsOverride
            prefillTps > 0f -> prefillTps
            nativePromptMsOverride != null && nativePromptMsOverride > 0f && promptTokensCount > 0 ->
                promptTokensCount / (nativePromptMsOverride / 1000f)
            else -> 0f
        }

        val finalTtftMs = when {
            firstTokenTimeNs > 0L -> ((firstTokenTimeNs - startTimeNs) / 1_000_000L).toInt().coerceAtLeast(1)
            nativePromptMsOverride != null && nativePromptMsOverride > 0f -> nativePromptMsOverride.toInt().coerceAtLeast(1)
            else -> 0
        }

        val finalItlMs = when {
            nativeDecodeMsOverride != null && nativeDecodeMsOverride > 0f && effectiveTokens >= 1 ->
                nativeDecodeMsOverride / effectiveTokens
            effectiveTokens >= 2 && decodeElapsedSec > 0.0005f ->
                (decodeElapsedSec * 1000f) / (effectiveTokens - 1)
            computedDecodeTps > 0f ->
                1000f / computedDecodeTps
            else -> 0f
        }

        if (computedDecodeTps > 0f && sparklineSeries.isEmpty()) {
            sparklineSeries.addLast(computedDecodeTps)
        }

        return StreamingStep(
            accumulatedText = cleanAccumulatedText,
            currentTps = computedDecodeTps,
            ttftMs = finalTtftMs,
            generatedTokens = effectiveTokens,
            energyMicroWh = computeHardwareEnergyMicroWh(totalElapsedSec),
            citedRagSnippets = citedRagSnippets,
            isFinished = true,
            instantTps = computedDecodeTps,
            peakTps = finalPeak,
            interTokenLatencyMs = finalItlMs,
            prefillTps = finalPrefillTps,
            promptTokensCount = promptTokensCount,
            phase = InferenceTelemetryPhase.COMPLETED,
            tpsSeries = sparklineSeries.toList()
        )
    }

    companion object {
        private const val WINDOW_MAX_TOKENS = 12
        private const val MAX_SPARKLINE_POINTS = 36
        private const val EMA_ALPHA = 0.42f
    }
}

class DeviceTelemetryManager(private val context: Context) {

    fun sampleTelemetry(): HardwareTelemetrySnapshot {
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
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: -1
        }

        val status = stickyIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val rawTempTenths = stickyIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val tempC = if (rawTempTenths > 0) rawTempTenths / 10f else -1f

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
        } else {
            -1
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
            cpuCores = cores
        )
    }
}
