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
import com.sit.litecad.math.Geometry2D
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
import kotlin.math.roundToLong

class LineToolHandler(
    private val snapService: SnapService,
    private val historyService: DocumentHistoryService,
    private val toolManager: ToolManager
) : Tool {

    private var hasStart by mutableStateOf(false)
    private var startPoint by mutableStateOf(Point2.Zero)
    private var previewEnd by mutableStateOf(Point2.Zero)

    private var startSnapKind by mutableStateOf<SnapKind?>(null)
    private var endSnapKind by mutableStateOf<SnapKind?>(null)

    private var previewLineColor by mutableStateOf(Color.Gray)

    private var lastSettings: ToolSettings? = null

    // Черновик незакоммиченного сегмента (пока открыт ввод длины)
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
        val resolved = resolveSnap(document, point, null, zoom, settings.line.orthoEnabled)
        hasStart = true
        startPoint = resolved
        previewEnd = resolved
        startSnapKind = querySnapKind(document, point, zoom)
        endSnapKind = startSnapKind
        previewLineColor = Color.Gray
        currentMeasurement = toolManager.settings.promptDragToEndpoint
    }

    override fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastSettings = settings
        if (!hasStart) return

        var resolved = resolveSnap(document, point, startPoint, zoom, settings.line.orthoEnabled)
        endSnapKind = querySnapKind(document, point, zoom)

        // Округление длины: не применяем, если палец примагнитился к геометрии.
        if (endSnapKind == null && settings.line.roundingMm > 0) {
            resolved = roundByStep(startPoint, resolved, settings.line.roundingMm.toDouble())
        }
        previewEnd = resolved

        previewLineColor = if (endSnapKind != null) Color.Black else Color.Gray

        val dist = MathUtils.distance(startPoint, previewEnd)
        currentMeasurement = "L: ${dist.toInt()}"
    }

    override fun onEnd(document: CadDocument) {
        if (!hasStart) return
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

        if (s.line.exactInputEnabled) {
            // ждём ввод длины
            draftSettings = s
            toolManager.requestInput(PendingInput.Length(dist))
        } else {
            // коммитим сразу
            commitSegment(document, previewEnd, s)
            reset()
        }
    }

    override fun onInputSubmitted(value: Double, document: CadDocument) {
        commitWithLength(value, document)
    }

    override fun onInputCancelled(document: CadDocument) {
        // Полная отмена — ничего не коммитим.
        draftSettings = null
        reset()
    }

    private fun commitWithLength(length: Double, document: CadDocument) {
        val s = draftSettings ?: lastSettings
        draftSettings = null

        if (s == null || !hasStart) {
            reset()
            return
        }

        val finalEnd = scaleEnd(startPoint, previewEnd, length)
        commitSegment(document, finalEnd, s)
        reset()
    }

    private fun scaleEnd(start: Point2, end: Point2, length: Double): Point2 {
        val d = MathUtils.distance(start, end)
        if (d <= TopologyTolerance.ForMutation) return end
        val k = length / d
        return Point2(start.X + (end.X - start.X) * k, start.Y + (end.Y - start.Y) * k)
    }

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

    private fun commitSegment(document: CadDocument, endPoint: Point2, settings: ToolSettings) {
        if (MathUtils.arePointsEqual(startPoint, endPoint, TopologyTolerance.ForMutation)) return
        historyService.record(document)

        val mutTolerance = TopologyTolerance.ForMutation
        val alignment = Geometry2D.getOrthoAlignment(startPoint, endPoint, mutTolerance)

        val template = CadEdge.createStyleTemplate()
        template.colorArgb = settings.line.color
        template.thickness = settings.line.thickness
        template.lineType = settings.line.lineType
        template.orthoAlignment = alignment

        EdgeOperations.addSegment(document, startPoint, endPoint, template, mutTolerance)
    }

    /** Тянет [end] вдоль направления [start]→[end] так, чтобы длина стала кратной [step] (мм). */
    private fun roundByStep(start: Point2, end: Point2, step: Double): Point2 {
        if (step <= 0.0) return end
        val length = MathUtils.distance(start, end)
        if (length <= TopologyTolerance.ForMutation) return end
        val rounded = (length / step).roundToLong() * step
        if (rounded <= TopologyTolerance.ForMutation) return end
        val k = rounded / length
        return Point2(start.X + (end.X - start.X) * k, start.Y + (end.Y - start.Y) * k)
    }

    private fun reset() {
        hasStart = false
        startPoint = Point2.Zero
        previewEnd = Point2.Zero
        startSnapKind = null
        endSnapKind = null
        previewLineColor = Color.Gray
        currentMeasurement = ""
        lastSettings = null
        draftSettings = null
    }

    override fun drawPreview(scope: DrawScope, camera: Camera) {
        if (!hasStart) return

        val p1 = camera.worldToScreen(startPoint)
        val p2 = camera.worldToScreen(previewEnd)

        val isSolid = previewLineColor != Color.Gray
        scope.drawLine(
            color = toolManager.previewStrokeColor(previewLineColor),
            start = p1,
            end = p2,
            strokeWidth = if (isSolid) 6f else 4f,
            pathEffect = if (isSolid) null else PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
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