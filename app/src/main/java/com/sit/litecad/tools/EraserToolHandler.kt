package com.sit.litecad.tools

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.sit.litecad.geometry.PolygonBuilder
import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.PolygonType
import com.sit.litecad.services.DocumentHistoryService
import com.sit.litecad.services.SelectionPickKind
import com.sit.litecad.services.SelectionPickOperations
import com.sit.litecad.services.SelectionPickTolerance
import com.sit.litecad.settings.ToolSettings
import com.sit.litecad.ui.canvas.Camera
import java.util.UUID

/**
 * Инструмент «Ластик». Работает по тапу.
 *
 *  - Тап по ребру (в пределах допуска ~24px, как в «Выборе») — удаляет сегмент целиком
 *    и перестраивает грани.
 *  - Тап по грани (внутри, не на ребре) — подавляет заливку грани, рёбра не трогает.
 *  - Тап по пустому — ничего.
 *
 * Приоритет: ребро раньше грани (см. [SelectionPickOperations.pickAt]).
 *
 * [Tool.onEnd] не получает zoom, поэтому масштаб сохраняется локально в [onStart]
 * (тот же приём, что в [RectangleToolHandler]) — интерфейс [Tool] не меняется.
 */
class EraserToolHandler(
    private val historyService: DocumentHistoryService
) : Tool {

    /** Точка последнего тапа в мировых координатах. */
    private var lastPoint = Point2.Zero

    /** Сработал ли onStart — чтобы onEnd не срабатывал «на пустом». */
    private var hasStart = false

    /** Последний масштаб камеры: нужен для перевода пиксельного допуска в мировые единицы. */
    private var lastZoom = 1.0

    override fun onStart(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastPoint = point
        lastZoom = zoom
        hasStart = true
    }

    override fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        // Ластик работает по тапу — перемещение ничего не делает.
    }

    override fun onEnd(document: CadDocument) {
        if (!hasStart) {
            reset()
            return
        }

        val tolerance = SelectionPickTolerance.pixelsToWorld(SelectionPickTolerance.DEFAULT_PIXELS, lastZoom)
        val pick = SelectionPickOperations.pickAt(document, lastPoint, tolerance)

        if (pick != null) {
            when (pick.kind) {
                SelectionPickKind.DIMENSION -> eraseDimension(document, pick.id)
                SelectionPickKind.EDGE -> eraseEdge(document, pick.id)
                SelectionPickKind.AXIS -> eraseAxis(document, pick.id)
                SelectionPickKind.POLYGON -> suppressFace(document, pick.id)
            }
        }

        reset()
    }

    /** Удаление ребра целиком + перестроение граней. */
    private fun eraseEdge(document: CadDocument, edgeId: UUID) {
        val edge = document.edgeById(edgeId) ?: return

        historyService.record(document)

        document.removeEdge(edge)
        TopologyService.pruneUnusedVertices(document)
        // Перестроить грани. Подавления НЕ инвалидируем —
        // иначе потеряем подавленные ранее грани.
        PolygonBuilder.syncFaces(document)
        document.bumpRevision()
    }

    /** Удаление оси целиком. */
    private fun eraseAxis(document: CadDocument, axisId: UUID) {
        val axis = document.axisById(axisId) ?: return

        historyService.record(document)

        document.removeAxis(axis)
        // Оси не участвуют в топологии граней и не связаны с вершинами —
        // syncFaces и pruneUnusedVertices не нужны.
        document.bumpRevision()
    }

    /** Удаление размера целиком. */
    private fun eraseDimension(document: CadDocument, dimensionId: UUID) {
        val dimension = document.dimensionById(dimensionId) ?: return

        historyService.record(document)

        document.removeDimension(dimension)
        // Размер — аннотация, не участвует в топологии. rebuildIndexes не нужен
        // (removeDimension сам синхронизирует dimensionsById).
        document.bumpRevision()
    }

    /** Подавление заливки грани. Рёбра не трогаем. */
    private fun suppressFace(document: CadDocument, polygonId: UUID) {
        val polygon = document.polygons.firstOrNull { it.id == polygonId } ?: return
        // Подавляем только производные грани.
        if (polygon.type != PolygonType.FACE) return

        historyService.record(document)

        PolygonBuilder.suppressFaceGeometry(document, polygon)
        // Пересобираем грани, чтобы подавленная грань исчезла из документа (заливка пропала).
        PolygonBuilder.syncFaces(document)
        document.bumpRevision()
    }

    override fun drawPreview(scope: DrawScope, camera: Camera) {
        // Превью не требуется: ластик работает мгновенно по тапу.
    }

    override fun cancel() {
        reset()
    }

    private fun reset() {
        lastPoint = Point2.Zero
        hasStart = false
    }
}
