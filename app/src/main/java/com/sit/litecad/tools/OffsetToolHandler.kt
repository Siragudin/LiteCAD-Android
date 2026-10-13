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

/** Пиксельный допуск попадания по ребру при выборе. */
private const val OFFSET_PICK_PX = 24.0

/** Длительность красной вспышки по невалидному ребру (мс). */
private const val FLASH_DURATION_MS = 500L

/** Радиус ручки-кружка в середине выделенного ребра (в пикселях). */
private const val HANDLE_RADIUS_PX = 9f

/**
 * Инструмент «Эквидистанта». Операция над выделенным ребром: ребро двигается только
 * по оси, заданной соседними стыкованными рёбрами; соседи тянутся/сокращаются вместе
 * с ним. Грани пересобираются через [PolygonBuilder.syncFaces].
 *
 * Жест — две операции:
 *  - операция 1 (тап) — выбор ребра (с проверкой валидности);
 *  - операция 2 (тап + протяжка) — смещение выбранного ребра вдоль оси движения.
 */
class OffsetToolHandler(
    private val historyService: DocumentHistoryService,
    private val toolManager: ToolManager
) : Tool {

    /** Выбранное ребро. */
    private var selectedEdgeId: UUID? by mutableStateOf(null)

    /** Затронутые рёбра: выделенное + соседи (для коммита). */
    private var selectedEdgeIdsForOffset: Set<UUID> = emptySet()

    /** Единичный вектор оси движения. */
    private var axisDir: Point2? = null

    /** Точка-ручка в середине выделенного ребра. */
    private var handleMid: Point2? = null

    /** Где палец коснулся в операции 2. */
    private var dragStart: Point2? by mutableStateOf(null)

    /** Где палец сейчас. */
    private var dragCurrent: Point2? by mutableStateOf(null)

    /** Ребро для красной вспышки. */
    private var flashEdgeId: UUID? by mutableStateOf(null)

    /** Счётчик запусков вспышки (для корутинного сброса). */
    private var flashRunId = 0

    /** Скоуп для гашения вспышки через заданную задержку. */
    private val flashScope = CoroutineScope(Dispatchers.Main)

    private var lastZoom = 1.0

    // Черновик отложенного смещения (пока открыт ввод расстояния).
    private var draftSign = 1
    private var draftEdgeId: UUID? = null
    private var draftEdgeIds: Set<UUID> = emptySet()
    private var draftAxisDir: Point2? = null

    override var currentMeasurement by mutableStateOf("")
        private set

    override fun onStart(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastZoom = zoom

        if (selectedEdgeId == null) {
            // Операция 1 — выбор ребра.
            val tolerance = SelectionPickTolerance.pixelsToWorld(OFFSET_PICK_PX, zoom)
            val pick = SelectionPickOperations.tryPickEdge(document, point, tolerance) ?: return

            val edge = document.edgeById(pick.id) ?: return
            val validation = validateEdge(document, edge)
            if (!validation.valid) {
                // Невалидное ребро — красная вспышка, ребро не выбираем.
                triggerFlash(edge.id)
                return
            }

            selectedEdgeId = edge.id
            selectedEdgeIdsForOffset = (listOf(edge.id) + validation.neighbors.map { it.id }).toSet()
            axisDir = validation.axis
            handleMid = midpointOf(document, edge)
        } else {
            // Операция 2 — начало протяжки.
            dragStart = point
            dragCurrent = point
        }
    }

    override fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        if (dragStart == null) return

        dragCurrent = point
        lastZoom = zoom

        val axis = axisDir ?: return
        val delta = deltaFromDrag(axis)
        currentMeasurement = "Смещение: ${abs(delta).toInt()}"
    }

    override fun onEnd(document: CadDocument) {
        if (dragStart == null) return

        val axis = axisDir ?: run {
            reset()
            return
        }
        val delta = deltaFromDrag(axis)

        if (abs(delta) <= TopologyTolerance.ForMutation) {
            // Смещения не было — откат.
            reset()
            return
        }

        if (toolManager.settings.offset.exactInputEnabled) {
            // Откладываем коммит — открываем панель точного ввода расстояния.
            draftSign = if (delta < 0.0) -1 else 1
            draftEdgeId = selectedEdgeId
            draftEdgeIds = selectedEdgeIdsForOffset
            draftAxisDir = axisDir
            toolManager.requestInput(PendingInput.OffsetDistance(abs(delta)))
        } else {
            commitOffset(document, delta)
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

        // Величину берём из ввода, знак — от направления протяжки.
        val finalDelta = draftSign * value
        commitOffset(document, finalDelta)
        clearDraft()
        reset()
    }

    override fun onOffsetInputCancelled(document: CadDocument) {
        // Откат — ничего не коммитим.
        clearDraft()
        reset()
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

        val edgeId = selectedEdgeId ?: return
        val edge = document.edgeById(edgeId) ?: return

        // Превью новых позиций рёбер во время протяжки — серый пунктир.
        val start = dragStart
        val axis = axisDir
        val current = dragCurrent
        if (start != null && current != null && axis != null) {
            val delta = deltaFromDrag(axis)
            if (abs(delta) > TopologyTolerance.ForMutation) {
                val neighbors = findAdjacentEdges(document, edge)
                val effect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                for ((_, newStart, newEnd) in newPositions(document, edge, neighbors, axis, delta)) {
                    scope.drawLine(
                        color = Color.Gray,
                        start = camera.worldToScreen(newStart),
                        end = camera.worldToScreen(newEnd),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round,
                        pathEffect = effect
                    )
                }
            }
        }

        // Ручка-кружок в середине выделенного ребра.
        val mid = handleMid
        if (mid != null) {
            val screen = camera.worldToScreen(mid)
            scope.drawCircle(Color(0xFF1565C0), HANDLE_RADIUS_PX, screen)
            scope.drawCircle(Color.White, HANDLE_RADIUS_PX - 3f, screen)
        }
    }

    // --- Коммит ---

    /**
     * Смещение выделенного ребра на [delta] вдоль оси движения. Соседи тянутся/сокращаются
     * вместе с ним. В режиме отложенного ввода работаем со снимком (draft*).
     */
    private fun commitOffset(document: CadDocument, delta: Double) {
        if (abs(delta) <= TopologyTolerance.ForMutation) return

        val edgeId = draftEdgeId ?: selectedEdgeId ?: return
        val axis = draftAxisDir ?: axisDir ?: return
        val affectedIds = if (draftEdgeIds.isNotEmpty()) draftEdgeIds else selectedEdgeIdsForOffset

        val edge = document.edgeById(edgeId) ?: return
        val edgesToMove = affectedIds.mapNotNull { document.edgeById(it) }
        if (edgesToMove.isEmpty()) return
        val neighbors = edgesToMove.filter { it.id != edgeId }

        val positions = newPositions(document, edge, neighbors, axis, delta)
        if (positions.isEmpty()) return

        historyService.record(document)

        // Удаляем оригиналы и подчищаем осиротевшие вершины.
        for (e in edgesToMove) {
            document.removeEdge(e)
        }
        TopologyService.pruneUnusedVertices(document)

        for ((template, newStart, newEnd) in positions) {
            EdgeOperations.addSegmentInternal(
                document, newStart, newEnd, template, TopologyTolerance.ForMutation
            )
        }

        PolygonBuilder.syncFaces(document)
        document.bumpRevision()

        // Снимаем выделение: геометрия изменилась.
        toolManager.selection.clear()
    }

    // --- Геометрия ---

    /** Новые позиции рёбер (шаблон, newStart, newEnd) для коммита и превью. */
    private fun newPositions(
        document: CadDocument,
        edge: CadEdge,
        neighbors: List<CadEdge>,
        axis: Point2,
        delta: Double
    ): List<Triple<CadEdge, Point2, Point2>> {
        val shiftX = axis.X * delta
        val shiftY = axis.Y * delta
        val result = mutableListOf<Triple<CadEdge, Point2, Point2>>()

        // Выделенное ребро: обе точки смещаются на delta вдоль оси.
        val s = TopologyService.getEdgeStartPoint(document, edge)
        val e = TopologyService.getEdgeEndPoint(document, edge)
        result.add(
            Triple(
                edge.cloneGeometry(),
                Point2(s.X + shiftX, s.Y + shiftY),
                Point2(e.X + shiftX, e.Y + shiftY)
            )
        )

        // Соседи: общий с выделенным конец смещается, дальний остаётся.
        for (neighbor in neighbors) {
            val ns = TopologyService.getEdgeStartPoint(document, neighbor)
            val ne = TopologyService.getEdgeEndPoint(document, neighbor)
            val sharedStart = neighbor.startVertexId == edge.startVertexId ||
                    neighbor.startVertexId == edge.endVertexId
            val sharedEnd = neighbor.endVertexId == edge.startVertexId ||
                    neighbor.endVertexId == edge.endVertexId
            val newNs = if (sharedStart) Point2(ns.X + shiftX, ns.Y + shiftY) else ns
            val newNe = if (sharedEnd) Point2(ne.X + shiftX, ne.Y + shiftY) else ne
            result.add(Triple(neighbor.cloneGeometry(), newNs, newNe))
        }

        return result
    }

    /** Проекция (dragCurrent − dragStart) на ось движения (со знаком). */
    private fun deltaFromDrag(axis: Point2): Double {
        val s = dragStart ?: return 0.0
        val c = dragCurrent ?: return 0.0
        return (c.X - s.X) * axis.X + (c.Y - s.Y) * axis.Y
    }

    /** Единичный вектор направления соседа(ей). */
    private fun axisFromNeighbors(
        document: CadDocument,
        neighbors: List<CadEdge>
    ): Point2? {
        val neighbor = neighbors.firstOrNull() ?: return null
        val s = TopologyService.getEdgeStartPoint(document, neighbor)
        val e = TopologyService.getEdgeEndPoint(document, neighbor)
        val dx = e.X - s.X
        val dy = e.Y - s.Y
        val length = MathUtils.distance(s, e)
        if (length <= TopologyTolerance.ForMutation) return null
        return Point2(dx / length, dy / length)
    }

    /** Середина ребра. */
    private fun midpointOf(document: CadDocument, edge: CadEdge): Point2 {
        val s = TopologyService.getEdgeStartPoint(document, edge)
        val e = TopologyService.getEdgeEndPoint(document, edge)
        return Point2((s.X + e.X) / 2.0, (s.Y + e.Y) / 2.0)
    }

    /** Все рёбра, имеющие общую вершину с [edge]. */
    private fun findAdjacentEdges(document: CadDocument, edge: CadEdge): List<CadEdge> {
        return document.edges.filter { other ->
            other.id != edge.id &&
                    (other.startVertexId == edge.startVertexId ||
                            other.startVertexId == edge.endVertexId ||
                            other.endVertexId == edge.startVertexId ||
                            other.endVertexId == edge.endVertexId)
        }
    }

    /**
     * Проверка валидности ребра для оффсета:
     *  - соседей 0 → невалидно;
     *  - в одной вершине больше 1 соседа (T-соединение) → невалидно;
     *  - соседей 2 → они должны быть параллельны;
     *  - сосед 1 → параллельность не проверяется.
     */
    private fun validateEdge(document: CadDocument, edge: CadEdge): ValidationResult {
        val neighbors = findAdjacentEdges(document, edge)
        if (neighbors.isEmpty()) return ValidationResult(false, null, emptyList())

        val startCount = neighbors.count {
            it.startVertexId == edge.startVertexId || it.endVertexId == edge.startVertexId
        }
        val endCount = neighbors.count {
            it.startVertexId == edge.endVertexId || it.endVertexId == edge.endVertexId
        }
        if (startCount > 1 || endCount > 1) {
            return ValidationResult(false, null, neighbors)
        }

        if (neighbors.size == 2) {
            val a = neighbors[0]
            val b = neighbors[1]
            val aS = TopologyService.getEdgeStartPoint(document, a)
            val aE = TopologyService.getEdgeEndPoint(document, a)
            val bS = TopologyService.getEdgeStartPoint(document, b)
            val bE = TopologyService.getEdgeEndPoint(document, b)
            val parallel = Geometry2D.areDirectionsParallel(
                aE.X - aS.X, aE.Y - aS.Y, MathUtils.distance(aS, aE),
                bE.X - bS.X, bE.Y - bS.Y, MathUtils.distance(bS, bE),
                TopologyTolerance.ForMutation
            )
            if (!parallel) return ValidationResult(false, null, neighbors)
        }

        val axis = axisFromNeighbors(document, neighbors)
            ?: return ValidationResult(false, null, neighbors)

        return ValidationResult(true, axis, neighbors)
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

    // --- Сброс ---

    private fun clearDraft() {
        draftSign = 1
        draftEdgeId = null
        draftEdgeIds = emptySet()
        draftAxisDir = null
    }

    private fun reset() {
        selectedEdgeId = null
        selectedEdgeIdsForOffset = emptySet()
        axisDir = null
        handleMid = null
        dragStart = null
        dragCurrent = null
        lastZoom = 1.0
        currentMeasurement = ""
        clearDraft()
        // flashEdgeId не трогаем — вспышку гасит отдельный запуск.
    }

    /** Результат проверки ребра: валидность, ось движения и список соседей. */
    private data class ValidationResult(
        val valid: Boolean,
        val axis: Point2?,
        val neighbors: List<CadEdge>
    )
}
