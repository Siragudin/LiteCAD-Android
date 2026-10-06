package com.sit.litecad.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.annotations.SerializedName
import java.util.UUID

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
        fun createStyleTemplate(): CadEdge {
            val emptyId = UUID(0L, 0L)
            return CadEdge(startVertexId = emptyId, endVertexId = emptyId)
        }
    }
}

data class CadDocument(
    @SerializedName("Version") val version: String = "0.4.5",
    @SerializedName("Vertices") val vertices: MutableList<CadVertex> = mutableListOf(),
    @SerializedName("Edges") val edges: MutableList<CadEdge> = mutableListOf()
) {
    var revision by mutableStateOf(0)
        private set

    fun bumpRevision() {
        revision++
    }

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