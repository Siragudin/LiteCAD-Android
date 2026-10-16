package com.sit.litecad.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sit.litecad.geometry.EdgeOperations
import com.sit.litecad.geometry.PolygonBuilder
import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.math.Geometry2D
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadAxis
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.CadEdge
import com.sit.litecad.model.EdgeLineType
import com.sit.litecad.services.DocumentHistoryService
import com.sit.litecad.services.SnapKind
import com.sit.litecad.services.SnapPoint
import com.sit.litecad.services.SnapQueryResult
import com.sit.litecad.services.SnapResult
import com.sit.litecad.services.SnapService
import com.sit.litecad.settings.ToolSettings
import com.sit.litecad.ui.canvas.Camera
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sign

/** Радиус «ручки» вершины на экране (в пикселях). */
private const val VERTEX_HANDLE_PX = 6f

/** Пиксельный допуск попадания в вершину при старте протяжки. */
private const val VERTEX_PICK_PX = 30.0

/**
 * Инструмент «Перемещение». Тянет либо вершину «за точку» (если старт рядом с вершиной),
 * либо выделенные рёбра и/или оси. В режиме «Копия» оригиналы не трогаются — создаются
 * сдвинутые дубли (и для рёбер, и для осей).
 *
 * Точный ввод включается флагом «Точный»: после протяжки открывается панель ввода
 * расстояния (ось определяется по доминирующей составляющей протяжки).
 */
