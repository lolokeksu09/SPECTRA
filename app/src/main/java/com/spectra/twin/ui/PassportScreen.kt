package com.spectra.twin.ui

import androidx.activity.compose.BackHandler
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spectra.twin.core.DeviceCapabilities
import com.spectra.twin.core.DevicePassportReader
import com.spectra.twin.core.PassportComparison
import com.spectra.twin.core.PassportSnapshot
import com.spectra.twin.core.PassportStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun PassportScreen(capabilities: DeviceCapabilities?, results: DiagnosticResults,
                            onCapabilitiesRefresh: (DeviceCapabilities) -> Unit = {},
                            storeOverride: PassportStore? = null, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context, storeOverride) { storeOverride ?: PassportStore(context) }
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf<List<PassportSnapshot>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var loading by remember { mutableStateOf(true) }
    var historyError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var currentExpanded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val statuses = DiagnosticKind.entries.filter { it != DiagnosticKind.SENSORS }.associate {
        it.title to when (results.get(it)) { true -> "Работает"; false -> "Есть проблема"; null -> "Не проверено" }
    }
    val fields = capabilities?.let { DevicePassportReader.fields(it, statuses) }
    fun reload() {
        if (loading || saving) return
        loading = true
        scope.launch {
            try { history = withContext(Dispatchers.IO) { store.readAll() }; historyError = false; selected = emptySet() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { historyError = true }
            finally { loading = false }
        }
    }
    LaunchedEffect(store) {
        try { history = withContext(Dispatchers.IO) { store.readAll() }; historyError = false }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { historyError = true }
        finally { loading = false }
    }
    BackHandler(onBack = onClose)
    Surface(color = Night, contentColor = MaterialTheme.colorScheme.onBackground) {
        LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("passport-list"), contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Паспорт устройства", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("Закрыть") }
                }
                Text("Данные Android относятся к установленной прошивке. Характеристики пресета — к Legion Y70 XT2611-1.",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            item {
                PassportPanel {
                    Text("Сохранить паспорт", style = MaterialTheme.typography.titleMedium)
                    Text("Снимок содержит сведения о прошивке, режимах дисплея, камерах, датчиках и подтверждённые результаты проверок.",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("Текущие заряд и температура сюда не записываются: они доступны в истории телеметрии.",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                    Button(enabled = !loading && !saving && !historyError, onClick = {
                        saving = true
                        val capturedStatuses = statuses.toMap()
                        scope.launch {
                            try {
                                val pair = withContext(Dispatchers.IO) {
                                    val fresh = DevicePassportReader.read(context)
                                    val snapshot = PassportSnapshot(System.currentTimeMillis(), DevicePassportReader.fields(fresh, capturedStatuses))
                                    fresh to store.add(snapshot)
                                }
                                onCapabilitiesRefresh(pair.first)
                                history = pair.second; selected = emptySet(); message = "Паспорт сохранён"
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { message = "Не удалось сохранить паспорт. Существующие данные не удалены." }
                            finally { saving = false }
                        }
                    }, modifier = Modifier.testTag("save-passport")) { Text(if (saving) "Сохранение…" else "Сохранить снимок") }
                    message?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (loading) item { Text("Загрузка сохранённых паспортов…", color = Muted) }
            if (historyError) item {
                PassportPanel {
                    Text("Не удалось прочитать сохранённые паспорта. Данные не удалены.")
                    OutlinedButton(onClick = { reload() }) { Text("Повторить") }
                }
            }
            item {
                PassportPanel {
                    Text("Текущие сведения", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { currentExpanded = !currentExpanded }, modifier = Modifier.testTag("passport-current")) {
                        Text(if (currentExpanded) "Скрыть сведения" else "Посмотреть сведения")
                    }
                    if (currentExpanded) {
                    Text("Android — сведения системы; пресет — характеристики модели; пользователь — подтверждённые результаты.",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                    if (fields == null) Text("Получение данных Android…", color = Muted)
                    fields?.forEach { (name, value) ->
                        Spacer(Modifier.height(10.dp))
                        Text(name, color = Ice, style = MaterialTheme.typography.labelLarge)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Режимы дисплея — не FPS игры. JPEG-размеры — размеры снимков, которые сообщает Android, а не точное разрешение сенсора. ID камеры не привязан к объективу на модели.",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("Характеристики пресета: Lenovo XT2611-1; источники указаны в документации проекта.",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://mitem.lenovovip.com.cn/pd/1054689.html"))) } }) {
                        Text("Источник характеристик Lenovo")
                    }
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.antutu.com/doc/136772"))) } }) {
                        Text("Источник характеристик камер")
                    }
                    }
                }
            }
            if (!loading && !historyError) {
                item {
                    PassportPanel {
                        Text("Сравнение паспортов", style = MaterialTheme.typography.titleMedium)
                        if (selected.size < 2) Text(
                            if (history.size < 2) "Сохраните два паспорта, например до и после обновления прошивки."
                            else "Выберите два снимка ниже.", color = Muted)
                        else {
                            val pair = selected.map { history[it] }.sortedBy { it.atMillis }
                            val format = SimpleDateFormat("dd.MM.yyyy · HH:mm:ss", Locale("ru"))
                            Text("До: ${format.format(Date(pair[0].atMillis))}", color = Muted)
                            Text("После: ${format.format(Date(pair[1].atMillis))}", color = Muted)
                            val differences = PassportComparison.differences(pair[0], pair[1])
                            if (differences.isEmpty()) Text("Изменений в сохранённых полях нет.")
                            differences.forEach { diff ->
                                Spacer(Modifier.height(10.dp))
                                Text(diff.name, color = Ice, style = MaterialTheme.typography.labelLarge)
                                Text("До: ${diff.before}", style = MaterialTheme.typography.bodySmall)
                                Text("После: ${diff.after}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Различия показывают сведения Android и пользовательские результаты, а не диагноз.",
                                color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                itemsIndexed(history, key = { index, snapshot -> "${snapshot.atMillis}-$index" }) { index, snapshot ->
                    PassportPanel {
                        val format = SimpleDateFormat("dd.MM.yyyy · HH:mm:ss", Locale("ru"))
                        Text(format.format(Date(snapshot.atMillis)), color = Ice)
                        Text(snapshot.fields["Android · Сборка"] ?: "Сборка не записана", color = Muted)
                        OutlinedButton(onClick = {
                            selected = if (index in selected) selected - index
                            else if (selected.size == 2) setOf(index) else selected + index
                        }) { Text(if (index in selected) "Убрать из сравнения" else "Выбрать для сравнения") }
                    }
                }
            }

        }
    }
}

@Composable
private fun PassportPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
    }
}
