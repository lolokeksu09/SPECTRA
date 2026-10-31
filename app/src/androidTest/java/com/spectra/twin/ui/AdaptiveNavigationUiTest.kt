package com.spectra.twin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveNavigationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactBarOpensRequestedDestination() {
        var selected by mutableStateOf(Page.TWIN)
        compose.setContent {
            SpectraTheme {
                NavigationMenu(selected, useRail = false, showRailLabels = true) { selected = it }
            }
        }
        compose.onNodeWithTag("navigation-bar").assertIsDisplayed()
        compose.onNodeWithTag("nav-history").performClick()
        compose.runOnIdle { assertEquals(Page.HISTORY, selected) }
    }

    @Test fun wideRailOpensRequestedDestination() {
        var selected by mutableStateOf(Page.TWIN)
        compose.setContent {
            SpectraTheme {
                Box(Modifier.height(700.dp)) {
                    NavigationMenu(selected, useRail = true, showRailLabels = true) { selected = it }
                }
            }
        }
        compose.onNodeWithTag("navigation-rail").assertIsDisplayed()
        compose.onNodeWithTag("nav-diagnostics").performClick()
        compose.runOnIdle { assertEquals(Page.DIAGNOSTICS, selected) }
    }
}
