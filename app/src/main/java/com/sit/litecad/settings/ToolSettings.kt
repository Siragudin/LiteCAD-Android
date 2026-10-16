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

/** Состояние настроек инструмента «Растянуть». */
class StretchSettingsState {
    var exactInputEnabled by mutableStateOf(false)
}

/** Состояние настроек инструмента «Эквидистанта». */
class OffsetSettingsState {
    var exactInputEnabled by mutableStateOf(true)
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

/** Состояние настроек инструмента «Заливка». */
class FillSettingsState {
    var color by mutableStateOf(FillColorTable.defaultArgb)
}

/** Общие настройки, не привязанные к конкретному инструменту. */
class CommonSettingsState {
    var snapEnabled by mutableStateOf(true)
    var activeLineTool by mutableStateOf("line")     // "line" | "axis"
    var activeCurveTool by mutableStateOf("circle")  // "circle" | "sector" | "arc"

    /** Показывать вершины документа на холсте. */
    var showVertices by mutableStateOf(false)
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
    val stretch = StretchSettingsState()
    val offset = OffsetSettingsState()
    val select = SelectSettingsState()
    val dim = DimSettingsState()
    val leader = LeaderSettingsState()
    val fill = FillSettingsState()
    val common = CommonSettingsState()

    // Шаблоны строк currentMeasurement (у хендлеров нет Context — заполняются из ресурсов в EditorScreen).
    var measurementOffset: String = "Offset: %d"
    var measurementStretch: String = "Stretch: %d"
    var measurementDimOffset: String = "Offset: %d"
    var measurementMirrorLength: String = "Length: %d"
    var measurementSectorAngle: String = "Angle: %d°"
    var promptDrag: String = "Drag"
    var promptDragHeight: String = "Drag height"
    var promptPickArcEnd: String = "Pick arc end"
    var promptDragToEndpoint: String = "Drag to endpoint"
    var promptDragToRadius: String = "Drag to radius"
    var promptDragToDimensionEnd: String = "Drag to end"
    var promptDragDimensionOffset: String = "Drag offset"
    var promptDragAxis: String = "Drag axis"
    var promptDragToCorner: String = "Drag to opposite corner"
    var promptDragForReference: String = "Drag for reference"
    var promptDragToRotate: String = "Drag to rotate"
    var promptDragSectorVertex: String = "Drag vertex to set angle"
}
