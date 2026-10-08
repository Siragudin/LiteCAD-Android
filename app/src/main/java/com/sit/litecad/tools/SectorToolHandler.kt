package com.sit.litecad.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sit.litecad.geometry.EdgeOperations
import com.sit.litecad.geometry.PolygonBuilder
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.CadEdge
import com.sit.litecad.services.DocumentHistoryService
import com.sit.litecad.services.SnapKind
import com.sit.litecad.services.SnapService
import com.sit.litecad.settings.ToolSettings
import com.sit.litecad.ui.canvas.Camera
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Радиус «вершины» на конце первого радиуса (в экранных пикселях, ≈12dp). */
private const val VERTEX_RADIUS_PX = 30f

/**
 * Инструмент «Сектор». UX из двух протяжек:
 *
 *  1. Первая протяжка задаёт центр и радиус — это первая (фиксированная) сторона сектора.
 *     По её завершению на конце радиуса появляется «вершина» (маленький кружок).
 *  2. Вторая протяжка начинается с этой вершины и задаёт угол сектора.
 *     Если «Точный» включён — открывается панель ввода угла; иначе коммит сразу.
 *
 * Углы считаются в мировых координатах (то же, что видит пользователь на экране),
 * поэтому дуга превью всегда идёт «в сторону пальца» и совпадает с тем, что будет
 * зафиксировано.
 */
