package com.sit.litecad.settings

import com.sit.litecad.R
import com.sit.litecad.model.EdgeLineType

/**
 * Список типов линий, совпадает с ПК-версией.
 */
object LineTypeTable {

    data class Entry(val nameRes: Int, val type: EdgeLineType)

    val values: List<Entry> = listOf(
        Entry(R.string.line_type_solid, EdgeLineType.SOLID),
        Entry(R.string.line_type_dashed, EdgeLineType.DASHED),
        Entry(R.string.line_type_dotted, EdgeLineType.DOTTED)
    )

    val DEFAULT: EdgeLineType = EdgeLineType.SOLID

    fun nameResFor(type: EdgeLineType): Int =
        values.firstOrNull { it.type == type }?.nameRes ?: R.string.line_type_solid
}
