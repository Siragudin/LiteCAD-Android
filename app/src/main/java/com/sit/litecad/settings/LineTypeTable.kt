package com.sit.litecad.settings

import com.sit.litecad.model.EdgeLineType

/**
 * Список типов линий, совпадает с ПК-версией.
 */
object LineTypeTable {

    data class Entry(val name: String, val type: EdgeLineType)

    val values: List<Entry> = listOf(
        Entry("Сплошная", EdgeLineType.SOLID),
        Entry("Штриховая", EdgeLineType.DASHED),
        Entry("Точечная", EdgeLineType.DOTTED)
    )

    val DEFAULT: EdgeLineType = EdgeLineType.SOLID

    fun nameFor(type: EdgeLineType): String =
        values.firstOrNull { it.type == type }?.name ?: "Сплошная"
}