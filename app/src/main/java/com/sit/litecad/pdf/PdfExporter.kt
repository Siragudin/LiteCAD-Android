package com.sit.litecad.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PathEffect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sit.litecad.geometry.DimensionGeometry
import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.leaders.LeaderGeometry
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.DimensionExtensionStyle
import com.sit.litecad.model.EdgeLineType
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Экспорт чертежа в PDF через android.graphics.pdf.PdfDocument + Canvas.
 * Работает в pt (72 pt/inch), координаты — из PdfExportLayout (в мм → pt).
 * Файл сохраняется в папку Downloads.
 */
object PdfExporter {

    private const val POINTS_PER_INCH = 72.0
    private const val MM_PER_INCH = 25.4

    /** Размер текста размеров/выносок в мм (ГОСТ 2.5 мм). */
    private const val ANNOTATION_TEXT_MM = 2.5

    /**
     * Экспортировать документ в PDF и сохранить в Downloads.
     * @return имя сохранённого файла (без пути) или null при ошибке.
     */
    fun exportToDownloads(
        context: Context,
        document: CadDocument,
        projectName: String
    ): String? {
        val fileName = sanitizeFileName(projectName) + ".pdf"
        val layout = PdfExportLayout.create(document)

        // Считаем размеры страницы в pt.
        val widthPt = (layout.pageSpec.pageWidthMm * POINTS_PER_INCH / MM_PER_INCH).toInt()
        val heightPt = (layout.pageSpec.pageHeightMm * POINTS_PER_INCH / MM_PER_INCH).toInt()

        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(widthPt, heightPt, 1).create()
        val page = pdf.startPage(pageInfo)
        try {
            drawPage(page.canvas, document, layout)
            pdf.finishPage(page)
        } catch (t: Throwable) {
            pdf.finishPage(page)
            pdf.close()
            throw t
        }

        val saved = writeToDownloads(context, pdf, fileName)
        pdf.close()
        return if (saved) fileName else null
    }

    private fun drawPage(canvas: Canvas, document: CadDocument, layout: PdfExportLayout) {
        canvas.drawColor(Color.WHITE)
        drawEdges(canvas, document, layout)
        drawAxes(canvas, document, layout)
        drawDimensions(canvas, document, layout)
        drawLeaders(canvas, document, layout)
    }

    // --- Рёбра ---

