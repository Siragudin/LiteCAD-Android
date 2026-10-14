package com.sit.litecad.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sit.litecad.model.DimensionExtensionStyle
import com.sit.litecad.model.EdgeLineType

/** Состояние настроек инструмента «Линия». */
class LineSettingsState {
    var orthoEnabled by mutableStateOf(false)
    var exactInputEnabled by mutableStateOf(false)
    var thickness by mutableStateOf(LineWeightTable.DEFAULT)
    var color by mutableStateOf(0xFF222222.toInt())
    var lineType by mutableStateOf(EdgeLineType.SOLID)

    /** Округление длины в мм: 0 — выкл, 10 — 1 см, 100 — 10 см, 1000 — 1 м. */
    var roundingMm by mutableStateOf(0)
}

/** Состояние настроек инструмента «Прямоугольник». */
class RectSettingsState {
    var exactInputEnabled by mutableStateOf(false)
    var thickness by mutableStateOf(LineWeightTable.DEFAULT)
    var color by mutableStateOf(0xFF222222.toInt())
    var lineType by mutableStateOf(EdgeLineType.SOLID)

    /** Округление размеров в мм: 0 — выкл, 10 — 1 см, 100 — 10 см, 1000 — 1 м. */
    var roundingMm by mutableStateOf(0)
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

/** Состояние настроек инструмента «Дуга». */
class ArcSettingsState {
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

/** Состояние настроек инструмента «Поворот». */
class RotateSettingsState {
    var copyMode by mutableStateOf(false)
    var exactInputEnabled by mutableStateOf(false)
}

/** Состояние настроек инструмента «Зеркало». */
class MirrorSettingsState {
    var orthoEnabled by mutableStateOf(true)
    var copyMode by mutableStateOf(true)
}

/** Состояние настроек инструмента «Эквидистанта». */
class OffsetSettingsState {
    var exactInputEnabled by mutableStateOf(false)
}

/** Состояние настроек инструмента «Ось». */
class AxisSettingsState {
    var orthoEnabled by mutableStateOf(true)
    var exactInputEnabled by mutableStateOf(false)
    var thickness by mutableStateOf(1.5)
    var color by mutableStateOf(0xFF1565C0.toInt())

    /** Округление длины в мм: 0 — выкл, 10 — 1 см, 100 — 10 см, 1000 — 1 м. */
    var roundingMm by mutableStateOf(0)
}

/** Состояние настроек инструмента «Выбор». */
class SelectSettingsState {
    var selectionMultiMode by mutableStateOf(false)
}

/** Состояние настроек инструмента «Размер». */
class DimSettingsState {
    var orthoEnabled by mutableStateOf(false)
    var exactInputEnabled by mutableStateOf(false)
    var extensionStyle by mutableStateOf(DimensionExtensionStyle.FULL)
    var color by mutableStateOf(0xFF1565C0.toInt())
    var thickness by mutableStateOf(1.5)
    var textSize by mutableStateOf(8.0)
}

/** Состояние настроек инструмента «Выноска». */
class LeaderSettingsState {
    var color by mutableStateOf(0xFF1565C0.toInt())
}

/** Общие настройки, не привязанные к конкретному инструменту. */
class CommonSettingsState {
    var snapEnabled by mutableStateOf(true)
    var activeLineTool by mutableStateOf("line")     // "line" | "axis"
    var activeCurveTool by mutableStateOf("circle")  // "circle" | "sector" | "arc"
}

/** Общий контейнер состояний всех инструментов. */
class ToolSettings {
    val line = LineSettingsState()
    val rect = RectSettingsState()
    val circle = CircleSettingsState()
    val sector = SectorSettingsState()
    val arc = ArcSettingsState()
    val axis = AxisSettingsState()
    val move = MoveSettingsState()
    val rotate = RotateSettingsState()
    val mirror = MirrorSettingsState()
    val offset = OffsetSettingsState()
    val select = SelectSettingsState()
    val dim = DimSettingsState()
    val leader = LeaderSettingsState()
    val common = CommonSettingsState()
}
