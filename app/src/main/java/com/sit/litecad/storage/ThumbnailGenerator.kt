package com.sit.litecad.storage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.sit.litecad.geometry.DimensionGeometry
import com.sit.litecad.geometry.DimensionLayout
import com.sit.litecad.geometry.FaceIdentity
import com.sit.litecad.geometry.PolygonGeometry
import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.leaders.LeaderGeometry
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.DimensionExtensionStyle
import com.sit.litecad.model.EdgeLineType
import com.sit.litecad.model.PolygonType
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Перевод мировых координат (Y вверх) в пиксели превью (Y вниз). */
private class ThumbTransform(val scale: Double, val offsetX: Double, val offsetY: Double) {
    fun x(worldX: Double): Float = (worldX * scale + offsetX).toFloat()
    fun y(worldY: Double): Float = (-worldY * scale + offsetY).toFloat()
}

/**
 * Генерация превью чертежа для карточки проекта (640×640, белый фон).
 * Рисует те же слои, что и редактор в СВЕТЛОЙ теме: заливки граней (только явно
 * залитые), рёбра, оси, размеры (с текстом) и выноски (с текстом). Инверсии нет,
 * сетку, мировые оси, выделение и превью инструмента не рисуем.
 */
object ThumbnailGenerator {

    private const val SIZE = 640

    /** Padding вокруг содержимого в мировых единицах (как в PdfExportLayout). */
    private const val PADDING_MM = 2.0

    fun generate(document: CadDocument): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // --- Границы содержимого: вершины + оси + размеры + выноски ---
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        var hasAny = false

        fun expand(x: Double, y: Double) {
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            hasAny = true
        }

        for (v in document.vertices) expand(v.X, v.Y)
        for (axis in document.axes) {
            expand(axis.Start.X, axis.Start.Y)
            expand(axis.End.X, axis.End.Y)
        }
        for (dim in document.dimensions) {
            val first = document.vertexById(dim.FirstVertexId) ?: continue
            val second = document.vertexById(dim.SecondVertexId) ?: continue
            val layout = DimensionGeometry.tryCreateLayout(
                first.position, second.position, dim.Offset,
                TopologyTolerance.ForMutation,
                dim.IsOrthogonal, dim.OrthogonalIsHorizontal
            ) ?: continue
            expand(layout.firstAnchor.X, layout.firstAnchor.Y)
            expand(layout.secondAnchor.X, layout.secondAnchor.Y)
            expand(layout.firstExtensionEnd.X, layout.firstExtensionEnd.Y)
            expand(layout.secondExtensionEnd.X, layout.secondExtensionEnd.Y)
            expand(layout.dimensionLineStart.X, layout.dimensionLineStart.Y)
            expand(layout.dimensionLineEnd.X, layout.dimensionLineEnd.Y)
        }
        for (leader in document.leaders) {
            val layout = LeaderGeometry.createLayout(leader.target, leader.textPosition, 1.0)
            expand(layout.target.X, layout.target.Y)
            expand(layout.elbow.X, layout.elbow.Y)
            expand(layout.landingEnd.X, layout.landingEnd.Y)
            expand(layout.chevronLeft.X, layout.chevronLeft.Y)
            expand(layout.chevronRight.X, layout.chevronRight.Y)
        }

        if (!hasAny) return bitmap

        // Небольшой padding, чтобы линии не прилипали к краю.
        minX -= PADDING_MM
        minY -= PADDING_MM
        maxX += PADDING_MM
        maxY += PADDING_MM

        val width = maxX - minX
        val height = maxY - minY
        val maxDim = max(width, height)
        if (maxDim <= 0.0) return bitmap

        val scale = (SIZE * 0.8) / maxDim
        val offsetX = (SIZE - width * scale) / 2.0 - minX * scale
        val offsetY = (SIZE - height * scale) / 2.0 + maxY * scale
        val t = ThumbTransform(scale, offsetX, offsetY)

        // Порядок как в редакторе: заливки → рёбра → оси → размеры → выноски.
        drawFaceFills(canvas, document, t)
        drawEdges(canvas, document, t)
        drawAxes(canvas, document, t)
        drawDimensions(canvas, document, t)
        drawLeaders(canvas, document, t)

