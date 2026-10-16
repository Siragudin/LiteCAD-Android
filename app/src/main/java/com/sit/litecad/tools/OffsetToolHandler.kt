package com.sit.litecad.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.sit.litecad.geometry.EdgeOperations
import com.sit.litecad.geometry.PolygonBuilder
import com.sit.litecad.geometry.PolygonGeometry
import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.math.Geometry2D
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.CadEdge
import com.sit.litecad.model.Loop
import com.sit.litecad.model.Polygon
import com.sit.litecad.model.PolygonType
import com.sit.litecad.services.DocumentHistoryService
import com.sit.litecad.services.SelectionPickTolerance
import com.sit.litecad.settings.ToolSettings
import com.sit.litecad.ui.canvas.Camera
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot

/** Пиксельный допуск попадания по границе грани при выборе. */
private const val OFFSET_PICK_PX = 24.0

/** Визуальный радиус ручки-кружка в середине ребра (в пикселях). */
private const val HANDLE_DRAW_RADIUS_PX = 9f

/** Радиус зоны попадания пальцем по ручке (в пикселях). Крупнее
 *  визуального кружка — чтобы пальцем было легко попасть. */
private const val HANDLE_PICK_RADIUS_PX = 24f

/** Минимальная допустимая площадь контура после смещения (как в PolygonBuilder). */
private const val MIN_FACE_AREA = 1e-10

/**
 * Инструмент «Эквидистанта» (истинный Offset). Смещает ВНЕШНИЙ контур грани
 * ([PolygonType.FACE]). Внутренние контуры (innerLoops) не участвуют.
 *
 * Две операции:
 *  1) тап по грани — выбирается грань: подсвечиваются рёбра её внешнего контура,
 *     в серединах этих рёбер появляются ручки-кружки;
 *  2) протяжка ОТ РУЧКИ (середины ребра) — задаёт направление и величину смещения
 *     (наружу/внутрь по знаку проекции протяжки на внешнюю нормаль схваченного ребра).
 *
 * На отпускании пальца строится НОВЫЙ внешний контур: каждое ребро смещается
 * параллельно себе, вершины нового контура ставятся в точки пересечения соседних
 * смещённых прямых (миттеры на углах). Исходная грань и её рёбра не изменяются.
 *
 * Точный ввод (кнопка «Точный»): на onEnd коммит откладывается, запоминается знак
 * направления и грань, и запрашивается ввод величины через [PendingInput.OffsetDistance].
 */
