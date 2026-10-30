package com.spectra.twin.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal enum class DiagnosticKind(val title: String, val key: String, val explanation: String) {
    TOUCH("Касания экрана", "touch", "Отметьте области, которые реагируют на касание."),
    COLORS("Цвета экрана", "colors", "Осмотрите экран на разных цветах."),
    CAMERA("Камеры", "camera", "Откройте камеру и проверьте изображение."),
    SPEAKER("Динамик", "speaker", "Воспроизведите тестовый сигнал."),
    MIC("Микрофон", "mic", "Запишите и прослушайте короткий звук."),
    VIBRATION("Вибрация", "vibration", "Запустите короткую вибрацию."),
    BUTTONS("Клавиши громкости", "buttons", "Нажмите обе клавиши громкости."),
    SENSORS("Датчики", "sensors", "Посмотрите датчики и значения Android.")
}

internal class DiagnosticResults(context: Context, preferenceName: String = "diagnostic-results-v1") {
    private val prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
    var revision by mutableIntStateOf(0)
        private set
    fun get(kind: DiagnosticKind): Boolean? {
        revision
        return if (prefs.contains(kind.key)) prefs.getBoolean(kind.key, false) else null
    }
    fun set(kind: DiagnosticKind, result: Boolean) {
        prefs.edit().putBoolean(kind.key, result).apply()
        revision++
    }
}

@Composable
internal fun DiagnosticsScreen(results: DiagnosticResults, onOpen: (DiagnosticKind) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Диагностический центр", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(5.dp))
            Text("Проверьте компоненты и сохраните свои результаты.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Отчёт", style = MaterialTheme.typography.titleMedium)
                    Text("Результаты подтверждены пользователем и хранятся на устройстве.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    DiagnosticKind.entries.filter { it != DiagnosticKind.SENSORS }.forEach { kind ->
                        val status = when (results.get(kind)) { true -> "Работает"; false -> "Есть проблема"; null -> "Не проверено" }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(kind.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(status, color = if (results.get(kind) == false) MaterialTheme.colorScheme.error else Ice,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        items(DiagnosticKind.entries.toList(), key = { it.key }) { kind ->
            Card(onClick = { onOpen(kind) }, modifier = Modifier.fillMaxWidth().testTag("diagnostic-${kind.key}"),
                colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(kind.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(kind.explanation, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun DiagnosticDetail(kind: DiagnosticKind, results: DiagnosticResults, onClose: () -> Unit) {
    if (kind == DiagnosticKind.TOUCH) {
        TouchTestScreen(onResult = { results.set(kind, it) }, onClose = onClose)
    } else if (kind == DiagnosticKind.COLORS) {
        ColorTestScreen(onResult = { results.set(kind, it) }, onClose = onClose)
    } else {
        BackHandler(onBack = onClose)
        Surface(color = Night, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(kind.title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineSmall)
            when (kind) {
                DiagnosticKind.SPEAKER -> SpeakerTest { results.set(kind, it); onClose() }
                DiagnosticKind.MIC -> MicrophoneTest { results.set(kind, it); onClose() }
                DiagnosticKind.VIBRATION -> VibrationTest { results.set(kind, it); onClose() }
                DiagnosticKind.BUTTONS -> VolumeButtonsTest { results.set(kind, it); onClose() }
                DiagnosticKind.CAMERA -> CameraAppTest { results.set(kind, it); onClose() }
                DiagnosticKind.SENSORS -> SensorTest(Modifier.fillMaxWidth())
                else -> Unit
            }
            OutlinedButton(onClick = onClose) { Text("Закрыть") }
        }
        }
    }
}

@Composable
internal fun ManualResultButtons(enabled: Boolean = true, onResult: (Boolean) -> Unit) {
    Text("Какой результат?", color = Muted, style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onResult(false) }, modifier = Modifier.weight(1f)) { Text("Проблема") }
        Button(onClick = { onResult(true) }, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Работает") }
    }
}
