package com.sit.litecad.storage

import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.CadEdge
import com.sit.litecad.model.CadVertex
import com.sit.litecad.model.EdgeLineType
import com.sit.litecad.model.OrthoAlignment
import com.sit.litecad.storage.dto.EdgeDto
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
            }.toMutableList()
            // Остальные поля (userPolygons, dimensions, axes, leaders, texts,
            // elevationBaseY, suppressedFaceGeometryKeys, faceFillStyles)
            // остаются пустыми, т.к. в Android-версии их пока нет.
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

        document.bumpRevision()

        document.rebuildIndexes()
        com.sit.litecad.geometry.PolygonBuilder.syncFaces(document)
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

    private fun parseOrthoAlignment(value: String): OrthoAlignment {
        return when (value.lowercase()) {
            "horizontal" -> OrthoAlignment.HORIZONTAL
            "vertical" -> OrthoAlignment.VERTICAL
            else -> OrthoAlignment.NONE
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