class MoveToolHandler(
    private val snapService: SnapService,
    private val historyService: DocumentHistoryService,
    private val toolManager: ToolManager
) : Tool {

    private var hasStart by mutableStateOf(false)
    private var startWorld by mutableStateOf(Point2.Zero)
    private var currentWorld by mutableStateOf(Point2.Zero)

    /** Тяним вершину «за точку» (если задано) — иначе набор выделенных рёбер. */
    private var movingVertexId: UUID? = null

    /** Позиция перетаскиваемой вершины на момент старта — для пробной позиции и ortho. */
    private var movingVertexStart: Point2? = null
    private var movingEdgeIds: Set<UUID> = emptySet()

    /** Выделенные оси на момент старта — едут вместе с delta. */
    private var movingAxisIds: Set<UUID> = emptySet()

    /** Позиция якоря группы на старте (для перетаскивания рёбер) — ortho и probe идут от неё. */
    private var movingAnchorStart: Point2? = null

    /** Все перемещаемые вершины группы (для снапа объекта и фильтра самоснапа). */
    private var movingVertexIds: Set<UUID> = emptySet()

    /** Режим «Копия» на момент старта. */
    private var dragCopy = false

    private var lastSnapKind by mutableStateOf<SnapKind?>(null)
    private var lastZoom = 1.0

    // Черновик незакоммиченного перемещения (пока открыт ввод смещения).
    private var draftStart: Point2? = null
    private var draftCurrent: Point2? = null
    private var draftMovingVertexId: UUID? = null
    private var draftMovingEdgeIds: Set<UUID> = emptySet()
    private var draftMovingAxisIds: Set<UUID> = emptySet()
    private var draftCopy = false
    private var draftAxis: MoveAxis? = null

    override var currentMeasurement by mutableStateOf("")
        private set

    override val previewMeasurements: List<PreviewMeasurement>
        get() = if (hasStart) {
            listOf(PreviewMeasurement("Δ", "${MathUtils.distance(startWorld, currentWorld).toInt()} мм"))
        } else {
            emptyList()
        }

    override fun onStart(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastZoom = zoom

        // Приоритет: вершина «за точку», затем выделенные рёбра и/или оси.
        val vertex = TopologyService.findVertex(document, point, VERTEX_PICK_PX / zoom)
        if (vertex != null) {
            movingVertexId = vertex.id
            movingVertexStart = vertex.position
            movingEdgeIds = emptySet()
            movingAxisIds = emptySet()
            movingVertexIds = emptySet()
            movingAnchorStart = null
        } else if (toolManager.selection.selectedEdgeIds.isNotEmpty() ||
            toolManager.selection.selectedAxisIds.isNotEmpty()
        ) {
            movingVertexId = null
            movingVertexStart = null
            movingEdgeIds = toolManager.selection.selectedEdgeIds.toSet()
            movingAxisIds = toolManager.selection.selectedAxisIds.toSet()
            // Снапим сам объект — собираем все перемещаемые вершины выделенных рёбер.
            val vids = linkedSetOf<UUID>()
            for (edgeId in movingEdgeIds) {
                val edge = document.edgeById(edgeId) ?: continue
                vids.add(edge.startVertexId)
                vids.add(edge.endVertexId)
            }
            movingVertexIds = vids
            // Якорь группы на старте (ortho/probe идут от него): ближайшая перемещаемая
            // вершина, а если рёбер нет — ближайший конец выделенной оси.
            val anchorId = findNearestMovingVertex(document, point)
            movingAnchorStart = anchorId?.let { document.vertexById(it)?.position }
                ?: findNearestAxisPoint(document, point)
        } else {
            // Тянуть нечего.
            hasStart = false
            return
        }

        startWorld = point
        currentWorld = point
        dragCopy = settings.move.copyMode
        lastSnapKind = null
        hasStart = true
        currentMeasurement = toolManager.settings.promptDrag
    }

    override fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        if (!hasStart) return
        lastZoom = zoom

        val vertexId = movingVertexId
        if (vertexId != null) {
            val vStart = movingVertexStart ?: point
            // Пробная позиция — куда бы ушла вершина без снапа.
            val probeRaw = shift(vStart, point - startWorld)
            // Ortho применяется ПЕРВЫМ и главнее снапа — ось от позиции вершины на старте.
            val probe = if (settings.move.orthoEnabled) {
                Geometry2D.applyOrtho(vStart, probeRaw)
            } else {
                probeRaw
            }
            // Снап вокруг уже орто-ограниченной точки. Ortho внутри resolveObjectSnap не применяем.
            val result = resolveObjectSnap(
                document = document,
                probe = probe,
                zoom = zoom,
                excludeVertexIds = setOf(vertexId),
                excludeEdgeIds = emptySet()
            )
            // Смещение объекта = путь якоря (с учётом ortho и снапа) от его старта,
            // а не от пальца — иначе ortho теряется при отсутствии снапа.
            currentWorld = Point2(
                startWorld.X + (result.position.X - vStart.X),
                startWorld.Y + (result.position.Y - vStart.Y)
            )
            lastSnapKind = result.kind
        } else {
            // Якорь зафиксирован на старте — не пересчитываем.
            val aStart = movingAnchorStart
            if (aStart == null) {
                currentWorld = point
                lastSnapKind = null
            } else {
                val probeRaw = shift(aStart, point - startWorld)
                // Ortho от якоря на старте — главнее снапа.
                val probe = if (settings.move.orthoEnabled) {
                    Geometry2D.applyOrtho(aStart, probeRaw)
                } else {
                    probeRaw
                }
                val result = resolveObjectSnap(
                    document = document,
                    probe = probe,
                    zoom = zoom,
                    excludeVertexIds = movingVertexIds,
                    excludeEdgeIds = movingEdgeIds
                )
                // Смещение объекта = путь якоря от его старта (ortho/снап учтены в probe/result).
                currentWorld = Point2(
                    startWorld.X + (result.position.X - aStart.X),
                    startWorld.Y + (result.position.Y - aStart.Y)
                )
                lastSnapKind = result.kind
            }
        }

        val dist = MathUtils.distance(startWorld, currentWorld)
        currentMeasurement = "Δ: ${dist.toInt()}"
    }

    override fun onEnd(document: CadDocument) {
        if (!hasStart) {
            reset()
            return
        }

        val delta = currentWorld - startWorld
        val isZero = abs(delta.X) <= TopologyTolerance.ForMutation &&
                abs(delta.Y) <= TopologyTolerance.ForMutation
        if (isZero) {
            reset()
            return
        }

        val move = toolManager.settings.move
        if (move.exactInputEnabled) {
            // Протяжка по оси → открываем панель точного ввода, коммит откладываем.
            val axis = if (abs(delta.X) >= abs(delta.Y)) MoveAxis.X else MoveAxis.Y
            val initial = if (axis == MoveAxis.X) abs(delta.X) else abs(delta.Y)

            draftStart = startWorld
            draftCurrent = currentWorld
            draftMovingVertexId = movingVertexId
            draftMovingEdgeIds = movingEdgeIds
            draftMovingAxisIds = movingAxisIds
            draftCopy = dragCopy
            draftAxis = axis

            toolManager.requestInput(PendingInput.MoveDistance(initial, axis))
        } else {
            commitMove(document, delta)
        }
    }

    override fun onMoveInputSubmitted(value: Double, document: CadDocument) {
        val ds = draftStart
        val dc = draftCurrent
        if (ds == null || dc == null) {
            clearDraft()
            reset()
            return
        }

        val axis = draftAxis ?: if (abs(dc.X - ds.X) >= abs(dc.Y - ds.Y)) MoveAxis.X else MoveAxis.Y
        // Знак берём из направления протяжки; величину — из введённого значения.
        val s = if (axis == MoveAxis.X) sign(dc.X - ds.X) else sign(dc.Y - ds.Y)
        val finalDelta = if (axis == MoveAxis.X) Point2(s * value, 0.0) else Point2(0.0, s * value)

        commitMove(document, finalDelta)
        clearDraft()
        reset()
    }

    override fun onMoveInputCancelled(document: CadDocument) {
        // Откат — ничего не коммитим.
        clearDraft()
        reset()
    }

    private fun commitMove(document: CadDocument, delta: Point2) {
        val hasDraft = draftStart != null
        val vertexId = if (hasDraft) draftMovingVertexId else movingVertexId
        val edgeIds = if (hasDraft) draftMovingEdgeIds else movingEdgeIds
        val axisIds = if (hasDraft) draftMovingAxisIds else movingAxisIds
        val copy = if (hasDraft) draftCopy else dragCopy

        historyService.record(document)

        when {
            // Копия выделенных рёбер: оригиналы не трогаем, добавляем сдвинутые дубли.
            copy && edgeIds.isNotEmpty() -> {
                for (edgeId in edgeIds) {
                    val edge = document.edgeById(edgeId) ?: continue
                    val s = TopologyService.getEdgeStartPoint(document, edge)
                    val e = TopologyService.getEdgeEndPoint(document, edge)
                    val template = edge.cloneGeometry()
                    EdgeOperations.addSegmentInternal(
                        document,
                        Point2(s.X + delta.X, s.Y + delta.Y),
                        Point2(e.X + delta.X, e.Y + delta.Y),
                        template,
                        TopologyTolerance.ForMutation
                    )
                }
                PolygonBuilder.syncFaces(document)
            }

            // Перемещение вершины «за точку»: пересоздаём инцидентные рёбра через
            // addSegmentInternal — он сам сваривает совпавшие вершины по новым координатам.
            vertexId != null -> {
                val incidentEdges = document.edges.filter {
                    it.startVertexId == vertexId || it.endVertexId == vertexId
                }

                val moved = incidentEdges.mapNotNull { edge ->
                    val s = TopologyService.getEdgeStartPoint(document, edge)
                    val e = TopologyService.getEdgeEndPoint(document, edge)
                    val ns = if (edge.startVertexId == vertexId) Point2(s.X + delta.X, s.Y + delta.Y) else s
                    val ne = if (edge.endVertexId == vertexId) Point2(e.X + delta.X, e.Y + delta.Y) else e
                    Triple(edge.cloneGeometry(), ns, ne)
                }

                for (edge in incidentEdges) {
                    document.removeEdge(edge)
                }
                // Старые вершины без рёбер — удалить, иначе останутся висеть в воздухе.
                TopologyService.pruneUnusedVertices(document)

                for ((template, newStart, newEnd) in moved) {
                    EdgeOperations.addSegmentInternal(
                        document, newStart, newEnd, template, TopologyTolerance.ForMutation
                    )
                }
                PolygonBuilder.syncFaces(document)
            }

            // Перемещение выделенных рёбер: удаляем оригиналы и добавляем сдвинутые через
            // addSegmentInternal — вершины свариваются по новым координатам.
            edgeIds.isNotEmpty() -> {
                val moved = edgeIds.mapNotNull { edgeId ->
                    val edge = document.edgeById(edgeId) ?: return@mapNotNull null
                    val s = TopologyService.getEdgeStartPoint(document, edge)
                    val e = TopologyService.getEdgeEndPoint(document, edge)
                    Triple(
                        edge.cloneGeometry(),
                        Point2(s.X + delta.X, s.Y + delta.Y),
                        Point2(e.X + delta.X, e.Y + delta.Y)
                    )
                }

                for (edgeId in edgeIds) {
                    document.edgeById(edgeId)?.let { document.removeEdge(it) }
                }
                // Старые вершины без рёбер — удалить, иначе останутся висеть в воздухе.
                TopologyService.pruneUnusedVertices(document)

                for ((template, newStart, newEnd) in moved) {
                    EdgeOperations.addSegmentInternal(
                        document, newStart, newEnd, template, TopologyTolerance.ForMutation
                    )
                }
                PolygonBuilder.syncFaces(document)
            }
        }

        // Оси — отдельная сущность (вне топологии граней): сдвигаем обе точки напрямую.
        // В режиме «Копия», как и для рёбер, оригинал оси сохраняется, а добавляется сдвинутая копия.
        if (axisIds.isNotEmpty()) {
            for (axisId in axisIds) {
                val axis = document.axisById(axisId) ?: continue
                val newStart = Point2(axis.Start.X + delta.X, axis.Start.Y + delta.Y)
                val newEnd = Point2(axis.End.X + delta.X, axis.End.Y + delta.Y)
                if (copy) {
                    // Новый id — CadAxis создаёт его по умолчанию; переносим только стиль.
                    document.addAxis(
                        CadAxis(
                            Start = newStart,
                            End = newEnd,
                            colorArgb = axis.colorArgb,
                            thickness = axis.thickness
                        )
                    )
                } else {
                    axis.Start = newStart
                    axis.End = newEnd
                }
            }
        }

        document.bumpRevision()
        reset()
    }

    /** Ближайшая к курсору перемещаемая вершина — якорь для снапа объекта при перетаскивании рёбер. */
    private fun findNearestMovingVertex(document: CadDocument, cursor: Point2): UUID? {
        var bestId: UUID? = null
        var bestDist = Double.MAX_VALUE
        for (vertexId in movingVertexIds) {
            val vertex = document.vertexById(vertexId) ?: continue
            val d = MathUtils.distance(vertex.position, cursor)
            if (d < bestDist) {
                bestDist = d
                bestId = vertexId
            }
        }
        return bestId
    }

    /** Ближайший к курсору конец перемещаемой оси — якорь группы, когда рёбер нет. */
    private fun findNearestAxisPoint(document: CadDocument, cursor: Point2): Point2? {
        var best: Point2? = null
        var bestDist = Double.MAX_VALUE
        for (axisId in movingAxisIds) {
            val axis = document.axisById(axisId) ?: continue
            val dStart = MathUtils.distance(axis.Start, cursor)
            if (dStart < bestDist) {
                bestDist = dStart
                best = axis.Start
            }
            val dEnd = MathUtils.distance(axis.End, cursor)
            if (dEnd < bestDist) {
                bestDist = dEnd
                best = axis.End
            }
        }
        return best
    }

    /**
     * Снап объекта: ищем привязку вокруг пробной позиции [probe] (уже ortho-ограниченной),
     * отбрасывая самоснап (снапы на вершинах/рёбрах перемещаемой группы). Отсутствующий/
     * отброшенный снап не срывает вызов — вернётся сам [probe] (SnapResult.resolve).
     * Ortho здесь НЕ применяется: он уже применён снаружи до снапа.
     */
    private fun resolveObjectSnap(
        document: CadDocument,
        probe: Point2,
        zoom: Double,
        excludeVertexIds: Set<UUID>,
        excludeEdgeIds: Set<UUID>
    ): ObjectSnapResult {
        val tolerance = 60.0 / zoom
        val raw = snapService.query(
            document = document,
            cursor = probe,
            tolerance = tolerance,
            includeOnEdge = true
        )
        val best = raw.Best.Snap
        val allowedBest = if (best != null && isSnapAllowed(best, excludeVertexIds, excludeEdgeIds)) {
            best
        } else {
            null
        }

        val filtered = SnapQueryResult(
            Best = SnapResult(allowedBest, allowedBest != null),
            Visible = raw.Visible
        )
        val resolved = snapService.resolveDrawingSnap(
            document = document,
            query = filtered,
            cursor = probe,
            drawingStart = null,
            tolerance = tolerance,
            orthoEnabled = false
        )
        return ObjectSnapResult(resolved, allowedBest?.Kind)
    }

    /**
     * Фильтр самоснапа: запрещаем привязку к вершинам/рёбрам перемещаемой группы.
     * Для Intersection/Alignment (VertexId/EdgeId == null) фильтр не срабатывает — это осознанно.
     */
    private fun isSnapAllowed(
        snap: SnapPoint,
        excludeVertexIds: Set<UUID>,
        excludeEdgeIds: Set<UUID>
    ): Boolean {
        if (snap.VertexId != null && snap.VertexId in excludeVertexIds) return false
        if (snap.EdgeId != null && snap.EdgeId in excludeEdgeIds) return false
        return true
    }

    private fun clearDraft() {
        draftStart = null
        draftCurrent = null
        draftMovingVertexId = null
        draftMovingEdgeIds = emptySet()
        draftMovingAxisIds = emptySet()
        draftCopy = false
        draftAxis = null
    }

    private fun reset() {
        hasStart = false
        startWorld = Point2.Zero
        currentWorld = Point2.Zero
        movingVertexId = null
        movingVertexStart = null
        movingEdgeIds = emptySet()
        movingAxisIds = emptySet()
        movingVertexIds = emptySet()
        movingAnchorStart = null
        dragCopy = false
        lastSnapKind = null
        lastZoom = 1.0
        currentMeasurement = ""
        clearDraft()
    }

    override fun drawPreview(scope: DrawScope, camera: Camera) {
        val document = toolManager.document

        // Ручки всех вершин — чтобы было видно, за что можно тянуть.
        for (vertex in document.vertices) {
            val center = camera.worldToScreen(vertex.position)
            scope.drawCircle(Color.Red, VERTEX_HANDLE_PX, center)
            scope.drawCircle(Color.Black, VERTEX_HANDLE_PX + 2f, center, style = Stroke(width = 2f))
        }

        if (!hasStart) return

        val delta = currentWorld - startWorld
        val vertexId = movingVertexId

        if (vertexId != null) {
            // Рёбра, инцидентные перетаскиваемой вершине, — в новых позициях.
            for (edge in document.edges) {
                if (edge.startVertexId != vertexId && edge.endVertexId != vertexId) continue
                val s = TopologyService.getEdgeStartPoint(document, edge)
                val e = TopologyService.getEdgeEndPoint(document, edge)
                val movedStart = if (edge.startVertexId == vertexId) shift(s, delta) else s
                val movedEnd = if (edge.endVertexId == vertexId) shift(e, delta) else e
                drawPreviewEdge(scope, camera, edge, movedStart, movedEnd)
            }
        } else if (movingEdgeIds.isNotEmpty()) {
            // Превью перемещения (или копии) выделенных рёбер.
            for (edgeId in movingEdgeIds) {
                val edge = document.edgeById(edgeId) ?: continue
                val s = TopologyService.getEdgeStartPoint(document, edge)
                val e = TopologyService.getEdgeEndPoint(document, edge)
                drawPreviewEdge(scope, camera, edge, shift(s, delta), shift(e, delta))
            }
        }

        // Превью перемещения выделенных осей — серый пунктир.
        if (movingAxisIds.isNotEmpty()) {
            val effect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
            for (axisId in movingAxisIds) {
                val axis = document.axisById(axisId) ?: continue
                scope.drawLine(
                    color = toolManager.previewStrokeColor(Color.Gray),
                    start = camera.worldToScreen(shift(axis.Start, delta)),
                    end = camera.worldToScreen(shift(axis.End, delta)),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                    pathEffect = effect
                )
            }
        }

        drawSnapMarker(scope, camera.worldToScreen(currentWorld), lastSnapKind)
    }

    private fun shift(point: Point2, delta: Point2): Point2 =
        Point2(point.X + delta.X, point.Y + delta.Y)

    /** Превью одного ребра в его собственном стиле (цвет/толщина/тип). */
    private fun drawPreviewEdge(scope: DrawScope, camera: Camera, edge: CadEdge, start: Point2, end: Point2) {
        val p1 = camera.worldToScreen(start)
        val p2 = camera.worldToScreen(end)
        val t = edge.thickness.toFloat()
        val effect = when (edge.lineType) {
            EdgeLineType.DASHED -> PathEffect.dashPathEffect(floatArrayOf(8f * t, 4f * t), 0f)
            EdgeLineType.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(2f * t, 4f * t), 0f)
            EdgeLineType.SOLID -> null
        }
        scope.drawLine(
            color = toolManager.previewStrokeColor(Color(edge.colorArgb)),
            start = p1,
            end = p2,
            strokeWidth = t,
            cap = StrokeCap.Round,
            pathEffect = effect
        )
    }

    private fun drawSnapMarker(scope: DrawScope, center: Offset, kind: SnapKind?) {
        if (kind == null) return
        val color = when (kind) {
            SnapKind.Endpoint -> Color.Green
            SnapKind.Intersection, SnapKind.AxisIntersection -> Color(0xFFFF9800)
            SnapKind.Midpoint -> Color.Cyan
            SnapKind.OnEdge -> Color.Red
            SnapKind.Alignment -> Color.Yellow
        }
        val radius = 14f
        scope.drawCircle(color, radius, center)
        scope.drawCircle(Color.Black, radius + 2f, center, style = Stroke(width = 3f))
    }

    override fun cancel() {
        reset()
    }

    /** Результат снапа объекта: итоговая позиция и вид привязки (null — снапа нет). */
    private data class ObjectSnapResult(val position: Point2, val kind: SnapKind?)
}
