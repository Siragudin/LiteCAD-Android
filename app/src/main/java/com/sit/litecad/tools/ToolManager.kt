package com.sit.litecad.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument

class ToolManager(
    private val document: CadDocument
) {
    // РЕГИСТРАЦИЯ: Добавляем нашу новую, умную линию в карту диспетчера!
    private val tools = mapOf<String, Tool>(
        "line" to LineToolHandler()
    )

    var activeToolId by mutableStateOf("hand")
        private set

    val activeTool: Tool?
        get() = tools[activeToolId]

    fun selectTool(id: String) {
        activeTool?.cancel()
        activeToolId = id
    }

    fun handleStart(worldPoint: Point2) {
        activeTool?.onStart(worldPoint, document)
    }

    fun handleMove(worldPoint: Point2) {
        activeTool?.onMove(worldPoint, document)
    }

    fun handleEnd() {
        activeTool?.onEnd(document)
    }
}
