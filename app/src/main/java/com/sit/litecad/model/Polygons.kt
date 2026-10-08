package com.sit.litecad.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Аналог C# DirectedEdgeReference. Ребро с направлением обхода.
 * Forward = true  — идём StartVertex → EndVertex,
 * Forward = false — идём EndVertex → StartVertex.
 */
data class DirectedEdgeReference(
    @SerializedName("EdgeId") val edgeId: UUID,
    @SerializedName("Forward") val forward: Boolean
)

/**
 * Аналог C# Loop. Упорядоченный замкнутый набор направленных рёбер.
 */
data class Loop(
    @SerializedName("Edges") val edges: MutableList<DirectedEdgeReference> = mutableListOf()
) {
    fun clone(): Loop = Loop(edges.toMutableList())
}

/**
 * Аналог C# PolygonType.
 */
enum class PolygonType {
    @SerializedName("Face") FACE,
    @SerializedName("Wall") WALL,
    @SerializedName("Room") ROOM,
    @SerializedName("Axis") AXIS
}

/**
 * Аналог C# Polygon. Замкнутая область: внешний контур + дырки.
 * PolygonType.Face — производная сущность, перестраивается PolygonBuilder.
 * Остальные типы — пользовательские, хранятся в документе и в снапшотах.
 */
data class Polygon(
    @SerializedName("Id") val id: UUID = UUID.randomUUID(),
    @SerializedName("Type") var type: PolygonType = PolygonType.FACE,
    @SerializedName("OuterLoop") var outerLoop: Loop = Loop(),
    @SerializedName("InnerLoops") val innerLoops: MutableList<Loop> = mutableListOf()
) {
    val edgeIds: List<UUID>
        get() = outerLoop.edges.map { it.edgeId }

    val holeEdgeIds: List<List<UUID>>
        get() = innerLoops.map { loop -> loop.edges.map { it.edgeId } }

    fun clone(): Polygon {
        val copy = Polygon(id = id, type = type)
        copy.outerLoop.edges.addAll(outerLoop.edges)
        for (inner in innerLoops) {
            copy.innerLoops.add(inner.clone())
        }
        return copy
    }
}

/**
 * Аналог C# FaceFillPattern.
 */
enum class FaceFillPattern {
    @SerializedName("Solid") SOLID,
    @SerializedName("Diagonal") DIAGONAL,
    @SerializedName("DiagonalWide") DIAGONAL_WIDE
}

/**
 * Аналог C# FaceFillStyle. Стиль заливки грани.
 */
data class FaceFillStyle(
    @SerializedName("FillColor") var fillColor: Int = DEFAULT_FILL_COLOR,
    @SerializedName("FillPattern") var fillPattern: FaceFillPattern = FaceFillPattern.SOLID
) {
    fun clone(): FaceFillStyle = FaceFillStyle(fillColor, fillPattern)

    companion object {
        // C#: Color.FromRgb(0xF0, 0xF0, 0xF0) → ARGB 0xFFF0F0F0
        const val DEFAULT_FILL_COLOR: Int = 0xFFF0F0F0.toInt()

        val Default: FaceFillStyle = FaceFillStyle()
    }
}