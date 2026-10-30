package com.spectra.twin.ui

import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.spectra.twin.render.FilamentDeviceView
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.spectra.twin.core.Metrics
import com.spectra.twin.core.TelemetrySnapshot
import com.spectra.twin.core.SnapshotComparison
import com.spectra.twin.core.SnapshotTrend
import com.spectra.twin.core.DeviceCapabilities
import com.spectra.twin.core.DevicePassportReader
import com.spectra.twin.render.DeviceGLView
import com.spectra.twin.render.DeviceFinish
import com.spectra.twin.render.DeviceView
import com.spectra.twin.render.ExternalPart
import androidx.compose.ui.platform.testTag
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Page(val title: String) {
    TWIN("3D-модель"), SYSTEM("Система"), THERMAL("Тепло"), HISTORY("История"),
    DIAGNOSTICS("Проверки")
}

@Composable
fun SpectraApp(model: SpectraViewModel) {
    var current by remember { mutableStateOf(Page.TWIN) }
    var diagnosticScreen by remember { mutableStateOf<DiagnosticKind?>(null) }
    val selectedPartState = remember { mutableStateOf<ExternalPart?>(null) }
    val viewSideState = remember { mutableStateOf(DeviceView.REAR) }
    val focusRequestState = remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val diagnosticResults = remember(context) { DiagnosticResults(context) }
    var passportOpen by remember { mutableStateOf(false) }
    var passportFocus by remember { mutableStateOf(PassportFocus.ALL) }
    val twinScroll = rememberScrollState()
    var returnToTwin by remember { mutableStateOf(false) }
    var capabilities by remember { mutableStateOf<DeviceCapabilities?>(null) }
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
    LaunchedEffect(context, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            capabilities = withContext(Dispatchers.IO) { DevicePassportReader.read(context) }
        }
    }
    BackHandler(enabled = returnToTwin && current == Page.SYSTEM && !passportOpen && diagnosticScreen == null) {
        current = Page.TWIN
        returnToTwin = false
    }

    if (diagnosticScreen != null) {
        DiagnosticDetail(diagnosticScreen!!, diagnosticResults, onClose = { diagnosticScreen = null })
        return
    }
    if (passportOpen) {
        PassportScreen(capabilities, diagnosticResults, focus = passportFocus,
            onCapabilitiesRefresh = { capabilities = it },
            onClose = { passportOpen = false })
        return
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
                        Page.DIAGNOSTICS -> Icons.Filled.TouchApp
                    }
                    NavigationBarItem(
                        selected = current == page,
                        onClick = { current = page; returnToTwin = false },
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
            if (current != Page.HISTORY && current != Page.DIAGNOSTICS) {
                val stamp = latest?.let { SimpleDateFormat("HH:mm:ss", Locale("ru")).format(Date(it.atMillis)) }
                val state = when {
                    model.telemetryStale && stamp != null -> "Данные устарели · последнее обновление $stamp"
                    model.telemetryStale -> "Ожидание данных Android"
                    else -> "Обновлено: $stamp"
                }
                Text(state, color = if (model.telemetryStale) Color(0xFFFFBEBE) else Muted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
            }
            when (current) {
                Page.TWIN -> TwinScreen(latest, model.finish, model::selectFinish,
                    model.importedModelUri, model::importModelUri,
                    model.blackModelUri, model::importBlackModelUri,
                    model::restoreBundledModel, model::snapshot, model.telemetryStale, model::reportImportPermissionError,
                    capabilities, diagnosticResults, selectedPartState, viewSideState, focusRequestState, twinScroll,
                    onBattery = { returnToTwin = true; current = Page.SYSTEM },
                    onDiagnostic = { diagnosticScreen = it }, onPassport = { focus ->
                        passportFocus = focus; passportOpen = true
                    })
                Page.SYSTEM -> SystemScreen(latest, onPassport = { passportFocus = PassportFocus.ALL; passportOpen = true })
                Page.THERMAL -> ThermalScreen(latest)
                Page.HISTORY -> HistoryScreen(model.history, model.historyLoading, model.historyError, model::reloadHistory)
                Page.DIAGNOSTICS -> DiagnosticsScreen(diagnosticResults, onOpen = { diagnosticScreen = it })
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
                       onSnapshot: () -> Unit, telemetryStale: Boolean, onImportError: () -> Unit,
                       capabilities: DeviceCapabilities?, diagnosticResults: DiagnosticResults,
                       selectedPartState: MutableState<ExternalPart?>, viewSideState: MutableState<DeviceView>,
                       focusRequestState: MutableIntState, twinScroll: ScrollState,
                       onBattery: () -> Unit,
                       onDiagnostic: (DiagnosticKind) -> Unit, onPassport: (PassportFocus) -> Unit) {
    var viewSide by viewSideState
    var selectedPart by selectedPartState
    var showParts by remember { mutableStateOf(false) }
    var focusRequest by focusRequestState
    if (showParts) {
        AlertDialog(
            onDismissRequest = { showParts = false },
            title = { Text("Детали телефона") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    itemsIndexed(ExternalPart.entries.toList()) { _, part ->
                        TextButton(onClick = {
                            showParts = false; selectedPart = part
                            viewSide = part.preferredSide(); focusRequest++
                        },
                            modifier = Modifier.fillMaxWidth()) { Text(part.title) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showParts = false }) { Text("Закрыть") } }
        )
    }
    val context = LocalContext.current
    val hasBuiltInBlack = remember(context) {
        runCatching {
            context.assets.list("models")?.contains("legion_y70_black.glb") == true
        }.getOrDefault(false)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val saved = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (saved) onImport(uri.toString()) else onImportError()
        }
    }
    val blackPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val saved = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.isSuccess
            if (saved) {
                onImportBlack(uri.toString())
                onFinishChange(DeviceFinish.BLACK)
            } else onImportError()
        }
    }
    // Keep the GLB viewer attached while scrolling. LazyColumn may recycle
    // its TextureView and then try to reuse a destroyed native Filament engine.
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(twinScroll)
            .padding(start = 18.dp, end = 18.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        StableTwinItem {
            Box(
                modifier = Modifier.fillMaxWidth().height(if (selectedPart != null) 260.dp else 425.dp)
                    .clip(RoundedCornerShape(26.dp)).background(Panel)
            ) {
                val activeUri = if (finish == DeviceFinish.WHITE) importedModelUri else blackModelUri
                PhonePreview(finish, viewSide, activeUri, hasBuiltInBlack, selectedPart, focusRequest,
                    { selectedPart = it }, Modifier.fillMaxSize())
                Column(modifier = Modifier.align(Alignment.TopStart).padding(20.dp)) {
                    Text("DIGITAL TWIN", color = Ice, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp)
                    Text("LEGION Y70 · glTF / Filament", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                Surface(
                    color = Color(0xC91C2935), shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 13.dp)
                ) {
                    Text("Коснитесь детали · вращайте одним пальцем · масштаб двумя",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        color = Color(0xFFDCE8F0), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        selectedPart?.let { part ->
            StableTwinItem {
                PanelCard {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(part.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { selectedPart = null }) { Text("Закрыть") }
                    }
                    Text(part.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    when (part) {
                        ExternalPart.SCREEN -> {
                            Text("Диагональ модели: 6,82″.")
                            Text("Android · частота: ${if (telemetryStale) "Данные устарели" else Metrics.hz(latest?.refreshRateHz)}")
                            Text("Частота дисплея не равна FPS игры.", style = MaterialTheme.typography.bodySmall)
                            Text("Android · режимы дисплея: ${capabilities?.displayModes?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Недоступно"}", style = MaterialTheme.typography.bodySmall)
                        }
                        ExternalPart.SELFIE -> Text("Характеристика модели: 32 Мп.")
                        ExternalPart.MAIN_CAMERA, ExternalPart.ULTRAWIDE -> Text("Камеры модели: основная 50 Мп и сверхширокоугольная 8 Мп. Соответствие объективов на модели уточняется.")
                        ExternalPart.USB -> {
                            Text("Модель: зарядка до 90 Вт, батарея 8000 мА·ч (типичная ёмкость).")
                            Text(if (telemetryStale) "Android · данные батареи устарели" else "Android · заряд: ${Metrics.percentLabel(latest?.batteryPercent)}")
                            Text("Android · состояние: ${if (telemetryStale) "Данные устарели" else when (latest?.isCharging) {
                                true -> "Заряжается или батарея полная"
                                false -> "Не заряжается"
                                null -> "Недоступно"
                            }}")
                            Text("Android · температура батареи: ${if (telemetryStale) "Данные устарели" else Metrics.temperature(latest?.batteryCelsius)}")
                            Text("Android · напряжение батареи: ${if (telemetryStale) "Данные устарели" else Metrics.voltage(latest?.batteryVoltageMv)}")
                            Text("90 Вт — не измерение текущей мощности.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = onBattery, modifier = Modifier.testTag("open-battery-telemetry")) {
                                Text("Показатели батареи")
                            }
                        }
                        else -> Unit
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Характеристики: Legion Y70 нового поколения (XT2611-1). Данные Android относятся к текущему устройству.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    val tests = part.relatedDiagnostics()
                    tests.forEach { kind ->
                        val status = when (diagnosticResults.get(kind)) {
                            true -> "Работает"
                            false -> "Есть проблема"
                            null -> "Не проверено"
                        }
                        Text("${kind.title} · результат пользователя: $status",
                            color = if (diagnosticResults.get(kind) == false) MaterialTheme.colorScheme.error else Muted,
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { onDiagnostic(kind) }, modifier = Modifier.fillMaxWidth().testTag("part-test-${kind.key}")) {
                            Text("Проверить: ${kind.title}")
                        }
                    }
                    if (part == ExternalPart.SCREEN) {
                        TextButton(onClick = { onPassport(PassportFocus.DISPLAY) }, modifier = Modifier.testTag("open-display-passport")) {
                            Text("Режимы дисплея в паспорте")
                        }
                    }
                    if (part == ExternalPart.SELFIE || part == ExternalPart.MAIN_CAMERA || part == ExternalPart.ULTRAWIDE) {
                        Text(cameraSummary(part, capabilities), style = MaterialTheme.typography.bodySmall)
                        Text("Android сообщает камеры по ID; привязка ID к этому объективу не подтверждена.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onPassport(PassportFocus.CAMERAS) }, modifier = Modifier.testTag("open-camera-passport")) {
                            Text("Камеры Android в паспорте")
                        }
                    }
                }
            }
        }
        StableTwinItem {
            OutlinedButton(onClick = { onPassport(PassportFocus.ALL) }, modifier = Modifier.fillMaxWidth().testTag("open-passport")) {
                Text("Паспорт устройства")
            }
        }
        StableTwinItem {
            OutlinedButton(onClick = { showParts = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Выбрать деталь из списка")
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
            // Dedicated close-up views let the user see the USB-C port, SIM
            // tray, speaker and Dolby mark rather than a tiny edge-on sliver.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = viewSide == DeviceView.BOTTOM,
                    onClick = { viewSide = DeviceView.BOTTOM },
                    label = { Text("Снизу") },
                    leadingIcon = {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = null, Modifier.size(16.dp))
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("view_bottom")
                )
                FilterChip(
                    selected = viewSide == DeviceView.TOP,
                    onClick = { viewSide = DeviceView.TOP },
                    label = { Text("Сверху") },
                    leadingIcon = {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = null, Modifier.size(16.dp))
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("view_top")
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
                ) { Text("Вернуться к встроенной модели") }
            }
        }
        StableTwinItem {
            Caption(
                if (hasBuiltInBlack) "Белая и чёрная модели Legion Y70 встроены для работы офлайн."
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
                enabled = latest != null && !telemetryStale,
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
                         hasBuiltInBlack: Boolean, selectedPart: ExternalPart?, focusRequest: Int,
                         onPartSelected: (ExternalPart) -> Unit, modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current
    var modelError by remember { mutableStateOf<String?>(null) }
    if (finish == DeviceFinish.WHITE || hasBuiltInBlack || importedModelUri != null) {
        val preview = remember { mutableStateOf<FilamentDeviceView?>(null) }
        var lastFocusRequest by remember { mutableIntStateOf(0) }
        Box(modifier) {
            AndroidView(
                factory = { ctx -> FilamentDeviceView(ctx).also { view ->
                    view.onStatus = { modelError = it }
                    preview.value = view
                } },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.onPartSelected = onPartSelected
                    view.setSource(importedModelUri?.let(Uri::parse), finish)
                    view.showSide(side)
                    view.selectPart(selectedPart)
                    if (lastFocusRequest != focusRequest && selectedPart != null) {
                        view.focusOnPart(selectedPart)
                        lastFocusRequest = focusRequest
                    }
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
private fun SystemScreen(latest: TelemetrySnapshot?, onPassport: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("Системная телеметрия", "Обновление примерно каждые 2,5 секунды во время просмотра") }
        item { OutlinedButton(onClick = onPassport, modifier = Modifier.fillMaxWidth()) { Text("Паспорт устройства") } }
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
private fun HistoryScreen(items: List<TelemetrySnapshot>, loading: Boolean, error: Boolean, retry: () -> Unit) {
    var selected by remember(items) { mutableStateOf(setOf<Int>()) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Снимки диагностики", "Локально на устройстве · до 100 последних снимков") }
        if (loading) {
            item { Caption("Загрузка истории…") }
        } else if (error) {
            item {
                PanelCard {
                    Text("Не удалось прочитать историю. Сохранённые данные не удалены.")
                    OutlinedButton(onClick = retry) { Text("Повторить") }
                }
            }
        } else if (items.isEmpty()) {
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
            item { SnapshotTrendCard(items) }
            item {
                PanelCard {
                    Text("Сравнение снимков", style = MaterialTheme.typography.titleMedium)
                    if (selected.size < 2) {
                        Text(if (items.size < 2) "Сохраните ещё один снимок для сравнения." else "Выберите два снимка ниже.", color = Muted)
                    } else {
                        val pair = selected.map { items[it] }.sortedBy { it.atMillis }
                        val before = pair[0]
                        val after = pair[1]
                        val format = SimpleDateFormat("dd.MM.yyyy · HH:mm:ss", Locale("ru"))
                        Text("До: ${format.format(Date(before.atMillis))}", color = Muted)
                        Text("После: ${format.format(Date(after.atMillis))}", color = Muted)
                        DataRow("Заряд", SnapshotComparison.battery(before.batteryPercent, after.batteryPercent))
                        DataRow("Температура батареи", SnapshotComparison.temperature(before.batteryCelsius, after.batteryCelsius))
                        DataRow("Доступная RAM", SnapshotComparison.memory(before.memoryAvailableBytes, after.memoryAvailableBytes))
                        DataRow("Свободно в накопителе", SnapshotComparison.memory(before.storageAvailableBytes, after.storageAvailableBytes))
                        DataRow("Тепловой статус", "${Metrics.thermalStatus(before.thermalStatus)} → ${Metrics.thermalStatus(after.thermalStatus)}")
                        Text("Изменение рассчитано как «после» минус «до».", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            itemsIndexed(items, key = { index, snapshot -> "${snapshot.atMillis}-$index" }) { index, snapshot ->
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
                    OutlinedButton(onClick = {
                        selected = if (index in selected) selected - index
                            else if (selected.size == 2) setOf(index) else selected + index
                    }) { Text(if (index in selected) "Убрать из сравнения" else "Выбрать для сравнения") }
                }
            }
        }
        item { Caption("Снимки записываются только по нажатию кнопки. Удаление данных приложения очищает историю.") }
    }
}

@Composable
internal fun SnapshotTrendCard(items: List<TelemetrySnapshot>) {
    var showTemperature by remember { mutableStateOf(false) }
    val sorted = remember(items) { items.sortedBy { it.atMillis } }
    val series = remember(sorted, showTemperature) {
        SnapshotTrend.build(sorted.map {
            it.atMillis to if (showTemperature) it.batteryCelsius else it.batteryPercent?.toDouble()
        }, if (showTemperature) -50.0..150.0 else 0.0..100.0)
    }
    val label = if (showTemperature) "Температура батареи" else "Заряд батареи"
    val unit = if (showTemperature) "°C" else "%"
    PanelCard {
        Text("Изменения по снимкам", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("Только сохранённые вручную снимки", color = Muted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showTemperature, onClick = { showTemperature = false }, label = { Text("Заряд") })
            FilterChip(selected = showTemperature, onClick = { showTemperature = true }, label = { Text("Температура") })
        }
        if (series == null) {
            Text("Нет доступных данных для графика", color = Muted)
        } else {
            val valueFormat = if (showTemperature) "%.1f" else "%.0f"
            val minimum = String.format(Locale.US, valueFormat, series.minimum)
            val maximum = String.format(Locale.US, valueFormat, series.maximum)
            Text("$label · $minimum–$maximum $unit", color = Ice, style = MaterialTheme.typography.labelMedium)
            val lineColor = Ice
            val gridColor = PanelUp
            Canvas(Modifier.fillMaxWidth().height(120.dp).padding(vertical = 10.dp)
                .semantics { contentDescription = "$label, от $minimum до $maximum $unit. Значения и время указаны в снимках ниже." }) {
                val inset = 4.dp.toPx()
                val chartWidth = (size.width - 2 * inset).coerceAtLeast(0f)
                val chartHeight = (size.height - 2 * inset).coerceAtLeast(0f)
                fun position(x: Float, y: Float) = Offset(inset + x * chartWidth, inset + (1f - y) * chartHeight)
                drawLine(gridColor, Offset(inset, inset), Offset(size.width - inset, inset), strokeWidth = 1.dp.toPx())
                drawLine(gridColor, Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset), strokeWidth = 1.dp.toPx())
                series.points.zipWithNext().forEach { (a, b) ->
                    if (a.y != null && b.y != null) drawLine(lineColor, position(a.x, a.y), position(b.x, b.y), strokeWidth = 2.dp.toPx())
                }
                series.points.forEach { point ->
                    point.y?.let { drawCircle(lineColor, radius = 3.dp.toPx(), center = position(point.x, it)) }
                }
            }
            val format = SimpleDateFormat("dd.MM · HH:mm", Locale("ru"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(format.format(Date(sorted.first().atMillis)), color = Muted, style = MaterialTheme.typography.labelSmall)
                Text(format.format(Date(sorted.last().atMillis)), color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            Text("Пропуски означают отсутствие данных; линии между ними не проводятся.",
                color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}


