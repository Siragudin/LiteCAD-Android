package com.sit.litecad.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.sit.litecad.geometry.EdgeOperations
import com.sit.litecad.geometry.PolygonBuilder
import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.math.Geometry2D
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.CadEdge
import com.sit.litecad.services.DocumentHistoryService
import com.sit.litecad.services.SelectionPickOperations
import com.sit.litecad.services.SelectionPickTolerance
import com.sit.litecad.settings.ToolSettings
import com.sit.litecad.ui.canvas.Camera
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

/** Пиксельный допуск попадания по ребру/маркеру при выборе. */
private const val EXTEND_PICK_PX = 24.0

/** Длительность красной вспышки по невалидному ребру (мс). */
private const val FLASH_DURATION_MS = 500L

/** Радиус маркера-кружка на валидном конце ребра (в пикселях). */
private const val MARKER_RADIUS_PX = 9f

/**
 * Инструмент «Удлинить». Операция над выделенным ребром: ребро продлевается вдоль
 * собственного направления до ближайшего пересечения с другим ребром. Если с какой-то
 * стороны пересечения нет — эта сторона невалидна.
 *
 * Жест — две операции:
 *  - операция 1 (тап) — выбор ребра (маркеры на валидных концах);
 *  - операция 2 (тап по маркеру) — удлинение в сторону этого конца.
 */
