package com.spectra.twin

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.spectra.twin.ui.SpectraApp
import com.spectra.twin.ui.SpectraTheme
import com.spectra.twin.ui.SpectraViewModel

class MainActivity : ComponentActivity() {
    private val telemetryModel: SpectraViewModel by viewModels()
    var diagnosticVolumeHandler: ((Int) -> Unit)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = diagnosticVolumeHandler
        if (handler != null && (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                    event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) handler(event.keyCode)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpectraTheme { SpectraApp(telemetryModel) }
        }
    }
}
