package com.spectra.twin.render

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spectra.twin.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Screenshot of the real app dialog; the separate Filament test exercises actual touches. */
@RunWith(AndroidJUnit4::class)
class ExternalPartUiTest {
    private fun findModel(view: View): FilamentDeviceView? {
        if (view is FilamentDeviceView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findModel(view.getChildAt(i))?.let { return it }
        }
        return null
    }
    @Test fun capturesPartCardInRealActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var found = false
            val limit = SystemClock.uptimeMillis() + 30000
            while (!found && SystemClock.uptimeMillis() < limit) {
                scenario.onActivity { activity ->
                    findModel(activity.window.decorView)?.let { view ->
                        found = true
                        view.onPartSelected(ExternalPart.MAIN_CAMERA)
                    }
                }
                if (!found) SystemClock.sleep(200)
            }
            assertTrue("3D model view unavailable in MainActivity", found)
            SystemClock.sleep(2000)
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "external-part-card.png")
                put(MediaStore.Downloads.MIME_TYPE, "image/png")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SPECTRA-part-ui/")
            }
            val resolver = instrumentation.targetContext.contentResolver
            val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
