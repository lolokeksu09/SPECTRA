package com.spectra.twin.ui

import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spectra.twin.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VolumeDispatchTest {
    @Test fun volumeKeysAreCapturedWhileHandlerIsActive() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val captured = mutableListOf<Int>()
            scenario.onActivity { activity ->
                activity.diagnosticVolumeHandler = { captured.add(it) }
                assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)))
                assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP)))
                assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)))
                activity.diagnosticVolumeHandler = null
            }
            assertEquals(listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN), captured)
        }
    }
}
