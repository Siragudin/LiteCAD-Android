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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Инструмент «Круг».
 * UX: тап/протяжка задаёт центр, протяжка — радиус, по отпусканию фиксируется круг.
 * Точный ввод: панель длины (радиус) — включается флагом «Точный».
 *
 * Круг строится из 36 сегментов по 10°. Каждый сегмент добавляется как обычное
 * ребро ([EdgeOperations.addSegmentInternal]); грани перестраиваются один раз в конце.
 */
class CircleToolHandler(
    private val snapService: SnapService,
    private val historyService: DocumentHistoryService,
    private val toolManager: ToolManager
) : Tool {

    private var hasStart by mutableStateOf(false)
    private var center by mutableStateOf(Point2.Zero)
    private var radiusPoint by mutableStateOf(Point2.Zero)

    private var startSnapKind by mutableStateOf<SnapKind?>(null)
    private var radiusSnapKind by mutableStateOf<SnapKind?>(null)

    private var previewColor by mutableStateOf(Color.Gray)

    private var lastSettings: ToolSettings? = null

    // Черновик незакоммиченного круга (пока открыт ввод радиуса)
    private var draftSettings: ToolSettings? = null

    override var currentMeasurement by mutableStateOf("")
        private set

    override fun onStart(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastSettings = settings
        val resolved = resolveSnap(document, point, null, zoom, false)
        hasStart = true
        center = resolved
        radiusPoint = resolved
        startSnapKind = querySnapKind(document, point, zoom)
        radiusSnapKind = startSnapKind
        previewColor = Color.Gray
        currentMeasurement = "Ведите до радиуса"
    }

    override fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastSettings = settings
        if (!hasStart) return

        val resolved = resolveSnap(document, point, center, zoom, false)
        radiusPoint = resolved
        radiusSnapKind = querySnapKind(document, point, zoom)

        previewColor = if (radiusSnapKind != null) Color.Black else Color.Gray

        val r = MathUtils.distance(center, radiusPoint)
        currentMeasurement = "R: ${r.toInt()}"
    }

    override fun onEnd(document: CadDocument) {
        if (!hasStart) return
        val s = lastSettings
        if (s == null) {
            reset()
            return
        }

        val radius = MathUtils.distance(center, radiusPoint)
        if (radius <= TopologyTolerance.ForMutation) {
            reset()
            return
        }

        if (s.circle.exactInputEnabled) {
            // ждём ввод радиуса
            draftSettings = s
            toolManager.requestInput(PendingInput.Length(radius))
        } else {
            // коммитим сразу
            commitCircle(document, center, radius, s)
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

        commitCircle(document, center, value, s)
        reset()
    }

    override fun onInputCancelled(document: CadDocument) {
        val s = draftSettings ?: lastSettings
        draftSettings = null

        if (s == null || !hasStart) {
            reset()
            return
        }

        // «принять как нарисовано» — коммит с исходным радиусом
        commitCircle(document, center, MathUtils.distance(center, radiusPoint), s)
        reset()
    }

    /**
     * Фиксирует круг радиусом [radius] вокруг [center].
     * Один шаг истории на весь круг; рёбра добавляются без промежуточного
     * перестроения граней — [PolygonBuilder.syncFaces] вызывается один раз в конце.
     */
    private fun commitCircle(document: CadDocument, center: Point2, radius: Double, settings: ToolSettings) {
        if (radius <= TopologyTolerance.ForMutation) return

        val mutTolerance = TopologyTolerance.ForMutation
        historyService.record(document)

        val template = CadEdge.createStyleTemplate()
        template.colorArgb = settings.circle.color
        template.thickness = settings.circle.thickness
        template.lineType = settings.circle.lineType

        val segments = 36
        val stepRad = 2.0 * PI / segments
        var prev = pointOnCircle(center, radius, 0.0)
        for (i in 1..segments) {
            val next = pointOnCircle(center, radius, stepRad * i)
            EdgeOperations.addSegmentInternal(document, prev, next, template, mutTolerance)
            prev = next
        }

        PolygonBuilder.syncFaces(document)
    }

    private fun pointOnCircle(center: Point2, radius: Double, angleRad: Double): Point2 =
        Point2(center.X + radius * cos(angleRad), center.Y + radius * sin(angleRad))

    private fun resolveSnap(
        document: CadDocument,
        cursor: Point2,
        drawingStart: Point2?,
        zoom: Double,
        orthoEnabled: Boolean
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
            orthoEnabled = orthoEnabled
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

    private fun reset() {
        hasStart = false
        center = Point2.Zero
        radiusPoint = Point2.Zero
        startSnapKind = null
        radiusSnapKind = null
        previewColor = Color.Gray
        currentMeasurement = ""
        lastSettings = null
        draftSettings = null
    }

    override fun drawPreview(scope: DrawScope, camera: Camera) {
        if (!hasStart) return

        val p1 = camera.worldToScreen(center)
        val p2 = camera.worldToScreen(radiusPoint)

        val isSolid = previewColor != Color.Gray
        val stroke = if (isSolid) 6f else 4f
        val effect = if (isSolid) null else PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)

        // «Резиновая» линия радиуса (центр → текущий радиус).
        scope.drawLine(
            color = previewColor,
            start = p1,
            end = p2,
            strokeWidth = stroke,
            pathEffect = effect
        )

        // Превью круга — 36 сегментов по 10° (как в commitCircle).
        val radius = MathUtils.distance(center, radiusPoint)
        if (radius > TopologyTolerance.ForMutation) {
            val segments = 36
            val stepRad = 2.0 * PI / segments
            var prev = camera.worldToScreen(pointOnCircle(center, radius, 0.0))
            for (i in 1..segments) {
                val next = camera.worldToScreen(pointOnCircle(center, radius, stepRad * i))
                scope.drawLine(
                    color = previewColor,
                    start = prev,
                    end = next,
                    strokeWidth = stroke,
                    pathEffect = effect
                )
                prev = next
            }
        }

        drawSnapMarker(scope, p1, startSnapKind)
        drawSnapMarker(scope, p2, radiusSnapKind)
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
