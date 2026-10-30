package com.spectra.twin.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PassportComparisonTest {
    @Test fun reportsOnlyChangedAddedAndRemovedFields() {
        val old = PassportSnapshot(1, mapOf("Модель" to "A", "Камеры" to "Недоступно", "Старый" to "1"))
        val new = PassportSnapshot(2, mapOf("Модель" to "A", "Камеры" to "2", "Новый" to "3"))
        assertEquals(listOf(
            PassportDifference("Камеры", "Недоступно", "2"),
            PassportDifference("Новый", "Нет в снимке", "3"),
            PassportDifference("Старый", "1", "Нет в снимке")
        ), PassportComparison.differences(old, new))
    }

    @Test fun identicalSnapshotsHaveNoDifferences() {
        val one = PassportSnapshot(1, mapOf("Дисплей" to "120 Гц"))
        assertEquals(emptyList<PassportDifference>(), PassportComparison.differences(one, one.copy(atMillis = 2)))
    }
}
