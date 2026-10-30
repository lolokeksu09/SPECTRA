package com.spectra.twin.core

import java.util.Locale

/** Only compare valid measurements from both snapshots; no missing value is treated as zero. */
object SnapshotComparison {
    fun battery(before: Int?, after: Int?): String =
        if (before == null || after == null || before !in 0..100 || after !in 0..100) "Недоступно"
        else String.format(Locale.US, "%+d п.п.", after - before)

    fun temperature(before: Double?, after: Double?): String =
        if (before == null || after == null || !before.isFinite() || !after.isFinite() ||
            before !in -50.0..150.0 || after !in -50.0..150.0) "Недоступно"
        else String.format(Locale.US, "%+.1f °C", after - before)

    fun memory(before: Long?, after: Long?): String =
        if (before == null || after == null || before < 0 || after < 0) "Недоступно"
        else String.format(Locale.US, "%+.2f ГБ", (after.toDouble() - before.toDouble()) / 1_073_741_824.0)
}
