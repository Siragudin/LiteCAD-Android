package com.sit.litecad.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.magnifier
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.EdgeLineType
import com.sit.litecad.tools.ToolManager

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CadCanvas(
    viewport: Viewport,
    document: CadDocument,
    toolManager: ToolManager,
    isGridVisible: Boolean,
    modifier: Modifier = Modifier
) {
    // Локальные стейты для размеров холста и точной экранной позиции пальца (Для Лупы)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var cursorPosition by remember { mutableStateOf(Offset.Unspecified) }

    val isHandMode = toolManager.activeToolId == "hand"

    // --- ЗЕРКАЛЬНАЯ СТРУКТУРА ПЕРВОГО ПРОЕКТА (РОДНОЙ BOX) ---
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            // АППАРАТНАЯ СИСТЕМНАЯ ЛУПА ANDROID (Замена старого SmartMagnifier)
            .magnifier(
                sourceCenter = {
                    // Лупа берет картинку строго из-за под пальца, если мы чертим
                    if (!isHandMode && cursorPosition.isSpecified) cursorPosition else Offset.Unspecified
                },
                magnifierCenter = {
                    // Окошко лупы смещается на 60 пикселей выше пальца, чтобы его было видно
                    if (cursorPosition.isSpecified) cursorPosition - Offset(0f, 180f) else Offset.Unspecified
                },
                zoom = 2.0f, // Коэффициент увеличения CAD сцены

            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // СЛОЙ ЖЕСТОВ 1: Управление камерой чертежа (Viewport)
                .pointerInput(toolManager.activeToolId) {
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
                // СЛОЙ ЖЕСТОВ 2: Рисование инструментами + отслеживание курсора для Лупы
                .pointerInput(toolManager.activeToolId) {
                    if (!isHandMode) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                cursorPosition = offset
                                val worldPoint = viewport.screenToWorld(offset)
                                toolManager.handleStart(worldPoint)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                cursorPosition = change.position // Передаем позицию пальца в лупу на лету!
                                val worldPoint = viewport.screenToWorld(change.position)
                                toolManager.handleMove(worldPoint)
                            },
                            onDragEnd = {
                                cursorPosition = Offset.Unspecified // Гасим лупу, когда палец оторвали
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
            // --- 1. РЕНДЕРИНГ БЕСКОНЕЧНОЙ СЕТКИ ---
            if (isGridVisible) {
                val step = 50f * viewport.zoom.toFloat()
                if (step > 10f) {
                    val startX = (viewport.panX % step).toFloat()
                    val startY = (viewport.panY % step).toFloat()

                    var x = startX
                    while (x <= size.width) {
                        drawLine(Color(0xFFEEEEEE), Offset(x, 0f), Offset(x, size.height))
                        x += step
                    }

                    var y = startY
                    while (y <= size.height) {
                        drawLine(Color(0xFFEEEEEE), Offset(0f, y), Offset(size.width, y))
                        y += step
                    }
                }
            }

            // --- КАТЕГОРИЯ: ВНУТРИМАТРИЧНЫЙ РЕНДЕРИНГ (КАК НА ПК) ---
            withTransform({
                translate(viewport.panX.toFloat(), viewport.panY.toFloat())
                scale(viewport.zoom.toFloat(), viewport.zoom.toFloat(), pivot = Offset.Zero)
            }) {
                // --- 2. РЕНДЕРИНГ РЁБЕР ГРАФА ТОПОЛОГИИ ---
                document.edges.forEach { edge ->
                    val start = TopologyService.getEdgeStartPoint(document, edge)
                    val end = TopologyService.getEdgeEndPoint(document, edge)

                    val drawColor = if (edge.isAxis) Color(0xFF990000) else Color(edge.colorArgb)

                    val pathEffect = when (edge.lineType) {
                        EdgeLineType.DASHED -> PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
                        EdgeLineType.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(2f, 4f), 0f)
                        EdgeLineType.SOLID -> null
                    }

                    drawLine(
                        color = drawColor,
                        start = Offset(start.X.toFloat(), start.Y.toFloat()),
                        end = Offset(end.X.toFloat(), end.Y.toFloat()),
                        strokeWidth = edge.thickness.toFloat(),
                        cap = StrokeCap.Round,
                        pathEffect = pathEffect
                    )
                }

                // --- 3. РЕНДЕРИНГ МИРОВОГО НУЛЯ (ОСЕЙ CAD) ---
                val zero = Point2.Zero
                val axisSize = 100f
                drawLine(Color.Red, Offset(zero.X.toFloat(), zero.Y.toFloat()), Offset((zero.X + axisSize).toFloat(), zero.Y.toFloat()), strokeWidth = 2f)
                drawLine(Color.Green, Offset(zero.X.toFloat(), zero.Y.toFloat()), Offset(zero.X.toFloat(), (zero.Y + axisSize).toFloat()), strokeWidth = 2f)
            }

            // --- 4. РЕНДЕРИНГ ПРЕВЬЮ АКТИВНОГО ИНСТРУМЕНТА ---
            toolManager.activeTool?.drawPreview(this, viewport)
        }

        // --- СЛОЙ ОВЕРЛЕЯ ВСПЛЫВАЮЩЕЙ ПАНЕЛИ ИЗМЕРЕНИЙ ДЛИНЫ ---
        // Точная копия UI-логики из вашего первого проекта!
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
