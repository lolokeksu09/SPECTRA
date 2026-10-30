package com.spectra.twin.ui

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spectra.twin.core.PassportSnapshot
import com.spectra.twin.core.PassportStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PassportStoreAndroidTest {
    @Test fun savesAndRejectsDamagedHistoryWithoutOverwritingIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "passport-test-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir(): File = folder }
        try {
            val store = PassportStore(isolated)
            val first = PassportSnapshot(1, mapOf("Android · Модель" to "A"))
            val second = PassportSnapshot(2, mapOf("Android · Модель" to "B"))
            assertTrue(store.readAll().isEmpty())
            store.add(first); store.add(second)
            assertEquals(listOf(second, first), PassportStore(isolated).readAll())
            val file = File(folder, "device-passports-v1.json")
            file.writeText("broken")
            assertTrue(runCatching { store.add(first) }.isFailure)
            assertEquals("broken", file.readText())
        } finally { folder.deleteRecursively() }
    }
}
