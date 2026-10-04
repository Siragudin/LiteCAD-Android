package com.sit.litecad.math

import kotlin.math.sqrt

object MathUtils {
    const val DefaultTolerance = 1e-5

    fun distance(p1: Point2, p2: Point2): Double {
        // ИСПРАВЛЕНО: строго заглавные X и Y, как в ПК версии PointF
        val dx = p1.X - p2.X
        val dy = p1.Y - p2.Y
        return sqrt(dx * dx + dy * dy)
    }

    fun arePointsEqual(p1: Point2, p2: Point2, tolerance: Double = DefaultTolerance): Boolean {
        return distance(p1, p2) <= tolerance
    }

    fun clamp(value: Double, min: Double, max: Double): Double {
        return value.coerceIn(min, max)
    }
}

object TopologyTolerance {
    const val ForMutation = 1e-4
}
