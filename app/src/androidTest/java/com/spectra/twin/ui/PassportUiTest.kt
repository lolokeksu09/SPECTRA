package com.spectra.twin.ui

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spectra.twin.core.DeviceCapabilities
import com.spectra.twin.core.PassportSnapshot
import com.spectra.twin.core.PassportStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PassportUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compareSelectedSavedPassportsAndSaveNewOne() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "passport-ui-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir(): File = folder }
        val store = PassportStore(isolated)
        store.add(PassportSnapshot(1000, mapOf("Android · Модель" to "Старое")))
        store.add(PassportSnapshot(2000, mapOf("Android · Модель" to "Новое")))
        val prefName = "passport-ui-prefs-${System.nanoTime()}"
        val results = DiagnosticResults(context, prefName)
        try {
            compose.setContent { SpectraTheme {
                PassportScreen(DeviceCapabilities(emptyList(), emptyList(), emptyList()), results, storeOverride = store, onClose = {})
            } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Выбрать для сравнения").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText("Выбрать для сравнения")[0].performScrollTo().performClick()
            compose.onAllNodesWithText("Выбрать для сравнения")[0].performScrollTo().performClick()
            compose.onNodeWithTag("passport-list").performScrollToNode(hasText("Сравнение паспортов"))
            compose.onNodeWithText("До: Старое").assertIsDisplayed()
            compose.onNodeWithText("После: Новое").assertIsDisplayed()
            val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "passport-comparison.png")
                put(MediaStore.Downloads.MIME_TYPE, "image/png")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SPECTRA-passport-ui/")
            }
            val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            requireNotNull(context.contentResolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            compose.onNodeWithTag("passport-list").performScrollToNode(hasText("Сохранить паспорт"))
            compose.onNodeWithTag("save-passport").performClick()
            compose.waitUntil(20_000) { store.readAll().size == 3 }
            assertEquals(3, store.readAll().size)
        } finally {
            context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().commit()
            folder.deleteRecursively()
        }
    }
}
