package com.spectra.twin.core

import java.util.Locale

/** Null means Android did not supply the measurement; never replace missing sensor data with 0. */
data class TelemetrySnapshot(
    val atMillis: Long,
    val deviceName: String,
    val batteryPercent: Int?,
    val batteryCelsius: Double?,
    val batteryVoltageMv: Int?,
    val batteryCurrentMa: Double?,
    val isCharging: Boolean?,
    val batteryHealth: String?,
    val memoryTotalBytes: Long?,
    val memoryAvailableBytes: Long?,
    val storageTotalBytes: Long?,
    val storageAvailableBytes: Long?,
    val refreshRateHz: Float?,
    val thermalStatus: Int?,
)

object Metrics {
    fun batteryPercent(level: Int, scale: Int): Int? =
        if (level < 0 || scale <= 0 || level > scale) null
        else ((level.toLong() * 100L + scale / 2) / scale).toInt().coerceIn(0, 100)

    fun percent(available: Long?, total: Long?): Int? =
        if (available == null || total == null || total <= 0 || available < 0 || available > total) null
        else (((total - available) * 100.0) / total).toInt().coerceIn(0, 100)

    fun percentLabel(value: Int?): String = value?.let { "$it%" } ?: "Недоступно"
    fun temperature(value: Double?): String =
        value?.takeIf { it.isFinite() && it in -50.0..150.0 }
            ?.let { String.format(Locale.US, "%.1f °C", it) } ?: "Недоступно"

    fun current(value: Double?): String =
        value?.takeIf { it.isFinite() }
            ?.let { String.format(Locale.US, "%+.0f мА", it) } ?: "Недоступно"

    fun voltage(value: Int?): String = value?.takeIf { it > 0 }?.let { "$it мВ" } ?: "Недоступно"

    fun size(bytes: Long?): String = bytes?.takeIf { it >= 0 }?.let {
        String.format(Locale.US, "%.1f ГБ", it / 1_073_741_824.0)
    } ?: "Недоступно"

    fun hz(value: Float?): String = value?.takeIf { it.isFinite() && it > 0f }
        ?.let { String.format(Locale.US, "%.0f Гц", it) } ?: "Недоступно"

    fun thermalStatus(status: Int?): String = when (status) {
        0 -> "Норма"
        1 -> "Незначительный нагрев"
        2 -> "Умеренный нагрев"
        3 -> "Сильный нагрев"
        4 -> "Критический нагрев"
        5 -> "Опасный нагрев"
        6 -> "Отключение из-за нагрева"
        else -> "Недоступно"
    }
}
