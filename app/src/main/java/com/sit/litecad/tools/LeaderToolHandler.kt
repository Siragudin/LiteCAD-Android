package com.sit.litecad.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sit.litecad.leaders.LeaderGeometry
import com.sit.litecad.leaders.LeaderLayout
import com.sit.litecad.leaders.LeaderService
import com.sit.litecad.math.Geometry2D
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.services.DocumentHistoryService
import com.sit.litecad.services.SelectionPickTolerance
import com.sit.litecad.services.SnapKind
import com.sit.litecad.services.SnapPoint
import com.sit.litecad.services.SnapQueryResult
import com.sit.litecad.services.SnapResult
import com.sit.litecad.services.SnapService
import com.sit.litecad.settings.ToolSettings
import com.sit.litecad.ui.canvas.Camera
import java.util.UUID

/** Пиксельный допуск объектного снапа при постановке цели выноски. */
private const val LEADER_SNAP_PX = 60.0

/** Допуск попадания пальцем в саму выноску (в экранных пикселях). */
private const val LEADER_PICK_PX = 24.0

/** Радиус точки-ручки выноски на экране (в пикселях). */
private const val HANDLE_RADIUS_PX = 9f

/**
 * Инструмент «Выноска отметки высоты». Работает двумя жестами:
 *
 *  - Жест 1 — тап по чертежу задаёт цель (снап к вершинам/рёбрам). Сразу после onStart
 *    создаётся черновая выноска в дефолтном положении (полка вправо, на уровне цели).
 *    В документ ничего не коммитится.
 *  - Жест 2 — тап + протяжка за саму выноску (тап должен попасть в стойку/полку/
 *    точку-ручку). Выноска едет горизонтально вместе с пальцем. Отпускание = коммит.
 *
 * Если после жеста 1 не было жеста 2 (тапнули рядом, в другом месте или сменили
 * инструмент) — выноска коммитится на месте первого тапа. Снятие не откатывает:
 * коммит обязателен.
 */
