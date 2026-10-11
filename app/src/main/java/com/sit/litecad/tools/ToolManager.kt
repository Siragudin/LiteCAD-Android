package com.sit.litecad.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.Selection
import com.sit.litecad.services.DocumentHistoryService
import com.sit.litecad.services.EditService
import com.sit.litecad.services.SnapService
import com.sit.litecad.settings.ToolSettings

class ToolManager(
    val document: CadDocument,
    private val snapService: SnapService,
    private val historyService: DocumentHistoryService,
    val settings: ToolSettings,
    val selection: Selection
) {
    private val tools = mapOf<String, Tool>(
        "line" to LineToolHandler(snapService, historyService, this),
        "axis" to AxisToolHandler(snapService, historyService, this),
        "rect" to RectangleToolHandler(snapService, historyService, this),
        "circle" to CircleToolHandler(snapService, historyService, this),
        "sector" to SectorToolHandler(snapService, historyService, this),
        "select" to SelectionToolHandler(this),
        "erase" to EraserToolHandler(historyService),
        "move" to MoveToolHandler(snapService, historyService, this)
    )

    var activeToolId by mutableStateOf("hand")
        private set

    var pendingInput by mutableStateOf<PendingInput?>(null)
        private set

    val activeTool: Tool?
        get() = tools[activeToolId]

    fun selectTool(id: String) {
        activeTool?.cancel()
        pendingInput = null
        activeToolId = id
    }

    fun handleStart(worldPoint: Point2, zoom: Double) {
        activeTool?.onStart(worldPoint, document, zoom, settings)
    }

    fun handleMove(worldPoint: Point2, zoom: Double) {
        activeTool?.onMove(worldPoint, document, zoom, settings)
    }

    fun handleEnd() {
        activeTool?.onEnd(document)
    }

    fun requestInput(input: PendingInput) {
        pendingInput = input
    }

    fun submitInput(value: Double) {
        when (val pending = pendingInput) {
            null -> return
            is PendingInput.Length -> {
                pendingInput = null
                activeTool?.onInputSubmitted(value, document)
            }
            is PendingInput.RectangleSize -> {
                pendingInput = null
                activeTool?.onRectangleInputSubmitted(pending.width, pending.height, document)
            }
            is PendingInput.SectorAngle -> {
                pendingInput = null
                activeTool?.onSectorAngleSubmitted(value, document)
            }
            is PendingInput.MoveDistance -> {
                pendingInput = null
                activeTool?.onMoveInputSubmitted(value, document)
            }
        }
    }

    /** Отправка размеров прямоугольника (Ширина × Высота) из панели точного ввода. */
    fun submitRectangleInput(width: Double, height: Double) {
        if (pendingInput !is PendingInput.RectangleSize) return
        pendingInput = null
        activeTool?.onRectangleInputSubmitted(width, height, document)
    }

    /** Отправка угла сектора из панели точного ввода. */
    fun submitSectorAngle(angle: Double) {
        if (pendingInput !is PendingInput.SectorAngle) return
        pendingInput = null
        activeTool?.onSectorAngleSubmitted(angle, document)
    }

    fun cancelInput() {
        when (pendingInput) {
            null -> return
            is PendingInput.Length -> {
                pendingInput = null
                activeTool?.onInputCancelled(document)
            }
            is PendingInput.RectangleSize -> {
                pendingInput = null
                activeTool?.onRectangleInputCancelled(document)
            }
            is PendingInput.SectorAngle -> {
                pendingInput = null
                activeTool?.onSectorAngleCancelled(document)
            }
            is PendingInput.MoveDistance -> {
                pendingInput = null
                activeTool?.onMoveInputCancelled(document)
            }
        }
    }

    /** Отмена ввода размеров прямоугольника — коммит "как нарисовано". */
    fun cancelRectangleInput() {
        if (pendingInput !is PendingInput.RectangleSize) return
        pendingInput = null
        activeTool?.onRectangleInputCancelled(document)
    }

    /** Отмена ввода угла сектора — фигура не рисуется. */
    fun cancelSectorAngle() {
        if (pendingInput !is PendingInput.SectorAngle) return
        pendingInput = null
        activeTool?.onSectorAngleCancelled(document)
    }

    /** Отправка смещения перемещения из панели точного ввода. */
    fun submitMoveInput(value: Double) {
        if (pendingInput !is PendingInput.MoveDistance) return
        pendingInput = null
        activeTool?.onMoveInputSubmitted(value, document)
    }

    /** Отмена ввода смещения перемещения — ничего не коммитим. */
    fun cancelMoveInput() {
        if (pendingInput !is PendingInput.MoveDistance) return
        pendingInput = null
        activeTool?.onMoveInputCancelled(document)
    }

    val canUndo: Boolean
        get() = historyService.canUndo

    val canRedo: Boolean
        get() = historyService.canRedo

    fun undo() {
        historyService.undo(document)
        activeTool?.cancel()
        pendingInput = null
        pruneSelection()
    }

    fun redo() {
        historyService.redo(document)
        activeTool?.cancel()
        pendingInput = null
        pruneSelection()
    }

    private fun pruneSelection() {
        val edgeIds = document.edges.map { it.id }.toHashSet()
        val axisIds = document.axes.map { it.id }.toHashSet()
        val polygonIds = document.polygons.map { it.id }.toHashSet()
        selection.pruneMissing(edgeIds, axisIds, polygonIds)


    }
    fun deleteSelected() {
        EditService.delete(document, selection, historyService)
        pruneSelection()
        activeTool?.cancel()
        pendingInput = null
    }
}