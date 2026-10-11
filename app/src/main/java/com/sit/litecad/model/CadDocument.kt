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
    @SerializedName("Edges") val edges: MutableList<CadEdge> = mutableListOf(),
    @SerializedName("Polygons") val polygons: MutableList<Polygon> = mutableListOf(),
    @SerializedName("Axes") val axes: MutableList<CadAxis> = mutableListOf(),
    @SerializedName("FaceFillStyles")
    val faceFillStyles: MutableMap<String, FaceFillStyle> = mutableMapOf(),
    @SerializedName("SuppressedFaceGeometryKeys")
    val suppressedFaceGeometryKeys: MutableSet<String> = mutableSetOf()
) {
    var revision by mutableStateOf(0)
        private set

    /**
     * Быстрый lookup по Id. Не сериализуется.
     * Синхронизируется вручную через addVertex/addEdge/removeEdge/clear.
     */
    private val verticesById: MutableMap<UUID, CadVertex> = mutableMapOf()
    private val edgesById: MutableMap<UUID, CadEdge> = mutableMapOf()
    private val axesById: MutableMap<UUID, CadAxis> = mutableMapOf()

    fun bumpRevision() {
        revision++
    }

    // --- lookup ---

    fun vertexById(id: UUID): CadVertex? = verticesById[id]
    fun edgeById(id: UUID): CadEdge? = edgesById[id]
    fun axisById(id: UUID): CadAxis? = axesById[id]

    // --- mutations ---

    fun addVertex(vertex: CadVertex) {
        vertices.add(vertex)
        verticesById[vertex.id] = vertex
    }

    fun addEdge(edge: CadEdge) {
        edges.add(edge)
        edgesById[edge.id] = edge
    }

    fun removeEdge(edge: CadEdge) {
        edges.remove(edge)
        edgesById.remove(edge.id)
    }

    fun addAxis(axis: CadAxis) {
        axes.add(axis)
        axesById[axis.id] = axis
    }

    fun removeAxis(axis: CadAxis) {
        axes.remove(axis)
        axesById.remove(axis.id)
    }

    /**
     * Полная пересборка индексов. Нужна после undo/redo, загрузки проекта,
     * любой замены содержимого списков в обход add/remove.
     */
    fun rebuildIndexes() {
        verticesById.clear()
        edgesById.clear()
        axesById.clear()
        for (v in vertices) verticesById[v.id] = v
        for (e in edges) edgesById[e.id] = e
        for (a in axes) axesById[a.id] = a
    }

    fun clear() {
        vertices.clear()
        edges.clear()
        axes.clear()
        polygons.clear()
        faceFillStyles.clear()
        suppressedFaceGeometryKeys.clear()
        verticesById.clear()
        edgesById.clear()
        axesById.clear()
    }

    fun clearTopology() {
        vertices.clear()
        edges.clear()
        verticesById.clear()
        edgesById.clear()
    }
}