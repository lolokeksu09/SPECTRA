package com.spectra.twin.ui

import android.Manifest
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MicrophoneUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recordsPlaysAndDeletesTemporaryFileOnExit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        val show = mutableStateOf(true)
        try {
            compose.setContent {
                SpectraTheme { Surface(color = Night) { if (show.value) MicrophoneTest {} } }
            }
            compose.onNodeWithText("Записать 5 секунд").performClick()
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText("Запись готова. Прослушайте её.").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Прослушать запись").performClick()
            compose.onNodeWithText("Прослушайте запись и подтвердите результат.").assertIsDisplayed()
            compose.runOnIdle { show.value = false }
            compose.waitForIdle()
            assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("spectra-mic-test-") })
        } finally {
            instrumentation.uiAutomation.revokeRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        }
    }
}
