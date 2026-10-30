package com.spectra.twin.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spectra.twin.core.TelemetrySnapshot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryCompactUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactSnapshotsCanExpandAndBeCompared() {
        val first = TelemetrySnapshot(1000L, "device", 40, 30.0, null, null, false, null,
            null, null, null, null, null, 0)
        val second = first.copy(atMillis = 2000L, batteryPercent = 50)
        compose.setContent { SpectraTheme { Surface(color = Night) {
            HistoryScreen(listOf(first, second), loading = false, error = false, retry = {})
        } } }
        compose.onNodeWithTag("history-list").performScrollToNode(hasTestTag("snapshot-detail-0"))
        compose.onNodeWithTag("snapshot-detail-0").performClick()
        compose.onNodeWithText("Тепловой статус").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("snapshot-detail-0").performScrollTo().performClick()
        compose.onNodeWithTag("snapshot-select-0").performScrollTo().performClick()
        compose.onNodeWithTag("history-list").performScrollToNode(hasTestTag("snapshot-select-1"))
        compose.onNodeWithTag("snapshot-select-1").performClick()
        compose.onNodeWithTag("history-list").performScrollToNode(hasText("Сравнение снимков"))
        compose.onNodeWithText("Изменение рассчитано как «после» минус «до».").performScrollTo().assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "history-comparison.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SPECTRA-trend-ui/")
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        requireNotNull(context.contentResolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
