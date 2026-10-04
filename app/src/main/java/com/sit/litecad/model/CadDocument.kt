package com.sit.litecad.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Эквивалент класса Edge из C# версии.
 * Линия не хранит координаты напрямую, а ссылается на ID вершин.
 */
data class CadEdge(
    @SerializedName("Id") val id: UUID = UUID.randomUUID(),
    @SerializedName("StartVertexId") var startVertexId: UUID,
    @SerializedName("EndVertexId") var endVertexId: UUID,
    @SerializedName("IsAxis") var isAxis: Boolean = false,
    @SerializedName("Thickness") var thickness: Double = 1.5,
    @SerializedName("LineType") var lineType: EdgeLineType = EdgeLineType.SOLID,
    @SerializedName("OrthoAlignment") var orthoAlignment: OrthoAlignment = OrthoAlignment.NONE,
    @SerializedName("ColorARGB") var colorArgb: Int = 0xFF222222.toInt()
) {
    /**
     * Клонирование стиля геометрии для создания новых ребер (аналог CloneGeometry на ПК)
     */
    fun cloneGeometry(): CadEdge {
        return CadEdge(
            id = UUID.randomUUID(),
            startVertexId = this.startVertexId,
            endVertexId = this.endVertexId,
            isAxis = this.isAxis,
            thickness = this.thickness,
            lineType = this.lineType,
            orthoAlignment = this.orthoAlignment,
            colorArgb = this.colorArgb
        )
    }

    companion object {
        /**
         * Создание пустого шаблона стиля (эквивалент CreateStyleTemplate на ПК)
         */
        fun createStyleTemplate(): CadEdge {
            val emptyId = UUID(0L, 0L) // Эквивалент Guid.Empty
            return CadEdge(startVertexId = emptyId, endVertexId = emptyId)
        }
    }
}

/**
 * Эквивалент класса CadDocument из C# версии.
 * Является главной базой данных активного чертежа.
 */
data class CadDocument(
    @SerializedName("Version") val version: String = "0.4.5",
    @SerializedName("Vertices") val vertices: MutableList<CadVertex> = mutableListOf(),
    @SerializedName("Edges") val edges: MutableList<CadEdge> = mutableListOf()
) {
    fun addVertex(vertex: CadVertex) {
        vertices.add(vertex)
    }

    fun addEdge(edge: CadEdge) {
        edges.add(edge)
    }

    fun removeEdge(edge: CadEdge) {
        edges.remove(edge)
    }

    fun clear() {
        vertices.clear()
        edges.clear()
    }
}
