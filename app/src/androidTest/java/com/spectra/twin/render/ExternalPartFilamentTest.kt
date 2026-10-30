package com.spectra.twin.render

import android.os.SystemClock
import android.view.MotionEvent
import android.view.TextureView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalPartFilamentTest {
    @Test fun rearMainCameraTapWorksAndDragDoesNotSelect() {
        for (finish in DeviceFinish.entries) {
            ActivityScenario.launch(FilamentRenderActivity::class.java).use { scenario ->
                val selected = AtomicReference<ExternalPart?>(null)
                scenario.onActivity {
                    it.model.onPartSelected = { part -> selected.set(part) }
                    it.model.setSource(null, finish)
                    it.model.showSide(DeviceView.REAR)
                }
                val loadLimit = SystemClock.uptimeMillis() + 45000
                var loaded = false
                while (!loaded && SystemClock.uptimeMillis() < loadLimit) {
                    scenario.onActivity { loaded = it.modelLoaded; assertNull(it.modelError) }
                    if (!loaded) SystemClock.sleep(100)
                }
                assertTrue("$finish model did not load", loaded)
                SystemClock.sleep(2500)
                fun touch(action: Int, x: Float, y: Float, down: Long) {
                    scenario.onActivity { activity ->
                        val v = activity.model.getChildAt(0) as TextureView
                        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, v.width * x, v.height * y, 0)
                        v.dispatchTouchEvent(event)
                        event.recycle()
                    }
                }
                var down = SystemClock.uptimeMillis()
                touch(MotionEvent.ACTION_DOWN, .35f, .28f, down)
                touch(MotionEvent.ACTION_MOVE, .48f, .28f, down)
                touch(MotionEvent.ACTION_UP, .48f, .28f, down)
                SystemClock.sleep(600)
                assertNull("drag must not select", selected.get())
                // Restore the same camera after the drag (showSide ignores same bookmark).
                scenario.onActivity { it.model.showSide(DeviceView.FRONT); it.model.showSide(DeviceView.REAR) }
                SystemClock.sleep(1800)
                down = SystemClock.uptimeMillis()
                touch(MotionEvent.ACTION_DOWN, .35f, .28f, down)
                touch(MotionEvent.ACTION_UP, .35f, .28f, down)
                val pickLimit = SystemClock.uptimeMillis() + 10000
                while (selected.get() == null && SystemClock.uptimeMillis() < pickLimit) SystemClock.sleep(100)
                assertEquals("$finish camera pick", ExternalPart.MAIN_CAMERA, selected.get())
            }
        }
    }
}
