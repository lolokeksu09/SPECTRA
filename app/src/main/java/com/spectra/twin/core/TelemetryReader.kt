package com.spectra.twin.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs

class TelemetryReader(private val context: Context) {
    fun read(): TelemetrySnapshot {
        // ACTION_BATTERY_CHANGED is sticky and requires no receiver registration for a null receiver.
        val battery = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val bm = context.getSystemService(BatteryManager::class.java)
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val chargingStatus = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean? = when (chargingStatus) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        }
        val tempRaw = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val celsius = tempRaw?.takeIf { it != Int.MIN_VALUE }
            ?.div(10.0)?.takeIf { it in -50.0..150.0 }
        val voltage = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
        val currentMicroAmps = runCatching {
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrNull()
        val current = currentMicroAmps?.takeUnless { it == Int.MIN_VALUE }?.div(1000.0)
        val memory = runCatching {
            ActivityManager.MemoryInfo().also {
                context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it)
            }
        }.getOrNull()
        val stat = runCatching { StatFs(Environment.getDataDirectory().absolutePath) }.getOrNull()
        val display = runCatching {
            context.getSystemService(DisplayManager::class.java)
                ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        }.getOrNull()
        val thermal = runCatching {
            context.getSystemService(PowerManager::class.java)?.currentThermalStatus
        }.getOrNull()
        val batteryHealth = when (battery?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Хорошее"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Перегрев"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Неисправно"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Перенапряжение"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Ошибка"
            BatteryManager.BATTERY_HEALTH_COLD -> "Переохлаждение"
            else -> null
        }
        return TelemetrySnapshot(
            atMillis = System.currentTimeMillis(),
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            batteryPercent = Metrics.batteryPercent(level, scale),
            batteryCelsius = celsius,
            batteryVoltageMv = voltage,
            batteryCurrentMa = current,
            isCharging = isCharging,
            batteryHealth = batteryHealth,
            memoryTotalBytes = memory?.totalMem?.takeIf { it > 0 },
            memoryAvailableBytes = memory?.availMem?.takeIf { it >= 0 },
            storageTotalBytes = stat?.totalBytes?.takeIf { it > 0 },
            storageAvailableBytes = stat?.availableBytes?.takeIf { it >= 0 },
            refreshRateHz = display?.refreshRate?.takeIf { it > 0f },
            thermalStatus = thermal,
        )
    }
}
