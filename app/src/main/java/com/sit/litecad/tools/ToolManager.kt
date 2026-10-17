package com.sit.litecad.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.sit.litecad.geometry.AxisPointSync
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.Selection
import com.sit.litecad.services.DocumentHistoryService
import com.sit.litecad.services.EditService
import com.sit.litecad.services.SnapService
import com.sit.litecad.settings.ToolSettings

class ToolManager(
    val document: CadDocument,
    private val snapService: SnapService,
    private val historyService: DocumentHistoryService,
    val settings: ToolSettings,
    val selection: Selection
) {
    private val tools = mapOf<String, Tool>(
        "line" to LineToolHandler(snapService, historyService, this),
        "axis" to AxisToolHandler(snapService, historyService, this),
        "rect" to RectangleToolHandler(snapService, historyService, this),
        "circle" to CircleToolHandler(snapService, historyService, this),
        "sector" to SectorToolHandler(snapService, historyService, this),
        "arc" to ArcToolHandler(snapService, historyService, this),
        "select" to SelectionToolHandler(this),
        "erase" to EraserToolHandler(historyService, this),
        "move" to MoveToolHandler(snapService, historyService, this),
        "rotate" to RotateToolHandler(snapService, historyService, this),
        "mirror" to MirrorToolHandler(snapService, historyService, this),
        "stretch" to StretchToolHandler(historyService, this),
        "offset" to OffsetToolHandler(historyService, this),
        "extend" to ExtendToolHandler(historyService, this),
        "leader" to LeaderToolHandler(snapService, historyService, this),
        "dim" to DimensionToolHandler(snapService, historyService, this),
        "fill" to FillToolHandler(historyService, this),
        "text" to TextToolHandler(historyService, this)
    )

    var activeToolId by mutableStateOf("hand")
        private set

    /** Тёмная тема редактора — превью инструментов рисуются белым (см. [previewStrokeColor]). */
    var isDarkTheme: Boolean = false

    /**
     * Множитель sp → px (density × fontScale) для ЭКРАННЫХ оценок геометрии надписей.
     * Надпись ([com.sit.litecad.model.CadText]) хранит размер в sp, поэтому её экранный
     * прямоугольник (хит-тест инструмента «Текст») считается только с этим множителем.
     * Заполняется из EditorScreen (LocalDensity); 1f — безопасный фолбэк для тестов.
     */
    var textPixelScale: Float = 1f

    var pendingInput by mutableStateOf<PendingInput?>(null)
        private set

    val activeTool: Tool?
        get() = tools[activeToolId]

    fun selectTool(id: String) {
        activeTool?.cancel()
        pendingInput = null
        activeToolId = id
    }

    fun handleStart(worldPoint: Point2, zoom: Double) {
        activeTool?.onStart(worldPoint, document, zoom, settings)
    }

    fun handleMove(worldPoint: Point2, zoom: Double) {
        activeTool?.onMove(worldPoint, document, zoom, settings)
    }

    fun handleEnd() {
        activeTool?.onEnd(document)
    }

    fun requestInput(input: PendingInput) {
        pendingInput = input
    }

    fun submitInput(value: Double) {
        when (val pending = pendingInput) {
            null -> return
            is PendingInput.Length -> {
                pendingInput = null
                activeTool?.onInputSubmitted(value, document)
            }
            is PendingInput.RectangleSize -> {
                pendingInput = null
                activeTool?.onRectangleInputSubmitted(pending.width, pending.height, document)
            }
            is PendingInput.SectorAngle -> {
                pendingInput = null
                activeTool?.onSectorAngleSubmitted(value, document)
            }
            is PendingInput.MoveDistance -> {
                pendingInput = null
                activeTool?.onMoveInputSubmitted(value, document)
            }
            is PendingInput.RotationAngle -> {
                pendingInput = null
                activeTool?.onRotationAngleSubmitted(value, document)
            }
            is PendingInput.StretchDistance -> {
                pendingInput = null
                activeTool?.onStretchInputSubmitted(value, document)
            }
            is PendingInput.OffsetDistance -> {
                pendingInput = null
                activeTool?.onOffsetInputSubmitted(value, document)
            }
            // Ввод строки идёт через submitText() — числовое значение здесь не используется.
            is PendingInput.Text -> Unit
        }
    }

    /** Отправка размеров прямоугольника (Ширина × Высота) из панели точного ввода. */
    fun submitRectangleInput(width: Double, height: Double) {
        if (pendingInput !is PendingInput.RectangleSize) return
        pendingInput = null
        activeTool?.onRectangleInputSubmitted(width, height, document)
    }

    /** Отправка угла сектора из панели точного ввода. */
    fun submitSectorAngle(angle: Double) {
        if (pendingInput !is PendingInput.SectorAngle) return
        pendingInput = null
        activeTool?.onSectorAngleSubmitted(angle, document)
    }

    fun cancelInput() {
        when (pendingInput) {
            null -> return
            is PendingInput.Length -> {
                pendingInput = null
                activeTool?.onInputCancelled(document)
            }
            is PendingInput.RectangleSize -> {
                pendingInput = null
                activeTool?.onRectangleInputCancelled(document)
            }
            is PendingInput.SectorAngle -> {
                pendingInput = null
                activeTool?.onSectorAngleCancelled(document)
            }
            is PendingInput.MoveDistance -> {
                pendingInput = null
                activeTool?.onMoveInputCancelled(document)
            }
            is PendingInput.RotationAngle -> {
                pendingInput = null
                activeTool?.onRotationAngleCancelled(document)
            }
            is PendingInput.StretchDistance -> {
                pendingInput = null
                activeTool?.onStretchInputCancelled(document)
            }
            is PendingInput.OffsetDistance -> {
                pendingInput = null
                activeTool?.onOffsetInputCancelled(document)
            }
            // Отмена ввода строки идёт через cancelText().
            is PendingInput.Text -> Unit
        }
    }

    /** Отмена ввода размеров прямоугольника — коммит "как нарисовано". */
    fun cancelRectangleInput() {
        if (pendingInput !is PendingInput.RectangleSize) return
        pendingInput = null
        activeTool?.onRectangleInputCancelled(document)
    }

    /** Отмена ввода угла сектора — фигура не рисуется. */
    fun cancelSectorAngle() {
        if (pendingInput !is PendingInput.SectorAngle) return
        pendingInput = null
        activeTool?.onSectorAngleCancelled(document)
    }

    /** Отправка смещения перемещения из панели точного ввода. */
    fun submitMoveInput(value: Double) {
        if (pendingInput !is PendingInput.MoveDistance) return
        pendingInput = null
        activeTool?.onMoveInputSubmitted(value, document)
    }

    /** Отмена ввода смещения перемещения — ничего не коммитим. */
    fun cancelMoveInput() {
        if (pendingInput !is PendingInput.MoveDistance) return
        pendingInput = null
        activeTool?.onMoveInputCancelled(document)
    }

    /** Отправка угла поворота из панели точного ввода. */
    fun submitRotationAngle(value: Double) {
        if (pendingInput !is PendingInput.RotationAngle) return
        pendingInput = null
        activeTool?.onRotationAngleSubmitted(value, document)
    }

    /** Отмена ввода угла поворота — ничего не коммитим. */
    fun cancelRotationAngle() {
        if (pendingInput !is PendingInput.RotationAngle) return
        pendingInput = null
        activeTool?.onRotationAngleCancelled(document)
    }

    /** Отправка величины растяжения из панели точного ввода. */
    fun submitStretchInput(value: Double) {
        if (pendingInput !is PendingInput.StretchDistance) return
        pendingInput = null
        activeTool?.onStretchInputSubmitted(value, document)
    }

    /** Отмена ввода растяжения — ничего не коммитим. */
    fun cancelStretchInput() {
        if (pendingInput !is PendingInput.StretchDistance) return
        pendingInput = null
        activeTool?.onStretchInputCancelled(document)
    }

    /** Отправка величины смещения (эквидистанты) из панели точного ввода. */
    fun submitOffsetInput(value: Double) {
        if (pendingInput !is PendingInput.OffsetDistance) return
        pendingInput = null
        activeTool?.onOffsetInputSubmitted(value, document)
    }

    /** Отмена ввода эквидистанты — ничего не коммитим. */
    fun cancelOffsetInput() {
        if (pendingInput !is PendingInput.OffsetDistance) return
        pendingInput = null
        activeTool?.onOffsetInputCancelled(document)
    }

    /** Отправка текста из панели ввода надписи (создание или редактирование). */
    fun submitText(newText: String) {
        val pending = pendingInput as? PendingInput.Text ?: return
        pendingInput = null
        activeTool?.onTextSubmitted(newText, pending, document)
    }

    /** Отмена ввода текста надписи — в документ ничего не коммитится. */
    fun cancelText() {
        if (pendingInput !is PendingInput.Text) return
        pendingInput = null
        activeTool?.onTextCancelled(document)
    }

    /** Цвет превью-линии под текущую тему: на тёмной — белый, на светлой — исходный. */
    fun previewStrokeColor(base: Color): Color = if (isDarkTheme) Color.White else base

    val canUndo: Boolean
        get() = historyService.canUndo

    val canRedo: Boolean
        get() = historyService.canRedo

    fun undo() {
        historyService.undo(document)
        // Документ заменён целиком — пересобрать производные точки осей.
        AxisPointSync.sync(document)
        activeTool?.cancel()
        pendingInput = null
        pruneSelection()
    }

    fun redo() {
        historyService.redo(document)
        // Документ заменён целиком — пересобрать производные точки осей.
        AxisPointSync.sync(document)
        activeTool?.cancel()
        pendingInput = null
        pruneSelection()
    }

    private fun pruneSelection() {
        val edgeIds = document.edges.map { it.id }.toHashSet()
        val axisIds = document.axes.map { it.id }.toHashSet()
        val polygonIds = document.polygons.map { it.id }.toHashSet()
        val textIds = document.texts.map { it.id }.toHashSet()
        selection.pruneMissing(edgeIds, axisIds, polygonIds, textIds)
    }
    fun deleteSelected() {
        EditService.delete(document, selection, historyService)
        pruneSelection()
        activeTool?.cancel()
        pendingInput = null
    }
}