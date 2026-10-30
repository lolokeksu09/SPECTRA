package com.spectra.twin.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spectra.twin.core.TelemetrySnapshot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnapshotTrendUiTest {
    @get:Rule val compose = createComposeRule()

    private fun snapshot(time: Long, charge: Int?, temperature: Double?) = TelemetrySnapshot(
        atMillis = time, deviceName = "test", batteryPercent = charge, batteryCelsius = temperature,
        batteryVoltageMv = null, batteryCurrentMa = null, isCharging = null, batteryHealth = null,
        memoryTotalBytes = null, memoryAvailableBytes = null, storageTotalBytes = null,
        storageAvailableBytes = null, refreshRateHz = null, thermalStatus = null
    )

    @Test fun switchesMetricsAndShowsAvailableRange() {
        compose.setContent {
            SpectraTheme { SnapshotTrendCard(listOf(snapshot(1000, 90, 30.0), snapshot(2000, null, null), snapshot(3000, 80, 35.0))) }
        }
        compose.onNodeWithText("Заряд батареи · 80–90 %").assertIsDisplayed()
        compose.onNodeWithText("Температура").performClick()
        compose.onNodeWithText("Температура батареи · 30.0–35.0 °C").assertIsDisplayed()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "snapshot-trend.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SPECTRA-trend-ui/")
        }
        val resolver = instrumentation.targetContext.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    @Test fun unavailableMetricDoesNotShowZero() {
        compose.setContent { SpectraTheme { SnapshotTrendCard(listOf(snapshot(1000, null, null))) } }
        compose.onNodeWithText("Нет доступных данных для графика").assertIsDisplayed()
        compose.onNodeWithText("Температура").performClick()
        compose.onNodeWithText("Нет доступных данных для графика").assertIsDisplayed()
    }
}
