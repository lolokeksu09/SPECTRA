package com.spectra.twin.render

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class BoundedInputTest {
    @Test fun acceptsExactLimitAndEmpty() {
        assertArrayEquals(byteArrayOf(1, 2, 3), BoundedInput.read(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3))
        assertArrayEquals(byteArrayOf(), BoundedInput.read(ByteArrayInputStream(byteArrayOf()), 0))
    }
    @Test fun rejectsOversizeWithoutReadingWholeFile() {
        val stream = ByteArrayInputStream(ByteArray(1_000_000))
        try { BoundedInput.read(stream, 32); fail("expected size error") }
        catch (e: IllegalArgumentException) { assertEquals(1_000_000 - 33, stream.available()) }
    }
}
