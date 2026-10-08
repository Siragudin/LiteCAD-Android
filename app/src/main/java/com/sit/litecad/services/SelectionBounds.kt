package com.sit.litecad.services


import com.sit.litecad.geometry.PolygonGeometry
import com.sit.litecad.math.Geometry2D
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.Polygon
import kotlin.math.max
import kotlin.math.min

/**
 * Прямоугольная рамка в мировых координатах.
 * min/max нормализованы (minX <= maxX).
 */
data class SelectionBounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY

    fun contains(point: Point2): Boolean =
        point.X in minX..maxX && point.Y in minY..maxY

    /** Оба конца внутри. */
    fun containsSegmentFully(start: Point2, end: Point2): Boolean =
        contains(start) && contains(end)

    /** Все вершины границы грани внутри. */
    fun containsPolygonFully(document: CadDocument, polygon: Polygon): Boolean {
        val points = PolygonGeometry.getOuterBoundaryPoints(document, polygon)
        if (points.size < 3) return false
        return points.all { contains(it) }
    }

    /** Отрезок либо хотя бы один конец внутри, либо пересекает любую из сторон рамки. */
    fun intersectsSegment(start: Point2, end: Point2): Boolean {
        if (contains(start) || contains(end)) return true

        val topLeft = Point2(minX, maxY)
        val topRight = Point2(maxX, maxY)
        val bottomRight = Point2(maxX, minY)
        val bottomLeft = Point2(minX, minY)

        return segmentIntersects(start, end, topLeft, topRight) ||
                segmentIntersects(start, end, topRight, bottomRight) ||
                segmentIntersects(start, end, bottomRight, bottomLeft) ||
                segmentIntersects(start, end, bottomLeft, topLeft)
    }

    /** Любая вершина грани внутри ИЛИ любое её ребро пересекает рамку. */
    fun intersectsPolygon(document: CadDocument, polygon: Polygon): Boolean {
        val points = PolygonGeometry.getOuterBoundaryPoints(document, polygon)
        if (points.size < 3) return false

        for (p in points) {
            if (contains(p)) return true
        }

        for (i in points.indices) {
            val start = points[i]
            val end = points[(i + 1) % points.size]
            if (intersectsSegment(start, end)) return true
        }

        return false
    }

    private fun segmentIntersects(a1: Point2, a2: Point2, b1: Point2, b2: Point2): Boolean =
        Geometry2D.tryGetSegmentIntersection(a1, a2, b1, b2, MathUtils.DefaultTolerance) != null

    companion object {
        fun fromPoints(a: Point2, b: Point2): SelectionBounds =
            SelectionBounds(
                minX = min(a.X, b.X),
                minY = min(a.Y, b.Y),
                maxX = max(a.X, b.X),
                maxY = max(a.Y, b.Y)
            )
    }
}