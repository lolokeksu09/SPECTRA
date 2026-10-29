package com.spectra.twin.ui

import android.app.Application
import android.content.Context
import com.spectra.twin.render.DeviceFinish
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spectra.twin.core.SnapshotStore
import com.spectra.twin.core.TelemetryReader
import com.spectra.twin.core.TelemetrySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpectraViewModel(app: Application) : AndroidViewModel(app) {
    private val reader = TelemetryReader(app)
    private val store = SnapshotStore(app)
    private val visualPrefs = app.getSharedPreferences("spectra-visual-v2", Context.MODE_PRIVATE)
    var importedModelUri by mutableStateOf(visualPrefs.getString("white_glb_uri", null))
        private set
    var blackModelUri by mutableStateOf(visualPrefs.getString("black_glb_uri", null))
        private set

    fun importModelUri(value: String) {
        importedModelUri = value
        visualPrefs.edit().putString("white_glb_uri", value).apply()
    }

    fun importBlackModelUri(value: String) {
        blackModelUri = value
        visualPrefs.edit().putString("black_glb_uri", value).apply()
    }
    /** Non-destructive: clear only an imported GLB URI, never diagnostic snapshots. */
    fun restoreBundledModel(finish: DeviceFinish) {
        when (finish) {
            DeviceFinish.WHITE -> {
                importedModelUri = null
                visualPrefs.edit().remove("white_glb_uri").apply()
            }
            DeviceFinish.BLACK -> {
                blackModelUri = null
                visualPrefs.edit().remove("black_glb_uri").apply()
            }
        }
    }
    private val preferences = app.getSharedPreferences("spectra-visual-v1", Context.MODE_PRIVATE)
    var finish by mutableStateOf(DeviceFinish.fromId(preferences.getString("finish", null)))
        private set

    fun selectFinish(value: DeviceFinish) {
        if (value == finish) return
        finish = value
        preferences.edit().putString("finish", value.id).apply()
    }
    var latest by mutableStateOf<TelemetrySnapshot?>(null)
        private set
    var history by mutableStateOf<List<TelemetrySnapshot>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
        private set
    private var poller: Job? = null
    private var saving = false

    init {
        viewModelScope.launch {
            try {
                history = withContext(Dispatchers.IO) { store.readAll() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Ошибка чтения истории: ${e.javaClass.simpleName}"
            }
        }
    }

    fun start() {
        if (poller?.isActive == true) return
        poller = viewModelScope.launch {
            while (isActive) {
                try {
                    val next = withContext(Dispatchers.IO) { reader.read() }
                    latest = next
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message = "Ошибка чтения датчиков: ${e.javaClass.simpleName}"
                }
                delay(2500)
            }
        }
    }

    fun stop() { poller?.cancel(); poller = null }

    fun snapshot() {
        val data = latest ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                history = withContext(Dispatchers.IO) { store.add(data) }
                message = "Снимок состояния сохранён"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Не удалось сохранить снимок: ${e.javaClass.simpleName}"
            } finally {
                saving = false
            }
        }
    }

    fun clearMessage() { message = null }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
