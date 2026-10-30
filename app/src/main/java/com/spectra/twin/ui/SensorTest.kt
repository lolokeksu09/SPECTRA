package com.spectra.twin.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

@Composable
internal fun SensorTest(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val sensors = remember(manager) { manager?.getSensorList(Sensor.TYPE_ALL).orEmpty() }
    var selected by remember(sensors) { mutableStateOf(sensors.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }
    var values by remember(selected) { mutableStateOf<List<Float>?>(null) }
    var listening by remember(selected) { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(manager, selected, owner) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) { values = event.values.toList() }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        fun stop() { manager?.unregisterListener(listener); listening = false; values = null }
        fun start() {
            listening = if (selected != null && manager != null)
                runCatching { manager.registerListener(listener, selected, SensorManager.SENSOR_DELAY_NORMAL) }.getOrDefault(false)
            else false
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose { owner.lifecycle.removeObserver(observer); stop() }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Доступные датчики и сырые значения, которые сообщает Android.", color = Muted)
        if (sensors.isEmpty()) {
            Text("Датчики недоступны")
        } else {
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text("Выбрать датчик (${sensors.size})") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                    sensors.forEach { sensor ->
                        DropdownMenuItem(text = { Text(sensor.name) }, onClick = { selected = sensor; expanded = false })
                    }
                }
            }
            Text(selected?.name ?: "Недоступно", style = MaterialTheme.typography.titleMedium)
            Text("Производитель: ${selected?.vendor ?: "Недоступно"}", color = Muted)
            Text("Тип Android: ${selected?.stringType ?: "Недоступно"}", color = Muted)
            val displayed = values?.joinToString(" · ") { String.format(Locale.US, "%.3f", it) }
            Text(when {
                !listening -> "Данные недоступны"
                displayed == null -> "Ожидание данных"
                else -> "Значения: $displayed"
            })
        }
    }
}
