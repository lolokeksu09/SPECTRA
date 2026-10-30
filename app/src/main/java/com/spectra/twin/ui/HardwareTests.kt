package com.spectra.twin.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.spectra.twin.MainActivity
import kotlinx.coroutines.delay

@Composable
internal fun SpeakerTest(onResult: (Boolean) -> Unit) {
    var tone by remember { mutableStateOf<ToneGenerator?>(null) }
    var played by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { tone?.release(); tone = null }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); tone?.release(); tone = null }
    }
    LaunchedEffect(tone) {
        val active = tone
        if (active != null) {
            delay(1200)
            if (tone === active) { active.release(); tone = null }
        }
    }
    Text("Будет воспроизведён короткий сигнал. Проверьте громкость и отключите наушники, если проверяете динамик.",
        color = Muted)
    Button(onClick = {
        tone?.release(); tone = null
        try {
            val generator = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
            tone = generator
            if (generator.startTone(ToneGenerator.TONE_PROP_BEEP, 600)) { played = true; error = null }
            else { error = "Не удалось запустить сигнал"; generator.release(); tone = null }
        } catch (_: Exception) { error = "Не удалось запустить сигнал" }
    }) { Text("Воспроизвести сигнал") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    ManualResultButtons(enabled = played, onResult = onResult)
}

@Composable
internal fun VibrationTest(onResult: (Boolean) -> Unit) {
    val context = LocalContext.current
    val vibrator = remember(context) { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    val available = remember(vibrator) { vibrator?.hasVibrator() == true }
    var started by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(vibrator) { onDispose { vibrator?.cancel() } }
    Text(if (available) "Почувствуйте короткую вибрацию." else "Android не сообщает о наличии вибратора.", color = Muted)
    Button(enabled = available, onClick = {
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            started = true; error = null
        } catch (_: Exception) { error = "Не удалось запустить вибрацию" }
    }) { Text("Запустить вибрацию") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    ManualResultButtons(enabled = started, onResult = onResult)
}

@Composable
internal fun VolumeButtonsTest(onResult: (Boolean) -> Unit) {
    val activity = LocalContext.current as? MainActivity
    var up by remember { mutableStateOf(false) }
    var down by remember { mutableStateOf(false) }
    DisposableEffect(activity) {
        val handler: (Int) -> Unit = { key ->
            if (key == KeyEvent.KEYCODE_VOLUME_UP) up = true
            if (key == KeyEvent.KEYCODE_VOLUME_DOWN) down = true
        }
        if (activity != null) activity.diagnosticVolumeHandler = handler
        onDispose {
            if (activity?.diagnosticVolumeHandler === handler) activity.diagnosticVolumeHandler = null
        }
    }
    Text("Нажмите громкость вверх и вниз. Во время проверки громкость не меняется.", color = Muted)
    Text("Громкость вверх: ${if (up) "нажата" else "ожидание"}")
    Text("Громкость вниз: ${if (down) "нажата" else "ожидание"}")
    ManualResultButtons(enabled = up && down, onResult = onResult)
}