class LeaderToolHandler(
    private val snapService: SnapService,
    private val historyService: DocumentHistoryService,
    private val toolManager: ToolManager
) : Tool {

    /** Цель поставлена (жест 1 выполнен). */
    private var hasTarget by mutableStateOf(false)

    /** Исходная снапнутая цель (жест 1) — задаёт уровень Y и сторону полки. */
    private var snapTarget by mutableStateOf(Point2.Zero)

    /** Текущая позиция выноски (шеврон, стойка, полка). При протяжке едет по X за пальцем. */
    private var target by mutableStateOf(Point2.Zero)

    /** Идёт ли протяжка. */
    private var dragging by mutableStateOf(false)

    /** Маркер снапа. */
    private var lastSnapKind by mutableStateOf<SnapKind?>(null)

    private var lastZoom = 1.0

    override var currentMeasurement by mutableStateOf("")
        private set

    override fun onStart(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastZoom = zoom

        if (!hasTarget) {
            // Жест 1 — ставим цель (снап на что угодно, без исключений).
            val snapped = resolveObjectSnap(document, point, zoom, emptySet(), emptySet())
            snapTarget = snapped.position
            target = snapped.position
            hasTarget = true
            lastSnapKind = snapped.kind
            currentMeasurement = ""
            return
        }

        // Жест 2 — проверяем, попал ли тап в саму выноску.
        val layout = LeaderGeometry.createLayout(target, sideProbe(), zoom)
        val tolerance = SelectionPickTolerance.pixelsToWorld(LEADER_PICK_PX, zoom)
        if (!hitTestLeader(layout, point, tolerance)) {
            // Промах — коммитим на текущем месте.
            commitLeader(document, target)
            reset()
            return
        }

        dragging = true
        currentMeasurement = "Тяните"
    }

    override fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastZoom = zoom

        val snapped = resolveObjectSnap(document, point, zoom, emptySet(), emptySet())
        lastSnapKind = snapped.kind

        if (dragging) {
            // Вся выноска едет за пальцем по X; Y фиксирован на уровне снапнутой цели.
            target = Point2(snapped.position.X, snapTarget.Y)
            currentMeasurement = ""
        }
    }

    override fun onEnd(document: CadDocument) {
        if (!dragging) return
        // Коммит на текущем месте (X пальца, Y снапнутой цели).
        commitLeader(document, target)
        reset()
    }

    override fun cancel() {
        if (hasTarget) {
            // Снятие инструмента — выноска коммитится на текущем месте.
            commitLeader(toolManager.document, target)
        }
        reset()
    }

    override fun drawPreview(scope: DrawScope, camera: Camera) {
        if (!hasTarget) return

        val layout = LeaderGeometry.createLayout(target, sideProbe(), lastZoom)
        val color = Color(toolManager.settings.leader.color)

        for ((a, b) in LeaderGeometry.getSegments(layout)) {
            scope.drawLine(
                color = color,
                start = camera.worldToScreen(a),
                end = camera.worldToScreen(b),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }

        // Точка-ручка в середине полки: цветной кружок с белой серединой.
        val handleWorld = Point2(
            (layout.elbow.X + layout.landingEnd.X) * 0.5,
            (layout.elbow.Y + layout.landingEnd.Y) * 0.5
        )
        val handleScreen = camera.worldToScreen(handleWorld)
        scope.drawCircle(color, HANDLE_RADIUS_PX, handleScreen)
        scope.drawCircle(Color.White, HANDLE_RADIUS_PX * 0.45f, handleScreen)

        drawSnapMarker(scope, camera.worldToScreen(target), lastSnapKind)
    }

    // --- Коммит ---

    private fun commitLeader(document: CadDocument, position: Point2) {
        historyService.record(document)
        // Позиция выноски: X — как у пальца, Y — от снапнутой цели.
        LeaderService.createElevation(document, position, position, toolManager.settings.leader.color)
        document.bumpRevision()
        toolManager.selection.clear()
    }

    /**
     * Точка-«зонд» для createLayout: задаёт сторону полки по направлению протяжки.
     * Тянем влево от исходной цели → полка слева (−1), иначе справа (+1).
     */
    private fun sideProbe(): Point2 {
        val side = if (target.X < snapTarget.X - 1e-9) -1 else 1
        return Point2(target.X + side, target.Y)
    }

    /** Попадание пальцем хоть в один отрезок выноски. */
    private fun hitTestLeader(layout: LeaderLayout, point: Point2, tolerance: Double): Boolean {
        for ((a, b) in LeaderGeometry.getSegments(layout)) {
            if (Geometry2D.tryHitTestSegment(point, a, b, tolerance) != null) return true
        }
        return false
    }

    private fun reset() {
        hasTarget = false
        snapTarget = Point2.Zero
        target = Point2.Zero
        dragging = false
        lastSnapKind = null
        lastZoom = 1.0
        currentMeasurement = ""
    }

    // --- Снап (логика скопирована из RotateToolHandler) ---

    /**
     * Снап объекта: ищем привязку вокруг [probe]. Отсутствующий/отброшенный снап
     * не срывает вызов — вернётся сам [probe].
     */
    private fun resolveObjectSnap(
        document: CadDocument,
        probe: Point2,
        zoom: Double,
        excludeVertexIds: Set<UUID>,
        excludeEdgeIds: Set<UUID>
    ): ObjectSnapResult {
        val tolerance = LEADER_SNAP_PX / zoom
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
     * Фильтр самоснапа: запрещаем привязку к вершинам/рёбрам исключённой группы.
     * Для Intersection/Alignment (VertexId/EdgeId == null) фильтр не срабатывает.
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

    /** Результат снапа объекта: итоговая позиция и вид привязки (null — снапа нет). */
    private data class ObjectSnapResult(val position: Point2, val kind: SnapKind?)
}
