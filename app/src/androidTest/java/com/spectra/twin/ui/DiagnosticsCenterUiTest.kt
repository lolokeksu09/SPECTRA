package com.spectra.twin.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticsCenterUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reportPersistsExplicitResultsAndOpensChosenTest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-diagnostics-${System.nanoTime()}"
        val results = DiagnosticResults(context, name)
        var opened: DiagnosticKind? = null
        try {
            compose.setContent { SpectraTheme { Surface(color = Night) { DiagnosticsScreen(results) { opened = it } } } }
            compose.onNodeWithText("Проверено: 0 из 7").assertIsDisplayed()
            compose.onNodeWithTag("diagnostic-touch").assertIsDisplayed()
            compose.onNodeWithTag("diagnostics-report-toggle").performClick()
            compose.onAllNodesWithText("Не проверено").assertCountEquals(7)
            compose.runOnIdle { results.set(DiagnosticKind.TOUCH, true); results.set(DiagnosticKind.MIC, false) }
            compose.onNodeWithText("Работает").assertIsDisplayed()
            compose.onNodeWithText("Есть проблема").assertIsDisplayed()
            assertEquals(true, DiagnosticResults(context, name).get(DiagnosticKind.TOUCH))
            assertEquals(false, DiagnosticResults(context, name).get(DiagnosticKind.MIC))
            assertNull(DiagnosticResults(context, name).get(DiagnosticKind.SPEAKER))
            compose.waitForIdle()
            val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "diagnostics-report.png")
                put(MediaStore.Downloads.MIME_TYPE, "image/png")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SPECTRA-diagnostics-ui/")
            }
            val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            requireNotNull(context.contentResolver.openOutputStream(uri)).use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
            compose.onNodeWithTag("diagnostic-touch").performScrollTo().performClick()
            assertEquals(DiagnosticKind.TOUCH, opened)
        } finally {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun colorTestRequiresViewingAllColorsBeforeSuccess() {
        var result: Boolean? = null
        compose.setContent { SpectraTheme { ColorTestScreen({ result = it }, {}) } }
        compose.onNodeWithText("Работает").assertIsNotEnabled()
        repeat(5) { compose.onNodeWithTag("next-color").performClick() }
        compose.onNodeWithText("Цвет 6 / 6: Серый").assertIsDisplayed()
        compose.onNodeWithText("Работает").performClick()
        assertEquals(true, result)
    }
}

