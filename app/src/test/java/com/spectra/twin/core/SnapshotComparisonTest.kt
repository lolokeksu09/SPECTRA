package com.spectra.twin.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotComparisonTest {
    @Test fun batteryUsesPercentagePointsAndRequiresValidMeasurements() {
        assertEquals("-12 п.п.", SnapshotComparison.battery(85, 73))
        assertEquals("+0 п.п.", SnapshotComparison.battery(10, 10))
        assertEquals("Недоступно", SnapshotComparison.battery(null, 10))
        assertEquals("Недоступно", SnapshotComparison.battery(101, 10))
    }

    @Test fun temperatureRejectsMissingNonFiniteAndOutOfRangeValues() {
        assertEquals("+4.5 °C", SnapshotComparison.temperature(30.0, 34.5))
        assertEquals("Недоступно", SnapshotComparison.temperature(30.0, null))
        assertEquals("Недоступно", SnapshotComparison.temperature(Double.NaN, 30.0))
        assertEquals("Недоступно", SnapshotComparison.temperature(-51.0, 30.0))
    }

    @Test fun memoryReportsSignedDifferenceAndRejectsMissingData() {
        assertEquals("-1.00 ГБ", SnapshotComparison.memory(2_147_483_648, 1_073_741_824))
        assertEquals("Недоступно", SnapshotComparison.memory(null, 100))
        assertEquals("Недоступно", SnapshotComparison.memory(-1, 100))
    }
}
