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
