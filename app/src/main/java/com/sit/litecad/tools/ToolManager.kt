package com.sit.litecad.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.services.SnapService

class ToolManager(
    private val document: CadDocument,
    private val snapService: SnapService
) {
    private val tools = mapOf<String, Tool>(
        "line" to LineToolHandler(snapService)
    )

    var activeToolId by mutableStateOf("hand")
        private set

    val activeTool: Tool?
        get() = tools[activeToolId]

    fun selectTool(id: String) {
        activeTool?.cancel()
        activeToolId = id
    }

    fun handleStart(worldPoint: Point2, zoom: Double, orthoEnabled: Boolean) {
        activeTool?.onStart(worldPoint, document, zoom, orthoEnabled)
    }

    fun handleMove(worldPoint: Point2, zoom: Double, orthoEnabled: Boolean) {
        activeTool?.onMove(worldPoint, document, zoom, orthoEnabled)
    }

    fun handleEnd() {
        activeTool?.onEnd(document)
    }
}