class SectorToolHandler(
    private val snapService: SnapService,
    private val historyService: DocumentHistoryService,
    private val toolManager: ToolManager
) : Tool {

    /** Фазы взаимодействия. */
    private enum class Phase {
        IDLE,         // ничего не начато
        FIRST_DRAG,   // идёт 1-я протяжка (центр + радиус)
        RADIUS_SET,   // 1-я протяжка завершена; ждём 2-ю (от «вершины»)
        SECOND_DRAG   // идёт 2-я протяжка (угол)
    }

    private var phase by mutableStateOf(Phase.IDLE)

    private var center by mutableStateOf(Point2.Zero)
    private var initialRadiusPoint by mutableStateOf(Point2.Zero)
    private var currentAnglePoint by mutableStateOf(Point2.Zero)

    private var startSnapKind by mutableStateOf<SnapKind?>(null)
    private var radiusSnapKind by mutableStateOf<SnapKind?>(null)
    private var angleSnapKind by mutableStateOf<SnapKind?>(null)

    private var previewColor by mutableStateOf(Color.Gray)

    // Текущее превью угла (для 2-й протяжки / замороженного превью)
    private var previewSign by mutableStateOf(1)
    private var previewAngleAbs by mutableStateOf(0.0)

    private var lastSettings: ToolSettings? = null
    private var lastZoom = 0.0

    // Черновик незакоммиченного сектора (пока открыт ввод угла)
    private var draftSettings: ToolSettings? = null
    private var draftCenter: Point2? = null
    private var draftRadius = 0.0
    private var draftDirectionRad = 0.0
    private var draftSign = 1

    override var currentMeasurement by mutableStateOf("")
        private set

    override fun onStart(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastSettings = settings
        lastZoom = zoom

        when (phase) {
            Phase.IDLE -> {
                val resolved = resolveSnap(document, point, null, zoom)
                center = resolved
                initialRadiusPoint = resolved
                phase = Phase.FIRST_DRAG
                startSnapKind = querySnapKind(document, point, zoom)
                radiusSnapKind = startSnapKind
                angleSnapKind = null
                previewColor = Color.Gray
                currentMeasurement = "R: 0"
            }

            Phase.RADIUS_SET -> {
                // Вторая протяжка должна начаться с «вершины»; иначе — сброс.
                val handleTolerance = VERTEX_RADIUS_PX / zoom
                if (MathUtils.distance(point, initialRadiusPoint) <= handleTolerance) {
                    phase = Phase.SECOND_DRAG
                    currentAnglePoint = resolveSnap(document, point, center, zoom)
                    angleSnapKind = querySnapKind(document, point, zoom)
                    previewSign = 1
                    previewAngleAbs = 0.0
                    currentMeasurement = "Угол: 0°"
                } else {
                    reset()
                }
            }

            Phase.FIRST_DRAG, Phase.SECOND_DRAG -> {
                // Повторный onStart без onEnd не ожидается — игнорируем.
            }
        }
    }

    override fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastSettings = settings
        lastZoom = zoom

        when (phase) {
            Phase.FIRST_DRAG -> {
                val resolved = resolveSnap(document, point, center, zoom)
                initialRadiusPoint = resolved
                radiusSnapKind = querySnapKind(document, point, zoom)
                previewColor = if (radiusSnapKind != null) Color.Black else Color.Gray
                currentMeasurement = "R: ${MathUtils.distance(center, initialRadiusPoint).toInt()}"
            }

            Phase.SECOND_DRAG -> {
                val resolved = resolveSnap(document, point, center, zoom)
                currentAnglePoint = resolved
                angleSnapKind = querySnapKind(document, point, zoom)
                updatePreviewAngle()
                currentMeasurement = "Угол: ${previewAngleAbs.toInt()}°"
            }

            else -> {}
        }
    }

    override fun onEnd(document: CadDocument) {
        when (phase) {
            Phase.FIRST_DRAG -> {
                val radius = MathUtils.distance(center, initialRadiusPoint)
                if (radius <= TopologyTolerance.ForMutation) {
                    reset()
                } else {
                    phase = Phase.RADIUS_SET
                    currentMeasurement = "Тяните вершину, чтобы задать угол"
                }
            }

            Phase.SECOND_DRAG -> finishSecondDrag(document)

            else -> {}
        }
    }

    private fun finishSecondDrag(document: CadDocument) {
        updatePreviewAngle()

        if (previewAngleAbs < 1.0) {
            // Палец вернулся в 0 (угол < 1°) — остаёмся в RADIUS_SET, точный ввод не открываем.
            returnToRadiusSet()
            return
        }

        val s = lastSettings
        if (s == null) {
            reset()
            return
        }

        val radius = MathUtils.distance(center, initialRadiusPoint)
        val directionRad = atan2(initialRadiusPoint.Y - center.Y, initialRadiusPoint.X - center.X)

        if (s.exactInputEnabled) {
            // Ждём ввод угла.
            draftSettings = s
            draftCenter = center
            draftRadius = radius
            draftDirectionRad = directionRad
            draftSign = previewSign
            toolManager.requestInput(PendingInput.SectorAngle(previewAngleAbs))
            // Превью сектора остаётся видимым поверх панели.
            currentMeasurement = "Угол: ${previewAngleAbs.toInt()}°"
        } else {
            commitSector(document, center, radius, directionRad, previewSign, previewAngleAbs.roundToInt().coerceIn(1, 360), s)
            returnToRadiusSet()
        }
    }

    override fun onSectorAngleSubmitted(angle: Double, document: CadDocument) {
        val s = draftSettings ?: lastSettings
        val c = draftCenter
        if (s == null || c == null) {
            clearDraft()
            reset()
            return
        }

        val deg = angle.roundToInt()
        if (deg < 1) {
            // Угол < 1° — ничего не рисуем.
            clearDraft()
            reset()
            return
        }

        commitSector(document, c, draftRadius, draftDirectionRad, draftSign, deg.coerceAtMost(360), s)
        clearDraft()
        returnToRadiusSet()
    }

    override fun onSectorAngleCancelled(document: CadDocument) {
        // Отмена — ничего не рисуем, полный сброс.
        clearDraft()
        reset()
    }

    /** Считает знак и абсолютную величину угла между начальным и текущим радиусами (в мире). */
    private fun updatePreviewAngle() {
        val v0x = initialRadiusPoint.X - center.X
        val v0y = initialRadiusPoint.Y - center.Y
        val v1x = currentAnglePoint.X - center.X
        val v1y = currentAnglePoint.Y - center.Y

        val cross = v0x * v1y - v0y * v1x
        val dot = v0x * v1x + v0y * v1y
        val signed = atan2(cross, dot)

        previewSign = if (signed < 0.0) -1 else 1
        previewAngleAbs = Math.toDegrees(abs(signed))
    }

    /**
     * Фиксирует сектор.
     * [directionRad] — направление первой (фиксированной) стороны; дуга идёт [sign] * (шаг 10°).
     * Один шаг истории на всю фигуру; грани перестраиваются один раз в конце.
     */
    private fun commitSector(
        document: CadDocument,
        center: Point2,
        radius: Double,
        directionRad: Double,
        sign: Int,
        angleDeg: Int,
        settings: ToolSettings
    ) {
        if (radius <= TopologyTolerance.ForMutation || angleDeg < 1) return

        val mutTolerance = TopologyTolerance.ForMutation
        historyService.record(document)

        val template = CadEdge.createStyleTemplate()
        template.colorArgb = settings.color
        template.thickness = settings.thickness
        template.lineType = settings.lineType

        val arc = arcPoints(center, radius, directionRad, sign, angleDeg.toDouble())

        // Сегменты дуги.
        for (i in 0 until arc.size - 1) {
            EdgeOperations.addSegmentInternal(document, arc[i], arc[i + 1], template, mutTolerance)
        }

        // Два радиуса: центр → начало дуги и центр → конец дуги. Замыкают контур.
        EdgeOperations.addSegmentInternal(document, center, arc.first(), template, mutTolerance)
        EdgeOperations.addSegmentInternal(document, center, arc.last(), template, mutTolerance)

        PolygonBuilder.syncFaces(document)
    }

    /** Точки дуги: 0°, 10°, ..., остаток. Отсчёт — от [directionRad] в сторону [sign]. */
    private fun arcPoints(
        center: Point2,
        radius: Double,
        directionRad: Double,
        sign: Int,
        angleDeg: Double
    ): List<Point2> {
        val points = mutableListOf<Point2>()
        val steps = (angleDeg / 10.0).toInt()
        for (i in 0..steps) {
            val a = directionRad + sign * Math.toRadians((i * 10).toDouble())
            points.add(pointOnCircle(center, radius, a))
        }
        val remainder = angleDeg - steps * 10.0
        if (remainder > 1e-6) {
            val a = directionRad + sign * Math.toRadians(angleDeg)
            points.add(pointOnCircle(center, radius, a))
        }
        return points
    }

    private fun pointOnCircle(center: Point2, radius: Double, angleRad: Double): Point2 =
        Point2(center.X + radius * cos(angleRad), center.Y + radius * sin(angleRad))

    private fun resolveSnap(
        document: CadDocument,
        cursor: Point2,
        drawingStart: Point2?,
        zoom: Double
    ): Point2 {
        val tolerance = 60.0 / zoom
        val query = snapService.query(
            document = document,
            cursor = cursor,
            tolerance = tolerance,
            includeOnEdge = true
        )
        return snapService.resolveDrawingSnap(
            document = document,
            query = query,
            cursor = cursor,
            drawingStart = drawingStart,
            tolerance = tolerance,
            orthoEnabled = false
        )
    }

    private fun querySnapKind(document: CadDocument, cursor: Point2, zoom: Double): SnapKind? {
        val tolerance = 60.0 / zoom
        val query = snapService.query(
            document = document,
            cursor = cursor,
            tolerance = tolerance,
            includeOnEdge = true
        )
        return query.Best.Snap?.Kind
    }

    /** Возврат в ожидание второй протяжки (центр и радиус сохраняются). */
    private fun returnToRadiusSet() {
        phase = Phase.RADIUS_SET
        previewAngleAbs = 0.0
        previewSign = 1
        angleSnapKind = null
        currentMeasurement = "Тяните вершину, чтобы задать угол"
    }

    private fun clearDraft() {
        draftSettings = null
        draftCenter = null
        draftRadius = 0.0
        draftDirectionRad = 0.0
        draftSign = 1
    }

    private fun reset() {
        phase = Phase.IDLE
        center = Point2.Zero
        initialRadiusPoint = Point2.Zero
        currentAnglePoint = Point2.Zero
        startSnapKind = null
        radiusSnapKind = null
        angleSnapKind = null
        previewColor = Color.Gray
        previewSign = 1
        previewAngleAbs = 0.0
        currentMeasurement = ""
        lastSettings = null
        lastZoom = 0.0
        clearDraft()
    }

    override fun drawPreview(scope: DrawScope, camera: Camera) {
        if (phase == Phase.IDLE) return

        val centerOffset = camera.worldToScreen(center)
        val vertexOffset = camera.worldToScreen(initialRadiusPoint)

        // Радиальная линия — первая сторона сектора.
        val lineColor = if (phase == Phase.FIRST_DRAG) previewColor else Color.Black
        val isDashed = lineColor == Color.Gray
        scope.drawLine(
            color = lineColor,
            start = centerOffset,
            end = vertexOffset,
            strokeWidth = if (isDashed) 4f else 6f,
            pathEffect = if (isDashed) PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f) else null
        )

        // Превью сектора (дуга + вторая сторона).
        if (previewAngleAbs >= 1.0) {
            val radius = MathUtils.distance(center, initialRadiusPoint)
            val directionRad = atan2(initialRadiusPoint.Y - center.Y, initialRadiusPoint.X - center.X)
            val arc = arcPoints(center, radius, directionRad, previewSign, previewAngleAbs)
            for (i in 0 until arc.size - 1) {
                scope.drawLine(
                    color = Color.Black,
                    start = camera.worldToScreen(arc[i]),
                    end = camera.worldToScreen(arc[i + 1]),
                    strokeWidth = 6f
                )
            }
            if (arc.isNotEmpty()) {
                scope.drawLine(
                    color = Color.Black,
                    start = centerOffset,
                    end = camera.worldToScreen(arc.last()),
                    strokeWidth = 6f
                )
            }
        }

        // «Вершина» — кружок на конце первого радиуса.
        if (phase == Phase.RADIUS_SET || phase == Phase.SECOND_DRAG) {
            scope.drawCircle(Color.White, VERTEX_RADIUS_PX, vertexOffset)
            scope.drawCircle(Color.Black, VERTEX_RADIUS_PX, vertexOffset, style = Stroke(width = 3f))
        }

        // Маркеры снапа.
        drawSnapMarker(scope, centerOffset, startSnapKind)
        drawSnapMarker(scope, vertexOffset, radiusSnapKind)
        if (phase == Phase.SECOND_DRAG) {
            drawSnapMarker(scope, camera.worldToScreen(currentAnglePoint), angleSnapKind)
        }
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
