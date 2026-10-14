package com.sit.litecad.tools

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.sit.litecad.geometry.FaceIdentity
import com.sit.litecad.geometry.PolygonGeometry
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.FaceFillPattern
import com.sit.litecad.model.FaceFillStyle
import com.sit.litecad.model.Polygon
import com.sit.litecad.model.PolygonType
import com.sit.litecad.services.DocumentHistoryService
import com.sit.litecad.settings.ToolSettings
import com.sit.litecad.ui.canvas.Camera

/**
 * Инструмент «Заливка». Закрашивает выбранную грань (FACE) выбранным цветом.
 *
 * Жест — только тап. Тап по грани находит грани FACE под пальцем (минимальную по
 * площади, как в [com.sit.litecad.services.SelectionPickOperations.tryPickPolygon]) и
 * записывает стиль заливки в [CadDocument.faceFillStyles] по ключу [FaceIdentity].
 * Если цвет уже совпадает — ничего не делаем. Снятие заливки (удаление грани) — ластик.
 */
class FillToolHandler(
    private val historyService: DocumentHistoryService,
    private val toolManager: ToolManager
) : Tool {

    private var lastPoint = Point2.Zero
    private var hasStart = false

    override fun onStart(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastPoint = point
        hasStart = true
    }

    override fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        // Только тап — ничего не делаем.
    }

    override fun onEnd(document: CadDocument) {
        if (!hasStart) {
            reset()
            return
        }

        val targetColor = toolManager.settings.fill.color
        applyFill(document, lastPoint, targetColor)
        reset()
    }

    override fun drawPreview(scope: DrawScope, camera: Camera) { /* ничего */ }

    override fun cancel() { reset() }

    private fun reset() {
        lastPoint = Point2.Zero
        hasStart = false
    }

    /** Тап по грани: закрасить FACE выбранным цветом. */
    private fun applyFill(document: CadDocument, world: Point2, targetColor: Int) {
        // Находим грань (минимальную по площади, содержащую точку) — только FACE.
        val polygon = findFaceAt(document, world) ?: return

        val key = FaceIdentity.create(document, polygon)
        if (key.isEmpty()) return

        val currentStyle = document.faceFillStyles[key]
        // Если уже залито тем же цветом (и SOLID) — ничего не делаем.
        if (currentStyle != null &&
            currentStyle.fillColor == targetColor &&
            currentStyle.fillPattern == FaceFillPattern.SOLID
        ) {
            return
        }

        historyService.record(document)
        document.faceFillStyles[key] = FaceFillStyle(
            fillColor = targetColor,
            fillPattern = FaceFillPattern.SOLID
        )
        document.bumpRevision()
    }

    /** Грань FACE, содержащая точку: минимальная по площади (как в SelectionPickOperations.tryPickPolygon). */
    private fun findFaceAt(document: CadDocument, world: Point2): Polygon? {
        var best: Polygon? = null
        var bestArea = Double.MAX_VALUE
        for (polygon in document.polygons) {
            if (polygon.type != PolygonType.FACE) continue
            if (polygon.outerLoop.edges.size < 3) continue
            if (!PolygonGeometry.containsPoint(document, polygon, world)) continue
            val area = PolygonGeometry.getArea(document, polygon)
            if (area >= bestArea) continue
            best = polygon
            bestArea = area
        }
        return best
    }
}
