package com.sit.litecad.storage

import com.sit.litecad.math.Point2
import com.sit.litecad.model.AxisPointKind
import com.sit.litecad.model.CadAxis
import com.sit.litecad.model.CadAxisPoint
import com.sit.litecad.model.CadDimension
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.CadEdge
import com.sit.litecad.model.CadVertex
import com.sit.litecad.model.DimensionAnchorType
import com.sit.litecad.model.DimensionExtensionStyle
import com.sit.litecad.model.EdgeLineType
import com.sit.litecad.model.FaceFillPattern
import com.sit.litecad.model.FaceFillStyle
import com.sit.litecad.model.Leader
import com.sit.litecad.model.LeaderKind
import com.sit.litecad.model.OrthoAlignment
import com.sit.litecad.storage.dto.AxisDto
import com.sit.litecad.storage.dto.AxisPointDto
import com.sit.litecad.storage.dto.DimensionDto
import com.sit.litecad.storage.dto.EdgeDto
import com.sit.litecad.storage.dto.FaceFillStyleDto
import com.sit.litecad.storage.dto.LeaderDto
import com.sit.litecad.storage.dto.ProjectDocumentDto
import com.sit.litecad.storage.dto.VertexDto
import java.util.UUID

object ProjectDocumentSerializer {

    const val CURRENT_FORMAT_VERSION = 1

    // --- СОХРАНЕНИЕ: CadDocument -> DTO -> JSON ---
    fun serialize(document: CadDocument, linearDisplayUnit: String = "Millimeters"): String {
        val dto = toDto(document, linearDisplayUnit)
        return GsonProvider.instance.toJson(dto)
    }

    fun toDto(document: CadDocument, linearDisplayUnit: String = "Millimeters"): ProjectDocumentDto {
        return ProjectDocumentDto(
            formatVersion = CURRENT_FORMAT_VERSION,
            linearDisplayUnit = linearDisplayUnit,
            vertices = document.vertices.map { vertex ->
                VertexDto(
                    id = vertex.id.toString(),
                    x = vertex.X,
                    y = vertex.Y
                )
            }.toMutableList(),
            edges = document.edges.map { edge ->
                EdgeDto(
                    id = edge.id.toString(),
                    startVertexId = edge.startVertexId.toString(),
                    endVertexId = edge.endVertexId.toString(),
                    isAxis = edge.isAxis,
                    color = argbToHex(edge.colorArgb),
                    thickness = edge.thickness,
                    lineType = enumToCamel(edge.lineType.name),
                    orthoAlignment = enumToCamel(edge.orthoAlignment.name)
                )
            }.toMutableList(),
            axes = document.axes.map { axis ->
                AxisDto(
                    id = axis.id.toString(),
                    startX = axis.Start.X,
                    startY = axis.Start.Y,
                    endX = axis.End.X,
                    endY = axis.End.Y,
                    color = argbToHex(axis.colorArgb),
                    thickness = axis.thickness
                )
            }.toMutableList(),
            axisPoints = document.axisPoints.map { point ->
                AxisPointDto(
                    id = point.id.toString(),
                    x = point.position.X,
                    y = point.position.Y,
                    kind = point.kind.name,
                    axisId = point.axisId.toString(),
                    otherAxisId = point.otherAxisId?.toString()
                )
            }.toMutableList(),
            dimensions = document.dimensions.map { dim ->
                DimensionDto(
                    id = dim.id.toString(),
                    firstVertexId = dim.FirstVertexId.toString(),
                    secondVertexId = dim.SecondVertexId.toString(),
                    firstAnchorX = dim.FirstAnchorPosition.X,
                    firstAnchorY = dim.FirstAnchorPosition.Y,
                    secondAnchorX = dim.SecondAnchorPosition.X,
                    secondAnchorY = dim.SecondAnchorPosition.Y,
                    firstAnchorType = dim.FirstAnchorType.name.lowercase(),
                    secondAnchorType = dim.SecondAnchorType.name.lowercase(),
                    offset = dim.Offset,
                    extensionStyle = dim.ExtensionStyle.name.lowercase(),
                    isOrthogonal = dim.IsOrthogonal,
                    orthogonalIsHorizontal = dim.OrthogonalIsHorizontal,
                    textSize = dim.TextSize,
                    color = argbToHex(dim.colorArgb),
                    thickness = dim.thickness
                )
            }.toMutableList(),
            leaders = document.leaders.map { leader ->
                LeaderDto(
                    id = leader.id.toString(),
                    kind = leader.kind.name.lowercase(),
                    targetX = leader.target.X,
                    targetY = leader.target.Y,
                    textX = leader.textPosition.X,
                    textY = leader.textPosition.Y,
                    text = leader.text,
                    color = argbToHex(leader.colorArgb)
                )
            }.toMutableList(),
            elevationBaseY = document.elevationBaseY,
            faceFillStyles = document.faceFillStyles.mapValues { (_, style) ->
                FaceFillStyleDto(
                    fillColor = argbToHex(style.fillColor),
                    fillPattern = enumToCamel(style.fillPattern.name)
                )
            }.toMutableMap(),
            suppressedFaceGeometryKeys = document.suppressedFaceGeometryKeys
                .map { it.replace("-", "") }
                .toMutableList()
            // Остальные поля (userPolygons, texts) остаются пустыми,
            // т.к. в Android-версии их пока нет.
        )
    }

