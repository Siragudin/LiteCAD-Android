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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Зона у края экрана (в пикселях), при удержании пальца в которой запускается автопрокрутка. */
private const val EDGE_SCROLL_ZONE_PX = 60f

/** Сколько миллисекунд палец должен непрерывно удерживаться в зоне края до старта скролла. */
private const val EDGE_SCROLL_DELAY_MS = 1000L

/** Скорость автопрокрутки холста — пикселей за кадр. */
private const val EDGE_SCROLL_SPEED_PX_PER_FRAME = 8f

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

    // Центрируем мировое начало (0,0) ровно один раз — при первом получении размера
    // канваса. При последующих изменениях размера (поворот экрана) не пересчитываем,
    // чтобы не сбивать панорамирование пользователя.
    var isCentered by remember { mutableStateOf(false) }

    // Автопрокрутка холста при протяжке у края экрана.
    var isDragging by remember { mutableStateOf(false) }
    var edgeScrollActive by remember { mutableStateOf(false) }

    val textMeasurer = rememberTextMeasurer()

    val isHandMode = toolManager.activeToolId == "hand"

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { newSize ->
                canvasSize = newSize
                if (!isCentered && newSize.width > 0 && newSize.height > 0) {
                    viewport.centerOn(newSize.width, newSize.height)
                    isCentered = true
                }
            }
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
                                isDragging = true
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
                                isDragging = false
                                edgeScrollActive = false
                                cursorPosition = Offset.Unspecified
                                onCursorWorldChanged(null)
                                toolManager.handleEnd()
                            },
                            onDragCancel = {
                                isDragging = false
                                edgeScrollActive = false
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

    // --- АВТОПРОКРУТКА ПРИ ПРОТЯЖКЕ У КРАЯ ЭКРАНА ---
    // Ключи: isDragging — старт/стоп вместе с жестом; activeToolId — при смене инструмента
    // старая корутина отменяется (isHandMode мог измениться).
    // В hand-режиме не работаем: там панорамирование есть из коробки.
    LaunchedEffect(isDragging, toolManager.activeToolId) {
        if (!isDragging || isHandMode) return@LaunchedEffect

        // Ждём, пока палец попадёт в зону края и удержится там EDGE_SCROLL_DELAY_MS.
        while (isActive) {
            val pos = cursorPosition
            if (!pos.isSpecified || canvasSize.width == 0 || canvasSize.height == 0) {
                edgeScrollActive = false
                delay(16)
                continue
            }

            // В какой стороне (или сторонах одновременно) палец в зоне.
            val nearLeft = pos.x <= EDGE_SCROLL_ZONE_PX
            val nearRight = pos.x >= canvasSize.width - EDGE_SCROLL_ZONE_PX
            val nearTop = pos.y <= EDGE_SCROLL_ZONE_PX
            val nearBottom = pos.y >= canvasSize.height - EDGE_SCROLL_ZONE_PX

            if (!nearLeft && !nearRight && !nearTop && !nearBottom) {
                edgeScrollActive = false
                delay(16)
                continue
            }

            // Палец в зоне — ждём задержку, отслеживая, что он не вышел и не отпущен.
            var heldFor = 0L
            var stillInZone = true
            while (heldFor < EDGE_SCROLL_DELAY_MS) {
                if (!isDragging) {
                    stillInZone = false
                    break
                }
                val p = cursorPosition
                if (!p.isSpecified) {
                    stillInZone = false
                    break
                }
                val nL = p.x <= EDGE_SCROLL_ZONE_PX
                val nR = p.x >= canvasSize.width - EDGE_SCROLL_ZONE_PX
                val nT = p.y <= EDGE_SCROLL_ZONE_PX
                val nB = p.y >= canvasSize.height - EDGE_SCROLL_ZONE_PX
                if (!nL && !nR && !nT && !nB) {
                    stillInZone = false
                    break
                }
                delay(16)
                heldFor += 16
            }

            // Вышел из зоны / отпустил раньше — таймер сбрасывается, скролл не начинаем.
            if (!stillInZone) {
                edgeScrollActive = false
                continue
            }

            // Задержка выдержана — начинаем скролл, пока палец в зоне и drag активен.
            edgeScrollActive = true

            while (isActive && isDragging) {
                val p = cursorPosition
                if (!p.isSpecified) break
                val nL = p.x <= EDGE_SCROLL_ZONE_PX
                val nR = p.x >= canvasSize.width - EDGE_SCROLL_ZONE_PX
                val nT = p.y <= EDGE_SCROLL_ZONE_PX
                val nB = p.y >= canvasSize.height - EDGE_SCROLL_ZONE_PX
                if (!nL && !nR && !nT && !nB) break

                var dx = 0f
                var dy = 0f
                if (nL) dx += EDGE_SCROLL_SPEED_PX_PER_FRAME
                if (nR) dx -= EDGE_SCROLL_SPEED_PX_PER_FRAME
                if (nT) dy += EDGE_SCROLL_SPEED_PX_PER_FRAME
                if (nB) dy -= EDGE_SCROLL_SPEED_PX_PER_FRAME

                viewport.addPan(Offset(dx, dy))

                // Палец на месте, мир сдвинулся — пересчитать мировую точку под пальцем
                // и переслать её в инструмент, иначе превью «зависнет» на старом месте.
                val world = viewport.screenToWorld(p)
                onCursorWorldChanged(world)
                toolManager.handleMove(world, viewport.zoom)

                delay(16)
            }

            edgeScrollActive = false
        }
    }
}