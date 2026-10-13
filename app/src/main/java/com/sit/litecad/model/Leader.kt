package com.sit.litecad.model

import com.sit.litecad.math.Point2
import java.util.UUID

/** Тип выноски. Пока используется только ELEVATION. */
enum class LeaderKind {
    TEXT,
    ELEVATION
}

/**
 * Выноска отметки высоты. Хранит цель (точка на чертеже), позицию полки с текстом
 * и текст (значение Δ высоты в мм).
 */
data class Leader(
    val id: UUID = UUID.randomUUID(),
    val kind: LeaderKind = LeaderKind.ELEVATION,
    var target: Point2,
    var textPosition: Point2,
    var text: String = "",
    var colorArgb: Int = 0xFF1565C0.toInt()
) {
    fun clone(): Leader = Leader(id, kind, target, textPosition, text, colorArgb)
}
