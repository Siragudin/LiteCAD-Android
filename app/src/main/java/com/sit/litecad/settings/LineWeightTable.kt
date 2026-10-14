package com.sit.litecad.settings

import com.sit.litecad.R

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

    /** Ресурс подписи толщины: «1», «1.5», … (неизвестное значение — как дефолт). */
    fun displayRes(value: Double): Int = when (value) {
        1.0 -> R.string.line_weight_1
        1.5 -> R.string.line_weight_1_5
        2.0 -> R.string.line_weight_2
        3.0 -> R.string.line_weight_3
        4.0 -> R.string.line_weight_4
        5.0 -> R.string.line_weight_5
        else -> R.string.line_weight_1
    }
}
