package com.sit.litecad.geometry

import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.Loop
import com.sit.litecad.model.Polygon
import java.util.UUID

object PolygonGeometry {

    /**
     * Возвращает точки контура в порядке обхода loop.
     * Для каждого DirectedEdgeReference берёт «стартовую» вершину (та, откуда идём).
     * Если хотя бы одно ребро не найдено — возвращает пустой список.
     */
    fun getBoundaryPoints(document: CadDocument, loop: Loop): List<Point2> {
        if (loop.edges.isEmpty()) return emptyList()
        val points = ArrayList<Point2>(loop.edges.size)
        for (ref in loop.edges) {
            val edge = document.edgeById(ref.edgeId) ?: return emptyList()
            val vertexId = if (ref.forward) edge.startVertexId else edge.endVertexId
            points.add(TopologyService.getVertexPosition(document, vertexId))
        }
        return points
    }

    fun getOuterBoundaryPoints(document: CadDocument, polygon: Polygon): List<Point2> =
        getBoundaryPoints(document, polygon.outerLoop)

    /**
     * Знаковая площадь контура (shoelace).
     * > 0 — CCW, < 0 — CW.
     */
    fun getSignedArea(document: CadDocument, loop: Loop): Double =
        MathUtils.signedPolygonArea(getBoundaryPoints(document, loop))

    /**
     * Площадь полигона: |outer| - сумма |holes|. Не меньше 0.
     */
    fun getArea(document: CadDocument, polygon: Polygon): Double {
        val outerArea = kotlin.math.abs(getSignedArea(document, polygon.outerLoop))
        val holeArea = polygon.innerLoops.sumOf { kotlin.math.abs(getSignedArea(document, it)) }
        return kotlin.math.max(0.0, outerArea - holeArea)
    }

    /**
     * Периметр: сумма длин рёбер внешнего контура + дырок.
     */
    fun getPerimeter(document: CadDocument, polygon: Polygon): Double {
        var perimeter = sumLoopEdgeLengths(document, polygon.outerLoop)
        for (hole in polygon.innerLoops) {
            perimeter += sumLoopEdgeLengths(document, hole)
        }
        return perimeter
    }

    /**
     * Габариты внешнего контура. null — если контур пуст.
     */
    fun getBounds(document: CadDocument, polygon: Polygon): Pair<Point2, Point2>? {
        val points = getOuterBoundaryPoints(document, polygon)
        if (points.isEmpty()) return null
        var minX = points[0].X
        var minY = points[0].Y
        var maxX = points[0].X
        var maxY = points[0].Y
        for (p in points) {
            if (p.X < minX) minX = p.X
            if (p.Y < minY) minY = p.Y
            if (p.X > maxX) maxX = p.X
            if (p.Y > maxY) maxY = p.Y
        }
        return Point2(minX, minY) to Point2(maxX, maxY)
    }

    /**
     * Точка внутри полигона: внутри outer и снаружи всех holes.
     */
    fun containsPoint(document: CadDocument, polygon: Polygon, point: Point2): Boolean {
        val outer = getOuterBoundaryPoints(document, polygon)
        if (outer.size < 3 || !MathUtils.pointInPolygon(point, outer)) {
            return false
        }
        for (hole in polygon.innerLoops) {
            val holePoints = getBoundaryPoints(document, hole)
            if (holePoints.size >= 3 && MathUtils.pointInPolygon(point, holePoints)) {
                return false
            }
        }
        return true
    }

    /**
     * Все edgeId полигона (outer + holes).
     */
    fun getAllEdgeIds(polygon: Polygon): Sequence<UUID> = sequence {
        for (ref in polygon.outerLoop.edges) yield(ref.edgeId)
        for (hole in polygon.innerLoops) {
            for (ref in hole.edges) yield(ref.edgeId)
        }
    }

    private fun sumLoopEdgeLengths(document: CadDocument, loop: Loop): Double {
        var length = 0.0
        for (ref in loop.edges) {
            val edge = document.edgeById(ref.edgeId) ?: continue
            length += MathUtils.distance(
                TopologyService.getEdgeStartPoint(document, edge),
                TopologyService.getEdgeEndPoint(document, edge)
            )
        }
        return length
    }
}