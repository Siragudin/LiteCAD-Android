package com.sit.litecad.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathEffect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sit.litecad.geometry.DimensionAnchorResolver
import com.sit.litecad.geometry.DimensionGeometry
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
import com.sit.litecad.model.TextExportSizes
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

        // Zoom для выносок: чтобы стойка занимала ≈ 8 мм на листе, сохраняя пропорции редактора.
        // leaderZoom = STEM_HEIGHT_SCREEN * scaleMmPerUnit / desiredStemMm,
        // тогда в мировых единицах стойка = desiredStemMm / scaleMmPerUnit, на листе = desiredStemMm мм.
        val desiredStemMm = 8.0
        val leaderZoom = if (layout.scaleMmPerUnit > 0.0) {
            LeaderGeometry.STEM_HEIGHT_SCREEN *
                layout.scaleMmPerUnit / desiredStemMm
        } else {
            1.0
        }

        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(widthPt, heightPt, 1).create()
        val page = pdf.startPage(pageInfo)
        try {
            drawPage(page.canvas, document, layout, leaderZoom)
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

    private fun drawPage(
        canvas: Canvas,
        document: CadDocument,
        layout: PdfExportLayout,
        leaderZoom: Double
    ) {
        canvas.drawColor(Color.WHITE)
        drawFaceFills(canvas, document, layout)
        drawEdges(canvas, document, layout)
        drawAxes(canvas, document, layout)
        drawDimensions(canvas, document, layout)
        drawLeaders(canvas, document, layout, leaderZoom)
        drawTexts(canvas, document, layout)
    }

    // --- Заливки граней ---

    /**
     * Заливки явно закрашенных граней ([com.sit.litecad.model.FaceFillStyle]).
     * Рисуются ДО рёбер, чтобы контуры оставались поверх заливки.
     * Грани без явной записи в [CadDocument.faceFillStyles] пропускаются
     * (дефолтную серую заливку не рисуем).
     */
    private fun drawFaceFills(canvas: Canvas, document: CadDocument, layout: PdfExportLayout) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL

        for (polygon in document.polygons) {
            if (polygon.type != PolygonType.FACE) continue

            val fillKey = FaceIdentity.create(document, polygon)
            if (fillKey.isEmpty()) continue

            // Только явно залитые грани: нет записи в карте — пропускаем.
            val fillStyle = document.faceFillStyles[fillKey] ?: continue

            val outerPoints = PolygonGeometry.getOuterBoundaryPoints(document, polygon)
            if (outerPoints.size < 3) continue

            val path = Path()
            path.fillType = Path.FillType.EVEN_ODD

            val first = toPagePt(layout, outerPoints[0])
            path.moveTo(first[0], first[1])
            for (i in 1 until outerPoints.size) {
                val p = toPagePt(layout, outerPoints[i])
                path.lineTo(p[0], p[1])
            }
            path.close()

            // Дырки — в тот же Path (EvenOdd вырежет их).
            for (hole in polygon.innerLoops) {
                val holePoints = PolygonGeometry.getBoundaryPoints(document, hole)
                if (holePoints.size < 3) continue

                val holeFirst = toPagePt(layout, holePoints[0])
                path.moveTo(holeFirst[0], holeFirst[1])
                for (i in 1 until holePoints.size) {
                    val p = toPagePt(layout, holePoints[i])
                    path.lineTo(p[0], p[1])
                }
                path.close()
            }

            paint.color = fillStyle.fillColor
            canvas.drawPath(path, paint)
        }
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
            paint.color = if (edge.isAxis) 0xFF990000.toInt() else edge.colorArgb
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

        val strokeWidthPt = PdfLineweightTable.toEffectivePoints(PenStyle.AXIS).toFloat()
        paint.strokeWidth = strokeWidthPt
        // Штрих-пунктир «точка-тире», как в CadRenderer; шаг масштабируется от толщины.
        paint.pathEffect = DashPathEffect(
            floatArrayOf(
                12f * strokeWidthPt,
                4f * strokeWidthPt,
                2f * strokeWidthPt,
                4f * strokeWidthPt
            ),
            0f
        )

        for (axis in document.axes) {
            paint.color = axis.colorArgb
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
            val firstPosition = DimensionAnchorResolver.resolveAnchorPosition(
                document, dim.FirstAnchorType, dim.FirstVertexId, dim.FirstAnchorPosition
            )
            val secondPosition = DimensionAnchorResolver.resolveAnchorPosition(
                document, dim.SecondAnchorType, dim.SecondVertexId, dim.SecondAnchorPosition
            )
            val dl = DimensionGeometry.tryCreateLayout(
                firstPosition, secondPosition, dim.Offset,
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

    private fun drawLeaders(
        canvas: Canvas,
        document: CadDocument,
        layout: PdfExportLayout,
        leaderZoom: Double
    ) {
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
            val ll = LeaderGeometry.createLayout(leader.target, leader.textPosition, leaderZoom)
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

    // --- Надписи ---

    /**
     * Пользовательские надписи [CadDocument.texts].
     *
     * Рисуются ПОСЛЕ рёбер/осей/размеров/выносок — текст всегда сверху
     * (как в CadRenderer). Цвет берётся из модели БЕЗ инверсии для тёмной темы:
     * лист PDF белый, инверсия чёрного в белый сделала бы текст невидимым.
     *
     * Экранный размер sp не зависит от зума, поэтому в PDF надпись имеет
     * фиксированную высоту по ГОСТ ([TextExportSizes.heightMm]), а не
     * масштабируется вместе с чертежом.
     */
    private fun drawTexts(canvas: Canvas, document: CadDocument, layout: PdfExportLayout) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            // Позиция надписи в модели — её ЦЕНТР (той же точкой пользуется хит-тест).
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }

        for (text in document.texts) {
            if (text.text.isEmpty()) continue

            val p = toPagePt(layout, text.position)
            paint.color = text.colorArgb
            paint.textSize = PdfLineweightTable
                .mmToPoints(TextExportSizes.heightMm(text.sizeSp).toDouble())
                .toFloat()

            // Базовая линия смещается так, чтобы центр строки совпал с мировой точкой.
            val metrics = paint.fontMetrics
            val baselineY = p[1] - (metrics.ascent + metrics.descent) / 2f

            val rotationDeg = TextExportSizes.pdfRotationDeg(text.rotation)
            if (rotationDeg == 0f) {
                canvas.drawText(text.text, p[0], baselineY, paint)
            } else {
                canvas.save()
                // 270° == -90°: вертикальная надпись читается снизу вверх.
                canvas.rotate(rotationDeg, p[0], p[1])
                canvas.drawText(text.text, p[0], baselineY, paint)
                canvas.restore()
            }
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