class OffsetToolHandler(
    private val historyService: DocumentHistoryService,
    private val toolManager: ToolManager
) : Tool {

    /** Выбранная грань (по id, чтобы пережить пересборки lookup-индексов документа). */
    private var selectedFaceId: UUID? by mutableStateOf(null)

    /** Рёбра ВНЕШНЕГО контура выбранной грани (только outerLoop). */
    private var selectedOuterEdgeIds: Set<UUID> = emptySet()

    /** Схваченное ребро (нужно для определения внешней нормали). */
    private var grabbedEdgeId: UUID? = null

    /** Внешняя нормаль схваченного ребра (единичная, в мировых единицах). */
    private var axisNormal: Point2? = null

    /** Начало протяжки. */
    private var dragStart: Point2? by mutableStateOf(null)

    /** Текущая точка протяжки. */
    private var dragCurrent: Point2? by mutableStateOf(null)

    // Черновик отложенного ввода расстояния (пока открыта панель «Точный»).
    private var draftSign = 1
    private var draftFaceId: UUID? = null

    override var currentMeasurement by mutableStateOf("")
        private set

    override fun onStart(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        val face = resolveSelectedFace(document)
        if (face == null) {
            // Операция 1 — выбор грани под точкой.
            val picked = pickFaceAt(document, point, zoom) ?: return
            selectedFaceId = picked.id
            selectedOuterEdgeIds = picked.outerLoop.edges.map { it.edgeId }.toSet()
        } else {
            // Операция 2 — касание ручки (середины ребра внешнего контура) и начало протяжки.
            val grabbed = pickHandleEdge(document, point, zoom) ?: return
            val normal = outwardNormal(document, face, grabbed) ?: return

            grabbedEdgeId = grabbed.id
            axisNormal = normal
            dragStart = point
            dragCurrent = point
        }
    }

    override fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        if (dragStart == null) return
        dragCurrent = point
        val normal = axisNormal ?: return
        currentMeasurement = toolManager.settings.measurementOffset.format(abs(dragDelta(normal)).toInt())
    }

    override fun onEnd(document: CadDocument) {
        if (dragStart == null || grabbedEdgeId == null) return
        val current = dragCurrent
        val normal = axisNormal
        if (current == null || normal == null) {
            clearDrag()
            return
        }

        val distance = dragDelta(normal)
        if (abs(distance) <= TopologyTolerance.ForMutation) {
            // Протяжки не было — остаёмся на выбранной грани.
            clearDrag()
            return
        }

        val face = resolveSelectedFace(document) ?: run {
            reset()
            return
        }

        if (toolManager.settings.offset.exactInputEnabled) {
            // Откладываем коммит — открываем панель точного ввода величины.
            draftSign = if (distance < 0.0) -1 else 1
            draftFaceId = face.id
            clearDrag()
            toolManager.requestInput(PendingInput.OffsetDistance(abs(distance)))
        } else {
            commitOffset(document, face, distance)
            reset()
        }
    }

    override fun onOffsetInputSubmitted(value: Double, document: CadDocument) {
        if (value <= 0.0) {
            // Нулевая величина — откат.
            clearDraft()
            reset()
            return
        }

        val face = draftFaceId
            ?.let { id -> document.polygons.firstOrNull { it.id == id && it.type == PolygonType.FACE } }
            ?: resolveSelectedFace(document)
        if (face == null) {
            clearDraft()
            reset()
            return
        }

        // Величину берём из ввода, знак — от направления протяжки.
        commitOffset(document, face, draftSign * value)
        clearDraft()
        reset()
    }

    override fun onOffsetInputCancelled(document: CadDocument) {
        // Откат — ничего не коммитим.
        clearDraft()
        reset()
    }

    override fun drawPreview(scope: DrawScope, camera: Camera) {
        val document = toolManager.document
        if (resolveSelectedFace(document) == null) return

        // --- Подсветка рёбер внешнего контура выбранной грани ---
        val highlightColor = Color(0xFF1976D2)
        for (edgeId in selectedOuterEdgeIds) {
            val edge = document.edgeById(edgeId) ?: continue
            scope.drawLine(
                color = highlightColor,
                start = camera.worldToScreen(TopologyService.getEdgeStartPoint(document, edge)),
                end = camera.worldToScreen(TopologyService.getEdgeEndPoint(document, edge)),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }

        // --- Ручки-кружки в серединах рёбер внешнего контура ---
        for (edgeId in selectedOuterEdgeIds) {
            val edge = document.edgeById(edgeId) ?: continue
            val screen = camera.worldToScreen(midpointOf(document, edge))
            scope.drawCircle(Color(0xFF1565C0), HANDLE_DRAW_RADIUS_PX, screen)
            scope.drawCircle(Color.White, HANDLE_DRAW_RADIUS_PX - 3f, screen)
        }

        // --- Превью нового внешнего контура во время протяжки (серый пунктир) ---
        val face = resolveSelectedFace(document) ?: return
        val start = dragStart
        val current = dragCurrent
        val normal = axisNormal
        if (start != null && current != null && normal != null) {
            val distance = dragDelta(normal)
            if (abs(distance) > TopologyTolerance.ForMutation) {
                val segments = computeNewSegments(document, face, distance)
                if (segments != null) {
                    val effect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                    for ((_, newStart, newEnd) in segments) {
                        scope.drawLine(
                            color = toolManager.previewStrokeColor(Color.Gray),
                            start = camera.worldToScreen(newStart),
                            end = camera.worldToScreen(newEnd),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round,
                            pathEffect = effect
                        )
                    }
                }
            }
        }
    }

    override fun cancel() {
        clearDraft()
        reset()
    }

    // --- Выбор грани ---

    private fun resolveSelectedFace(document: CadDocument): Polygon? {
        val id = selectedFaceId ?: return null
        return document.polygons.firstOrNull { it.id == id && it.type == PolygonType.FACE }
    }

    /** Грань под точкой: первая FACE, содержащая точку (outer за вычетом дырок). */
    private fun pickFaceAt(document: CadDocument, point: Point2, zoom: Double): Polygon? {
        for (polygon in document.polygons) {
            if (polygon.type != PolygonType.FACE) continue
            if (polygon.outerLoop.edges.size < 3) continue
            if (PolygonGeometry.containsPoint(document, polygon, point)) return polygon
        }

        // Допуск по границе внешнего контура (тап чуть мимо ребра).
        val tolerance = SelectionPickTolerance.pixelsToWorld(OFFSET_PICK_PX, zoom)
        for (polygon in document.polygons) {
            if (polygon.type != PolygonType.FACE) continue
            val outer = PolygonGeometry.getOuterBoundaryPoints(document, polygon)
            if (outer.size < 3) continue
            if (isNearLoop(point, outer, tolerance)) return polygon
        }
        return null
    }

    /**
     * Ближайшая ручка (середина ребра внешнего контура) в пределах радиуса ручки.
     * Палец должен попасть именно в ручку — иначе протяжка не начинается.
     */
    private fun pickHandleEdge(document: CadDocument, point: Point2, zoom: Double): CadEdge? {
        val radius = HANDLE_PICK_RADIUS_PX.toDouble() / zoom
        var best: CadEdge? = null
        var bestDistance = radius
        for (edgeId in selectedOuterEdgeIds) {
            val edge = document.edgeById(edgeId) ?: continue
            val distance = MathUtils.distance(point, midpointOf(document, edge))
            if (distance <= bestDistance) {
                best = edge
                bestDistance = distance
            }
        }
        return best
    }

    private fun isNearLoop(point: Point2, loop: List<Point2>, tolerance: Double): Boolean {
        for (i in loop.indices) {
            val a = loop[i]
            val b = loop[(i + 1) % loop.size]
            if (Geometry2D.tryHitTestSegment(point, a, b, tolerance) != null) return true
        }
        return false
    }

    // --- Направление ---

    /**
     * Единичная внешняя нормаль ребра (ориентация контура не важна — определяется пробой):
     * берём перпендикуляр к ребру, сдвигаем середину ребра на ε вдоль него; если проба
     * оказалась внутри грани — нормаль смотрела внутрь, инвертируем.
     */
    private fun outwardNormal(document: CadDocument, face: Polygon, edge: CadEdge): Point2? {
        val start = TopologyService.getEdgeStartPoint(document, edge)
        val end = TopologyService.getEdgeEndPoint(document, edge)
        val dx = end.X - start.X
        val dy = end.Y - start.Y
        val length = hypot(dx, dy)
        if (length <= TopologyTolerance.ForMutation) return null

        val nx = dy / length
        val ny = -dx / length

        val mid = Point2((start.X + end.X) / 2.0, (start.Y + end.Y) / 2.0)
        val eps = (length * 1e-3).coerceAtLeast(TopologyTolerance.ForMutation)
        val probe = Point2(mid.X + nx * eps, mid.Y + ny * eps)
        val inside = PolygonGeometry.containsPoint(document, face, probe)

        return if (inside) Point2(-nx, -ny) else Point2(nx, ny)
    }

    /** Проекция вектора протяжки на внешнюю нормаль (со знаком). */
    private fun dragDelta(normal: Point2): Double {
        val s = dragStart ?: return 0.0
        val c = dragCurrent ?: return 0.0
        return (c.X - s.X) * normal.X + (c.Y - s.Y) * normal.Y
    }

    // --- Коммит ---

    /** Построить новый смещённый внешний контур для [face] на знаковую величину [distance]. */
    private fun commitOffset(document: CadDocument, face: Polygon, distance: Double) {
        if (abs(distance) <= TopologyTolerance.ForMutation) return

        val segments = computeNewSegments(document, face, distance) ?: return
        if (segments.isEmpty()) return

        historyService.record(document)
        for ((template, newStart, newEnd) in segments) {
            if (MathUtils.distance(newStart, newEnd) <= TopologyTolerance.ForMutation) continue
            EdgeOperations.addSegmentInternal(
                document, newStart, newEnd, template, TopologyTolerance.ForMutation
            )
        }
        PolygonBuilder.syncFaces(document)
        document.bumpRevision()
    }

    // --- Геометрия нового контура ---

    /**
     * Новые рёбра (шаблон, newStart, newEnd) для ВНЕШНЕГО контура грани, или null если
     * геометрия вырождена/неподдерживаема. innerLoops не участвуют.
     */
    private fun computeNewSegments(
        document: CadDocument,
        face: Polygon,
        distance: Double
    ): List<Triple<CadEdge, Point2, Point2>>? {
        val outer = buildOffsetLoop(document, face, face.outerLoop, distance) ?: return null

        // Проверка вырождения внешнего контура: знак площади должен сохраниться.
        val originalArea = PolygonGeometry.getSignedArea(document, face.outerLoop)
        val newArea = MathUtils.signedPolygonArea(outer.map { it.second })
        if (abs(newArea) <= MIN_FACE_AREA) return null
        if (originalArea * newArea <= 0.0) return null

        return outer
    }

    /**
     * Смещение одного замкнутого контура: каждое ребро сдвигается параллельно себе на
     * [distance] вдоль своей внешней нормали, вершины нового контура — пересечения
     * соседних смещённых прямых. null — если контур вырожден или рёбра параллельны.
     */
    private fun buildOffsetLoop(
        document: CadDocument,
        face: Polygon,
        loop: Loop,
        distance: Double
    ): List<Triple<CadEdge, Point2, Point2>>? {
        val references = loop.edges
        val count = references.size
        if (count < 3) return null

        val edges = ArrayList<CadEdge>(count)
        val startPoints = ArrayList<Point2>(count)
        val directions = ArrayList<Point2>(count)

        for (reference in references) {
            val edge = document.edgeById(reference.edgeId) ?: return null
            val start: Point2
            val end: Point2
            if (reference.forward) {
                start = TopologyService.getEdgeStartPoint(document, edge)
                end = TopologyService.getEdgeEndPoint(document, edge)
            } else {
                start = TopologyService.getEdgeEndPoint(document, edge)
                end = TopologyService.getEdgeStartPoint(document, edge)
            }
            if (MathUtils.distance(start, end) <= TopologyTolerance.ForMutation) return null
            edges.add(edge)
            startPoints.add(start)
            directions.add(Point2(end.X - start.X, end.Y - start.Y))
        }

        // Сдвинутые прямые: точка на прямой + направление.
        val linePoints = ArrayList<Point2>(count)
        for (i in 0 until count) {
            val normal = outwardNormal(document, face, edges[i]) ?: return null
            linePoints.add(
                Point2(
                    startPoints[i].X + normal.X * distance,
                    startPoints[i].Y + normal.Y * distance
                )
            )
        }

        // Вершина i нового контура — пересечение прямых (i-1) и i.
        val newVertices = ArrayList<Point2>(count)
        for (i in 0 until count) {
            val previous = (i - 1 + count) % count
            val intersection = lineIntersection(
                linePoints[previous], directions[previous],
                linePoints[i], directions[i]
            ) ?: return null
            newVertices.add(intersection)
        }

        val result = ArrayList<Triple<CadEdge, Point2, Point2>>(count)
        for (i in 0 until count) {
            result.add(
                Triple(
                    edges[i].cloneGeometry(),
                    newVertices[i],
                    newVertices[(i + 1) % count]
                )
            )
        }
        return result
    }

    /** Пересечение двух прямых (по точке и направлению). null — если параллельны/вырождены. */
    private fun lineIntersection(
        point1: Point2, direction1: Point2,
        point2: Point2, direction2: Point2
    ): Point2? {
        val length1 = hypot(direction1.X, direction1.Y)
        val length2 = hypot(direction2.X, direction2.Y)
        if (length1 <= TopologyTolerance.ForMutation || length2 <= TopologyTolerance.ForMutation) {
            return null
        }
        if (Geometry2D.areDirectionsParallel(
                direction1.X, direction1.Y, length1,
                direction2.X, direction2.Y, length2,
                TopologyTolerance.ForMutation
            )
        ) {
            return null
        }

        val denominator = direction1.X * direction2.Y - direction1.Y * direction2.X
        if (abs(denominator) <= 1e-12) return null
        val t = ((point2.X - point1.X) * direction2.Y - (point2.Y - point1.Y) * direction2.X) / denominator
        return Point2(point1.X + t * direction1.X, point1.Y + t * direction1.Y)
    }

    private fun midpointOf(document: CadDocument, edge: CadEdge): Point2 {
        val start = TopologyService.getEdgeStartPoint(document, edge)
        val end = TopologyService.getEdgeEndPoint(document, edge)
        return Point2((start.X + end.X) / 2.0, (start.Y + end.Y) / 2.0)
    }

    // --- Сброс ---

    /** Сброс протяжки, но сохранение выбранной грани. */
    private fun clearDrag() {
        grabbedEdgeId = null
        axisNormal = null
        dragStart = null
        dragCurrent = null
        currentMeasurement = ""
    }

    private fun clearDraft() {
        draftSign = 1
        draftFaceId = null
    }

    private fun reset() {
        selectedFaceId = null
        selectedOuterEdgeIds = emptySet()
        clearDrag()
        clearDraft()
    }
}
