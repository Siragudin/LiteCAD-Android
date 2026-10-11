package com.sit.litecad.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sit.litecad.geometry.DimensionGeometry
import com.sit.litecad.geometry.FaceIdentity
import com.sit.litecad.geometry.PolygonGeometry
import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.DimensionExtensionStyle
import com.sit.litecad.model.EdgeLineType
import com.sit.litecad.model.Polygon
import com.sit.litecad.model.PolygonType
import com.sit.litecad.tools.ToolManager
import com.sit.litecad.model.Selection
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Кэш ключей заливки граней.
 *
 * Ключ ([FaceIdentity]) зависит только от топологии грани, а PolygonBuilder пересоздаёт
 * FACE-полигоны при каждом изменении топологии (новый Id). Поэтому кэш по Id полигона
 * корректно инвалидируется и позволяет НЕ пересчитывать ключ на каждом кадре отрисовки.
 * Именно это вычисление (O(E²) по длине контура) и вешало перерисовку больших чертежей.
 */
private val faceFillKeyCache = HashMap<UUID, String>()

private fun cachedFaceFillKey(document: CadDocument, polygon: Polygon): String {
    val cached = faceFillKeyCache[polygon.id]
    if (cached != null) return cached
    val key = FaceIdentity.create(document, polygon)
    faceFillKeyCache[polygon.id] = key
    return key
}

/** Убирает из кэша ключи граней, которых больше нет в документе. */
private fun pruneFaceFillKeyCache(usedFaceIds: Set<UUID>) {
    if (faceFillKeyCache.size > usedFaceIds.size) {
        faceFillKeyCache.keys.retainAll(usedFaceIds)
    }
}

