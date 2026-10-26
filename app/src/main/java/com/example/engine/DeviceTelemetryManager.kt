package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.abs

data class HardwareTelemetrySnapshot(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val batteryTempCelsius: Float = 0f,
    val batteryVoltageMv: Int = 0,
    val currentNowMicroAmps: Long = 0L,
    val instantaneousMilliwatts: Int = 0,
    val totalRamMb: Int = 0,
    val availableRamMb: Int = 0,
    val usedRamMb: Int = 0,
    val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    val savedCarbonGramsSession: Float = 0f
)

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
        val measuredMw = if (isValidCurrent && voltageMv > 0) {
            val currentMa = abs(safeCurrentNow) / 1000f
            ((currentMa * (voltageMv / 1000f))).toInt()
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
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            usedRamMb = usedRamMb,
            cpuCores = cores,
            savedCarbonGramsSession = savedCarbonGrams
        )
    }
}
