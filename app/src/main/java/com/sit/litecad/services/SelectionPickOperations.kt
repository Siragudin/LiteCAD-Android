package com.sit.litecad.services

import com.sit.litecad.geometry.DimensionGeometry
import com.sit.litecad.geometry.PolygonGeometry
import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.math.Geometry2D
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import java.util.UUID

enum class SelectionPickKind {
    DIMENSION,
    EDGE,
    AXIS,
    POLYGON
}

data class SelectionPick(
    val kind: SelectionPickKind,
    val id: UUID
)

object SelectionPickOperations {

    /**
     * Приоритет: Dimension → Edge → Axis → Polygon.
     * Для Edge — ближайшее ребро в пределах tolerance.
     * Для Axis — ближайшая ось в пределах tolerance.
     * Для Polygon — минимальная по площади грань, содержащая точку (чтобы клик в дырку
     * попадал в маленькую грань, а не в большую).
     */
    fun pickAt(
        document: CadDocument,
        world: Point2,
        tolerance: Double
    ): SelectionPick? {
        tryPickDimension(document, world, tolerance)?.let { return it }
        tryPickEdge(document, world, tolerance)?.let { return it }
        tryPickAxis(document, world, tolerance)?.let { return it }
        tryPickPolygon(document, world, tolerance)?.let { return it }
        return null
    }

    /**
     * Попадание по размеру: проверяем все три отрезка (выносная 1, выносная 2,
     * размерная линия). Ближайший размер в пределах tolerance.
     */
    fun tryPickDimension(
        document: CadDocument,
        world: Point2,
        tolerance: Double
    ): SelectionPick? {
        var closestDimensionId: UUID? = null
        var closestDistance = tolerance

        for (dim in document.dimensions) {
            val first = document.vertexById(dim.FirstVertexId) ?: continue
            val second = document.vertexById(dim.SecondVertexId) ?: continue

            val layout = DimensionGeometry.tryCreateLayout(
                first.position, second.position, dim.Offset,
                TopologyTolerance.ForMutation,
                dim.IsOrthogonal, dim.OrthogonalIsHorizontal
            ) ?: continue

            val distExt1 = Geometry2D.tryHitTestSegment(
                world, layout.firstAnchor, layout.firstExtensionEnd, tolerance
            )
            val distExt2 = Geometry2D.tryHitTestSegment(
                world, layout.secondAnchor, layout.secondExtensionEnd, tolerance
            )
            val distDim = Geometry2D.tryHitTestSegment(
                world, layout.dimensionLineStart, layout.dimensionLineEnd, tolerance
            )

            val dist = listOfNotNull(distExt1, distExt2, distDim).minOrNull() ?: continue
            if (dist > closestDistance) continue

            closestDimensionId = dim.id
            closestDistance = dist
        }

        return closestDimensionId?.let { SelectionPick(SelectionPickKind.DIMENSION, it) }
    }

    fun tryPickEdge(document: CadDocument, world: Point2, tolerance: Double): SelectionPick? {
        var closestEdgeId: UUID? = null
        var closestDistance = tolerance

        for (edge in document.edges) {
            val start = TopologyService.getEdgeStartPoint(document, edge)
            val end = TopologyService.getEdgeEndPoint(document, edge)
            val dist = Geometry2D.tryHitTestSegment(world, start, end, tolerance) ?: continue
            if (dist > closestDistance) continue

            closestEdgeId = edge.id
            closestDistance = dist
        }

        return closestEdgeId?.let { SelectionPick(SelectionPickKind.EDGE, it) }
    }

    fun tryPickAxis(document: CadDocument, world: Point2, tolerance: Double): SelectionPick? {
        var closestAxisId: UUID? = null
        var closestDistance = tolerance

        for (axis in document.axes) {
            val dist = Geometry2D.tryHitTestSegment(world, axis.Start, axis.End, tolerance) ?: continue
            if (dist > closestDistance) continue

            closestAxisId = axis.id
            closestDistance = dist
        }

        return closestAxisId?.let { SelectionPick(SelectionPickKind.AXIS, it) }
    }

    fun tryPickPolygon(document: CadDocument, world: Point2, tolerance: Double): SelectionPick? {
        var closestPolygonId: UUID? = null
        var closestArea = Double.MAX_VALUE

        for (polygon in document.polygons) {
            if (polygon.outerLoop.edges.size < 3) continue
            if (!PolygonGeometry.containsPoint(document, polygon, world)) continue

            val area = PolygonGeometry.getArea(document, polygon)
            if (area >= closestArea) continue

            closestPolygonId = polygon.id
            closestArea = area
        }

        return closestPolygonId?.let { SelectionPick(SelectionPickKind.POLYGON, it) }
    }
}