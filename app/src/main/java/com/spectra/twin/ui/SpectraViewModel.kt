package com.spectra.twin.ui

import android.app.Application
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
