package com.sit.litecad.tools

import com.sit.litecad.math.Point2
import java.util.UUID

/** Ось точного смещения при перемещении. */
enum class MoveAxis { X, Y }

sealed interface PendingInput {
    data class Length(val initial: Double) : PendingInput
    data class RectangleSize(val width: Double, val height: Double) : PendingInput

    /** Ожидание ввода угла сектора (в градусах). По умолчанию — 90°. */
    data class SectorAngle(val initial: Double = 90.0) : PendingInput

    /** Ожидание ввода точного смещения перемещения вдоль одной оси. */
    data class MoveDistance(val initial: Double, val axis: MoveAxis) : PendingInput

    /** Ожидание ввода угла поворота (в градусах). */
    data class RotationAngle(val initial: Double) : PendingInput

    /** Ожидание ввода величины растяжения. */
    data class StretchDistance(val initial: Double) : PendingInput

    /** Ожидание ввода величины смещения при эквидистанте. */
    data class OffsetDistance(val initial: Double) : PendingInput

    /**
     * Ожидание ввода текста надписи.
     * @param initialText предзаполненный текст (пустая строка — новая надпись)
     * @param position мировая позиция надписи (мм) — для новой надписи точка тапа
     * @param editingId Id редактируемой надписи; null — создаётся новая
     */
    data class Text(
        val initialText: String,
        val position: Point2,
        val editingId: UUID?
    ) : PendingInput
}
