package com.spectra.twin.render

import org.junit.Assert.*
import org.junit.Test

class GlbHeaderTest {
    private fun valid() = byteArrayOf(
        0x67,0x6c,0x54,0x46, 2,0,0,0, 24,0,0,0,
        4,0,0,0, 0x4a,0x53,0x4f,0x4e, 0x7b,0x7d,0x20,0x20)
    @Test fun acceptsHeader() { GlbHeader.validate(valid()) }
    @Test fun rejectsWrongLength() {
        val b = valid(); b[8] = 22
        assertThrows(IllegalArgumentException::class.java) { GlbHeader.validate(b) }
    }
    @Test fun rejectsNonGlb() {
        assertThrows(IllegalArgumentException::class.java) { GlbHeader.validate("abc".toByteArray()) }
    }
}
