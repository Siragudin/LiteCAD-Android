package com.sit.litecad.model

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

/** Стиль выносных линий размера. */
enum class DimensionExtensionStyle { FULL, SHORT }

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
