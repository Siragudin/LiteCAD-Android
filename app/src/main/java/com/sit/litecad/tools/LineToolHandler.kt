package com.sit.litecad.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sit.litecad.geometry.EdgeOperations
import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.math.Geometry2D
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.CadEdge
import com.sit.litecad.ui.canvas.Viewport
import kotlin.math.abs

enum class MobileSnapType { NONE, ENDPOINT, MIDPOINT, ON_EDGE }

class LineToolHandler : Tool {
    private var hasStart by mutableStateOf(false)
    private var startPoint by mutableStateOf(Point2.Zero)
    private var previewEnd by mutableStateOf(Point2.Zero)

    private var startSnapType by mutableStateOf(MobileSnapType.NONE)
    private var endSnapType by mutableStateOf(MobileSnapType.NONE)
    private var previewLineColor by mutableStateOf(Color.Gray)

    // Реализация свойства интерфейса для вывода длины линии на экран
    override var currentMeasurement by mutableStateOf("")
        private set

    override fun onStart(point: Point2, document: CadDocument) {
        val snapResult = getSnappedPoint(document, point, zoom = 1.0)

        if (!hasStart) {
            hasStart = true
            startPoint = snapResult.point
            previewEnd = snapResult.point
            startSnapType = snapResult.type
            currentMeasurement = "Начало отрезка" // Из вашего старого проекта
        } else {
            commitSegment(document, snapResult.point)
        }
    }

    override fun onMove(point: Point2, document: CadDocument) {
        if (!hasStart) return

        val isOrthoEnabled = false

        if (isOrthoEnabled) {
            val axisPoint = Geometry2D.applyOrtho(startPoint, point)
            previewEnd = axisPoint
            previewLineColor = if (abs(axisPoint.X - startPoint.X) > abs(axisPoint.Y - startPoint.Y)) Color.Red else Color.Green
            endSnapType = MobileSnapType.NONE
        } else {
            val snapResult = getSnappedPoint(document, point, zoom = 1.0)
            previewEnd = snapResult.point
            endSnapType = snapResult.type
            previewLineColor = if (snapResult.type != MobileSnapType.NONE) Color.Black else Color.Gray
        }

        // Обновляем текущую длину в реальном времени (Зеркально вашему первому проекту)
        val dist = MathUtils.distance(startPoint, previewEnd)
        currentMeasurement = "L: ${dist.toInt()}"
    }

    override fun onEnd(document: CadDocument) {}

    private fun commitSegment(document: CadDocument, endPoint: Point2) {
        if (MathUtils.arePointsEqual(startPoint, endPoint, TopologyTolerance.ForMutation)) return

        val mutTolerance = TopologyTolerance.ForMutation
        val alignment = Geometry2D.getOrthoAlignment(startPoint, endPoint, mutTolerance)

        val template = CadEdge.createStyleTemplate()
        template.colorArgb = 0xFF222222.toInt()
        template.thickness = 2.0
        template.orthoAlignment = alignment

        EdgeOperations.addSegment(document, startPoint, endPoint, template, mutTolerance)

        startPoint = endPoint
        previewEnd = endPoint
        startSnapType = endSnapType
        endSnapType = MobileSnapType.NONE
        currentMeasurement = "Выберите след. точку"
    }

    override fun drawPreview(scope: DrawScope, viewport: Viewport) {
        if (!hasStart) return

        val p1 = viewport.worldToScreen(startPoint)
        val p2 = viewport.worldToScreen(previewEnd)

        val isSolid = previewLineColor != Color.Gray
        scope.drawLine(
            color = previewLineColor,
            start = p1,
            end = p2,
            strokeWidth = if (isSolid) 6f else 4f,
            pathEffect = if (isSolid) null else PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
        )

        drawSnapMarker(scope, p1, startSnapType)
        drawSnapMarker(scope, p2, endSnapType)
    }

    private fun drawSnapMarker(scope: DrawScope, center: androidx.compose.ui.geometry.Offset, type: MobileSnapType) {
        if (type == MobileSnapType.NONE) return
        val color = when (type) {
            MobileSnapType.ENDPOINT -> Color.Green
            MobileSnapType.MIDPOINT -> Color.Cyan
            MobileSnapType.ON_EDGE -> Color.Red
            else -> Color.Gray
        }
        val radius = 14f
        scope.drawCircle(color, radius, center)
        scope.drawCircle(Color.Black, radius + 2f, center, style = Stroke(width = 3f))
    }

    private class SnapResult(val point: Point2, val type: MobileSnapType)

    private fun getSnappedPoint(document: CadDocument, worldPos: Point2, zoom: Double): SnapResult {
        val thresholdWorld = 40.0 / zoom

        var closestVertex: Point2? = null
        var minVertexDist = Double.MAX_VALUE
        for (vertex in document.vertices) {
            val vPoint = vertex.position
            val d = MathUtils.distance(vPoint, worldPos)
            if (d < thresholdWorld && d < minVertexDist) {
                minVertexDist = d
                closestVertex = vPoint
            }
        }
        if (closestVertex != null) return SnapResult(closestVertex, MobileSnapType.ENDPOINT)

        var bestPoint: Point2? = null
        var minEdgeDist = Double.MAX_VALUE
        var bestType = MobileSnapType.NONE

        for (edge in document.edges) {
            val edgeStart = TopologyService.getEdgeStartPoint(document, edge)
            val edgeEnd = TopologyService.getEdgeEndPoint(document, edge)

            val midPoint = Point2((edgeStart.X + edgeEnd.X) / 2.0, (edgeStart.Y + edgeEnd.Y) / 2.0)
            val distToMid = MathUtils.distance(midPoint, worldPos)
            if (distToMid < thresholdWorld && distToMid < minEdgeDist) {
                minEdgeDist = distToMid
                bestPoint = midPoint
                bestType = MobileSnapType.MIDPOINT
            }

            val projResult = Geometry2D.tryProjectPointOnSegment(worldPos, edgeStart, edgeEnd)
            if (projResult != null && projResult.distance < thresholdWorld && projResult.distance < minEdgeDist) {
                val bonus = if (bestType == MobileSnapType.MIDPOINT) 0.8 else 1.0
                if (projResult.distance < minEdgeDist * bonus) {
                    minEdgeDist = projResult.distance
                    bestPoint = projResult.projection
                    bestType = MobileSnapType.ON_EDGE
                }
            }
        }

        if (bestPoint != null) return SnapResult(bestPoint, bestType)
        return SnapResult(worldPos, MobileSnapType.NONE)
    }

    override fun cancel() {
        hasStart = false
        startPoint = Point2.Zero
        previewEnd = Point2.Zero
        startSnapType = MobileSnapType.NONE
        endSnapType = MobileSnapType.NONE
        previewLineColor = Color.Gray
        currentMeasurement = ""
    }
}
