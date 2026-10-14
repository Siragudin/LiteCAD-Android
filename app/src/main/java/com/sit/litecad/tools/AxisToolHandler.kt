package com.sit.litecad.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sit.litecad.geometry.AxisService
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import com.sit.litecad.services.DocumentHistoryService
import com.sit.litecad.services.SnapKind
import com.sit.litecad.services.SnapService
import com.sit.litecad.settings.ToolSettings
import com.sit.litecad.ui.canvas.Camera
import kotlin.math.abs

/**
 * Инструмент «Ось». Одна ось на две точки (не цепочка): после второй точки — коммит.
 * Ось — отдельная сущность ([com.sit.litecad.model.CadAxis]), не ребро.
 *
 * Ortho (по умолчанию включён) применяется от [startPoint]: сначала фиксируем ось,
 * затем ищем снап вдоль неё. Снап видит и оси, и рёбра (через [SnapService]).
 * Цвет/толщина всегда «точка-тире» и берутся из настроек инструмента.
 */
class AxisToolHandler(
    private val snapService: SnapService,
    private val historyService: DocumentHistoryService,
    private val toolManager: ToolManager
) : Tool {

    private var hasStart by mutableStateOf(false)
    private var startPoint by mutableStateOf(Point2.Zero)
    private var previewEnd by mutableStateOf(Point2.Zero)

    private var startSnapKind by mutableStateOf<SnapKind?>(null)
    private var endSnapKind by mutableStateOf<SnapKind?>(null)

    private var previewColor by mutableStateOf(Color.Gray)

    private var lastSettings: ToolSettings? = null

    // Черновик незакоммиченной оси (пока открыт ввод длины)
    private var draftSettings: ToolSettings? = null

    override var currentMeasurement by mutableStateOf("")
        private set

    override val previewMeasurements: List<PreviewMeasurement>
        get() = if (hasStart) {
            listOf(PreviewMeasurement("L", "${MathUtils.distance(startPoint, previewEnd).toInt()} мм"))
        } else {
            emptyList()
        }

    override fun onStart(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastSettings = settings
        val tolerance = 60.0 / zoom
        val query = snapService.query(document, point, tolerance, includeOnEdge = true)
        val resolved = query.Best.Snap?.Position ?: point
        hasStart = true
        startPoint = resolved
        previewEnd = resolved
        startSnapKind = query.Best.Snap?.Kind
        endSnapKind = startSnapKind
        previewColor = Color.Gray
        currentMeasurement = "Ведите до конечной точки"
    }

    override fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        if (!hasStart) return
        lastSettings = settings

        val (resolved, kind) = resolveSnapWithOrtho(document, point, startPoint, zoom, settings.axis.orthoEnabled)
        previewEnd = resolved
        endSnapKind = kind

        previewColor = if (kind != null) Color.Black else Color.Gray

        val dist = MathUtils.distance(startPoint, previewEnd)
        currentMeasurement = "L: ${dist.toInt()}"
    }

    /**
     * Снап с ortho от [start]: сначала выбираем ось (горизонтальная или вертикальная —
     * по доминирующей координате курсора), затем ловим снап вдоль неё.
     */
    private fun resolveSnapWithOrtho(
        document: CadDocument,
        cursor: Point2,
        start: Point2,
        zoom: Double,
        orthoEnabled: Boolean
    ): Pair<Point2, SnapKind?> {
        val tolerance = 60.0 / zoom
        val query = snapService.query(document, cursor, tolerance, includeOnEdge = true)
        val best = query.Best.Snap

        if (!orthoEnabled) {
            return (best?.Position ?: cursor) to best?.Kind
        }

        val dx = abs(cursor.X - start.X)
        val dy = abs(cursor.Y - start.Y)
        val probe = if (dx >= dy) {
            // Горизонтальная ось: Y фиксирован.
            Point2(best?.Position?.X ?: cursor.X, start.Y)
        } else {
            // Вертикальная ось: X фиксирован.
            Point2(start.X, best?.Position?.Y ?: cursor.Y)
        }
        return probe to best?.Kind
    }

    override fun onEnd(document: CadDocument) {
        if (!hasStart) {
            reset()
            return
        }
        val s = lastSettings
        if (s == null) {
            reset()
            return
        }

        val dist = MathUtils.distance(startPoint, previewEnd)
        if (dist <= TopologyTolerance.ForMutation) {
            reset()
            return
        }

        if (s.axis.exactInputEnabled) {
            // ждём ввод длины
            draftSettings = s
            toolManager.requestInput(PendingInput.Length(dist))
        } else {
            // коммитим сразу
            commitAxis(document, previewEnd, s)
            reset()
        }
    }

    override fun onInputSubmitted(value: Double, document: CadDocument) {
        val s = draftSettings ?: lastSettings
        draftSettings = null

        if (s == null || !hasStart) {
            reset()
            return
        }

        val finalEnd = scaleEnd(startPoint, previewEnd, value)
        commitAxis(document, finalEnd, s)
        reset()
    }

    override fun onInputCancelled(document: CadDocument) {
        val s = draftSettings ?: lastSettings
        draftSettings = null

        if (s == null || !hasStart) {
            reset()
            return
        }

        // «принять как нарисовано» — коммит с исходной длиной
        commitAxis(document, previewEnd, s)
        reset()
    }

    private fun scaleEnd(start: Point2, end: Point2, length: Double): Point2 {
        val d = MathUtils.distance(start, end)
        if (d <= TopologyTolerance.ForMutation) return end
        val k = length / d
        return Point2(start.X + (end.X - start.X) * k, start.Y + (end.Y - start.Y) * k)
    }

    private fun commitAxis(document: CadDocument, endPoint: Point2, settings: ToolSettings) {
        if (MathUtils.arePointsEqual(startPoint, endPoint, TopologyTolerance.ForMutation)) return
        historyService.record(document)

        val axis = AxisService.create(document, startPoint, endPoint, TopologyTolerance.ForMutation) ?: return
        // Цвет/толщина — из настроек. При слиянии коллинеарных ось-результат
        // тоже получает текущий цвет/толщину (перезаписываем).
        axis.colorArgb = settings.axis.color
        axis.thickness = settings.axis.thickness

        document.bumpRevision()
    }

    private fun reset() {
        hasStart = false
        startPoint = Point2.Zero
        previewEnd = Point2.Zero
        startSnapKind = null
        endSnapKind = null
        previewColor = Color.Gray
        currentMeasurement = ""
        lastSettings = null
        draftSettings = null
    }

    override fun drawPreview(scope: DrawScope, camera: Camera) {
        if (!hasStart) return

        val p1 = camera.worldToScreen(startPoint)
        val p2 = camera.worldToScreen(previewEnd)

        // Всегда «точка-тире» — как у закоммиченной оси.
        val isSnapped = endSnapKind != null
        val t = if (isSnapped) 6f else 4f
        val effect = PathEffect.dashPathEffect(floatArrayOf(12f * t, 4f * t, 2f * t, 4f * t), 0f)
        scope.drawLine(
            color = previewColor,
            start = p1,
            end = p2,
            strokeWidth = t,
            pathEffect = effect
        )

        drawSnapMarker(scope, p1, startSnapKind)
        drawSnapMarker(scope, p2, endSnapKind)
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
}