    private fun drawEdges(canvas: Canvas, document: CadDocument, layout: PdfExportLayout) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        for (edge in document.edges) {
            val style = when {
                edge.isAxis -> PenStyle.AXIS_EDGE
                edge.lineType == EdgeLineType.DASHED -> PenStyle.EDGE_DASHED
                edge.lineType == EdgeLineType.DOTTED -> PenStyle.EDGE_DOTTED
                else -> PenStyle.EDGE
            }
            paint.color = edge.colorArgb
            paint.strokeWidth = PdfLineweightTable.toEffectivePoints(style).toFloat()
            paint.pathEffect = edgePathEffect(edge.lineType, paint.strokeWidth)

            val start = TopologyService.getEdgeStartPoint(document, edge)
            val end = TopologyService.getEdgeEndPoint(document, edge)
            val p1 = toPagePt(layout, start)
            val p2 = toPagePt(layout, end)
            canvas.drawLine(p1[0], p1[1], p2[0], p2[1], paint)
        }
    }

    // --- Оси ---

    private fun drawAxes(canvas: Canvas, document: CadDocument, layout: PdfExportLayout) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.pathEffect = null

        for (axis in document.axes) {
            paint.color = axis.colorArgb
            paint.strokeWidth = PdfLineweightTable.toEffectivePoints(PenStyle.AXIS).toFloat()
            val p1 = toPagePt(layout, axis.Start)
            val p2 = toPagePt(layout, axis.End)
            canvas.drawLine(p1[0], p1[1], p2[0], p2[1], paint)
        }
    }

    // --- Размеры ---

    private fun drawDimensions(canvas: Canvas, document: CadDocument, layout: PdfExportLayout) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = PdfLineweightTable.toEffectivePoints(PenStyle.DIMENSION).toFloat()
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
            textSize = PdfLineweightTable.mmToPoints(ANNOTATION_TEXT_MM).toFloat()
        }

        for (dim in document.dimensions) {
            val first = document.vertexById(dim.FirstVertexId) ?: continue
            val second = document.vertexById(dim.SecondVertexId) ?: continue
            val dl = DimensionGeometry.tryCreateLayout(
                first.position, second.position, dim.Offset,
                TopologyTolerance.ForMutation,
                dim.IsOrthogonal, dim.OrthogonalIsHorizontal
            ) ?: continue

            linePaint.color = dim.colorArgb
            textPaint.color = dim.colorArgb

            // Выносные линии.
            val firstExtStart = if (dim.ExtensionStyle == DimensionExtensionStyle.FULL) dl.firstAnchor
            else lerpBack(dl.firstAnchor, dl.firstExtensionEnd, 0.25)
            val secondExtStart = if (dim.ExtensionStyle == DimensionExtensionStyle.FULL) dl.secondAnchor
            else lerpBack(dl.secondAnchor, dl.secondExtensionEnd, 0.25)

            drawLineWorld(canvas, layout, linePaint, firstExtStart, dl.firstExtensionEnd)
            drawLineWorld(canvas, layout, linePaint, secondExtStart, dl.secondExtensionEnd)
            drawLineWorld(canvas, layout, linePaint, dl.dimensionLineStart, dl.dimensionLineEnd, widthMultiplier = 1.5f)

            // Засечки под 45° (как в редакторе).
            val tickHalfPt = 10f
            val tickAngleRad = dl.textAngleRadians + Math.PI / 4.0
            drawTickPt(canvas, layout, dl.firstExtensionEnd, tickAngleRad, tickHalfPt, linePaint)
            drawTickPt(canvas, layout, dl.secondExtensionEnd, tickAngleRad, tickHalfPt, linePaint)

            // Текст над размерной линией.
            drawDimensionText(canvas, layout, dl, textPaint)
        }
    }

    private fun drawDimensionText(
        canvas: Canvas,
        layout: PdfExportLayout,
        dl: com.sit.litecad.geometry.DimensionLayout,
        textPaint: Paint
    ) {
        val mid = Point2(
            (dl.dimensionLineStart.X + dl.dimensionLineEnd.X) / 2.0,
            (dl.dimensionLineStart.Y + dl.dimensionLineEnd.Y) / 2.0
        )
        // Сдвиг текста по нормали на 6 pt.
        val midPt = toPagePt(layout, mid)
        val startPt = toPagePt(layout, dl.dimensionLineStart)
        val endPt = toPagePt(layout, dl.dimensionLineEnd)
        val sdx = endPt[0] - startPt[0]
        val sdy = endPt[1] - startPt[1]
        val len = kotlin.math.sqrt(sdx * sdx + sdy * sdy)
        var nx = 0f
        var ny = 0f
        if (len > 1e-3f) {
            nx = -sdy / len
            ny = sdx / len
            if (ny > 0f) {
                nx = -nx
                ny = -ny
            }
        }
        val gapPt = 6f
        val cx = midPt[0] + nx * gapPt
        val cy = midPt[1] + ny * gapPt

        // Угол текста по ГОСТ-читаемости (как в CadRenderer).
        val angleDeg: Float = when {
            abs(sdy) < abs(sdx) * 0.01f -> 0f
            abs(sdx) < abs(sdy) * 0.01f -> -90f
            else -> {
                var a = Math.toDegrees(atan2(sdy.toDouble(), sdx.toDouble())).toFloat()
                if (a > 90f || a <= -90f) a += 180f
                a
            }
        }

        val text = formatDimensionValue(dl.measuredDistance)
        canvas.save()
        canvas.rotate(angleDeg, cx, cy)
        // textAlign = CENTER; рисуем по базовой линии, чуть выше центра.
        val metrics = textPaint.fontMetrics
        val baseY = cy - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, cx, baseY, textPaint)
        canvas.restore()
    }

    private fun formatDimensionValue(value: Double): String =
        if (abs(value - value.toLong().toDouble()) < 0.05) value.toLong().toString()
        else String.format(java.util.Locale.US, "%.1f", value)

    private fun drawTickPt(
        canvas: Canvas,
        layout: PdfExportLayout,
        centerWorld: Point2,
        angleRad: Double,
        halfLengthPt: Float,
        paint: Paint
    ) {
        val c = toPagePt(layout, centerWorld)
        val dx = (cos(angleRad) * halfLengthPt).toFloat()
        val dy = (sin(angleRad) * halfLengthPt).toFloat()
        canvas.drawLine(c[0] - dx, c[1] - dy, c[0] + dx, c[1] + dy, paint)
    }

    // --- Выноски ---

    private fun drawLeaders(canvas: Canvas, document: CadDocument, layout: PdfExportLayout) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = PdfLineweightTable.toEffectivePoints(PenStyle.DIMENSION).toFloat()
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            textSize = PdfLineweightTable.mmToPoints(ANNOTATION_TEXT_MM).toFloat()
        }

        for (leader in document.leaders) {
            if (leader.text.isBlank()) continue
            val ll = LeaderGeometry.createLayout(leader.target, leader.textPosition, 1.0)
            paint.color = leader.colorArgb
            textPaint.color = leader.colorArgb

            for ((a, b) in LeaderGeometry.getSegments(ll)) {
                drawLineWorld(canvas, layout, paint, a, b)
            }

            // Текст по центру полки, чуть выше.
            val midShelf = Point2(
                (ll.elbow.X + ll.landingEnd.X) / 2.0,
                (ll.elbow.Y + ll.landingEnd.Y) / 2.0
            )
            val p = toPagePt(layout, midShelf)
            val baseY = p[1] - 2f  // чуть выше полки
            canvas.drawText(leader.text, p[0], baseY, textPaint)
        }
    }

    // --- Хелперы ---

    /** Мировая точка → [x,y] в pt (Canvas pdf в pt). */
    private fun toPagePt(layout: PdfExportLayout, p: Point2): FloatArray {
        val page = layout.worldToPageMm(p)
        return floatArrayOf(
            (page.xMm * POINTS_PER_INCH / MM_PER_INCH).toFloat(),
            (page.yMm * POINTS_PER_INCH / MM_PER_INCH).toFloat()
        )
    }

    private fun drawLineWorld(
        canvas: Canvas,
        layout: PdfExportLayout,
        paint: Paint,
        a: Point2,
        b: Point2,
        widthMultiplier: Float = 1f
    ) {
        val p1 = toPagePt(layout, a)
        val p2 = toPagePt(layout, b)
        val old = paint.strokeWidth
        if (widthMultiplier != 1f) paint.strokeWidth = old * widthMultiplier
        canvas.drawLine(p1[0], p1[1], p2[0], p2[1], paint)
        if (widthMultiplier != 1f) paint.strokeWidth = old
    }

    private fun lerpBack(anchor: Point2, end: Point2, t: Double): Point2 = Point2(
        end.X - (end.X - anchor.X) * t,
        end.Y - (end.Y - anchor.Y) * t
    )

    /** Штрих-паттерн для ребра в pt (масштабируется от толщины). */
    private fun edgePathEffect(lineType: EdgeLineType, strokeWidthPt: Float): PathEffect? =
        when (lineType) {
            EdgeLineType.DASHED -> DashPathEffect(
                floatArrayOf(8f * strokeWidthPt, 4f * strokeWidthPt), 0f
            )
            EdgeLineType.DOTTED -> DashPathEffect(
                floatArrayOf(2f * strokeWidthPt, 4f * strokeWidthPt), 0f
            )
            EdgeLineType.SOLID -> null
        }

    private fun sanitizeFileName(name: String): String {
        val trimmed = name.trim().ifBlank { "Drawing" }
        val invalid = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        val cleaned = trimmed.map { if (it in invalid) '_' else it }.joinToString("")
        return cleaned.ifBlank { "Drawing" }
    }

    /**
     * Сохранить PdfDocument в Downloads через MediaStore.
     * @return true если успешно.
     */
    private fun writeToDownloads(context: Context, pdf: PdfDocument, fileName: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out: OutputStream ->
                pdf.writeTo(out)
            } ?: return false
            true
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
