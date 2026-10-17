package com.sit.litecad.dxf

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sit.litecad.geometry.DimensionAnchorResolver
import com.sit.litecad.geometry.DimensionGeometry
import com.sit.litecad.geometry.DimensionLayout
import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.leaders.LeaderGeometry
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.DimensionExtensionStyle
import com.sit.litecad.model.TextExportSizes
import java.io.OutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Экспорт чертежа в DXF R12 (AC1009, ASCII) и сохранение в Downloads.
 *
 * Логика обхода сущностей повторяет PDF-экспортер, но результат
 * пишется в DXF-строки, а не на Canvas. Модуль изолирован: зависит только от модели
 * и геометрии, без Compose и UI.
 *
 * Слои: EDGES, AXES, DIMENSIONS, LEADERS.
 * Толщины линий не выгружаются — в R12 поля lineweight нет.
 */
object DxfExporter {

    /** Имена слоёв DXF. */
    private const val LAYER_EDGES = "EDGES"
    private const val LAYER_AXES = "AXES"
    private const val LAYER_DIMENSIONS = "DIMENSIONS"
    private const val LAYER_LEADERS = "LEADERS"
    private const val LAYER_TEXTS = "TEXTS"

    /** Минимальная высота текста аннотации, мм (нижний порог). */
    private const val MIN_ANNOTATION_HEIGHT_MM = 25.0

    /** Максимальная высота текста аннотации, мм (верхний порог). */
    private const val MAX_ANNOTATION_HEIGHT_MM = 200.0

    /** Доля от характерного размера чертежа (3%). */
    private const val ANNOTATION_HEIGHT_RATIO = 0.03

    /**
     * Параметры аннотации, вычисленные под конкретный чертёж.
     * Держат текст размеров/выносок читаемым на чертежах любого масштаба.
     */
    private data class AnnotationScale(
        val textHeight: Double,          // h
        val tickHalf: Double,            // h × 1.4
        val textNormalOffset: Double,    // h × 0.85
        val leaderStemTarget: Double,    // h × 3.2
        val centerPattern: List<Double>  // [h×0.4, -h×0.08, +h×0.08, -h×0.08]
    )

    /**
     * Экспортировать документ в DXF R12 и сохранить в Downloads.
     * @return имя сохранённого файла (без пути) или null при ошибке.
     */
    fun exportToDownloads(
        context: Context,
        document: CadDocument,
        projectName: String
    ): String? {
        val fileName = sanitizeFileName(projectName) + ".dxf"
        val scale = computeAnnotationScale(document)

        val writer = DxfWriter()
        writer.beginDocument()

        // Таблицы: типы линий и слои.
        writer.beginTables()
        writer.addLineType("CONTINUOUS", "Solid line", emptyList())
        writer.addLineType("CENTER", "Center ____ _ ____ _ ___", scale.centerPattern)
        writer.addLayer(LAYER_EDGES, 7, "CONTINUOUS")
        writer.addLayer(LAYER_AXES, 1, "CENTER")
        writer.addLayer(LAYER_DIMENSIONS, 7, "CONTINUOUS")
        writer.addLayer(LAYER_LEADERS, 7, "CONTINUOUS")
        writer.addLayer(LAYER_TEXTS, 7, "CONTINUOUS")
        writer.endTables()

        // Геометрия.
        writer.beginEntities()
        writeEdges(writer, document)
        writeAxes(writer, document)
        writeDimensions(writer, document, scale)
        writeLeaders(writer, document, scale)
        writeTexts(writer, document)
        writer.endEntities()

        writer.endDocument()

        val dxf = writer.build()
        val saved = writeToDownloads(context, dxf.toByteArray(Charsets.UTF_8), fileName)
        return if (saved) fileName else null
    }

    // --- Рёбра ---

    private fun writeEdges(writer: DxfWriter, document: CadDocument) {
        for (edge in document.edges) {
            val start = TopologyService.getEdgeStartPoint(document, edge)
            val end = TopologyService.getEdgeEndPoint(document, edge)
            // Рёбра-оси уходят на слой AXES, как в редакторе.
            val layer = if (edge.isAxis) LAYER_AXES else LAYER_EDGES
            writer.addLine(start.X, start.Y, end.X, end.Y, layer)
        }
    }

    // --- Оси ---

    private fun writeAxes(writer: DxfWriter, document: CadDocument) {
        for (axis in document.axes) {
            writer.addLine(axis.Start.X, axis.Start.Y, axis.End.X, axis.End.Y, LAYER_AXES)
        }
    }