    // --- ЗАГРУЗКА: JSON -> DTO -> CadDocument ---
    fun deserialize(json: String): ProjectDocumentDto {
        return GsonProvider.instance.fromJson(json, ProjectDocumentDto::class.java)
            ?: throw IllegalStateException("Project JSON is empty.")
    }

    fun apply(document: CadDocument, dto: ProjectDocumentDto) {
        if (dto.formatVersion != CURRENT_FORMAT_VERSION) {
            throw IllegalStateException("Unsupported project format version: ${dto.formatVersion}.")
        }

        document.vertices.clear()
        document.edges.clear()

        val vertexIdMap = HashMap<String, UUID>()

        for (v in dto.vertices) {
            val uuid = parseUuid(v.id)
            document.vertices.add(
                CadVertex(id = uuid, X = v.x, Y = v.y)
            )
            vertexIdMap[v.id] = uuid
        }

        for (e in dto.edges) {
            val startId = vertexIdMap[e.startVertexId] ?: parseUuid(e.startVertexId)
            val endId = vertexIdMap[e.endVertexId] ?: parseUuid(e.endVertexId)

            document.edges.add(
                CadEdge(
                    id = parseUuid(e.id),
                    startVertexId = startId,
                    endVertexId = endId,
                    isAxis = e.isAxis,
                    thickness = e.thickness,
                    lineType = parseLineType(e.lineType),
                    orthoAlignment = parseOrthoAlignment(e.orthoAlignment),
                    colorArgb = hexToArgb(e.color)
                )
            )
        }

        document.axes.clear()
        for (a in dto.axes) {
            document.axes.add(
                CadAxis(
                    id = parseUuid(a.id),
                    Start = Point2(a.startX, a.startY),
                    End = Point2(a.endX, a.endY),
                    colorArgb = hexToArgb(a.color),
                    thickness = a.thickness
                )
            )
        }

        // Точки осей читаются из файла (старые .sit без них — пустой список);
        // в конце apply() список всё равно пересоберётся через AxisPointSync.
        document.axisPoints.clear()
        for (p in dto.axisPoints) {
            document.axisPoints.add(
                CadAxisPoint(
                    id = parseUuid(p.id),
                    position = Point2(p.x, p.y),
                    kind = parseAxisPointKind(p.kind),
                    axisId = parseUuid(p.axisId),
                    otherAxisId = p.otherAxisId?.let { parseUuid(it) }
                )
            )
        }

        document.dimensions.clear()
        for (d in dto.dimensions) {
            val firstId = vertexIdMap[d.firstVertexId] ?: parseUuid(d.firstVertexId)
            val secondId = vertexIdMap[d.secondVertexId] ?: parseUuid(d.secondVertexId)
            document.dimensions.add(
                CadDimension(
                    id = parseUuid(d.id),
                    FirstVertexId = firstId,
                    SecondVertexId = secondId,
                    FirstAnchorPosition = Point2(d.firstAnchorX, d.firstAnchorY),
                    SecondAnchorPosition = Point2(d.secondAnchorX, d.secondAnchorY),
                    FirstAnchorType = parseDimensionAnchorType(d.firstAnchorType),
                    SecondAnchorType = parseDimensionAnchorType(d.secondAnchorType),
                    Offset = d.offset,
                    ExtensionStyle = parseDimensionExtensionStyle(d.extensionStyle),
                    IsOrthogonal = d.isOrthogonal,
                    OrthogonalIsHorizontal = d.orthogonalIsHorizontal,
                    TextSize = d.textSize,
                    colorArgb = hexToArgb(d.color),
                    thickness = d.thickness
                )
            )
        }

        document.leaders.clear()
        for (l in dto.leaders) {
            document.leaders.add(
                Leader(
                    id = parseUuid(l.id),
                    kind = parseLeaderKind(l.kind),
                    target = Point2(l.targetX, l.targetY),
                    textPosition = Point2(l.textX, l.textY),
                    text = l.text,
                    colorArgb = hexToArgb(l.color)
                )
            )
        }
        document.elevationBaseY = dto.elevationBaseY

        document.faceFillStyles.clear()
        for ((key, styleDto) in dto.faceFillStyles) {
            // Ключ ПК-формата — UUID без дефисов (Guid.ToString("N")).
            // Старые Android-ключи содержали дефисы — нормализуем, чтобы старые
            // проекты продолжали открываться и заливки находили свои грани.
            val normalizedKey = key.replace("-", "")
            document.faceFillStyles[normalizedKey] = FaceFillStyle(
                fillColor = hexToArgb(styleDto.fillColor),
                fillPattern = parseFillPattern(styleDto.fillPattern)
            )
        }

        // Ключи подавленных граней — тоже в формате "N" (без дефисов).
        // Важно: восстанавливаем ДО PolygonBuilder.syncFaces, иначе syncFaces
        // пересоберёт уже подавленные грани заново.
        document.suppressedFaceGeometryKeys.clear()
        for (key in dto.suppressedFaceGeometryKeys) {
            document.suppressedFaceGeometryKeys.add(key.replace("-", ""))
        }

        document.bumpRevision()

        document.rebuildIndexes()
        com.sit.litecad.geometry.PolygonBuilder.syncFaces(document)
        // Пересобрать точки осей из осей (в т.ч. для старых .sit без axisPoints).
        com.sit.litecad.geometry.AxisPointSync.sync(document)
    }

