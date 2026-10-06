package com.sit.litecad.settings

/**
 * Список толщин линий в экранных единицах (пиксели при зуме 1.0).
 * Совпадает со списком в ПК-версии.
 */
object LineWeightTable {

    val values: List<Double> = listOf(
        1.0,
        1.5,
        2.0,
        3.0,
        4.0,
        5.0
    )

    const val DEFAULT: Double = 1.0

    fun display(value: Double): String =
        if (value == value.toInt().toDouble()) {
            value.toInt().toString()
        } else {
            value.toString()
        }
}