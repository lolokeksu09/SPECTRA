package com.spectra.twin.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TouchTestUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun marksEveryAreaAndSupportsResetAndExit() {
        var closed = false
        compose.setContent { SpectraTheme { TouchTestScreen { closed = true } } }
        compose.onNodeWithText("Отмечено: 0 / 24").assertIsDisplayed()
        compose.onNodeWithTag("touch-cell-0").performClick()
        compose.onNodeWithTag("touch-cell-0").performClick()
        compose.onNodeWithText("Отмечено: 1 / 24").assertIsDisplayed()
        compose.onNodeWithTag("touch-cell-1").performClick()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "touch-test.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SPECTRA-touch-ui/")
        }
        val resolver = instrumentation.targetContext.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        for (i in 2 until 24) compose.onNodeWithTag("touch-cell-$i").performClick()
        compose.onNodeWithText("Отмечено: 24 / 24").assertIsDisplayed()
        compose.onNodeWithTag("touch-reset").performClick()
        compose.onNodeWithText("Отмечено: 0 / 24").assertIsDisplayed()
        compose.onNodeWithTag("touch-close").performClick()
        assertTrue(closed)
    }
}
