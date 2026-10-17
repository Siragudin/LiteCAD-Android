package com.sit.litecad.model

import com.google.gson.annotations.SerializedName
import com.sit.litecad.math.Point2
import java.util.UUID

/**
 * Эквивалент EdgeLineType из C# версии
 */
enum class EdgeLineType {
    SOLID, DASHED, DOTTED
}

/**
 * Эквивалент OrthoAlignment из C# версии
 */
enum class OrthoAlignment {
    NONE, HORIZONTAL, VERTICAL
}

/**
 * Эквивалент класса Vertex из C# версии.
 * Содержит уникальный UUID (Guid) и физические мировые координаты в мм.
 */
data class CadVertex(
    val id: UUID = UUID.randomUUID(),
    var X: Double, // Сделать заглавной
    var Y: Double  // Сделать заглавной
) {
    val position: Point2
        get() = Point2(X, Y)
}

/**
 * Эквивалент класса Axis из ПК-версии.
 * Ось — отдельная сущность, не ребро. Не участвует в топологии граней,
 * не разбивает чужие рёбра. Используется как опорная линия построений
 * и как источник снап-точек (концы, середина, тело).
 */
data class CadAxis(
    val id: UUID = UUID.randomUUID(),
    var Start: Point2,
    var End: Point2,
    var colorArgb: Int = 0xFF1565C0.toInt(),
    var thickness: Double = 1.5
) {
    fun clone(): CadAxis = CadAxis(id, Start, End, colorArgb, thickness)
}

/**
 * Вид точки оси: конец—начало, конец—конец или пересечение осей.
 */
enum class AxisPointKind { END_START, END_END, INTERSECTION }

/**
 * Производная точка оси — источник снап-точек и якорь размеров.
 * Полностью пересобирается [com.sit.litecad.geometry.AxisPointSync] из документа.axes,
 * но участвует в снапшоте undo/redo и сериализуется в .sit.
 */
data class CadAxisPoint(
    val id: UUID = UUID.randomUUID(),
    var position: Point2,
    val kind: AxisPointKind,
    val axisId: UUID,
    val otherAxisId: UUID? = null
) {
    fun clone(): CadAxisPoint = copy(position = Point2(position.X, position.Y))
}

/** Стиль выносных линий размера. */
enum class DimensionExtensionStyle { FULL, SHORT }

/**
 * Тип якоря размера. VERTEX — привязка к [CadVertex],
 * AXIS_POINT — привязка к точке оси [CadAxisPoint].
 */
enum class DimensionAnchorType { VERTEX, AXIS_POINT }

/**
 * Линейный размер между двумя вершинами документа.
 * Хранит Id вершин (не координаты) — чтобы размер «следовал» за геометрией.
 * Anchor-позиции — снимок на момент создания, для валидации.
 */
data class CadDimension(
    val id: UUID = UUID.randomUUID(),
    val FirstVertexId: UUID,
    val SecondVertexId: UUID,
    val FirstAnchorPosition: Point2,
    val SecondAnchorPosition: Point2,
    // Тип якоря для каждого конца. Id хранятся в FirstVertexId/SecondVertexId
    // (имена сохранены для обратной совместимости .sit).
    @SerializedName("FirstAnchorType") val FirstAnchorType: DimensionAnchorType = DimensionAnchorType.VERTEX,
    @SerializedName("SecondAnchorType") val SecondAnchorType: DimensionAnchorType = DimensionAnchorType.VERTEX,
    var Offset: Double = 0.0,
    var ExtensionStyle: DimensionExtensionStyle = DimensionExtensionStyle.FULL,
    var IsOrthogonal: Boolean = false,
    var OrthogonalIsHorizontal: Boolean = true,
    var TextSize: Double = 8.0,
    var colorArgb: Int = 0xFF1565C0.toInt(),   // синий по умолчанию (как у ПК DimensionNormal)
    var thickness: Double = 1.5
) {
    fun clone(): CadDimension = copy()
}
