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
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Пиксельный допуск объектного снапа при повороте. */
private const val ROTATE_SNAP_PX = 60.0

/** Допуск Ortho-снапа по «круглым» углам (в градусах). */
private const val RIGHT_ANGLE_TOLERANCE_DEG = 5.0

/** Порог «нулевого» угла — такой поворот не коммитим. */
private const val MIN_COMMIT_ANGLE_DEG = 0.5

/** Радиус маркера якоря на экране (в пикселях). */
private const val PIVOT_MARKER_PX = 9f

private const val TWO_PI = 2.0 * PI

/**
 * Инструмент «Поворот». Операция над выделенными рёбрами: оси и грани игнорируются,
 * грани пересоберутся через [PolygonBuilder.syncFaces]. Вращение — чистая геометрия
 * (поворот точки вокруг точки), топология не меняется.
 *
 * Жест двухфазный:
 *  - phase 0 — idle;
 *  - phase 1 — якорь поставлен, ждём референс (задаётся протяжкой: onEnd фиксирует
 *    референсную точку — нулевую отметку угла). Чистый тап оставляет якорь и ждёт дальше;
 *  - phase 2 — крутим: угол между «референс − якорь» и «палец − якорь». Отпускание — коммит.
 *
 * Точный ввод (флаг «Точный») вместо коммита открывает панель угла. Режим «Копия»
 * оставляет оригиналы и добавляет повёрнутые дубли рёбер.
 */
