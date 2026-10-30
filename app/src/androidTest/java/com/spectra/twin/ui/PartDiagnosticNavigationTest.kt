package com.spectra.twin.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spectra.twin.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PartDiagnosticNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun returnsToSelectedPartAfterOpeningItsCheck() {
        compose.onNodeWithText("Выбрать деталь из списка").performScrollTo().performClick()
        compose.onNodeWithText("Экран").performClick()
        compose.onNodeWithTag("part-test-touch").performScrollTo().performClick()
        compose.onNodeWithText("Проверка касаний").assertIsDisplayed()
        compose.onNodeWithText("Проблема").performClick()
        compose.onNodeWithTag("part-test-touch").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Касания экрана · результат пользователя: Есть проблема").performScrollTo().assertIsDisplayed()
    }

    @Test fun cameraCardOpensCameraSectionOfPassport() {
        compose.onNodeWithText("Выбрать деталь из списка").performScrollTo().performClick()
        compose.onNodeWithText("Верхняя задняя камера").performClick()
        compose.onNodeWithTag("open-camera-passport").performScrollTo().performClick()
        compose.onNodeWithText("Камеры, доступные Android").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("passport-show-all").assertIsDisplayed()
    }

    @Test fun screenCardOpensDisplaySectionOfPassport() {
        compose.onNodeWithText("Выбрать деталь из списка").performScrollTo().performClick()
        compose.onNodeWithText("Экран").performClick()
        compose.onNodeWithTag("open-display-passport").performScrollTo().performClick()
        compose.onNodeWithText("Режимы дисплея Android").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("passport-show-all").performScrollTo().assertIsDisplayed()
    }

    @Test fun usbOpensBatteryAndBackReturnsToSelectedPart() {
        compose.onNodeWithText("Выбрать деталь из списка").performScrollTo().performClick()
        compose.onNodeWithText("USB-C").performClick()
        compose.onNodeWithTag("open-battery-telemetry").performScrollTo().performClick()
        compose.onNodeWithText("АККУМУЛЯТОР").assertIsDisplayed()
        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("open-battery-telemetry").assertIsDisplayed()
        val context = compose.activity
        val bitmap = requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "usb-return.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SPECTRA-part-ui/")
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        requireNotNull(context.contentResolver.openOutputStream(uri)).use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}

