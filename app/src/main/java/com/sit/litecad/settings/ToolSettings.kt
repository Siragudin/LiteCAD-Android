package com.sit.litecad.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sit.litecad.model.EdgeLineType

/**
 * Общие настройки активного инструмента.
 * Один экземпляр на EditorScreen, читается всеми инструментами.
 */
class ToolSettings {

    var thickness: Double by mutableStateOf(LineWeightTable.DEFAULT)

    var color: Int by mutableStateOf(0xFF222222.toInt())

    var lineType: EdgeLineType by mutableStateOf(EdgeLineType.SOLID)

    var orthoEnabled: Boolean by mutableStateOf(false)

    var snapEnabled: Boolean by mutableStateOf(true)

    var exactInputEnabled: Boolean by mutableStateOf(false)

    var selectionMultiMode: Boolean by mutableStateOf(false)
}