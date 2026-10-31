package com.spectra.twin.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveNavigationTest {
    @Test fun railNeedsWidthAndUsableHeight() {
        assertFalse(useNavigationRail(599, 900))
        assertFalse(useNavigationRail(600, 479))
        assertTrue(useNavigationRail(600, 480))
        assertTrue(useNavigationRail(840, 700))
    }

    @Test fun previewIsShorterInLandscapeWithoutLosingSelectedPart() {
        assertEquals(425, twinPreviewHeightDp(800, false))
        assertEquals(260, twinPreviewHeightDp(800, true))
        assertEquals(290, twinPreviewHeightDp(440, false))
        assertEquals(220, twinPreviewHeightDp(440, true))
    }
}