        return bitmap
    }

    // --- Заливки граней (только явно залитые), до рёбер ---
    private fun drawFaceFills(canvas: Canvas, document: CadDocument, t: ThumbTransform) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL

        for (polygon in document.polygons) {
            if (polygon.type != PolygonType.FACE) continue

            val key = FaceIdentity.create(document, polygon)
            if (key.isEmpty()) continue

            // Только явно залитые грани (фолбэк не рисуем — превью всегда светлое).
            val style = document.faceFillStyles[key] ?: continue

            val outerPoints = PolygonGeometry.getOuterBoundaryPoints(document, polygon)
            if (outerPoints.size < 3) continue

            val path = Path()
            path.fillType = Path.FillType.EVEN_ODD

            path.moveTo(t.x(outerPoints[0].X), t.y(outerPoints[0].Y))
            for (i in 1 until outerPoints.size) {
                path.lineTo(t.x(outerPoints[i].X), t.y(outerPoints[i].Y))
            }
            path.close()

            // Дырки — в тот же Path (EvenOdd вырежет их).
            for (hole in polygon.innerLoops) {
                val holePoints = PolygonGeometry.getBoundaryPoints(document, hole)
                if (holePoints.size < 3) continue
                path.moveTo(t.x(holePoints[0].X), t.y(holePoints[0].Y))
                for (i in 1 until holePoints.size) {
                    path.lineTo(t.x(holePoints[i].X), t.y(holePoints[i].Y))
                }
                path.close()
            }

            paint.color = style.fillColor
            canvas.drawPath(path, paint)
        }
    }

    // --- Рёбра (логика как была) ---
    private fun drawEdges(canvas: Canvas, document: CadDocument, t: ThumbTransform) {
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        for (edge in document.edges) {
            val start = TopologyService.getEdgeStartPoint(document, edge)
            val end = TopologyService.getEdgeEndPoint(document, edge)

            paint.color = if (edge.isAxis) Color.rgb(0x99, 0x00, 0x00) else edge.colorArgb
            paint.strokeWidth = edge.thickness.toFloat()
            paint.pathEffect = when (edge.lineType) {
                EdgeLineType.DASHED -> DashPathEffect(floatArrayOf(10f, 5f), 0f)
                EdgeLineType.DOTTED -> DashPathEffect(floatArrayOf(2f, 5f), 0f)
                EdgeLineType.SOLID -> null
            }

            canvas.drawLine(t.x(start.X), t.y(start.Y), t.x(end.X), t.y(end.Y), paint)
        }
    }

    // --- Оси (как в CadRenderer) ---
    private fun drawAxes(canvas: Canvas, document: CadDocument, t: ThumbTransform) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        for (axis in document.axes) {
            val thickness = axis.thickness.toFloat()
            paint.color = axis.colorArgb
            paint.strokeWidth = thickness
            // Штрих-пунктир «точка-тире», как в CadRenderer; шаг масштабируется от толщины.
            paint.pathEffect = DashPathEffect(
                floatArrayOf(12f * thickness, 4f * thickness, 2f * thickness, 4f * thickness), 0f
            )
            canvas.drawLine(t.x(axis.Start.X), t.y(axis.Start.Y), t.x(axis.End.X), t.y(axis.End.Y), paint)
        }
    }

    // --- Размеры (как в CadRenderer) + текст ---
    private fun drawDimensions(canvas: Canvas, document: CadDocument, t: ThumbTransform) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
            textSize = 12f
        }

        for (dim in document.dimensions) {
            val first = document.vertexById(dim.FirstVertexId) ?: continue
            val second = document.vertexById(dim.SecondVertexId) ?: continue
            val layout = DimensionGeometry.tryCreateLayout(
                first.position, second.position, dim.Offset,
                TopologyTolerance.ForMutation,
                dim.IsOrthogonal, dim.OrthogonalIsHorizontal
            ) ?: continue

            val thickness = dim.thickness.toFloat()
            linePaint.color = dim.colorArgb
            linePaint.strokeWidth = thickness
            textPaint.color = dim.colorArgb

            // Выносные линии: Full — от anchor, Short — 25% длины от конца (как в CadRenderer).
            val firstExtStart = if (dim.ExtensionStyle == DimensionExtensionStyle.FULL) {
                layout.firstAnchor
            } else {
                lerpBack(layout.firstAnchor, layout.firstExtensionEnd, 0.25)
            }
            val secondExtStart = if (dim.ExtensionStyle == DimensionExtensionStyle.FULL) {
                layout.secondAnchor
            } else {
                lerpBack(layout.secondAnchor, layout.secondExtensionEnd, 0.25)
            }

            // 3 линии: 2 выносные + размерная (размерная — толще в 1.5×).
            drawLine(canvas, t, linePaint, firstExtStart, layout.firstExtensionEnd, thickness)
            drawLine(canvas, t, linePaint, secondExtStart, layout.secondExtensionEnd, thickness)
            drawLine(canvas, t, linePaint, layout.dimensionLineStart, layout.dimensionLineEnd, thickness * 1.5f)

            // Засечки под 45°, длина — фиксированная 10 px (как в CadRenderer).
            val tickHalfPx = 10f
            val tickAngle = layout.textAngleRadians + PI / 4.0
            drawTick(canvas, t, layout.firstExtensionEnd, tickAngle, tickHalfPx, linePaint)
            drawTick(canvas, t, layout.secondExtensionEnd, tickAngle, tickHalfPx, linePaint)

            // Текст размера.
            drawDimensionText(canvas, t, layout, textPaint)
        }
    }

    private fun drawDimensionText(
        canvas: Canvas,
        t: ThumbTransform,
        layout: DimensionLayout,
        textPaint: Paint
    ) {
        val midX = (layout.dimensionLineStart.X + layout.dimensionLineEnd.X) / 2.0
        val midY = (layout.dimensionLineStart.Y + layout.dimensionLineEnd.Y) / 2.0
        val midScreenX = t.x(midX)
        val midScreenY = t.y(midY)

        // Угол читаемости по ГОСТ: считаем в пикселях превью (Y вниз), как в CadRenderer.
        val sdx = t.x(layout.dimensionLineEnd.X) - t.x(layout.dimensionLineStart.X)
        val sdy = t.y(layout.dimensionLineEnd.Y) - t.y(layout.dimensionLineStart.Y)

        val angleDeg: Float = when {
            abs(sdy) < abs(sdx) * 0.01f -> 0f
            abs(sdx) < abs(sdy) * 0.01f -> -90f
            else -> {
                var a = Math.toDegrees(atan2(sdy.toDouble(), sdx.toDouble())).toFloat()
                if (a > 90f || a <= -90f) a += 180f
                a
            }
        }

        // Нормаль от читаемого угла + сдвиг на 16 px (как в CadRenderer).
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val perpNx = sin(angleRad).toFloat()
        val perpNy = -cos(angleRad).toFloat()
        val gapPx = 16f
        val cx = midScreenX + perpNx * gapPx
        val cy = midScreenY + perpNy * gapPx

        val text = formatDimensionValue(layout.measuredDistance)
        canvas.save()
        canvas.rotate(angleDeg, cx, cy)
        // textAlign = CENTER; рисуем по базовой линии, по центру (как в PdfExporter).
        val metrics = textPaint.fontMetrics
        val baseY = cy - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, cx, baseY, textPaint)
        canvas.restore()
    }

    // --- Выноски (как в CadRenderer) + текст ---
    private fun drawLeaders(canvas: Canvas, document: CadDocument, t: ThumbTransform) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 2f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
            textSize = LeaderGeometry.TEXT_HEIGHT_SCREEN.toFloat()
        }

        for (leader in document.leaders) {
            val layout = LeaderGeometry.createLayout(leader.target, leader.textPosition, 1.0)
            paint.color = leader.colorArgb
            textPaint.color = leader.colorArgb

            for ((a, b) in LeaderGeometry.getSegments(layout)) {
                canvas.drawLine(t.x(a.X), t.y(a.Y), t.x(b.X), t.y(b.Y), paint)
            }

            // Текст по центру полки, чуть выше (как в CadRenderer).
            if (leader.text.isNotEmpty()) {
                val tx = t.x(layout.textPosition.X)
                val ty = t.y(layout.textPosition.Y)
                val metrics = textPaint.fontMetrics
                val baseY = ty - 2f - metrics.descent
                canvas.drawText(leader.text, tx, baseY, textPaint)
            }
        }
    }

    // --- Хелперы ---

    /** Отрезок a→b с явной толщиной (толщина кисти восстанавливается после). */
    private fun drawLine(
        canvas: Canvas,
        t: ThumbTransform,
        paint: Paint,
        a: Point2,
        b: Point2,
        width: Float
    ) {
        val old = paint.strokeWidth
        paint.strokeWidth = width
        canvas.drawLine(t.x(a.X), t.y(a.Y), t.x(b.X), t.y(b.Y), paint)
        paint.strokeWidth = old
    }

    /** Засечка размера: короткий отрезок под заданным углом (в пикселях превью). */
    private fun drawTick(
        canvas: Canvas,
        t: ThumbTransform,
        centerWorld: Point2,
        angleRad: Double,
        halfPx: Float,
        paint: Paint
    ) {
        val cx = t.x(centerWorld.X)
        val cy = t.y(centerWorld.Y)
        val dx = (cos(angleRad) * halfPx).toFloat()
        val dy = (sin(angleRad) * halfPx).toFloat()
        canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, paint)
    }

    /** Значение размера для отображения: мм, 1 знак после запятой; целое — без десятых. */
    private fun formatDimensionValue(value: Double): String =
        if (abs(value - value.toLong().toDouble()) < 0.05) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }

    /** Точка на отрезке [anchor]→[end], отступив от [end] на долю [t] (как в PdfExporter). */
    private fun lerpBack(anchor: Point2, end: Point2, t: Double): Point2 = Point2(
        end.X - (end.X - anchor.X) * t,
        end.Y - (end.Y - anchor.Y) * t
    )
}
