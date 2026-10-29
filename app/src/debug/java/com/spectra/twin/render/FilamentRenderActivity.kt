package com.spectra.twin.render

import android.os.Bundle
import androidx.activity.ComponentActivity

/** Non-exported debug-only host for on-device gltfio regression evidence. */
class FilamentRenderActivity : ComponentActivity() {
    lateinit var model: FilamentDeviceView
        private set
    @Volatile var modelLoaded = false
        private set
    @Volatile var modelError: String? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = FilamentDeviceView(this)
        model.onStatus = { message ->
            modelError = message
            modelLoaded = message == null
        }
        setContentView(model)
    }

    override fun onResume() { super.onResume(); model.resume() }
    override fun onPause() { model.pause(); super.onPause() }
}
