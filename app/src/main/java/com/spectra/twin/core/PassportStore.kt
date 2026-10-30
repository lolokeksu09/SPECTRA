package com.spectra.twin.core

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

/** Separate from telemetry history. A damaged file is never replaced by an empty history. */
class PassportStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "device-passports-v1.json"))
    private val lock = Any()

    fun readAll(): List<PassportSnapshot> = synchronized(lock) {
        val bytes = try { file.openRead().use { it.readBytes() } } catch (_: FileNotFoundException) {
            return@synchronized emptyList()
        }
        val array = JSONArray(String(bytes, Charsets.UTF_8))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val entries = item.getJSONObject("fields")
            val values = linkedMapOf<String, String>()
            val keys = entries.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                values[key] = entries.getString(key)
            }
            PassportSnapshot(item.getLong("time"), values)
        }
    }

    fun add(snapshot: PassportSnapshot): List<PassportSnapshot> = synchronized(lock) {
        val values = listOf(snapshot) + readAll()
        val data = JSONArray()
        values.forEach { item ->
            val fields = JSONObject()
            item.fields.forEach { (name, value) -> fields.put(name, value) }
            data.put(JSONObject().put("time", item.atMillis).put("fields", fields))
        }
        val stream = file.startWrite()
        try {
            stream.write(data.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (t: Throwable) {
            file.failWrite(stream)
            throw t
        }
        values
    }
}
