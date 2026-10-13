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
import com.sit.litecad.model.CadDocument
import com.sit.litecad.services.DocumentHistoryService
import com.sit.litecad.services.SnapKind
import com.sit.litecad.services.SnapPoint
import com.sit.litecad.services.SnapQueryResult
import com.sit.litecad.services.SnapResult
import com.sit.litecad.services.SnapService
import com.sit.litecad.settings.ToolSettings
import com.sit.litecad.ui.canvas.Camera
import java.util.UUID

/** Пиксельный допуск объектного снапа при отражении. */
private const val MIRROR_SNAP_PX = 60.0

/** Радиус маркера якоря оси на экране (в пикселях). */
private const val PIVOT_MARKER_PX = 9f

/**
 * Инструмент «Зеркало». Операция над выделенными рёбрами: оси и грани игнорируются,
 * грани пересоберутся через [PolygonBuilder.syncFaces]. Отражение — чистая геометрия
 * (отражение точки через прямую), топология не меняется.
 *
 * Жест однофазный: onStart задаёт начало оси отражения (она же якорь), протяжка задаёт
 * конец оси, onEnd коммитит. Режим «Копия» оставляет оригиналы и добавляет отражённые дубли.
 */
class MirrorToolHandler(
    private val snapService: SnapService,
    private val historyService: DocumentHistoryService,
    private val toolManager: ToolManager
) : Tool {

    /** Идёт ли жест. */
    private var active by mutableStateOf(false)

    /** Начало оси отражения. */
    private var axisStart by mutableStateOf(Point2.Zero)

    /** Конец оси отражения. */
    private var axisEnd by mutableStateOf(Point2.Zero)

    /** Вид снапа пальца — для маркера. */
    private var lastSnapKind by mutableStateOf<SnapKind?>(null)

    private var lastZoom = 1.0

    override var currentMeasurement by mutableStateOf("")
        private set

    override fun onStart(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        // Без выделенных рёбер жест игнорируем — ничего не рисуем и не коммитим.
        if (toolManager.selection.selectedEdgeIds.isEmpty()) return

        lastZoom = zoom

        val excludeEdgeIds = toolManager.selection.selectedEdgeIds.toSet()
        val excludeVertexIds = collectVertexIds(document, excludeEdgeIds)

        val snapped = resolveObjectSnap(
            document = document,
            probe = point,
            zoom = zoom,
            excludeVertexIds = excludeVertexIds,
            excludeEdgeIds = excludeEdgeIds
        )

        axisStart = snapped.position
        axisEnd = axisStart
        lastSnapKind = snapped.kind
        active = true
        currentMeasurement = "Тяните ось"
    }

    override fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        if (!active) return

        lastZoom = zoom

        val excludeEdgeIds = toolManager.selection.selectedEdgeIds.toSet()
        val excludeVertexIds = collectVertexIds(document, excludeEdgeIds)

        val snapped = resolveObjectSnap(
            document = document,
            probe = point,
            zoom = zoom,
            excludeVertexIds = excludeVertexIds,
            excludeEdgeIds = excludeEdgeIds
        )

        val resolved = if (settings.mirror.orthoEnabled) {
            Geometry2D.applyOrtho(axisStart, snapped.position)
        } else {
            snapped.position
        }

        axisEnd = resolved
        lastSnapKind = snapped.kind
        currentMeasurement = "Длина: ${MathUtils.distance(axisStart, axisEnd).toInt()}"
    }

    override fun onEnd(document: CadDocument) {
        if (!active) return

        val dist = MathUtils.distance(axisStart, axisEnd)
        if (dist <= TopologyTolerance.ForMutation) {
            // Ось не задана — откат.
            reset()
            return
        }

        commitMirror(document, axisStart, axisEnd, toolManager.settings.mirror.copyMode)
        reset()
    }

    override fun cancel() {
        reset()
    }

    override fun drawPreview(scope: DrawScope, camera: Camera) {
        if (!active) return

        // Линия оси отражения — тонкая, серая, пунктиром.
        scope.drawLine(
            color = Color.Gray,
            start = camera.worldToScreen(axisStart),
            end = camera.worldToScreen(axisEnd),
            strokeWidth = 2f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
        )

        // Зеркальные рёбра выделения — ghost-стиль: серый пунктир постоянного стиля.
        val document = toolManager.document
        val ghostEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
        for (edgeId in toolManager.selection.selectedEdgeIds) {
            val edge = document.edgeById(edgeId) ?: continue
            val s = TopologyService.getEdgeStartPoint(document, edge)
            val e = TopologyService.getEdgeEndPoint(document, edge)
            scope.drawLine(
                color = Color.Gray,
                start = camera.worldToScreen(mirrorPoint(s, axisStart, axisEnd)),
                end = camera.worldToScreen(mirrorPoint(e, axisStart, axisEnd)),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
                pathEffect = ghostEffect
            )
        }

        // Маркер якоря оси.
        val anchorScreen = camera.worldToScreen(axisStart)
        scope.drawCircle(Color(0xFF1565C0), PIVOT_MARKER_PX, anchorScreen)
        scope.drawCircle(Color.Black, PIVOT_MARKER_PX + 2f, anchorScreen, style = Stroke(width = 2f))

        // Маркер снапа пальца.
        drawSnapMarker(scope, camera.worldToScreen(axisEnd), lastSnapKind)
    }

    /**
     * Отражение выделенных рёбер через прямую [axisStart]—[axisEnd]. В режиме [copy]
     * оригиналы не трогаются — добавляются отражённые дубли.
     */
    private fun commitMirror(document: CadDocument, axisStart: Point2, axisEnd: Point2, copy: Boolean) {
        // Ось невалидна — выходим.
        if (MathUtils.distance(axisStart, axisEnd) <= TopologyTolerance.ForMutation) return

        val edgeIds = toolManager.selection.selectedEdgeIds.toSet()
        if (edgeIds.isEmpty()) return

        historyService.record(document)

        // Готовим отражённые сегменты заранее — до удаления оригиналов.
        val mirrored = edgeIds.mapNotNull { edgeId ->
            val edge = document.edgeById(edgeId) ?: return@mapNotNull null
            val s = TopologyService.getEdgeStartPoint(document, edge)
            val e = TopologyService.getEdgeEndPoint(document, edge)
            Triple(edge.cloneGeometry(), mirrorPoint(s, axisStart, axisEnd), mirrorPoint(e, axisStart, axisEnd))
        }

        // Без «Копии» старые рёбра удаляем и подчищаем осиротевшие вершины.
        if (!copy) {
            for (edgeId in edgeIds) {
                document.edgeById(edgeId)?.let { document.removeEdge(it) }
            }
            TopologyService.pruneUnusedVertices(document)
        }

        for ((template, newStart, newEnd) in mirrored) {
            EdgeOperations.addSegmentInternal(
                document, newStart, newEnd, template, TopologyTolerance.ForMutation
            )
        }

        PolygonBuilder.syncFaces(document)
        document.bumpRevision()

        // Снимаем выделение: оригиналы могли исчезнуть.
        toolManager.selection.clear()
    }

    /** Отражение точки [p] через прямую [axisStart]—[axisEnd]. */
    private fun mirrorPoint(p: Point2, axisStart: Point2, axisEnd: Point2): Point2 {
        val dx = axisEnd.X - axisStart.X
        val dy = axisEnd.Y - axisStart.Y
        val lengthSq = dx * dx + dy * dy
        if (lengthSq <= TopologyTolerance.ForMutation * TopologyTolerance.ForMutation) return p
        val t = ((p.X - axisStart.X) * dx + (p.Y - axisStart.Y) * dy) / lengthSq
        val footX = axisStart.X + t * dx
        val footY = axisStart.Y + t * dy
        return Point2(2 * footX - p.X, 2 * footY - p.Y)
    }

    /** Все вершины указанных рёбер — для фильтра самоснапа. */
    private fun collectVertexIds(document: CadDocument, edgeIds: Set<UUID>): Set<UUID> {
        if (edgeIds.isEmpty()) return emptySet()
        val ids = linkedSetOf<UUID>()
        for (edgeId in edgeIds) {
            val edge = document.edgeById(edgeId) ?: continue
            ids.add(edge.startVertexId)
            ids.add(edge.endVertexId)
        }
        return ids
    }

    // --- Снап (логика скопирована из RotateToolHandler) ---

    /**
     * Снап объекта: ищем привязку вокруг [probe], отбрасывая самоснап (снапы на вершинах/рёбрах
     * выделенной группы). Отсутствующий/отброшенный снап не срывает вызов — вернётся сам [probe].
     */
    private fun resolveObjectSnap(
        document: CadDocument,
        probe: Point2,
        zoom: Double,
        excludeVertexIds: Set<UUID>,
        excludeEdgeIds: Set<UUID>
    ): ObjectSnapResult {
        val tolerance = MIRROR_SNAP_PX / zoom
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
     * Фильтр самоснапа: запрещаем привязку к вершинам/рёбрам выделенной группы.
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

    /** Маркер снапа пальца — цветной кружок по [kind] (скопировано из RotateToolHandler). */
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

    private fun reset() {
        active = false
        axisStart = Point2.Zero
        axisEnd = Point2.Zero
        lastSnapKind = null
        lastZoom = 1.0
        currentMeasurement = ""
    }

    /** Результат снапа объекта: итоговая позиция и вид привязки (null — снапа нет). */
    private data class ObjectSnapResult(val position: Point2, val kind: SnapKind?)
}
