package com.spectra.twin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.spectra.twin.ui.SpectraApp
import com.spectra.twin.ui.SpectraTheme
import com.spectra.twin.ui.SpectraViewModel

class MainActivity : ComponentActivity() {
    private val telemetryModel: SpectraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpectraTheme { SpectraApp(telemetryModel) }
        }
    }
}
