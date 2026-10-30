package com.spectra.twin.ui

import com.spectra.twin.core.TelemetrySnapshot
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class VisibleTelemetryTest {
    private val sample = TelemetrySnapshot(
        atMillis = 1L, deviceName = "device", batteryPercent = 50, batteryCelsius = 30.0,
        batteryVoltageMv = 4000, batteryCurrentMa = null, isCharging = false,
        batteryHealth = null, memoryTotalBytes = null, memoryAvailableBytes = null,
        storageTotalBytes = null, storageAvailableBytes = null, refreshRateHz = null,
        thermalStatus = null
    )

    @Test fun staleSnapshotIsNotDisplayedAsCurrentAndFreshDataReturns() {
        assertSame(sample, visibleTelemetry(sample, false))
        assertNull(visibleTelemetry(sample, true))
        assertNull(visibleTelemetry(null, false))
        assertSame(sample, visibleTelemetry(sample, false))
    }
}
