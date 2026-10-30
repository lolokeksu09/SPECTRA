package com.spectra.twin.render

import org.junit.Assert.*
import org.junit.Test

class ExternalPartTest {
    @Test fun modelNamesMapToPhysicalExteriorParts() {
        assertEquals(ExternalPart.MAIN_CAMERA, ExternalPart.fromNode("015_primary_central_lens"))
        assertEquals(ExternalPart.ULTRAWIDE, ExternalPart.fromNode("030_bottom_central_lens"))
        assertEquals(ExternalPart.FLICKER, ExternalPart.fromNode("021_top_right_black_glass"))
        assertEquals(ExternalPart.FLASH, ExternalPart.fromNode("061_LED_warm_diffuser"))
        assertEquals(ExternalPart.SCREEN, ExternalPart.fromNode("004_front_glass_with_photo_reference_LEGION_wallpaper"))
        assertEquals(ExternalPart.USB, ExternalPart.fromNode("v6_USB_C_inset_satin_rim"))
        assertNull(ExternalPart.fromNode("unknown_external_asset"))
        assertNull(ExternalPart.fromNode(null))
    }
}
