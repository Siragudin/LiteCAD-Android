package com.sit.litecad.tools

sealed interface PendingInput {
    data class Length(val initial: Double) : PendingInput
    data class RectangleSize(val width: Double, val height: Double) : PendingInput
}
