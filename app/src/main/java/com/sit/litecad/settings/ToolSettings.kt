package com.sit.litecad.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sit.litecad.model.EdgeLineType

/** Состояние настроек инструмента «Линия». */
class LineSettingsState {
    var orthoEnabled by mutableStateOf(false)
    var exactInputEnabled by mutableStateOf(false)
    var thickness by mutableStateOf(LineWeightTable.DEFAULT)
    var color by mutableStateOf(0xFF222222.toInt())
    var lineType by mutableStateOf(EdgeLineType.SOLID)
}

/** Состояние настроек инструмента «Прямоугольник». */
class RectSettingsState {
    var exactInputEnabled by mutableStateOf(false)
    var thickness by mutableStateOf(LineWeightTable.DEFAULT)
    var color by mutableStateOf(0xFF222222.toInt())
    var lineType by mutableStateOf(EdgeLineType.SOLID)
}

/** Состояние настроек инструмента «Круг». */
class CircleSettingsState {
    var exactInputEnabled by mutableStateOf(false)
    var thickness by mutableStateOf(LineWeightTable.DEFAULT)
    var color by mutableStateOf(0xFF222222.toInt())
    var lineType by mutableStateOf(EdgeLineType.SOLID)
}

/** Состояние настроек инструмента «Сектор». */
class SectorSettingsState {
    var exactInputEnabled by mutableStateOf(false)
    var thickness by mutableStateOf(LineWeightTable.DEFAULT)
    var color by mutableStateOf(0xFF222222.toInt())
    var lineType by mutableStateOf(EdgeLineType.SOLID)
}

/** Состояние настроек инструмента «Перемещение». */
class MoveSettingsState {
    var orthoEnabled by mutableStateOf(false)
    var copyMode by mutableStateOf(false)
    var exactInputEnabled by mutableStateOf(false)
}

/** Состояние настроек инструмента «Ось». */
class AxisSettingsState {
    var orthoEnabled by mutableStateOf(true)
    var exactInputEnabled by mutableStateOf(false)
    var thickness by mutableStateOf(1.5)
    var color by mutableStateOf(0xFF1565C0.toInt())
}

/** Состояние настроек инструмента «Выбор». */
class SelectSettingsState {
    var selectionMultiMode by mutableStateOf(false)
}

/** Общие настройки, не привязанные к конкретному инструменту. */
class CommonSettingsState {
    var snapEnabled by mutableStateOf(true)
}

/** Общий контейнер состояний всех инструментов. */
class ToolSettings {
    val line = LineSettingsState()
    val rect = RectSettingsState()
    val circle = CircleSettingsState()
    val sector = SectorSettingsState()
    val axis = AxisSettingsState()
    val move = MoveSettingsState()
    val select = SelectSettingsState()
    val common = CommonSettingsState()
}
