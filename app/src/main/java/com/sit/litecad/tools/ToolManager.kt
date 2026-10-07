package com.sit.litecad.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.services.DocumentHistoryService
import com.sit.litecad.services.SnapService
import com.sit.litecad.settings.ToolSettings

class ToolManager(
    private val document: CadDocument,
    private val snapService: SnapService,
    private val historyService: DocumentHistoryService,
    val settings: ToolSettings
) {
    private val tools = mapOf<String, Tool>(
        "line" to LineToolHandler(snapService, historyService, this)
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
        if (pendingInput == null) return
        pendingInput = null
        activeTool?.onInputSubmitted(value, document)
    }

    fun cancelInput() {
        if (pendingInput == null) return
        pendingInput = null
        activeTool?.onInputCancelled(document)
    }

    val canUndo: Boolean
        get() = historyService.canUndo

    val canRedo: Boolean
        get() = historyService.canRedo

    fun undo() {
        historyService.undo(document)
        activeTool?.cancel()
        pendingInput = null
    }

    fun redo() {
        historyService.redo(document)
        activeTool?.cancel()
        pendingInput = null
    }
}