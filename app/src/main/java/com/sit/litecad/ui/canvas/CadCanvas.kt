package com.sit.litecad.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.tools.ToolManager

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CadCanvas(
    viewport: Viewport,
    document: CadDocument,
    toolManager: ToolManager,
    isGridVisible: Boolean,
    isOrthoEnabled: Boolean,
    isDarkTheme: Boolean,
    onCursorWorldChanged: (Point2?) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var cursorPosition by remember { mutableStateOf(Offset.Unspecified) }

    val textMeasurer = rememberTextMeasurer()

    val isHandMode = toolManager.activeToolId == "hand"

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
    ) {
        val revision = document.revision
        val selectionRevision = toolManager.selection.revision
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(toolManager.activeToolId, isOrthoEnabled) {
                    if (isHandMode) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldZoom = viewport.zoom
                            val newZoom = (oldZoom * zoom).coerceIn(viewport.minZoom, viewport.maxZoom)

                            val worldTargetX = (centroid.x - viewport.panX) / oldZoom
                            val worldTargetY = (viewport.panY - centroid.y) / oldZoom

                            viewport.zoom = newZoom
                            viewport.panX = centroid.x - (worldTargetX * newZoom) + pan.x
                            viewport.panY = centroid.y + (worldTargetY * newZoom) + pan.y
                        }
                    }
                }
                .pointerInput(toolManager.activeToolId) {
                    // Тап обрабатывается ОТДЕЛЬНО и идёт ПЕРЕД драгом: detectDragGestures
                    // не срабатывает на чистый тап (стартует только после touch slop).
                    if (!isHandMode) {
                        detectTapGestures { offset ->
                            val worldPoint = viewport.screenToWorld(offset)
                            toolManager.handleStart(worldPoint, viewport.zoom)
                            toolManager.handleEnd()
                        }
                    }
                }
                .pointerInput(toolManager.activeToolId) {
                    if (!isHandMode) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                cursorPosition = offset
                                val worldPoint = viewport.screenToWorld(offset)
                                onCursorWorldChanged(worldPoint)
                                toolManager.handleStart(worldPoint, viewport.zoom)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                cursorPosition = change.position
                                val worldPoint = viewport.screenToWorld(change.position)
                                onCursorWorldChanged(worldPoint)
                                toolManager.handleMove(worldPoint, viewport.zoom)
                            },
                            onDragEnd = {
                                cursorPosition = Offset.Unspecified
                                onCursorWorldChanged(null)
                                toolManager.handleEnd()
                            },
                            onDragCancel = {
                                cursorPosition = Offset.Unspecified
                                onCursorWorldChanged(null)
                                toolManager.activeTool?.cancel()
                            }
                        )
                    }
                }
        ) {
            @Suppress("UNUSED_EXPRESSION")
            revision
            @Suppress("UNUSED_EXPRESSION")
            selectionRevision
            drawCadScene(
                camera = viewport.toCamera(),
                document = document,
                toolManager = toolManager,
                isGridVisible = isGridVisible,
                isDarkTheme = isDarkTheme,
                textMeasurer = textMeasurer
            )
        }

        CadMagnifier(
            viewport = viewport,
            document = document,
            toolManager = toolManager,
            canvasSize = canvasSize,
            cursorPosition = cursorPosition,
            isGridVisible = isGridVisible,
            isDarkTheme = isDarkTheme,
            visible = !isHandMode && toolManager.activeToolId != "leader",
            textMeasurer = textMeasurer
        )

        val measurementText = toolManager.activeTool?.currentMeasurement ?: ""
        if (measurementText.isNotEmpty()) {

        }
    }
}