class ExtendToolHandler(
    private val historyService: DocumentHistoryService,
    private val toolManager: ToolManager
) : Tool {

    /** Выделенное ребро. */
    private var selectedEdgeId: UUID? by mutableStateOf(null)

    /** Валидный маркер начала (или null). */
    private var startMarker: Point2? by mutableStateOf(null)

    /** Валидный маркер конца (или null). */
    private var endMarker: Point2? by mutableStateOf(null)

    /** Точка пересечения при удлинении start. */
    private var startIntersection: Point2? = null

    /** Точка пересечения при удлинении end. */
    private var endIntersection: Point2? = null

    /** Ребро для красной вспышки. */
    private var flashEdgeId: UUID? by mutableStateOf(null)

    /** Счётчик запусков вспышки (для корутинного сброса). */
    private var flashRunId = 0

    /** Скоуп для гашения вспышки через заданную задержку. */
    private val flashScope = CoroutineScope(Dispatchers.Main)

    private var lastZoom = 1.0

    override var currentMeasurement by mutableStateOf("")
        private set

    override fun onStart(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastZoom = zoom
        val tolerance = SelectionPickTolerance.pixelsToWorld(EXTEND_PICK_PX, zoom)

        val currentId = selectedEdgeId
        if (currentId == null) {
            // Операция 1 — выбор ребра.
            selectEdgeAt(document, point, tolerance)
            return
        }

        // Операция 2 — тап при уже выделенном ребре.
        val startMarkerPoint = startMarker
        if (startMarkerPoint != null && MathUtils.distance(point, startMarkerPoint) <= tolerance) {
            commitExtend(document, isStart = true)
            return
        }

        val endMarkerPoint = endMarker
        if (endMarkerPoint != null && MathUtils.distance(point, endMarkerPoint) <= tolerance) {
            commitExtend(document, isStart = false)
            return
        }

        // Повторный тап по телу выделенного ребра — ничего не делаем.
        val currentEdge = document.edgeById(currentId)
        if (currentEdge != null) {
            val s = TopologyService.getEdgeStartPoint(document, currentEdge)
            val e = TopologyService.getEdgeEndPoint(document, currentEdge)
            if (Geometry2D.tryHitTestSegment(point, s, e, tolerance) != null) return
        }

        // Тап по другому ребру / пустому месту — снять выделение и попробовать выбрать новое.
        reset()
        selectEdgeAt(document, point, tolerance)
    }

    override fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        // Инструмент работает по тапам — движения не обрабатываем.
        lastZoom = zoom
    }

    override fun onEnd(document: CadDocument) {
        // Инструмент работает по тапам — отпускание не обрабатываем.
    }

    override fun cancel() {
        flashRunId++
        flashEdgeId = null
        reset()
    }

    override fun drawPreview(scope: DrawScope, camera: Camera) {
        val document = toolManager.document

        // Красная вспышка по невалидному ребру.
        val flashId = flashEdgeId
        if (flashId != null) {
            val edge = document.edgeById(flashId)
            if (edge != null) {
                val s = TopologyService.getEdgeStartPoint(document, edge)
                val e = TopologyService.getEdgeEndPoint(document, edge)
                scope.drawLine(
                    color = Color.Red,
                    start = camera.worldToScreen(s),
                    end = camera.worldToScreen(e),
                    strokeWidth = (edge.thickness * 2).toFloat(),
                    cap = StrokeCap.Round
                )
            }
        }

        if (selectedEdgeId == null) return

        // Маркеры только на валидных концах.
        startMarker?.let { drawMarker(scope, camera.worldToScreen(it)) }
        endMarker?.let { drawMarker(scope, camera.worldToScreen(it)) }
    }

    // --- Выбор ребра ---

    /** Операция 1: выбрать ребро под пальцем и выставить маркеры валидных сторон. */
    private fun selectEdgeAt(document: CadDocument, point: Point2, tolerance: Double) {
        val pick = SelectionPickOperations.tryPickEdge(document, point, tolerance)
        if (pick == null) {
            toolManager.selection.clear()
            return
        }

        val edge = document.edgeById(pick.id) ?: return

        val startValidation = validateSide(document, edge, isStart = true)
        val endValidation = validateSide(document, edge, isStart = false)

        if (startValidation == null && endValidation == null) {
            // Невалидно с обеих сторон — вспышка, ребро не выделяем.
            triggerFlash(edge.id)
            return
        }

        selectedEdgeId = edge.id
        startMarker = if (startValidation != null) TopologyService.getEdgeStartPoint(document, edge) else null
        endMarker = if (endValidation != null) TopologyService.getEdgeEndPoint(document, edge) else null
        startIntersection = startValidation?.intersection
        endIntersection = endValidation?.intersection
    }

    // --- Валидность стороны ---

    /**
     * Проверка валидности стороны ребра для удлинения:
     *  - если за концом есть коллинеарное ребро (общая вершина) → сторона невалидна;
     *  - иначе ищем ближайшее пересечение полупрямой продления с другими рёбрами,
     *    исключая попадание в общие вершины S/E. Нет пересечений → невалидна.
     */
    private fun validateSide(document: CadDocument, edge: CadEdge, isStart: Boolean): SideValidation? {
        val sPt = TopologyService.getEdgeStartPoint(document, edge)
        val ePt = TopologyService.getEdgeEndPoint(document, edge)

        val outward = if (isStart) sPt else ePt
        val inward = if (isStart) ePt else sPt
        val outwardVertexId = if (isStart) edge.startVertexId else edge.endVertexId

        val dx = outward.X - inward.X
        val dy = outward.Y - inward.Y
        val length = MathUtils.distance(outward, inward)
        if (length <= TopologyTolerance.ForMutation) return null
        val dirX = dx / length
        val dirY = dy / length

        // Коллинеарное ребро сразу за концом — удлинять некуда.
        for (other in document.edges) {
            if (other.id == edge.id) continue
            val sharesOutward = other.startVertexId == outwardVertexId || other.endVertexId == outwardVertexId
            if (!sharesOutward) continue
            val oS = TopologyService.getEdgeStartPoint(document, other)
            val oE = TopologyService.getEdgeEndPoint(document, other)
            if (Geometry2D.areSegmentsCollinear(sPt, ePt, oS, oE, TopologyTolerance.ForMutation)) {
                return null
            }
        }

        // Ближайшее пересечение полупрямой продления с другими рёбрами.
        var bestT = Double.MAX_VALUE
        var bestPoint: Point2? = null
        var bestEdge: CadEdge? = null

        for (other in document.edges) {
            if (other.id == edge.id) continue
            val oS = TopologyService.getEdgeStartPoint(document, other)
            val oE = TopologyService.getEdgeEndPoint(document, other)

            val ex = oE.X - oS.X
            val ey = oE.Y - oS.Y
            val denom = dirX * ey - dirY * ex
            if (abs(denom) <= TopologyTolerance.ForMutation) continue

            val diffX = oS.X - outward.X
            val diffY = oS.Y - outward.Y
            val t = (diffX * ey - diffY * ex) / denom
            val u = (diffX * dirY - diffY * dirX) / denom

            // Пересечение строго за концом и внутри отрезка.
            if (t <= TopologyTolerance.ForMutation) continue
            if (u < -TopologyTolerance.ForMutation || u > 1.0 + TopologyTolerance.ForMutation) continue

            val px = outward.X + t * dirX
            val py = outward.Y + t * dirY

            // Не считаем «уже соединённое» ребро пересечением: точка в вершине S или E.
            if (MathUtils.arePointsEqual(Point2(px, py), sPt, TopologyTolerance.ForMutation)) continue
            if (MathUtils.arePointsEqual(Point2(px, py), ePt, TopologyTolerance.ForMutation)) continue

            if (t < bestT) {
                bestT = t
                bestPoint = Point2(px, py)
                bestEdge = other
            }
        }

        val intersection = bestPoint ?: return null
        val otherEdge = bestEdge ?: return null
        return SideValidation(intersection, otherEdge)
    }

    // --- Коммит ---

    /** Удлинить выделенное ребро в сторону [isStart] до сохранённого пересечения. */
    private fun commitExtend(document: CadDocument, isStart: Boolean) {
        val edgeId = selectedEdgeId ?: run {
            reset()
            return
        }
        val edge = document.edgeById(edgeId) ?: run {
            reset()
            return
        }
        val intersection = (if (isStart) startIntersection else endIntersection) ?: run {
            reset()
            return
        }

        val s = TopologyService.getEdgeStartPoint(document, edge)
        val e = TopologyService.getEdgeEndPoint(document, edge)
        val template = edge.cloneGeometry()

        historyService.record(document)

        document.removeEdge(edge)
        TopologyService.pruneUnusedVertices(document)

        if (isStart) {
            EdgeOperations.addSegmentInternal(document, intersection, e, template, TopologyTolerance.ForMutation)
        } else {
            EdgeOperations.addSegmentInternal(document, s, intersection, template, TopologyTolerance.ForMutation)
        }

        PolygonBuilder.syncFaces(document)
        document.bumpRevision()

        toolManager.selection.clear()
        reset()
    }

    // --- Вспышка ---

    /** Запустить красную вспышку по ребру и погасить её через [FLASH_DURATION_MS]. */
    private fun triggerFlash(edgeId: UUID) {
        flashEdgeId = edgeId
        val runId = ++flashRunId
        flashScope.launch {
            delay(FLASH_DURATION_MS)
            if (runId == flashRunId) {
                flashEdgeId = null
            }
        }
    }

    // --- Превью ---

    private fun drawMarker(scope: DrawScope, center: Offset) {
        scope.drawCircle(Color(0xFF1565C0), MARKER_RADIUS_PX, center)
        scope.drawCircle(Color.White, MARKER_RADIUS_PX - 3f, center)
    }

    // --- Сброс ---

    private fun reset() {
        selectedEdgeId = null
        startMarker = null
        endMarker = null
        startIntersection = null
        endIntersection = null
        currentMeasurement = ""
        // flashEdgeId не трогаем — вспышку гасит отдельный запуск.
    }

    /** Результат проверки стороны: точка пересечения и ребро, с которым пересеклись. */
    private data class SideValidation(
        val intersection: Point2,
        val otherEdge: CadEdge
    )
}
