package com.spectra.twin.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.io.File

@Composable
internal fun MicrophoneTest(onResult: (Boolean) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var recording by remember { mutableStateOf(false) }
    var file by remember { mutableStateOf<File?>(null) }
    var ready by remember { mutableStateOf(false) }
    var listened by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun cleanup() {
        if (recording) runCatching { recorder?.stop() }
        recorder?.release(); recorder = null; recording = false
        player?.release(); player = null
        file?.delete(); file = null; ready = false; listened = false
    }
    fun finishRecording() {
        val active = recorder ?: return
        val success = runCatching { active.stop() }.isSuccess
        active.release(); recorder = null; recording = false
        ready = success && file?.exists() == true
        if (!ready) { file?.delete(); file = null; message = "Не удалось сохранить запись. Попробуйте ещё раз." }
        else message = "Запись готова. Прослушайте её."
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message = if (granted) "Разрешение получено. Нажмите «Записать» ещё раз."
            else "Доступ к микрофону не предоставлен. Проверка недоступна."
    }
    DisposableEffect(owner) {
        context.cacheDir.listFiles()?.filter { it.name.startsWith("spectra-mic-test-") }?.forEach { it.delete() }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { cleanup(); message = "Проверка остановлена при уходе с экрана." }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); cleanup() }
    }
    LaunchedEffect(recording) {
        if (recording) { delay(5000); finishRecording() }
    }

    Text("Запишите 5 секунд и прослушайте. Запись временно хранится в кэше приложения и удаляется при выходе.",
        color = Muted)
    Button(enabled = !recording, onClick = {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            cleanup()
            try {
                file = File.createTempFile("spectra-mic-test-", ".m4a", context.cacheDir)
                @Suppress("DEPRECATION")
                val active = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
                recorder = active
                active.setAudioSource(MediaRecorder.AudioSource.MIC)
                active.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                active.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                active.setOutputFile(file!!.absolutePath)
                active.prepare()
                active.start()
                recording = true; message = "Идёт запись…"
            } catch (e: Exception) {
                Log.w("SpectraMic", "Unable to start microphone test", e)
                cleanup(); message = "Не удалось начать запись. Попробуйте ещё раз."
            }
        }
    }) { Text(if (recording) "Идёт запись…" else "Записать 5 секунд") }
    Button(enabled = ready && !recording, onClick = {
        player?.release(); player = null
        try {
            val active = MediaPlayer()
            player = active
            active.setDataSource(file!!.absolutePath)
            active.setOnCompletionListener { it.release(); if (player === it) player = null }
            active.prepare(); active.start()
            listened = true; message = "Прослушайте запись и подтвердите результат."
        } catch (_: Exception) { player?.release(); player = null; message = "Не удалось воспроизвести запись." }
    }) { Text("Прослушать запись") }
    message?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
    ManualResultButtons(enabled = listened, onResult = onResult)
}
