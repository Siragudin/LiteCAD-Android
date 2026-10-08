package com.sit.litecad.math


import kotlin.math.sqrt

object MathUtils {
    const val DefaultTolerance = 1e-5

    fun distance(p1: Point2, p2: Point2): Double {
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

    /**
     * Знаковая площадь замкнутого полигона (формула шнуровки).
     * > 0 — обход против часовой (CCW), < 0 — по часовой (CW).
     * Если точек < 3 — возвращает 0.
     */
    fun signedPolygonArea(points: List<Point2>): Double {
        if (points.size < 3) return 0.0
        var sum = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.X * b.Y - b.X * a.Y
        }
        return sum / 2.0
    }

    /**
     * Проверка, лежит ли точка внутри полигона (ray casting).
     * Границы полигона обрабатываются как «внутри» только по строгому правилу — точка ровно на ребре
     * может дать как true, так и false. Для топологии это компенсируется проверками в вызывающем коде
     * (см. IsPointStrictlyInsideContour в PolygonBuilder).
     */
    fun pointInPolygon(point: Point2, polygon: List<Point2>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            val intersect = ((pi.Y > point.Y) != (pj.Y > point.Y)) &&
                    (point.X < (pj.X - pi.X) * (point.Y - pi.Y) / (pj.Y - pi.Y) + pi.X)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }
}

object TopologyTolerance {
    const val ForMutation = 1e-4
}