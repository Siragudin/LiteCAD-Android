package com.sit.litecad.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
    isSnapEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var cursorPosition by remember { mutableStateOf(Offset.Unspecified) }

    val isHandMode = toolManager.activeToolId == "hand"

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(toolManager.activeToolId, isOrthoEnabled) {
                    if (isHandMode) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldZoom = viewport.zoom
                            val newZoom = (oldZoom * zoom).coerceIn(viewport.minZoom, viewport.maxZoom)

                            val worldTargetX = (centroid.x - viewport.panX) / oldZoom
                            val worldTargetY = (centroid.y - viewport.panY) / oldZoom

                            viewport.zoom = newZoom
                            viewport.panX = centroid.x - (worldTargetX * newZoom) + pan.x
                            viewport.panY = centroid.y - (worldTargetY * newZoom) + pan.y
                        }
                    }
                }
                .pointerInput(toolManager.activeToolId) {
                    if (!isHandMode) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                cursorPosition = offset
                                val worldPoint = viewport.screenToWorld(offset)
                                toolManager.handleStart(worldPoint, viewport.zoom, isOrthoEnabled)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                cursorPosition = change.position
                                val worldPoint = viewport.screenToWorld(change.position)
                                toolManager.handleMove(worldPoint, viewport.zoom, isOrthoEnabled)
                            },
                            onDragEnd = {
                                cursorPosition = Offset.Unspecified
                                toolManager.handleEnd()
                            },
                            onDragCancel = {
                                cursorPosition = Offset.Unspecified
                                toolManager.activeTool?.cancel()
                            }
                        )
                    }
                }
        ) {
            drawCadScene(
                camera = viewport.toCamera(),
                document = document,
                toolManager = toolManager,
                isGridVisible = isGridVisible
            )
        }

        CadMagnifier(
            viewport = viewport,
            document = document,
            toolManager = toolManager,
            canvasSize = canvasSize,
            cursorPosition = cursorPosition,
            isGridVisible = isGridVisible,
            visible = !isHandMode
        )

        val measurementText = toolManager.activeTool?.currentMeasurement ?: ""
        if (measurementText.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 16.dp, start = 16.dp),
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = measurementText,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}