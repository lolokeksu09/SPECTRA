package com.spectra.twin.render

import org.junit.Assert.*
import org.junit.Test

class ModelSourcesTest {
    @Test fun bundledModelNamesAreStable() {
        assertEquals("models/legion_y70_white.glb", ModelSources.builtinAsset(DeviceFinish.WHITE))
        assertEquals("models/legion_y70_black.glb", ModelSources.builtinAsset(DeviceFinish.BLACK))
    }

    @Test fun blackModelPickerIsRequiredOnlyWithoutBundledOrImportedModel() {
        assertFalse(ModelSources.needsBlackImport(DeviceFinish.WHITE, false, null))
        assertTrue(ModelSources.needsBlackImport(DeviceFinish.BLACK, false, null))
        assertFalse(ModelSources.needsBlackImport(DeviceFinish.BLACK, true, null))
        assertFalse(ModelSources.needsBlackImport(DeviceFinish.BLACK, false, "content://example/model"))
    }
}
