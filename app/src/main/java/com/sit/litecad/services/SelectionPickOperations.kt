package com.sit.litecad.services

import com.sit.litecad.geometry.DimensionAnchorResolver
import com.sit.litecad.geometry.DimensionGeometry
import com.sit.litecad.geometry.PolygonGeometry
import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.math.Geometry2D
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.CadText
import com.sit.litecad.model.TextRotation
import java.util.UUID
import kotlin.math.abs

/** Оценка средней ширины символа относительно размера шрифта (как в TextToolHandler). */
private const val TEXT_CHAR_WIDTH_FACTOR = 0.6

/** Оценка высоты строки относительно размера шрифта (как в TextToolHandler). */
private const val TEXT_LINE_HEIGHT_FACTOR = 1.2

enum class SelectionPickKind {
    DIMENSION,
    EDGE,
    AXIS,
    TEXT,
    POLYGON
}

data class SelectionPick(
    val kind: SelectionPickKind,
    val id: UUID
)

object SelectionPickOperations {

    /**
     * Приоритет: Dimension → Edge → Axis → Text → Polygon.
     * Для Edge — ближайшее ребро в пределах tolerance.
     * Для Axis — ближайшая ось в пределах tolerance.
     * Для Text — ближайшая надпись, в чей экранный прямоугольник попала точка.
     * Для Polygon — минимальная по площади грань, содержащая точку (чтобы клик в дырку
     * попадал в маленькую грань, а не в большую).
     *
     * @param tolerance мировой допуск попадания (обычно pixelsToWorld(24px, zoom))
     * @param zoom масштаб камеры — нужен только для надписей: их экранный размер фиксирован
     * @param textPixelScale множитель sp → px (density × fontScale), см. ToolManager.textPixelScale
     */
    fun pickAt(
        document: CadDocument,
        world: Point2,
        tolerance: Double,
        zoom: Double = 1.0,
        textPixelScale: Float = 1f
    ): SelectionPick? {
        tryPickDimension(document, world, tolerance)?.let { return it }
        tryPickEdge(document, world, tolerance)?.let { return it }
        tryPickAxis(document, world, tolerance)?.let { return it }
        tryPickText(document, world, tolerance, zoom, textPixelScale)?.let { return it }
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
            val firstPosition = DimensionAnchorResolver.resolveAnchorPosition(
                document, dim.FirstAnchorType, dim.FirstVertexId, dim.FirstAnchorPosition
            )
            val secondPosition = DimensionAnchorResolver.resolveAnchorPosition(
                document, dim.SecondAnchorType, dim.SecondVertexId, dim.SecondAnchorPosition
            )

            val layout = DimensionGeometry.tryCreateLayout(
                firstPosition, secondPosition, dim.Offset,
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

    /**
     * Попадание по надписи: ближайшая к точке надпись, в чей прямоугольник она попала.
     *
     * Прямоугольник задан в экранных пикселях (размер надписи фиксирован в sp и не
     * зависит от зума), поэтому в мире его размер = пиксели / [zoom].
     */
    fun tryPickText(
        document: CadDocument,
        world: Point2,
        tolerance: Double,
        zoom: Double,
        textPixelScale: Float = 1f
    ): SelectionPick? {
        var closestTextId: UUID? = null
        var closestDistance = Double.MAX_VALUE

        for (text in document.texts) {
            val (halfWidth, halfHeight) = textWorldHalfExtents(text, zoom, textPixelScale)

            val dx = abs(world.X - text.position.X)
            val dy = abs(world.Y - text.position.Y)
            if (dx > halfWidth + tolerance || dy > halfHeight + tolerance) continue

            val distance = dx * dx + dy * dy
            if (distance >= closestDistance) continue

            closestTextId = text.id
            closestDistance = distance
        }

        return closestTextId?.let { SelectionPick(SelectionPickKind.TEXT, it) }
    }

    /**
     * Полуразмеры экранного прямоугольника надписи в МИРОВЫХ единицах (мм).
     *
     * Размер шрифта фиксирован в sp → на экране это [pixelScale] × sp пикселей,
     * в мире — те же пиксели, делённые на [zoom]. Для [TextRotation.DEG_90]
     * рамка повёрнута на 90°, т.е. полуоси меняются местами (в мире рамка
     * остаётся параллельной осям, т.к. экранный Y инвертирован).
     */
    fun textWorldHalfExtents(
        text: CadText,
        zoom: Double,
        pixelScale: Float
    ): Pair<Double, Double> {
        val safeZoom = if (zoom > 1e-9) zoom else 1.0
        val fontPx = text.sizeSp.sp * pixelScale
        val halfWidth = text.text.length * fontPx * TEXT_CHAR_WIDTH_FACTOR * 0.5 / safeZoom
        val halfHeight = fontPx * TEXT_LINE_HEIGHT_FACTOR * 0.5 / safeZoom
        return if (text.rotation == TextRotation.DEG_90) {
            halfHeight to halfWidth
        } else {
            halfWidth to halfHeight
        }
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