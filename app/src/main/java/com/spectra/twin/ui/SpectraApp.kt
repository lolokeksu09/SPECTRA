package com.spectra.twin.ui

import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.spectra.twin.render.FilamentDeviceView
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.spectra.twin.core.Metrics
import com.spectra.twin.core.TelemetrySnapshot
import com.spectra.twin.render.DeviceGLView
import com.spectra.twin.render.DeviceFinish
import com.spectra.twin.render.DeviceView
import androidx.compose.ui.platform.testTag
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Page(val title: String) {
    TWIN("3D-модель"), SYSTEM("Система"), THERMAL("Тепло"), HISTORY("История")
}

@Composable
fun SpectraApp(model: SpectraViewModel) {
    var current by remember { mutableStateOf(Page.TWIN) }
    val latest = model.latest
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, model) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> model.start()
                Lifecycle.Event.ON_PAUSE -> model.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.start()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); model.stop() }
    }
    LaunchedEffect(model.message) {
        model.message?.let { snackbar.showSnackbar(it); model.clearMessage() }
    }

    Scaffold(
        containerColor = Night,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                Page.entries.forEach { page ->
                    val icon = when (page) {
                        Page.TWIN -> Icons.Filled.ViewInAr
                        Page.SYSTEM -> Icons.Filled.Memory
                        Page.THERMAL -> Icons.Filled.Thermostat
                        Page.HISTORY -> Icons.Filled.History
                    }
                    NavigationBarItem(
                        selected = current == page,
                        onClick = { current = page },
                        label = { Text(page.title, maxLines = 1) },
                        icon = { Icon(icon, contentDescription = page.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Night,
                            selectedTextColor = Ice,
                            indicatorColor = Ice,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Header(latest?.deviceName ?: "Android", current.title)
            when (current) {
                Page.TWIN -> TwinScreen(latest, model.finish, model::selectFinish,
                    model.importedModelUri, model::importModelUri,
                    model.blackModelUri, model::importBlackModelUri,
                    model::restoreBundledModel, model::snapshot)
                Page.SYSTEM -> SystemScreen(latest)
                Page.THERMAL -> ThermalScreen(latest)
                Page.HISTORY -> HistoryScreen(model.history)
            }
        }
    }
}

@Composable
private fun Header(device: String, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("S P E C T R A", style = MaterialTheme.typography.titleMedium,
                color = Ice, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text(title, style = MaterialTheme.typography.labelMedium, color = Muted)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 185.dp)) {
            Text(device, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface, overflow = TextOverflow.Ellipsis)
            Text("ANDROID ${Build.VERSION.RELEASE}", color = Muted,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TwinScreen(latest: TelemetrySnapshot?, finish: DeviceFinish,
                       onFinishChange: (DeviceFinish) -> Unit,
                       importedModelUri: String?, onImport: (String) -> Unit,
                       blackModelUri: String?, onImportBlack: (String) -> Unit,
                       onRestoreBundled: (DeviceFinish) -> Unit,
                       onSnapshot: () -> Unit) {
    var viewSide by remember { mutableStateOf(DeviceView.REAR) }
    val context = LocalContext.current
    val hasBuiltInBlack = remember(context) {
        runCatching {
            context.assets.list("models")?.contains("legion_y70_black.glb") == true
        }.getOrDefault(false)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onImport(uri.toString())
        }
    }
    val blackPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onImportBlack(uri.toString())
            onFinishChange(DeviceFinish.BLACK)
        }
    }
    // Keep the GLB viewer attached while scrolling. LazyColumn may recycle
    // its TextureView and then try to reuse a destroyed native Filament engine.
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        StableTwinItem {
            Box(
                modifier = Modifier.fillMaxWidth().height(425.dp)
                    .clip(RoundedCornerShape(26.dp)).background(Panel)
            ) {
                val activeUri = if (finish == DeviceFinish.WHITE) importedModelUri else blackModelUri
                PhonePreview(finish, viewSide, activeUri, hasBuiltInBlack, Modifier.fillMaxSize())
                Column(modifier = Modifier.align(Alignment.TopStart).padding(20.dp)) {
                    Text("DIGITAL TWIN", color = Ice, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp)
                    Text("LEGION Y70 · glTF / Filament", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                Surface(
                    color = Color(0xC91C2935), shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 13.dp)
                ) {
                    Text("Вращение 360° по двум осям · масштаб двумя пальцами",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        color = Color(0xFFDCE8F0), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        StableTwinItem {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = viewSide == DeviceView.REAR,
                    onClick = { viewSide = DeviceView.REAR },
                    label = { Text("Сзади") },
                    leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null, Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("view_rear")
                )
                FilterChip(
                    selected = viewSide == DeviceView.FRONT,
                    onClick = { viewSide = DeviceView.FRONT },
                    label = { Text("Спереди") },
                    leadingIcon = { Icon(Icons.Filled.Smartphone, contentDescription = null, Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("view_front")
                )
            }
        }
        StableTwinItem {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ЦВЕТ КОРПУСА", style = MaterialTheme.typography.labelMedium, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DeviceFinish.entries.forEach { option ->
                        FilterChip(
                            selected = finish == option,
                            onClick = {
                                if (option == DeviceFinish.BLACK &&
                                    blackModelUri == null && !hasBuiltInBlack) {
                                    blackPicker.launch(arrayOf("model/gltf-binary", "application/octet-stream", "*/*"))
                                } else {
                                    onFinishChange(option)
                                }
                            },
                            label = { Text(option.title) },
                            leadingIcon = {
                                Box(Modifier.size(14.dp).clip(RoundedCornerShape(50)).background(
                                    if (option == DeviceFinish.WHITE) Color(0xFFEDEFF2) else Color(0xFF25282D)
                                ))
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("finish_" + option.id)
                        )
                    }
                }
            }
        }
        StableTwinItem {
            OutlinedButton(
                onClick = {
                    if (finish == DeviceFinish.BLACK) {
                        blackPicker.launch(arrayOf("model/gltf-binary", "application/octet-stream", "*/*"))
                    } else {
                        picker.launch(arrayOf("model/gltf-binary", "application/octet-stream", "*/*"))
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(if (finish == DeviceFinish.BLACK) "Выбрать чёрную модель GLB" else
                    if (importedModelUri == null) "Импортировать свой GLB" else "Заменить GLB")
            }
        }
        // Imported models override bundled assets. This reset is explicit,
        // affects only the chosen finish, and does not erase snapshot history.
        if ((finish == DeviceFinish.WHITE && importedModelUri != null) ||
            (finish == DeviceFinish.BLACK && blackModelUri != null && hasBuiltInBlack)) {
            StableTwinItem {
                OutlinedButton(
                    onClick = { onRestoreBundled(finish) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("Вернуться к встроенной модели v9") }
            }
        }
        StableTwinItem {
            Caption(
                if (hasBuiltInBlack) "Белая и чёрная 3D-модели Legion Y70 v9 встроены в APK и работают офлайн."
                else if (blackModelUri == null) "Белый GLB встроен в APK. Для чёрного корпуса выберите GLB из архива один раз. Выбор сохраняется, работа офлайн."
                else "Белый GLB встроен, чёрный GLB выбран из памяти телефона. Работа офлайн."
            )
        }
        StableTwinItem {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                HeroMetric("БАТАРЕЯ", Metrics.percentLabel(latest?.batteryPercent),
                    Icons.Filled.BatteryFull, Modifier.weight(1f))
                HeroMetric("ТЕМПЕРАТУРА", Metrics.temperature(latest?.batteryCelsius),
                    Icons.Filled.Thermostat, Modifier.weight(1f))
            }
        }
        StableTwinItem {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                HeroMetric("RAM ЗАНЯТО", Metrics.percentLabel(Metrics.percent(latest?.memoryAvailableBytes,
                    latest?.memoryTotalBytes)), Icons.Filled.Memory, Modifier.weight(1f))
                HeroMetric("ТЕПЛОВОЙ СТАТУС", Metrics.thermalStatus(latest?.thermalStatus),
                    Icons.Filled.DeviceThermostat, Modifier.weight(1f), compact = true)
            }
        }
        StableTwinItem {
            Button(
                onClick = onSnapshot,
                enabled = latest != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(15.dp)
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(Modifier.width(9.dp))
                Text("Сохранить снимок диагностики")
            }
        }
        StableTwinItem { Caption("Показатели получены от Android. Отсутствующие датчики отмечаются как недоступные. Внешняя 3D-модель приближена по изображениям, не является CAD-сканом. Внутренние компоненты не отображаются.") }
    }
}

@Composable
private fun StableTwinItem(content: @Composable () -> Unit) {
    content()
}

@Composable
private fun PhonePreview(finish: DeviceFinish, side: DeviceView, importedModelUri: String?,
                         hasBuiltInBlack: Boolean, modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current
    var modelError by remember { mutableStateOf<String?>(null) }
    if (finish == DeviceFinish.WHITE || hasBuiltInBlack || importedModelUri != null) {
        val preview = remember { mutableStateOf<FilamentDeviceView?>(null) }
        Box(modifier) {
            AndroidView(
                factory = { ctx -> FilamentDeviceView(ctx).also { view ->
                    view.onStatus = { modelError = it }
                    preview.value = view
                } },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.setSource(importedModelUri?.let(Uri::parse), finish)
                    view.showSide(side)
                }
            )
            modelError?.let { message ->
                Text(message, color = Color(0xFFFFBEBE),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.Center).padding(18.dp))
            }
        }
        DisposableEffect(lifecycle, preview.value) {
            val view = preview.value
            if (view != null) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> view.resume()
                        Lifecycle.Event.ON_PAUSE -> view.pause()
                        else -> Unit
                    }
                }
                lifecycle.lifecycle.addObserver(observer)
                if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.resume()
                onDispose { lifecycle.lifecycle.removeObserver(observer); view.pause() }
            } else onDispose {}
        }
    } else {
        val preview = remember { mutableStateOf<DeviceGLView?>(null) }
        AndroidView(
            factory = { ctx -> DeviceGLView(ctx).also { preview.value = it } },
            modifier = modifier,
            update = { view ->
                view.setFinish(DeviceFinish.BLACK)
                view.showSide(side)
            }
        )
        DisposableEffect(lifecycle, preview.value) {
            val view = preview.value
            if (view != null) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> view.onResume()
                        Lifecycle.Event.ON_PAUSE -> view.onPause()
                        else -> Unit
                    }
                }
                lifecycle.lifecycle.addObserver(observer)
                if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
                onDispose { lifecycle.lifecycle.removeObserver(observer); view.onPause() }
            } else onDispose {}
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                       modifier: Modifier = Modifier, compact: Boolean = false) {
    Card(modifier = modifier.height(108.dp), colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(15.dp),
            verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, modifier = Modifier.size(15.dp), tint = Ice)
                Text(label, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Text(value, style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White,
            fontWeight = FontWeight.SemiBold)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
}

@Composable
private fun DataRow(label: String, value: String, detail: String? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Muted, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(14.dp))
            Text(value, color = Color(0xFFEAF2F8), fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium)
        }
        detail?.let { Text(it, color = Muted, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 3.dp)) }
    }
    HorizontalDivider(color = PanelUp)
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, color = Muted, style = MaterialTheme.typography.bodySmall,
        lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun SystemScreen(latest: TelemetrySnapshot?) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("Системная телеметрия", "Обновление примерно каждые 2,5 секунды во время просмотра") }
        item {
            PanelCard {
                Text("АККУМУЛЯТОР", color = Ice, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                DataRow("Заряд", Metrics.percentLabel(latest?.batteryPercent))
                DataRow("Состояние", latest?.batteryHealth ?: "Недоступно")
                DataRow("Зарядка", when (latest?.isCharging) { true -> "Подключена"; false -> "Не заряжается"; null -> "Недоступно" })
                DataRow("Температура батареи", Metrics.temperature(latest?.batteryCelsius))
                DataRow("Напряжение", Metrics.voltage(latest?.batteryVoltageMv))
                DataRow("Ток батареи", Metrics.current(latest?.batteryCurrentMa), "Знак тока зависит от реализации прошивки")
            }
        }
        item {
            PanelCard {
                Text("ПАМЯТЬ И ДИСПЛЕЙ", color = Ice, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                DataRow("RAM всего", Metrics.size(latest?.memoryTotalBytes))
                DataRow("RAM доступно", Metrics.size(latest?.memoryAvailableBytes))
                DataRow("RAM занято", Metrics.percentLabel(Metrics.percent(latest?.memoryAvailableBytes, latest?.memoryTotalBytes)))
                DataRow("Раздел данных всего", Metrics.size(latest?.storageTotalBytes))
                DataRow("Раздел данных доступно", Metrics.size(latest?.storageAvailableBytes))
                DataRow("Частота дисплея", Metrics.hz(latest?.refreshRateHz), "Не равна частоте кадров в игре")
            }
        }
        item { Caption("Не показываем CPU/GPU температуру и FPS других приложений: обычные Android API не гарантируют эти данные. Свободная RAM учитывает системные кэши.") }
    }
}

