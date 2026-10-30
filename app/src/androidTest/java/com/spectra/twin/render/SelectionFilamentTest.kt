package com.spectra.twin.render

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.TextureView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class SelectionFilamentTest {
    @Test fun highlightRestoresAndBookmarksReachRelevantSide() {
        for (finish in DeviceFinish.entries) {
            ActivityScenario.launch(FilamentRenderActivity::class.java).use { scenario ->
                var loaded = false
                scenario.onActivity { it.model.setSource(null, finish); it.model.showSide(DeviceView.REAR) }
                val limit = SystemClock.uptimeMillis() + 45000
                while (!loaded && SystemClock.uptimeMillis() < limit) {
                    scenario.onActivity { loaded = it.modelLoaded; assertNull(it.modelError) }
                    if (!loaded) SystemClock.sleep(100)
                }
                assertTrue("load $finish", loaded)
                SystemClock.sleep(1800)
                fun frame(): Bitmap {
                    var bitmap: Bitmap? = null
                    scenario.onActivity { bitmap = (it.model.getChildAt(0) as TextureView).bitmap }
                    return requireNotNull(bitmap)
                }
                // CPU asset completion precedes the first GPU frame. Do not compare a blank surface.
                var before = frame()
                val frameLimit = SystemClock.uptimeMillis() + 30000
                fun visible(b: Bitmap): Int {
                    var n = 0
                    for (y in 0 until b.height step 4) for (x in 0 until b.width step 4) {
                        val p = b.getPixel(x, y)
                        if (((p shr 16) and 255) > 20 || ((p shr 8) and 255) > 20 || (p and 255) > 20) n++
                    }
                    return n
                }
                while (visible(before) < 500 && SystemClock.uptimeMillis() < frameLimit) {
                    before.recycle(); SystemClock.sleep(300); before = frame()
                }
                assertTrue("$finish baseline frame is blank", visible(before) >= 500)
                SystemClock.sleep(1000)
                before.recycle(); before = frame()
                scenario.onActivity { it.model.selectPart(ExternalPart.MAIN_CAMERA) }
                SystemClock.sleep(1800)
                val selected = frame()
                scenario.onActivity { it.model.selectPart(null) }
                SystemClock.sleep(1800)
                val restored = frame()
                fun difference(a: Bitmap, b: Bitmap): Int {
                    var count = 0
                    for (y in 0 until a.height step 2) for (x in 0 until a.width step 2) {
                        val pa = a.getPixel(x, y); val pb = b.getPixel(x, y)
                        val d = abs(((pa shr 16) and 255) - ((pb shr 16) and 255)) +
                            abs(((pa shr 8) and 255) - ((pb shr 8) and 255)) + abs((pa and 255) - (pb and 255))
                        if (d > 30) count++
                    }
                    return count
                }
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val resolver = instrumentation.targetContext.contentResolver
                for ((label, bitmap) in listOf("before" to before, "selected" to selected, "restored" to restored)) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, "${finish.id}-$label.png")
                        put(MediaStore.Downloads.MIME_TYPE, "image/png")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SPECTRA-selection/")
                    }
                    val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
                    requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                }
                val changed = difference(before, selected)
                val remaining = difference(before, restored)
                assertTrue("$finish highlight not visible: $changed", changed > 30)
                assertTrue("$finish material not restored: $remaining / $changed", remaining < changed / 3)
                before.recycle(); selected.recycle(); restored.recycle()
                scenario.onActivity { activity ->
                    val model = activity.model
                    fun value(name: String): Float = FilamentDeviceView::class.java.getDeclaredField(name)
                        .apply { isAccessible = true }.getFloat(model)
                    model.focusOnPart(ExternalPart.USB)
                    assertTrue("USB faces bottom", value("pitch") < -1.4f)
                    model.focusOnPart(ExternalPart.SCREEN)
                    assertTrue("screen faces front", abs(value("yaw") - Math.PI.toFloat()) < .01f)
                    model.focusOnPart(ExternalPart.VOLUME)
                    assertTrue("volume faces side", abs(value("yaw") + (Math.PI / 2).toFloat()) < .01f)
                }
            }
        }
    }
}
