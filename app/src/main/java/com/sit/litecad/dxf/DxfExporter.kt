package com.sit.litecad.dxf

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sit.litecad.geometry.DimensionGeometry
import com.sit.litecad.geometry.DimensionLayout
import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.leaders.LeaderGeometry
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.DimensionExtensionStyle
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
 * Логика обхода сущностей повторяет [com.sit.litecad.pdf.PdfExporter], но результат
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

    /** Высота текста размеров/выносок в мм (ГОСТ 2.5 мм), как в PDF. */
    private const val ANNOTATION_TEXT_MM = 2.5

    /** Половина длины засечки под 45°: 10 pt ≈ 3.53 мм (как в PDF). */
    private const val TICK_HALF_MM = 3.53

    /** Сдвиг текста размера по нормали: 6 pt ≈ 2.12 мм (как в PDF). */
    private const val TEXT_NORMAL_OFFSET_MM = 2.12

    /** Желаемая высота стойки выноски на выгрузке, мм. */
    private const val LEADER_STEM_TARGET_MM = 8.0

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

        val writer = DxfWriter()
        writer.beginDocument()

        // Таблицы: типы линий и слои.
        writer.beginTables()
        writer.addLineType("CONTINUOUS", "Solid line", emptyList())
        writer.addLineType("CENTER", "Center ____ _ ____ _ ___", listOf(20.0, -4.0, 4.0, -4.0))
        writer.addLayer(LAYER_EDGES, 7, "CONTINUOUS")
        writer.addLayer(LAYER_AXES, 1, "CENTER")
        writer.addLayer(LAYER_DIMENSIONS, 7, "CONTINUOUS")
        writer.addLayer(LAYER_LEADERS, 7, "CONTINUOUS")
        writer.endTables()

        // Геометрия.
        writer.beginEntities()
        writeEdges(writer, document)
        writeAxes(writer, document)
        writeDimensions(writer, document)
        writeLeaders(writer, document)
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

    private fun writeDimensions(writer: DxfWriter, document: CadDocument) {
        for (dim in document.dimensions) {
            val first = document.vertexById(dim.FirstVertexId) ?: continue
            val second = document.vertexById(dim.SecondVertexId) ?: continue
            val dl = DimensionGeometry.tryCreateLayout(
                first.position, second.position, dim.Offset,
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
            addTick(writer, dl.firstExtensionEnd, tickAngleRad)
            addTick(writer, dl.secondExtensionEnd, tickAngleRad)

            // Текст значения размера.
            writeDimensionText(writer, dl)
        }
    }

    private fun addDimensionLine(writer: DxfWriter, a: Point2, b: Point2) {
        writer.addLine(a.X, a.Y, b.X, b.Y, LAYER_DIMENSIONS)
    }

    private fun addTick(writer: DxfWriter, center: Point2, angleRad: Double) {
        val dx = cos(angleRad) * TICK_HALF_MM
        val dy = sin(angleRad) * TICK_HALF_MM
        writer.addLine(
            center.X - dx, center.Y - dy,
            center.X + dx, center.Y + dy,
            LAYER_DIMENSIONS
        )
    }

    private fun writeDimensionText(writer: DxfWriter, dl: DimensionLayout) {
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

        val tx = midX + nx * TEXT_NORMAL_OFFSET_MM
        val ty = midY + ny * TEXT_NORMAL_OFFSET_MM
        val angleDeg = dimensionTextAngleDegrees(dl)

        writer.addText(
            tx, ty,
            ANNOTATION_TEXT_MM,
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

    private fun writeLeaders(writer: DxfWriter, document: CadDocument) {
        // Подбираем zoom так, чтобы стойка занимала ≈ 8 мм на выгрузке (как в PDF).
        val leaderZoom = LeaderGeometry.STEM_HEIGHT_SCREEN / LEADER_STEM_TARGET_MM
        for (leader in document.leaders) {
            if (leader.text.isBlank()) continue
            val ll = LeaderGeometry.createLayout(leader.target, leader.textPosition, leaderZoom)
            for ((a, b) in LeaderGeometry.getSegments(ll)) {
                writer.addLine(a.X, a.Y, b.X, b.Y, LAYER_LEADERS)
            }

            // Текст — по центру полки.
            val midX = (ll.elbow.X + ll.landingEnd.X) / 2.0
            val midY = (ll.elbow.Y + ll.landingEnd.Y) / 2.0
            writer.addText(midX, midY, ANNOTATION_TEXT_MM, leader.text, LAYER_LEADERS)
        }
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
