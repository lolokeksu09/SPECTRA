package com.spectra.twin.render

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.os.Debug
import android.os.Environment
import android.provider.MediaStore
import android.view.TextureView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject

/** Real Android Filament/gltfio loading + GPU output; this does not certify the Legion hardware. */
@RunWith(AndroidJUnit4::class)
class WhiteModelFilamentTest {
    private val evidenceUris = mutableMapOf<String, Uri>()

    private fun saveEvidence(name: String, mime: String, writer: (OutputStream) -> Unit) {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val uri = evidenceUris.getOrPut(name) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SPECTRA-v18/")
            }
            requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        }
        requireNotNull(resolver.openOutputStream(uri, "wt")).use(writer)
    }

    private fun saveJson(name: String, text: String) {
        saveEvidence(name, "application/json") { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun field(view: FilamentDeviceView, name: String, value: Any) {
        // Confined to the test APK: never modify production camera or lighting code for screenshots.
        FilamentDeviceView::class.java.getDeclaredField(name).apply { isAccessible = true }.set(view, value)
    }

    @Test fun whiteV17AndV18RenderInUnmodifiedFilament() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val metrics = JSONArray()
        for (version in listOf("v17", "v18")) {
            val source = if (version == "v17") {
                val file = File(app.cacheDir, "white-v17-test.glb")
                instrumentation.context.assets.open("models/white-v17.glb").use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                Uri.fromFile(file)
            } else null
            ActivityScenario.launch(FilamentRenderActivity::class.java).use { scenario ->
                val started = SystemClock.uptimeMillis()
                scenario.onActivity { it.model.setSource(source, DeviceFinish.WHITE) }
                val limit = SystemClock.uptimeMillis() + 45_000
                var loaded = false
                while (!loaded && SystemClock.uptimeMillis() < limit) {
                    scenario.onActivity { activity ->
                        assertNull("$version gltfio error", activity.modelError)
                        loaded = activity.modelLoaded
                    }
                    if (!loaded) SystemClock.sleep(100)
                }
                assertTrue("$version gltfio load timed out", loaded)
                val loadMs = SystemClock.uptimeMillis() - started
                val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                metrics.put(JSONObject().put("version", version).put("gltfio_load_ms", loadMs)
                    .put("process_pss_kib", memory.totalPss)
                    .put("environment", "Android API 35 emulator / SwiftShader; not Legion hardware"))
                for (shot in listOf("rear", "cameras", "bottom", "top")) {
                    scenario.onActivity { activity ->
                        val view = activity.model
                        view.showSide(when (shot) {
                            "bottom" -> DeviceView.BOTTOM
                            "top" -> DeviceView.TOP
                            else -> DeviceView.REAR
                        })
                        if (shot == "cameras") {
                            field(view, "yaw", 0f)
                            field(view, "pitch", -.08f)
                            field(view, "focusY", .67)
                            field(view, "radius", 2.15f)
                        }
                    }
                    // A nonblank previous frame is not evidence for a new bookmark.
                    // Let the native presentation queue advance before validating its shape.
                    SystemClock.sleep(1500)
                    // CPU-side gltfio load completion precedes the first GPU frame.
                    // Wait for visible GPU output with a deadline; retain failure evidence too.
                    val frameLimit = SystemClock.uptimeMillis() + 30_000
                    var visible = 0
                    var dark = 0
                    var visibleSpan = 0
                    var correctShape = false
                    var lastPixels: Bitmap? = null
                    var state = JSONObject()
                    do {
                        SystemClock.sleep(300)
                        var bitmap: Bitmap? = null
                        scenario.onActivity { activity ->
                            assertNull(activity.modelError)
                            val view = activity.model
                            val surface = view.getChildAt(0) as TextureView
                            state = JSONObject().put("surface_available", surface.isAvailable)
                                .put("surface_shown", surface.isShown)
                                .put("window_visibility", surface.windowVisibility)
                            for (name in listOf("active", "attached", "disposed", "lastFrame", "frameFailures")) {
                                val f = FilamentDeviceView::class.java.getDeclaredField(name).apply { isAccessible = true }
                                state.put(name, f.get(view))
                            }
                            if (surface.isAvailable) bitmap = surface.bitmap
                        }
                        bitmap?.let { pixels ->
                            lastPixels?.recycle()
                            lastPixels = pixels
                            visible = 0
                            dark = 0
                            var minY = pixels.height
                            var maxY = -1
                            for (y in 0 until pixels.height step 4) for (x in 0 until pixels.width step 4) {
                                val p = pixels.getPixel(x, y)
                                val r = (p shr 16) and 255
                                val g = (p shr 8) and 255
                                val b = p and 255
                                if (r > 65 || g > 65 || b > 65) {
                                    visible++
                                    minY = minOf(minY, y)
                                    maxY = maxOf(maxY, y)
                                } else dark++
                            }
                            visibleSpan = maxY - minY
                            correctShape = when (shot) {
                                "bottom", "top" -> visibleSpan in 10..(pixels.height * .30).toInt()
                                "rear" -> visibleSpan > pixels.height * .35 && visibleSpan < pixels.height * .85
                                else -> visibleSpan > pixels.height * .60
                            }
                        }
                    } while ((visible <= 150 || dark <= 150 || !correctShape) && SystemClock.uptimeMillis() < frameLimit)
                    state.put("visible_samples", visible).put("dark_samples", dark)
                        .put("visible_span_pixels", visibleSpan).put("correct_bookmark_shape", correctShape)
                    saveJson("white-$version-$shot-state.json", state.toString(2))
                    saveJson("android-load-metrics.json", metrics.toString(2))
                    val pixels = requireNotNull(lastPixels) { "GPU readback unavailable: $state" }
                    saveEvidence("white-$version-$shot.png", "image/png") {
                        assertTrue(pixels.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    assertTrue(pixels.width >= 300 && pixels.height >= 300)
                    pixels.recycle()
                    assertTrue("$version/$shot blank render: $state", visible > 150)
                    assertTrue("$version/$shot missing background: $state", dark > 150)
                    assertTrue("$version/$shot stale or incorrect bookmark frame: $state", correctShape)
                }
            }
        }
        saveJson("android-load-metrics.json", metrics.toString(2))
    }
}