class RotateToolHandler(
    private val snapService: SnapService,
    private val historyService: DocumentHistoryService,
    private val toolManager: ToolManager
) : Tool {

    /** 0 — idle, 1 — ждём референс (после якоря), 2 — крутим. */
    private var phase by mutableStateOf(0)

    /** Якорь поворота. */
    private var pivot by mutableStateOf<Point2?>(null)

    /** Референсная точка — нулевая отметка угла (конец первой протяжки). */
    private var reference: Point2? = null

    /** Текущая снапнутая позиция пальца. */
    private var currentWorld by mutableStateOf(Point2.Zero)

    /** Вид снапа пальца — для маркера. */
    private var lastSnapKind by mutableStateOf<SnapKind?>(null)

    private var lastZoom = 1.0

    /** Снимок settings.rotate.copyMode на момент старта фазы 2. */
    private var dragCopy = false

    // Черновик отложенного поворота (пока открыт ввод угла).
    private var draftAngleSign = 1
    private var draftEdgeIds: Set<UUID> = emptySet()
    private var draftCopy = false

    override var currentMeasurement by mutableStateOf("")
        private set

    override val previewMeasurements: List<PreviewMeasurement>
        get() = if (phase == 2) {
            listOf(PreviewMeasurement("θ", "${abs(Math.toDegrees(currentSignedAngleRad())).toInt()}°"))
        } else {
            emptyList()
        }

    override fun onStart(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastZoom = zoom

        when (phase) {
            0 -> {
                // Первое касание — ставим якорь (снап вершина → ребро → грань).
                val snapped = resolveObjectSnap(
                    document = document,
                    probe = point,
                    zoom = zoom,
                    excludeVertexIds = emptySet(),
                    excludeEdgeIds = emptySet()
                )
                pivot = snapped.position
                // currentWorld стартует в якоре: тогда «тап без протяжки» не примет
                // снапнутую позицию якоря за референс (см. проверку в onEnd фазы 1).
                currentWorld = snapped.position
                lastSnapKind = snapped.kind
                phase = 1
                currentMeasurement = toolManager.settings.promptDragForReference
            }

            1 -> {
                // Референс задаётся в onEnd протяжки; здесь только точка для превью линии.
                currentWorld = point
                lastSnapKind = null
            }

            else -> {
                // Фаза 2 — старт вращения (жест идёт после onEnd фазы 1).
                dragCopy = settings.rotate.copyMode
                currentWorld = point
            }
        }
    }

    override fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastZoom = zoom

        // Объектный снап пальца; исключаем выделенные рёбра — не снапимся на самого себя.
        val excludeEdgeIds = toolManager.selection.selectedEdgeIds.toSet()
        val excludeVertexIds = collectVertexIds(document, excludeEdgeIds)
        val snapped = resolveObjectSnap(
            document = document,
            probe = point,
            zoom = zoom,
            excludeVertexIds = excludeVertexIds,
            excludeEdgeIds = excludeEdgeIds
        )
        currentWorld = snapped.position
        lastSnapKind = snapped.kind

        when (phase) {
            1 -> currentMeasurement = toolManager.settings.promptDrag
            2 -> currentMeasurement = "θ: ${Math.toDegrees(currentAngleRad()).toInt()}°"
            else -> {}
        }
    }

    override fun onEnd(document: CadDocument) {
        when (phase) {
            1 -> {
                val p = pivot
                if (p == null) {
                    reset()
                    return
                }
                if (MathUtils.distance(currentWorld, p) < TopologyTolerance.ForMutation) {
                    // Протяжки не было — якорь остаётся, ждём следующего жеста для референса.
                    return
                }
                reference = currentWorld
                phase = 2
                currentMeasurement = toolManager.settings.promptDragToRotate
            }

            2 -> {
                val deg = Math.toDegrees(currentSignedAngleRad())
                if (abs(deg) < MIN_COMMIT_ANGLE_DEG) {
                    // Практически нулевой поворот — откат.
                    reset()
                    return
                }

                if (toolManager.settings.rotate.exactInputEnabled) {
                    // Откладываем коммит — открываем панель точного ввода угла.
                    draftAngleSign = if (deg < 0.0) -1 else 1
                    draftEdgeIds = toolManager.selection.selectedEdgeIds.toSet()
                    draftCopy = dragCopy
                    toolManager.requestInput(PendingInput.RotationAngle(abs(deg)))
                } else {
                    commitRotate(document, currentAngleRad(), dragCopy)
                    reset()
                }
            }

            else -> {}
        }
    }

    override fun onRotationAngleSubmitted(value: Double, document: CadDocument) {
        // Страховка: коммит-функция тоже отсекает 0 / 360 / > 360.
        if (value <= 0.0 || value >= 360.0) {
            clearDraft()
            reset()
            return
        }

        // Знак (направление) берём от направления протяжки; величину — из введённого значения.
        val signedRad = Math.toRadians(if (draftAngleSign < 0) -value else value)
        commitRotate(document, signedRad, draftCopy)
        clearDraft()
        reset()
    }

    override fun onRotationAngleCancelled(document: CadDocument) {
        // Откат — ничего не коммитим.
        clearDraft()
        reset()
    }

    /**
     * Поворот выделенных рёбер вокруг якоря на [angleRad]. В режиме [copy] оригиналы
     * не трогаются — добавляются повёрнутые дубли.
     */
    private fun commitRotate(document: CadDocument, angleRad: Double, copy: Boolean) {
        if (abs(angleRad) <= 1e-9) return
        val p = pivot ?: return

        // В точном вводе работаем со снимком выделения, иначе — с текущим.
        val edgeIds = if (draftEdgeIds.isNotEmpty()) draftEdgeIds else toolManager.selection.selectedEdgeIds.toSet()
        if (edgeIds.isEmpty()) return

        historyService.record(document)

        // Готовим повёрнутые сегменты заранее — до удаления оригиналов.
        val rotated = edgeIds.mapNotNull { edgeId ->
            val edge = document.edgeById(edgeId) ?: return@mapNotNull null
            val s = TopologyService.getEdgeStartPoint(document, edge)
            val e = TopologyService.getEdgeEndPoint(document, edge)
            Triple(edge.cloneGeometry(), rotatePoint(s, p, angleRad), rotatePoint(e, p, angleRad))
        }

        // Без «Копии» старые рёбра удаляем и подчищаем осиротевшие вершины.
        if (!copy) {
            for (edgeId in edgeIds) {
                document.edgeById(edgeId)?.let { document.removeEdge(it) }
            }
            TopologyService.pruneUnusedVertices(document)
        }

        for ((template, newStart, newEnd) in rotated) {
            EdgeOperations.addSegmentInternal(
                document, newStart, newEnd, template, TopologyTolerance.ForMutation
            )
        }

        PolygonBuilder.syncFaces(document)
        document.bumpRevision()
    }

    override fun drawPreview(scope: DrawScope, camera: Camera) {
        val p = pivot
        if (phase >= 1 && p != null) {
            // Линия от якоря к пальцу — тонкая, серая.
            scope.drawLine(
                color = toolManager.previewStrokeColor(Color.Gray),
                start = camera.worldToScreen(p),
                end = camera.worldToScreen(currentWorld),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )

            // Маркер якоря.
            val pivotScreen = camera.worldToScreen(p)
            scope.drawCircle(Color(0xFF1565C0), PIVOT_MARKER_PX, pivotScreen)
            scope.drawCircle(Color.Black, PIVOT_MARKER_PX + 2f, pivotScreen, style = Stroke(width = 2f))
        }

        // Превью повёрнутых рёбер выделения — в стиле исходного ребра.
        if (phase == 2 && p != null && reference != null) {
            val angleRad = currentAngleRad()
            val document = toolManager.document
            for (edgeId in toolManager.selection.selectedEdgeIds) {
                val edge = document.edgeById(edgeId) ?: continue
                val s = TopologyService.getEdgeStartPoint(document, edge)
                val e = TopologyService.getEdgeEndPoint(document, edge)
                drawPreviewEdge(scope, camera, edge, rotatePoint(s, p, angleRad), rotatePoint(e, p, angleRad))
            }
        }

        drawSnapMarker(scope, camera.worldToScreen(currentWorld), lastSnapKind)
    }

    override fun cancel() {
        reset()
    }

    // --- Геометрия поворота ---

    /** Поворот точки [p] вокруг [pivot] на [angleRad] (стандартный CCW в мировых осях). */
    private fun rotatePoint(p: Point2, pivot: Point2, angleRad: Double): Point2 {
        val c = cos(angleRad)
        val s = sin(angleRad)
        val dx = p.X - pivot.X
        val dy = p.Y - pivot.Y
        return Point2(pivot.X + dx * c - dy * s, pivot.Y + dx * s + dy * c)
    }

    /** Знаковый угол между векторами [v1] и [v2] через atan2(cross, dot), диапазон (-π, π]. */
    private fun signedAngle(v1: Point2, v2: Point2): Double =
        atan2(v1.X * v2.Y - v1.Y * v2.X, v1.X * v2.X + v1.Y * v2.Y)

    /** Знаковый угол поворота (рад) между (референс − якорь) и (палец − якорь). */
    private fun currentSignedAngleRad(): Double {
        val p = pivot ?: return 0.0
        val r = reference ?: return 0.0
        return signedAngle(
            Point2(r.X - p.X, r.Y - p.Y),
            Point2(currentWorld.X - p.X, currentWorld.Y - p.Y)
        )
    }

    /** Угол поворота (рад, [0, 2π)) с Ortho-снапом — для превью и измерения. */
    private fun currentAngleRad(): Double {
        val signed = currentSignedAngleRad()
        var norm = signed % TWO_PI
        if (norm < 0.0) norm += TWO_PI
        return snapRightAngleRad(norm)
    }

    /** Ortho-снап: если [angle] попадает в ±5° от 0 / 90 / 180 / 270 — вернуть ровное значение. */
    private fun snapRightAngleRad(angle: Double): Double {
        val tolerance = RIGHT_ANGLE_TOLERANCE_DEG * PI / 180.0
        val targets = doubleArrayOf(0.0, PI / 2.0, PI, 3.0 * PI / 2.0, TWO_PI)
        for (target in targets) {
            if (abs(angle - target) <= tolerance) {
                return if (target == TWO_PI) 0.0 else target
            }
        }
        return angle
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

    // --- Снап (логика скопирована из MoveToolHandler) ---

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
        val tolerance = ROTATE_SNAP_PX / zoom
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

    // --- Превью (дублируется из MoveToolHandler: приватные хелперы) ---

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

    private fun clearDraft() {
        draftAngleSign = 1
        draftEdgeIds = emptySet()
        draftCopy = false
    }

    private fun reset() {
        phase = 0
        pivot = null
        reference = null
        currentWorld = Point2.Zero
        lastSnapKind = null
        lastZoom = 1.0
        dragCopy = false
        currentMeasurement = ""
        clearDraft()
    }

    /** Результат снапа объекта: итоговая позиция и вид привязки (null — снапа нет). */
    private data class ObjectSnapResult(val position: Point2, val kind: SnapKind?)
}
