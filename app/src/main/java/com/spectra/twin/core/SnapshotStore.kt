package com.spectra.twin.core

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

/** User-initiated snapshots only. Atomic replacement ensures interrupted writes keep old data. */
class SnapshotStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "snapshots-v1.json"))
    private val lock = Any()

    fun readAll(): List<TelemetrySnapshot> = synchronized(lock) {
        val bytes = try {
            file.openRead().use { it.readBytes() }
        } catch (_: FileNotFoundException) {
            return@synchronized emptyList()
        }
        val json = JSONArray(String(bytes, Charsets.UTF_8))
        // Reject damaged data instead of silently replacing it with a fresh empty history.
        (0 until json.length()).map { index -> decode(json.getJSONObject(index)) }
    }

    fun add(item: TelemetrySnapshot): List<TelemetrySnapshot> = synchronized(lock) {
        val values = (listOf(item) + readAll()).take(100)
        val json = JSONArray()
        values.forEach { json.put(encode(it)) }
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (t: Throwable) {
            file.failWrite(stream)
            throw t
        }
        values
    }

    private fun encode(s: TelemetrySnapshot) = JSONObject().apply {
        put("t", s.atMillis); put("model", s.deviceName)
        put("battery", s.batteryPercent); put("temp", s.batteryCelsius)
        put("mv", s.batteryVoltageMv); put("ma", s.batteryCurrentMa)
        put("charging", s.isCharging); put("health", s.batteryHealth)
        put("ramTotal", s.memoryTotalBytes); put("ramAvailable", s.memoryAvailableBytes)
        put("diskTotal", s.storageTotalBytes); put("diskAvailable", s.storageAvailableBytes)
        put("hz", s.refreshRateHz); put("thermal", s.thermalStatus)
    }

    private fun decode(o: JSONObject) = TelemetrySnapshot(
        atMillis = o.getLong("t"),
        deviceName = o.optString("model", "Android"),
        batteryPercent = o.optIntOrNull("battery"),
        batteryCelsius = o.optDoubleOrNull("temp"),
        batteryVoltageMv = o.optIntOrNull("mv"),
        batteryCurrentMa = o.optDoubleOrNull("ma"),
        isCharging = if (o.isNull("charging")) null else o.optBoolean("charging"),
        batteryHealth = if (o.isNull("health")) null else o.optString("health"),
        memoryTotalBytes = o.optLongOrNull("ramTotal"),
        memoryAvailableBytes = o.optLongOrNull("ramAvailable"),
        storageTotalBytes = o.optLongOrNull("diskTotal"),
        storageAvailableBytes = o.optLongOrNull("diskAvailable"),
        refreshRateHz = o.optDoubleOrNull("hz")?.toFloat(),
        thermalStatus = o.optIntOrNull("thermal"),
    )

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null
    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) getDouble(key) else null
    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null
}
