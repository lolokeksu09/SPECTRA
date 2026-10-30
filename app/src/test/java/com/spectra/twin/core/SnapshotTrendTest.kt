package com.spectra.twin.core

import org.junit.Assert.*
import org.junit.Test

class SnapshotTrendTest {
    @Test fun sortsByTimeAndPreservesMissingPoints() {
        val result = SnapshotTrend.build(listOf(30L to 80.0, 10L to 90.0, 20L to null), 0.0..100.0)!!
        assertEquals(listOf(0f, 0.5f, 1f), result.points.map { it.x })
        assertEquals(listOf(1f, null, 0f), result.points.map { it.y })
        assertEquals(80.0, result.minimum, 0.0)
        assertEquals(90.0, result.maximum, 0.0)
    }

    @Test fun rejectsInvalidValuesAndHandlesSingleTimeOrConstantData() {
        assertNull(SnapshotTrend.build(listOf(1L to Double.NaN, 2L to 101.0), 0.0..100.0))
        val sameTime = SnapshotTrend.build(listOf(1L to 25.0, 1L to 25.0), 0.0..100.0)!!
        assertEquals(listOf(0.5f, 0.5f), sameTime.points.map { it.x })
        assertEquals(listOf(0.5f, 0.5f), sameTime.points.map { it.y })
        val gap = SnapshotTrend.build(listOf(1L to 10.0, 2L to Double.POSITIVE_INFINITY), 0.0..100.0)!!
        assertNull(gap.points.last().y)
    }
}
