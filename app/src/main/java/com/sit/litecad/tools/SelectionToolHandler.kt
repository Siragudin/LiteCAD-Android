package com.sit.litecad.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.CadText
import com.sit.litecad.services.SelectionBounds
import com.sit.litecad.services.SelectionPickKind
import com.sit.litecad.services.SelectionPickOperations
import com.sit.litecad.settings.ToolSettings
import com.sit.litecad.ui.canvas.Camera
import com.sit.litecad.services.SelectionPickTolerance

class SelectionToolHandler(
    private val toolManager: ToolManager
) : Tool {

    private enum class DragMode { NONE, TAP, MARQUEE }

    private var isDragging by mutableStateOf(false)
    private var dragMode by mutableStateOf(DragMode.NONE)
    private var startWorld by mutableStateOf(Point2.Zero)
    private var currentWorld by mutableStateOf(Point2.Zero)

    // Экранные координаты начала/конца протяжки (вспомогательное состояние превью).
    private var startScreen by mutableStateOf(Offset.Zero)
    private var lastScreen by mutableStateOf(Offset.Zero)

    // zoom на момент нажатия — для перевода пиксельного допуска выделения в мировые единицы.
    private var zoomAtStart = 1.0

    override var currentMeasurement by mutableStateOf("")
        private set

    override fun onStart(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        isDragging = true
        dragMode = DragMode.TAP
        startWorld = point
        currentWorld = point
        startScreen = Offset.Zero
        lastScreen = Offset.Zero
        zoomAtStart = zoom
        currentMeasurement = ""
    }

    override fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        if (!isDragging) return

        currentWorld = point

        // Если onMove пришёл — значит пользователь явно тянет рамку.
        // Тап приходит напрямую onStart -> onEnd, минуя onMove (см. CadCanvas.detectTapGestures),
        // поэтому здесь всегда MARQUEE.
        dragMode = DragMode.MARQUEE
    }

    override fun onEnd(document: CadDocument) {
        if (!isDragging) {
            reset()
            return
        }

        val additive = toolManager.settings.select.selectionMultiMode

        when (dragMode) {
            DragMode.TAP -> applyTap(document, additive)
            DragMode.MARQUEE -> applyMarquee(document, additive)
            DragMode.NONE -> Unit
        }

        reset()
    }

    private fun applyTap(document: CadDocument, additive: Boolean) {
        val pickTolerance = SelectionPickTolerance.pixelsToWorld(24.0, zoomAtStart)
        val pick = SelectionPickOperations.pickAt(
            document = document,
            world = currentWorld,
            tolerance = pickTolerance,
            zoom = zoomAtStart,
            textPixelScale = toolManager.textPixelScale
        )

        val selection = toolManager.selection

        if (pick == null) {
            // Тап по пустому месту
            if (!additive) selection.clear()
            return
        }

        when (pick.kind) {
            SelectionPickKind.EDGE -> {
                val already = selection.selectedEdgeIds.contains(pick.id)
                if (additive && already) {
                    selection.removeEdge(pick.id)
                } else {
                    if (!additive) selection.clear()
                    selection.addEdge(pick.id)
                }
            }
            SelectionPickKind.AXIS -> {
                val already = selection.selectedAxisIds.contains(pick.id)
                if (additive && already) {
                    selection.removeAxis(pick.id)
                } else {
                    if (!additive) selection.clear()
                    selection.addAxis(pick.id)
                }
            }
            SelectionPickKind.POLYGON -> {
                val already = selection.selectedPolygonIds.contains(pick.id)
                if (additive && already) {
                    selection.removePolygon(pick.id)
                } else {
                    if (!additive) selection.clear()
                    selection.addPolygon(pick.id)
                }
            }
            SelectionPickKind.TEXT -> {
                val already = selection.selectedTextIds.contains(pick.id)
                if (additive && already) {
                    selection.removeText(pick.id)
                } else {
                    if (!additive) selection.clear()
                    selection.addText(pick.id)
                }
            }
            SelectionPickKind.DIMENSION -> {
                // Выделение размеров пока не поддерживается (отдельная задача).
            }
        }
    }

    private fun applyMarquee(document: CadDocument, additive: Boolean) {
        val selection = toolManager.selection
        if (!additive) selection.clear()

        // Режим зависит от направления протяжки по экрану.
        // Экранный X = мировой X * zoom + panX. Т.к. zoom > 0 и panX константа,
        // сравнение мировых X эквивалентно сравнению экранных.
        val isWindow = currentWorld.X >= startWorld.X

        val bounds = SelectionBounds.fromPoints(startWorld, currentWorld)

        if (isWindow) {
            // Window: только полностью внутри
            for (edge in document.edges) {
                val s = com.sit.litecad.geometry.TopologyService.getEdgeStartPoint(document, edge)
                val e = com.sit.litecad.geometry.TopologyService.getEdgeEndPoint(document, edge)
                if (bounds.containsSegmentFully(s, e)) selection.addEdge(edge.id)
            }
            for (axis in document.axes) {
                if (bounds.containsSegmentFully(axis.Start, axis.End)) selection.addAxis(axis.id)
            }
            for (polygon in document.polygons) {
                if (polygon.outerLoop.edges.size < 3) continue
                if (bounds.containsPolygonFully(document, polygon)) selection.addPolygon(polygon.id)
            }
            for (text in document.texts) {
                if (textFullyInside(text, bounds)) selection.addText(text.id)
            }
        } else {
            // Crossing: пересекает или внутри
            for (edge in document.edges) {
                val s = com.sit.litecad.geometry.TopologyService.getEdgeStartPoint(document, edge)
                val e = com.sit.litecad.geometry.TopologyService.getEdgeEndPoint(document, edge)
                if (bounds.intersectsSegment(s, e)) selection.addEdge(edge.id)
            }
            for (axis in document.axes) {
                if (bounds.intersectsSegment(axis.Start, axis.End)) selection.addAxis(axis.id)
            }
            for (polygon in document.polygons) {
                if (polygon.outerLoop.edges.size < 3) continue
                if (bounds.intersectsPolygon(document, polygon)) selection.addPolygon(polygon.id)
            }
            for (text in document.texts) {
                if (textIntersects(text, bounds)) selection.addText(text.id)
            }
        }
    }

    /**
     * Window-режим: надпись целиком внутри рамки.
     * Рамка надписи в мире параллельна осям (её размер — экранный, поэтому делится на zoom),
     * так что достаточно проверить два противоположных угла.
     */
    private fun textFullyInside(text: CadText, bounds: SelectionBounds): Boolean {
        val (halfWidth, halfHeight) = SelectionPickOperations.textWorldHalfExtents(
            text, zoomAtStart, toolManager.textPixelScale
        )
        return bounds.contains(Point2(text.position.X - halfWidth, text.position.Y - halfHeight)) &&
            bounds.contains(Point2(text.position.X + halfWidth, text.position.Y + halfHeight))
    }

    /** Crossing-режим: рамка надписи пересекает рамку выделения или лежит внутри неё. */
    private fun textIntersects(text: CadText, bounds: SelectionBounds): Boolean {
        val (halfWidth, halfHeight) = SelectionPickOperations.textWorldHalfExtents(
            text, zoomAtStart, toolManager.textPixelScale
        )
        val minX = text.position.X - halfWidth
        val maxX = text.position.X + halfWidth
        val minY = text.position.Y - halfHeight
        val maxY = text.position.Y + halfHeight
        return !(bounds.maxX < minX || bounds.minX > maxX ||
            bounds.maxY < minY || bounds.minY > maxY)
    }

    override fun drawPreview(scope: DrawScope, camera: Camera) {
        if (!isDragging || dragMode != DragMode.MARQUEE) return

        val p1 = camera.worldToScreen(startWorld)
        val p2 = camera.worldToScreen(currentWorld)

        val left = minOf(p1.x, p2.x)
        val top = minOf(p1.y, p2.y)
        val width = kotlin.math.abs(p2.x - p1.x)
        val height = kotlin.math.abs(p2.y - p1.y)

        val isWindow = currentWorld.X >= startWorld.X
        val strokeColor = if (isWindow) Color(0xFF1976D2) else Color(0xFF2E7D32)
        val fillColor = if (isWindow) Color(0x221976D2) else Color(0x222E7D32)

        scope.drawRect(
            color = fillColor,
            topLeft = Offset(left, top),
            size = Size(width, height)
        )
        scope.drawRect(
            color = strokeColor,
            topLeft = Offset(left, top),
            size = Size(width, height),
            style = Stroke(
                width = 2f,
                pathEffect = if (isWindow) null else PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            )
        )
    }

    override fun cancel() {
        reset()
    }

    private fun reset() {
        isDragging = false
        dragMode = DragMode.NONE
        startWorld = Point2.Zero
        currentWorld = Point2.Zero
        currentMeasurement = ""
    }
}