@Composable
private fun ThermalScreen(latest: TelemetrySnapshot?) {
    val thermal = latest?.thermalStatus
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("Тепловая система", "Официальный статус Android Thermal API") }
        item {
            PanelCard {
                Icon(Icons.Filled.Thermostat, contentDescription = null, tint = Ice,
                    modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(18.dp))
                Text(Metrics.thermalStatus(thermal), color = Color.White,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(9.dp))
                Text("Тепловая серьёзность: ${thermal?.toString() ?: "—"} / 6",
                    style = MaterialTheme.typography.bodyMedium, color = Muted)
                Spacer(Modifier.height(15.dp))
                if (thermal != null) {
                    LinearProgressIndicator(
                        progress = { (thermal.coerceIn(0, 6) / 6f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)),
                        color = if (thermal >= 3) MaterialTheme.colorScheme.error else Ice,
                        trackColor = PanelUp
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Температура батареи: ${Metrics.temperature(latest?.batteryCelsius)}",
                    color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            PanelCard {
                Text("Как читать показатели", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                DataRow("0–1", "Норма / лёгкий нагрев")
                DataRow("2–3", "Умеренный / сильный")
                DataRow("4–6", "Критический и выше")
            }
        }
        item { Caption("Значение 0–6 — не градусы и не прямое измерение CPU. Система оценивает общий тепловой режим. Визуальная тепловая карта компонентов появится только при наличии подтверждённых датчиков.") }
    }
}

@Composable
private fun HistoryScreen(items: List<TelemetrySnapshot>) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Снимки диагностики", "Локально на устройстве · до 100 последних снимков") }
        if (items.isEmpty()) {
            item {
                PanelCard {
                    Icon(Icons.Filled.History, contentDescription = null, tint = Ice,
                        modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("История пока пуста", color = Color.White,
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Откройте вкладку 3D-модель и нажмите «Сохранить снимок диагностики».",
                        color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            itemsIndexed(items, key = { index, snapshot -> "${snapshot.atMillis}-$index" }) { _, snapshot ->
                PanelCard {
                    Text(SimpleDateFormat("dd.MM.yyyy · HH:mm:ss", Locale("ru"))
                        .format(Date(snapshot.atMillis)), color = Ice,
                        style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(9.dp))
                    DataRow("Батарея", Metrics.percentLabel(snapshot.batteryPercent))
                    DataRow("Температура батареи", Metrics.temperature(snapshot.batteryCelsius))
                    DataRow("Тепловой статус", Metrics.thermalStatus(snapshot.thermalStatus))
                    DataRow("RAM занято", Metrics.percentLabel(Metrics.percent(
                        snapshot.memoryAvailableBytes, snapshot.memoryTotalBytes)))
                }
            }
        }
        item { Caption("Снимки записываются только по нажатию кнопки. Удаление данных приложения очищает историю.") }
    }
}