fun DrawScope.drawCadScene(
    camera: Camera,
    document: CadDocument,
    toolManager: ToolManager,
    isGridVisible: Boolean,
    textMeasurer: TextMeasurer
) {
    // --- СЕТКА ---
    if (isGridVisible) {
        val step = 50f * camera.zoom.toFloat()
        if (step > 10f) {
            val startX = (camera.panX % step).toFloat()
            val startY = (camera.panY % step).toFloat()

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


    // --- ГРАНИ (заливка, отладочный цвет) ---
    val usedFaceIds = HashSet<UUID>()
    document.polygons.forEach { polygon ->
        if (polygon.type != PolygonType.FACE) return@forEach
        usedFaceIds.add(polygon.id)

        val outerPoints = PolygonGeometry
            .getOuterBoundaryPoints(document, polygon)
            .map { camera.worldToScreen(it) }

        if (outerPoints.size < 3) return@forEach

        val path: androidx.compose.ui.graphics.Path = androidx.compose.ui.graphics.Path()
        path.moveTo(outerPoints[0].x, outerPoints[0].y)
        for (i in 1 until outerPoints.size) {
            path.lineTo(outerPoints[i].x, outerPoints[i].y)
        }
        path.close()
        path.fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd

        // Дырки
        for (hole in polygon.innerLoops) {
            val holePoints = PolygonGeometry
                .getBoundaryPoints(document, hole)
                .map { camera.worldToScreen(it) }
            if (holePoints.size < 3) continue
            path.moveTo(holePoints[0].x, holePoints[0].y)
            for (i in 1 until holePoints.size) {
                path.lineTo(holePoints[i].x, holePoints[i].y)
            }
            path.close()
        }

        val fillKey = cachedFaceFillKey(document, polygon)
        val fillStyle = document.faceFillStyles[fillKey] ?: com.sit.litecad.model.FaceFillStyle.Default
        val fillColor = Color(fillStyle.fillColor)

        drawPath(
            path = path,
            color = fillColor,
            style = Fill
        )
    }
    pruneFaceFillKeyCache(usedFaceIds)

    // --- РЁБРА (в экранных координатах, чтобы толщина не масштабировалась) ---
    document.edges.forEach { edge ->
        val start = TopologyService.getEdgeStartPoint(document, edge)
        val end = TopologyService.getEdgeEndPoint(document, edge)

        val p1 = camera.worldToScreen(start)
        val p2 = camera.worldToScreen(end)

        val drawColor = if (edge.isAxis) Color(0xFF990000) else Color(edge.colorArgb)

        val t = edge.thickness.toFloat()
        val pathEffect = when (edge.lineType) {
            EdgeLineType.DASHED -> PathEffect.dashPathEffect(floatArrayOf(8f * t, 4f * t), 0f)
            EdgeLineType.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(2f * t, 4f * t), 0f)
            EdgeLineType.SOLID -> null
        }

        drawLine(
            color = drawColor,
            start = p1,
            end = p2,
            strokeWidth = edge.thickness.toFloat(),
            cap = StrokeCap.Round,
            pathEffect = pathEffect
        )
    }

    // --- ОСИ (отдельная сущность, паттерн «точка-тире») ---
    document.axes.forEach { axis ->
        val p1 = camera.worldToScreen(axis.Start)
        val p2 = camera.worldToScreen(axis.End)
        val t = axis.thickness.toFloat()
        val effect = PathEffect.dashPathEffect(floatArrayOf(12f * t, 4f * t, 2f * t, 4f * t), 0f)
        drawLine(
            color = Color(axis.colorArgb),
            start = p1,
            end = p2,
            strokeWidth = t,
            cap = StrokeCap.Round,
            pathEffect = effect
        )
    }

    // --- РАЗМЕРЫ (без текста) ---
    document.dimensions.forEach { dim ->
        val first = document.vertexById(dim.FirstVertexId) ?: return@forEach
        val second = document.vertexById(dim.SecondVertexId) ?: return@forEach
        val layout = DimensionGeometry.tryCreateLayout(
            first.position, second.position, dim.Offset,
            TopologyTolerance.ForMutation,
            dim.IsOrthogonal, dim.OrthogonalIsHorizontal
        ) ?: return@forEach

        val color = Color(dim.colorArgb)
        val t = dim.thickness.toFloat()

        // Выносные линии: Full — от anchor, Short — 25% длины от конца.
        val firstExtStart = if (dim.ExtensionStyle == DimensionExtensionStyle.FULL) {
            layout.firstAnchor
        } else {
            val dx = layout.firstExtensionEnd.X - layout.firstAnchor.X
            val dy = layout.firstExtensionEnd.Y - layout.firstAnchor.Y
            Point2(layout.firstExtensionEnd.X - dx * 0.25, layout.firstExtensionEnd.Y - dy * 0.25)
        }
        val secondExtStart = if (dim.ExtensionStyle == DimensionExtensionStyle.FULL) {
            layout.secondAnchor
        } else {
            val dx = layout.secondExtensionEnd.X - layout.secondAnchor.X
            val dy = layout.secondExtensionEnd.Y - layout.secondAnchor.Y
            Point2(layout.secondExtensionEnd.X - dx * 0.25, layout.secondExtensionEnd.Y - dy * 0.25)
        }

        // Рисуем 3 линии: 2 выносные + размерная.
        drawLine(color, camera.worldToScreen(firstExtStart), camera.worldToScreen(layout.firstExtensionEnd), strokeWidth = t)
        drawLine(color, camera.worldToScreen(secondExtStart), camera.worldToScreen(layout.secondExtensionEnd), strokeWidth = t)
        drawLine(color, camera.worldToScreen(layout.dimensionLineStart), camera.worldToScreen(layout.dimensionLineEnd), strokeWidth = t * 1.5f)

        // Засечки (tick) под 45° к размерной линии, длина — фиксированная в экранных пикселях.
        val tickHalfPx = 10f
        val tickAngle = layout.textAngleRadians + PI / 4.0
        drawTick(camera, layout.firstExtensionEnd, tickAngle, tickHalfPx, color, t)
        drawTick(camera, layout.secondExtensionEnd, tickAngle, tickHalfPx, color, t)

        // --- ТЕКСТ РАЗМЕРА ---
        val midDim = Point2(
            (layout.dimensionLineStart.X + layout.dimensionLineEnd.X) / 2.0,
            (layout.dimensionLineStart.Y + layout.dimensionLineEnd.Y) / 2.0
        )
        val midScreen = camera.worldToScreen(midDim)
        val textValue = formatDimensionValue(layout.measuredDistance)
        val textStyle = TextStyle(
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )
        val textLayoutResult = textMeasurer.measure(text = textValue, style = textStyle)

        // Угол читаемости по ГОСТ: считаем в ЭКРАННЫХ координатах.
        // Вертикаль → всегда -90° (снизу вверх), горизонталь → 0°,
        // диагональ → atan2(dy,dx), нормализованный в [-90°, +90°].
        val sStart = camera.worldToScreen(layout.dimensionLineStart)
        val sEnd = camera.worldToScreen(layout.dimensionLineEnd)
        val sdx = (sEnd.x - sStart.x).toDouble()
        val sdy = (sEnd.y - sStart.y).toDouble()

        val angleDeg: Float = when {
            abs(sdy) < abs(sdx) * 0.01 -> 0f
            abs(sdx) < abs(sdy) * 0.01 -> -90f
            else -> {
                var a = Math.toDegrees(atan2(sdy, sdx)).toFloat()
                if (a > 90f || a <= -90f) a += 180f
                a
            }
        }

        // Нормаль в ЭКРАННЫХ координатах — от читаемого угла.
        // Горизонталь → вверх, вертикаль → влево, диагональ → вверх с наклоном.
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val perpNx = sin(angleRad).toFloat()
        val perpNy = -cos(angleRad).toFloat()
        val gapPx = 16f
        val textCenter = Offset(
            midScreen.x + perpNx * gapPx,
            midScreen.y + perpNy * gapPx
        )
        val topLeft = Offset(
            textCenter.x - textLayoutResult.size.width / 2f,
            textCenter.y - textLayoutResult.size.height / 2f
        )

        rotate(
            degrees = angleDeg,
            pivot = textCenter
        ) {
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = topLeft
            )
        }
    }

    // --- МИРОВОЙ НОЛЬ (оси) ---
    val zeroScreen = camera.worldToScreen(Point2.Zero)
    val axisSize = 100.0
    val xAxisEnd = camera.worldToScreen(Point2(axisSize, 0.0))
    val yAxisEnd = camera.worldToScreen(Point2(0.0, axisSize))

    drawLine(Color.Red, zeroScreen, xAxisEnd, strokeWidth = 2f)
    drawLine(Color.Green, zeroScreen, yAxisEnd, strokeWidth = 2f)

    // --- ВЫДЕЛЕНИЕ ---
    val selection = toolManager.selection
    val selectedStroke = Color(0xFF1976D2)   // синий
    val selectedFill = Color(0x551976D2)     // синий полупрозрачный
    val selectedEdgeStroke = 4f              // толщина в экранных пикселях

    // Грани — заливка
    document.polygons.forEach { polygon ->
        if (polygon.id !in selection.selectedPolygonIds) return@forEach
        if (polygon.outerLoop.edges.size < 3) return@forEach

        val outerPoints = PolygonGeometry
            .getOuterBoundaryPoints(document, polygon)
            .map { camera.worldToScreen(it) }
        if (outerPoints.size < 3) return@forEach

        val path = Path()
        path.moveTo(outerPoints[0].x, outerPoints[0].y)
        for (i in 1 until outerPoints.size) {
            path.lineTo(outerPoints[i].x, outerPoints[i].y)
        }
        path.close()
        path.fillType = PathFillType.EvenOdd

        for (hole in polygon.innerLoops) {
            val holePoints = PolygonGeometry.getBoundaryPoints(document, hole)
                .map { camera.worldToScreen(it) }
            if (holePoints.size < 3) continue
            path.moveTo(holePoints[0].x, holePoints[0].y)
            for (i in 1 until holePoints.size) {
                path.lineTo(holePoints[i].x, holePoints[i].y)
            }
            path.close()
        }

        drawPath(path, selectedFill, style = Fill)
    }

    // Рёбра — толстые синие линии поверх
    document.edges.forEach { edge ->
        if (edge.id !in selection.selectedEdgeIds) return@forEach
        val p1 = camera.worldToScreen(TopologyService.getEdgeStartPoint(document, edge))
        val p2 = camera.worldToScreen(TopologyService.getEdgeEndPoint(document, edge))
        drawLine(
            color = selectedStroke,
            start = p1,
            end = p2,
            strokeWidth = selectedEdgeStroke,
            cap = StrokeCap.Round
        )
    }

    // Оси — толстые синие линии поверх
    document.axes.forEach { axis ->
        if (axis.id !in selection.selectedAxisIds) return@forEach
        val p1 = camera.worldToScreen(axis.Start)
        val p2 = camera.worldToScreen(axis.End)
        drawLine(
            color = selectedStroke,
            start = p1, end = p2,
            strokeWidth = selectedEdgeStroke,
            cap = StrokeCap.Round
        )
    }

    // --- ПРЕВЬЮ АКТИВНОГО ИНСТРУМЕНТА ---
    toolManager.activeTool?.drawPreview(this, camera)
}

/** Значение размера для отображения: мм, 1 знак после запятой; целое — без десятых. */
private fun formatDimensionValue(value: Double): String {
    return if (abs(value - value.toLong().toDouble()) < 0.05) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}

/** Засечка размера: короткий отрезок в экранных пикселях под заданным углом. */
private fun DrawScope.drawTick(
    camera: Camera,
    centerWorld: Point2,
    angleRad: Double,
    halfLengthPx: Float,
    color: Color,
    strokeWidth: Float
) {
    val center = camera.worldToScreen(centerWorld)
    val dx = cos(angleRad).toFloat() * halfLengthPx
    val dy = sin(angleRad).toFloat() * halfLengthPx
    drawLine(
        color = color,
        start = Offset(center.x - dx, center.y - dy),
        end = Offset(center.x + dx, center.y + dy),
        strokeWidth = strokeWidth
    )
}