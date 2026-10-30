package com.spectra.twin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeaderUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longDeviceNameDoesNotOverlapBrandOnNarrowScreen() {
        compose.setContent {
            SpectraTheme {
                Box(Modifier.width(320.dp)) {
                    Header("motorola edge 70 max with a very long device name", "3D-модель")
                }
            }
        }
        val brand = compose.onNodeWithTag("header-brand").fetchSemanticsNode().boundsInRoot
        val device = compose.onNodeWithTag("header-device").fetchSemanticsNode().boundsInRoot
        assertTrue("Brand and device name must not overlap", brand.right < device.left)
    }
}
