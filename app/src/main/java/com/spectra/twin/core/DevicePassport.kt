package com.spectra.twin.core

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import java.util.Locale

data class CameraCapability(val id: String, val facing: String, val sizes: List<String>?, val features: List<String>?)
data class DeviceCapabilities(
    val displayModes: List<String>?,
    val cameras: List<CameraCapability>?,
    val sensors: List<String>?
)

object DevicePassportReader {
    fun read(context: Context): DeviceCapabilities {
        val displayModes = runCatching {
            val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
                ?: return@runCatching null
            display.supportedModes.map {
                "${it.physicalWidth} × ${it.physicalHeight} · ${String.format(Locale.US, "%.1f", it.refreshRate)} Гц"
            }.distinct().sorted()
        }.getOrNull()
        val cameras = runCatching {
            val manager = context.getSystemService(CameraManager::class.java) ?: return@runCatching null
            manager.cameraIdList.sorted().map { id ->
                runCatching {
                    val c = manager.getCameraCharacteristics(id)
                    val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                        CameraCharacteristics.LENS_FACING_FRONT -> "фронтальная"
                        CameraCharacteristics.LENS_FACING_BACK -> "задняя"
                        CameraCharacteristics.LENS_FACING_EXTERNAL -> "внешняя"
                        else -> "ориентация неизвестна"
                    }
                    val sizes = runCatching {
                        c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                            ?.getOutputSizes(ImageFormat.JPEG)
                            ?.sortedByDescending { it.width.toLong() * it.height }
                            ?.map { "${it.width} × ${it.height}" }?.distinct()
                    }.getOrNull()
                    val features = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.mapNotNull {
                        when (it) {
                            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW"
                            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "ручной сенсор"
                            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "логическая мультикамера"
                            else -> null
                        }
                    }?.distinct()?.sorted()
                    CameraCapability(id, facing, sizes, features)
                }.getOrElse { CameraCapability(id, "данные недоступны", null, null) }
            }
        }.getOrNull()
        val sensors = runCatching {
            context.getSystemService(SensorManager::class.java)?.getSensorList(Sensor.TYPE_ALL)
                ?.map { "${it.name} · ${it.vendor} · ${it.stringType}" }?.sorted()
        }.getOrNull()
        return DeviceCapabilities(displayModes, cameras, sensors)
    }

    fun fields(capabilities: DeviceCapabilities, results: Map<String, String>): Map<String, String> {
        val fields = linkedMapOf(
            "Android · Производитель" to (Build.MANUFACTURER.takeIf { it.isNotBlank() } ?: "Недоступно"),
            "Android · Модель" to (Build.MODEL.takeIf { it.isNotBlank() } ?: "Недоступно"),
            "Android · Версия" to (Build.VERSION.RELEASE.takeIf { it.isNotBlank() } ?: "Недоступно"),
            "Android · Сборка" to (Build.DISPLAY.takeIf { it.isNotBlank() } ?: "Недоступно"),
            "Android · Идентификатор сборки" to (Build.FINGERPRINT.takeIf { it.isNotBlank() } ?: "Недоступно"),
            "Пресет · Экран" to "6,82″",
            "Пресет · Камеры" to "50 Мп и 8 Мп сзади; 32 Мп спереди",
            "Пресет · Батарея" to "8000 мА·ч (типичная), зарядка до 90 Вт",
            "Android · Режимы дисплея" to listValue(capabilities.displayModes),
            "Android · Датчики" to listValue(capabilities.sensors),
            "Android · Камеры" to when (capabilities.cameras) {
                null -> "Недоступно"
                emptyList<CameraCapability>() -> "Не обнаружены"
                else -> capabilities.cameras.joinToString("\n") { camera ->
                    "ID ${camera.id} · ${camera.facing}\nJPEG: ${listValue(camera.sizes)}\nВыбранные возможности (RAW, ручной сенсор, мультикамера): ${camera.features?.let { if (it.isEmpty()) "не заявлены" else it.joinToString(", ") } ?: "Недоступно"}"
                }
            }
        )
        results.forEach { (name, status) -> fields["Пользователь · $name"] = status }
        return fields
    }

    private fun listValue(values: List<String>?): String =
        when { values == null -> "Недоступно"; values.isEmpty() -> "Не сообщается"; else -> values.joinToString(", ") }
}

data class PassportSnapshot(val atMillis: Long, val fields: Map<String, String>)
data class PassportDifference(val name: String, val before: String, val after: String)

object PassportComparison {
    fun differences(before: PassportSnapshot, after: PassportSnapshot): List<PassportDifference> =
        (before.fields.keys + after.fields.keys).sorted().mapNotNull { key ->
            val a = before.fields[key] ?: "Нет в снимке"
            val b = after.fields[key] ?: "Нет в снимке"
            if (a == b) null else PassportDifference(key, a, b)
        }
}