    // --- УТИЛИТЫ ---
    private fun parseUuid(value: String): UUID {
        return try {
            UUID.fromString(value)
        } catch (e: IllegalArgumentException) {
            UUID.randomUUID()
        }
    }

    private fun enumToCamel(name: String): String {
        // SOLID -> solid, ON_EDGE -> onEdge
        val parts = name.lowercase().split('_')
        if (parts.isEmpty()) return name.lowercase()
        return parts[0] + parts.drop(1).joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private fun parseLineType(value: String): EdgeLineType {
        return when (value.lowercase()) {
            "dashed" -> EdgeLineType.DASHED
            "dotted" -> EdgeLineType.DOTTED
            else -> EdgeLineType.SOLID
        }
    }

    private fun parseFillPattern(value: String): FaceFillPattern {
        return when (value.lowercase()) {
            "diagonal" -> FaceFillPattern.DIAGONAL
            "diagonalwide" -> FaceFillPattern.DIAGONAL_WIDE
            else -> FaceFillPattern.SOLID
        }
    }

    private fun parseOrthoAlignment(value: String): OrthoAlignment {
        return when (value.lowercase()) {
            "horizontal" -> OrthoAlignment.HORIZONTAL
            "vertical" -> OrthoAlignment.VERTICAL
            else -> OrthoAlignment.NONE
        }
    }

    private fun parseDimensionExtensionStyle(value: String): DimensionExtensionStyle {
        return when (value.lowercase()) {
            "short" -> DimensionExtensionStyle.SHORT
            else -> DimensionExtensionStyle.FULL
        }
    }

    private fun parseLeaderKind(value: String): LeaderKind {
        return when (value.lowercase()) {
            "elevation" -> LeaderKind.ELEVATION
            else -> LeaderKind.TEXT
        }
    }

    private fun parseAxisPointKind(value: String): AxisPointKind {
        return when (value.uppercase()) {
            "END_END" -> AxisPointKind.END_END
            "INTERSECTION" -> AxisPointKind.INTERSECTION
            else -> AxisPointKind.END_START
        }
    }

    private fun parseDimensionAnchorType(value: String): DimensionAnchorType {
        return when (value.lowercase()) {
            "axis_point", "axispoint" -> DimensionAnchorType.AXIS_POINT
            else -> DimensionAnchorType.VERTEX
        }
    }

    // ARGB Int -> "#AARRGGBB"
    private fun argbToHex(argb: Int): String {
        return "#" + String.format("%08X", argb)
    }

    // "#AARRGGBB" или "#RRGGBB" -> ARGB Int
    private fun hexToArgb(hex: String): Int {
        var text = hex.trim().removePrefix("#")
        if (text.length == 6) text = "FF$text"
        return try {
            text.toLong(16).toInt()
        } catch (e: NumberFormatException) {
            0xFF222222.toInt()
        }
    }
}