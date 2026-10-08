package com.sit.litecad.tools

sealed interface PendingInput {
    data class Length(val initial: Double) : PendingInput
    data class RectangleSize(val width: Double, val height: Double) : PendingInput

    /** Ожидание ввода угла сектора (в градусах). По умолчанию — 90°. */
    data class SectorAngle(val initial: Double = 90.0) : PendingInput
}
