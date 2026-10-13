package com.sit.litecad.leaders

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Форматирование отметок высоты. Значения — в мм, округление до целого.
 * Модельные Y растут вверх, поэтому точка выше базы даёт положительную дельту.
 */
object ElevationFormatting {

    /** Δ высоты: Y текущей минус Y базы. */
    fun computeDelta(baseModelY: Double, currentModelY: Double): Double =
        currentModelY - baseModelY

    /** Формат "+300" / "-150" / "0". */
    fun format(deltaMillimeters: Double): String {
        val rounded = deltaMillimeters.roundToLong()
        if (abs(rounded) < 1L) return "0"
        return if (rounded > 0) "+$rounded" else rounded.toString()
    }
}
