package com.sit.litecad.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.EdgeLineType
import com.sit.litecad.tools.ToolManager

fun DrawScope.drawCadScene(
    camera: Camera,
    document: CadDocument,
    toolManager: ToolManager,
    isGridVisible: Boolean
) {
    // --- СЕТКА ---
    if (isGridVisible) {
        val step = 50f * camera.zoom.toFloat()
        if (step > 10f) {
            val startX = (camera.panX % step).toFloat()
            val startY = (camera.panY % step).toFloat()

            var x = startX
            while (x <= size.width) {
                drawLine(Color(0xFFEEEEEE), Offset(x, 0f), Offset(x, size.height))
                x += step
            }

            var y = startY
            while (y <= size.height) {
                drawLine(Color(0xFFEEEEEE), Offset(0f, y), Offset(size.width, y))
                y += step
            }
        }
    }

    // --- РЁБРА (в экранных координатах, чтобы толщина не масштабировалась) ---
    document.edges.forEach { edge ->
        val start = TopologyService.getEdgeStartPoint(document, edge)
        val end = TopologyService.getEdgeEndPoint(document, edge)

        val p1 = camera.worldToScreen(start)
        val p2 = camera.worldToScreen(end)

        val drawColor = if (edge.isAxis) Color(0xFF990000) else Color(edge.colorArgb)

        val t = edge.thickness.toFloat()
        val pathEffect = when (edge.lineType) {
            EdgeLineType.DASHED -> PathEffect.dashPathEffect(floatArrayOf(8f * t, 4f * t), 0f)
            EdgeLineType.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(2f * t, 4f * t), 0f)
            EdgeLineType.SOLID -> null
        }

        drawLine(
            color = drawColor,
            start = p1,
            end = p2,
            strokeWidth = edge.thickness.toFloat(),
            cap = StrokeCap.Round,
            pathEffect = pathEffect
        )
    }

    // --- МИРОВОЙ НОЛЬ (оси) ---
    val zeroScreen = camera.worldToScreen(Point2.Zero)
    val axisSize = 100.0
    val xAxisEnd = camera.worldToScreen(Point2(axisSize, 0.0))
    val yAxisEnd = camera.worldToScreen(Point2(0.0, axisSize))

    drawLine(Color.Red, zeroScreen, xAxisEnd, strokeWidth = 2f)
    drawLine(Color.Green, zeroScreen, yAxisEnd, strokeWidth = 2f)

    // --- ПРЕВЬЮ АКТИВНОГО ИНСТРУМЕНТА ---
    toolManager.activeTool?.drawPreview(this, camera)
}