    // --- Размеры ---

    private fun writeDimensions(writer: DxfWriter, document: CadDocument, scale: AnnotationScale) {
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

            // Начала выносных линий: FULL — от anchor, SHORT — не доходя до anchor (как в PDF).
            val firstExtStart = if (dim.ExtensionStyle == DimensionExtensionStyle.FULL) {
                dl.firstAnchor
            } else {
                lerpBack(dl.firstAnchor, dl.firstExtensionEnd, 0.25)
            }
            val secondExtStart = if (dim.ExtensionStyle == DimensionExtensionStyle.FULL) {
                dl.secondAnchor
            } else {
                lerpBack(dl.secondAnchor, dl.secondExtensionEnd, 0.25)
            }

            // Выносные линии и размерная линия.
            addDimensionLine(writer, firstExtStart, dl.firstExtensionEnd)
            addDimensionLine(writer, secondExtStart, dl.secondExtensionEnd)
            addDimensionLine(writer, dl.dimensionLineStart, dl.dimensionLineEnd)

            // Засечки под 45° к размерной линии.
            val tickAngleRad = dl.textAngleRadians + PI / 4.0
            addTick(writer, dl.firstExtensionEnd, tickAngleRad, scale.tickHalf)
            addTick(writer, dl.secondExtensionEnd, tickAngleRad, scale.tickHalf)

            // Текст значения размера.
            writeDimensionText(writer, dl, scale)
        }
    }

    private fun addDimensionLine(writer: DxfWriter, a: Point2, b: Point2) {
        writer.addLine(a.X, a.Y, b.X, b.Y, LAYER_DIMENSIONS)
    }

    private fun addTick(writer: DxfWriter, center: Point2, angleRad: Double, tickHalf: Double) {
        val dx = cos(angleRad) * tickHalf
        val dy = sin(angleRad) * tickHalf
        writer.addLine(
            center.X - dx, center.Y - dy,
            center.X + dx, center.Y + dy,
            LAYER_DIMENSIONS
        )
    }

    private fun writeDimensionText(writer: DxfWriter, dl: DimensionLayout, scale: AnnotationScale) {
        val midX = (dl.dimensionLineStart.X + dl.dimensionLineEnd.X) / 2.0
        val midY = (dl.dimensionLineStart.Y + dl.dimensionLineEnd.Y) / 2.0

        val sdx = dl.dimensionLineEnd.X - dl.dimensionLineStart.X
        val sdy = dl.dimensionLineEnd.Y - dl.dimensionLineStart.Y
        val len = hypot(sdx, sdy)

        var nx = 0.0
        var ny = 0.0
        if (len > 1e-9) {
            nx = -sdy / len
            ny = sdx / len
            // Текст — по верхнюю сторону размерной линии (нормаль в +Y мира).
            if (ny < 0.0) {
                nx = -nx
                ny = -ny
            }
        }

        val tx = midX + nx * scale.textNormalOffset
        val ty = midY + ny * scale.textNormalOffset
        val angleDeg = dimensionTextAngleDegrees(dl)

        writer.addText(
            tx, ty,
            scale.textHeight,
            formatDimensionValue(dl.measuredDistance),
            LAYER_DIMENSIONS,
            angleDeg
        )
    }

    /** Угол текста размера с нормализацией в читаемый диапазон (как в PDF). */
    private fun dimensionTextAngleDegrees(dl: DimensionLayout): Double {
        val sdx = dl.dimensionLineEnd.X - dl.dimensionLineStart.X
        val sdy = dl.dimensionLineEnd.Y - dl.dimensionLineStart.Y
        return when {
            abs(sdy) < abs(sdx) * 0.01 -> 0.0
            abs(sdx) < abs(sdy) * 0.01 -> 90.0
            else -> {
                var a = Math.toDegrees(atan2(sdy, sdx))
                if (a > 90.0 || a <= -90.0) a += 180.0
                a
            }
        }
    }

    // --- Выноски ---

    private fun writeLeaders(writer: DxfWriter, document: CadDocument, scale: AnnotationScale) {
        // Подбираем zoom так, чтобы стойка занимала желаемую высоту (scale.leaderStemTarget).
        val leaderZoom = LeaderGeometry.STEM_HEIGHT_SCREEN / scale.leaderStemTarget
        for (leader in document.leaders) {
            if (leader.text.isBlank()) continue
            val ll = LeaderGeometry.createLayout(leader.target, leader.textPosition, leaderZoom)
            for ((a, b) in LeaderGeometry.getSegments(ll)) {
                writer.addLine(a.X, a.Y, b.X, b.Y, LAYER_LEADERS)
            }

            // Текст — по центру полки.
            val midX = (ll.elbow.X + ll.landingEnd.X) / 2.0
            val midY = (ll.elbow.Y + ll.landingEnd.Y) / 2.0
            writer.addText(midX, midY, scale.textHeight, leader.text, LAYER_LEADERS)
        }
    }

    // --- Надписи ---

    /**
     * Пользовательские надписи [CadDocument.texts] на слой TEXTS.
     *
     * Высота — фиксированная по ГОСТ ([TextExportSizes.heightMm]), как и на экране,
     * где размер sp не зависит от зума; масштаб аннотаций [AnnotationScale] для них
     * НЕ применяется.
     *
     * Цвет: в R12 (AC1009) нет true color (group 420 появился в R2004), а маппинг
     * ARGB → ACI требует палитры, поэтому надпись пишется цветом слоя TEXTS (ACI 7),
     * как это уже сделано для размеров и выносок.
     */
    private fun writeTexts(writer: DxfWriter, document: CadDocument) {
        for (text in document.texts) {
            if (text.text.isEmpty()) continue
            writer.addCenteredText(
                x = text.position.X,
                y = text.position.Y,
                height = TextExportSizes.heightMm(text.sizeSp).toDouble(),
                text = text.text,
                layer = LAYER_TEXTS,
                rotationDeg = TextExportSizes.dxfRotationDeg(text.rotation).toDouble()
            )
        }
    }

    // --- Масштаб аннотаций ---

    /** Параметры аннотации под конкретный чертёж: высота текста и производные. */
    private fun computeAnnotationScale(document: CadDocument): AnnotationScale {
        val characteristic = characteristicSize(document)
        val h = (characteristic * ANNOTATION_HEIGHT_RATIO)
            .coerceIn(MIN_ANNOTATION_HEIGHT_MM, MAX_ANNOTATION_HEIGHT_MM)
        return AnnotationScale(
            textHeight = h,
            tickHalf = h * 1.4,
            textNormalOffset = h * 0.85,
            leaderStemTarget = h * 3.2,
            centerPattern = listOf(h * 0.4, -h * 0.08, h * 0.08, -h * 0.08)
        )
    }

    /**
     * Характерный размер чертежа: max(ширина, высота) габаритов содержимого
     * (вершины, оси, размеры, выноски). Пустой чертёж → 0.0.
     * Дублирует логику PdfExportLayout.computeWorldBounds, чтобы dxf/ оставался изолированным.
     */
    private fun characteristicSize(document: CadDocument): Double {
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
            val firstPosition = DimensionAnchorResolver.resolveAnchorPosition(
                document, dim.FirstAnchorType, dim.FirstVertexId, dim.FirstAnchorPosition
            )
            val secondPosition = DimensionAnchorResolver.resolveAnchorPosition(
                document, dim.SecondAnchorType, dim.SecondVertexId, dim.SecondAnchorPosition
            )
            val layout = DimensionGeometry.tryCreateLayout(
                firstPosition, secondPosition, dim.Offset,
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

        if (!hasAny) return 0.0
        return maxOf(maxX - minX, maxY - minY)
    }

    // --- Хелперы ---

    private fun lerpBack(anchor: Point2, end: Point2, t: Double): Point2 = Point2(
        end.X - (end.X - anchor.X) * t,
        end.Y - (end.Y - anchor.Y) * t
    )

    private fun formatDimensionValue(value: Double): String =
        if (abs(value - value.toLong().toDouble()) < 0.05) value.toLong().toString()
        else String.format(java.util.Locale.US, "%.1f", value)

    private fun sanitizeFileName(name: String): String {
        val trimmed = name.trim().ifBlank { "Drawing" }
        val invalid = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        val cleaned = trimmed.map { if (it in invalid) '_' else it }.joinToString("")
        return cleaned.ifBlank { "Drawing" }
    }

    /**
     * Сохранить байты в Downloads через MediaStore.
     * @return true если успешно.
     */
    private fun writeToDownloads(context: Context, bytes: ByteArray, fileName: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/vnd.dxf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out: OutputStream ->
                out.write(bytes)
            } ?: return false
            true
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
