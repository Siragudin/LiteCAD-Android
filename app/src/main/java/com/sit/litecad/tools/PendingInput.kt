package com.sit.litecad.tools

/** Ось точного смещения при перемещении. */
enum class MoveAxis { X, Y }

sealed interface PendingInput {
    data class Length(val initial: Double) : PendingInput
    data class RectangleSize(val width: Double, val height: Double) : PendingInput

    /** Ожидание ввода угла сектора (в градусах). По умолчанию — 90°. */
    data class SectorAngle(val initial: Double = 90.0) : PendingInput

    /** Ожидание ввода точного смещения перемещения вдоль одной оси. */
    data class MoveDistance(val initial: Double, val axis: MoveAxis) : PendingInput
}
