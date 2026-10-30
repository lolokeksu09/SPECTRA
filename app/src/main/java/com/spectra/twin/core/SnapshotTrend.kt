package com.spectra.twin.core

/** Normalized chart points; null values deliberately break the plotted line. */
data class TrendPoint(val x: Float, val y: Float?)
data class TrendSeries(val points: List<TrendPoint>, val minimum: Double, val maximum: Double)

object SnapshotTrend {
    fun build(values: List<Pair<Long, Double?>>, validRange: ClosedFloatingPointRange<Double>): TrendSeries? {
        if (values.isEmpty()) return null
        val sorted = values.sortedBy { it.first }
        val valid = sorted.mapNotNull { (_, value) ->
            value?.takeIf { it.isFinite() && it in validRange }
        }
        if (valid.isEmpty()) return null
        val minimum = valid.minOrNull()!!
        val maximum = valid.maxOrNull()!!
        val start = sorted.first().first.toDouble()
        val end = sorted.last().first.toDouble()
        val span = end - start
        val points = sorted.map { (timestamp, raw) ->
            val x = if (span > 0.0) ((timestamp.toDouble() - start) / span).toFloat() else 0.5f
            val value = raw?.takeIf { it.isFinite() && it in validRange }
            val y = value?.let {
                if (maximum == minimum) 0.5f else ((it - minimum) / (maximum - minimum)).toFloat()
            }
            TrendPoint(x, y)
        }
        return TrendSeries(points, minimum, maximum)
    }
}
