package com.sit.litecad.tools

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.CadText
import com.sit.litecad.model.TextRotation
import com.sit.litecad.model.TextSizeSp
import com.sit.litecad.services.DocumentHistoryService
import com.sit.litecad.settings.ToolSettings
import com.sit.litecad.ui.canvas.Camera
import java.util.UUID
import kotlin.math.abs

/** Допуск попадания пальцем в надпись (в экранных пикселях). */
private const val TEXT_PICK_TOLERANCE_PX = 24.0

/** Оценка средней ширины символа относительно размера шрифта (для хит-теста). */
private const val TEXT_CHAR_WIDTH_FACTOR = 0.6

/** Оценка высоты строки относительно размера шрифта (для хит-теста). */
private const val TEXT_LINE_HEIGHT_FACTOR = 1.2

/**
 * Инструмент «Текст» (надписи на чертеже).
 *
 * Работает одним тапом:
 *  - тап по существующей надписи — открывает панель ввода с её текстом (редактирование);
 *  - тап по пустому месту — открывает панель ввода для новой надписи в точке тапа.
 *
 * Пока панель ввода открыта (есть [pendingPosition] или [editingTextId]), тапы по холсту
 * игнорируются: CadCanvas не блокирует ввод при открытом pendingInput, поэтому защита
 * живёт здесь.
 *
 * Размер шрифта хранится в sp — это фиксированный ЭКРАННЫЙ размер, он не зависит от зума.
 * Поэтому хит-тест считается в экранных пикселях (мировое смещение × zoom), а не в мм.
 */
class TextToolHandler(
    private val historyService: DocumentHistoryService,
    private val toolManager: ToolManager
) : Tool {

    /** Id редактируемой надписи (тап попал в существующий текст). */
    private var editingTextId: UUID? = null

    /** Мировая позиция новой надписи (тап по пустому месту). */
    private var pendingPosition: Point2? = null

    override fun onStart(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        // Панель ввода уже открыта — тапы по холсту не обрабатываем.
        if (pendingPosition != null || editingTextId != null) return

        val hit = findTextAt(point, document, zoom)
        if (hit != null) {
            editingTextId = hit.id
            toolManager.requestInput(
                PendingInput.Text(
                    initialText = hit.text,
                    position = hit.position,
                    editingId = hit.id
                )
            )
            return
        }

        pendingPosition = point
        toolManager.requestInput(
            PendingInput.Text(
                initialText = "",
                position = point,
                editingId = null
            )
        )
    }

    override fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        // Надпись ставится тапом, протяжка не используется.
    }

    override fun onEnd(document: CadDocument) {
        // Коммит выполняется по нажатию галочки в панели ввода (onTextSubmitted).
    }

    override fun drawPreview(scope: DrawScope, camera: Camera) {
        // Превью не требуется: надпись появляется только после ввода текста.
    }

    override fun cancel() {
        reset()
    }

    override fun onTextSubmitted(newText: String, pending: PendingInput.Text, document: CadDocument) {
        val textSettings = toolManager.settings.text
        val value = newText.trim()

        if (pending.editingId != null) {
            // Редактирование существующей надписи.
            val existing = document.textById(pending.editingId)
            if (existing != null) {
                if (value.isEmpty()) {
                    // Пустой текст = удаление надписи.
                    historyService.record(document)
                    document.removeText(existing)
                    document.bumpRevision()
                } else if (hasChanges(existing, value, textSettings.sizeSp, textSettings.rotation, textSettings.color)) {
                    historyService.record(document)
                    existing.text = value
                    existing.sizeSp = textSettings.sizeSp
                    existing.rotation = textSettings.rotation
                    existing.colorArgb = textSettings.color
                    document.bumpRevision()
                }
            }
        } else if (value.isNotEmpty()) {
            // Новая надпись. Пустой текст — просто отмена, в документ ничего не пишем.
            historyService.record(document)
            document.addText(
                CadText(
                    position = pending.position,
                    text = value,
                    sizeSp = textSettings.sizeSp,
                    rotation = textSettings.rotation,
                    colorArgb = textSettings.color
                )
            )
            document.bumpRevision()
        }

        reset()
    }

    override fun onTextCancelled(document: CadDocument) {
        // Полная отмена: в документ ничего не коммитим.
        reset()
    }

    private fun reset() {
        editingTextId = null
        pendingPosition = null
    }

    /** Есть ли реальные изменения — чтобы не плодить пустые шаги undo. */
    private fun hasChanges(
        text: CadText,
        value: String,
        sizeSp: TextSizeSp,
        rotation: TextRotation,
        colorArgb: Int
    ): Boolean =
        text.text != value ||
            text.sizeSp != sizeSp ||
            text.rotation != rotation ||
            text.colorArgb != colorArgb

    // --- Хит-тест ---

    /** Ближайшая к тапу надпись, в чей экранный прямоугольник он попал (иначе null). */
    private fun findTextAt(point: Point2, document: CadDocument, zoom: Double): CadText? {
        var best: CadText? = null
        var bestDistance = Double.MAX_VALUE

        for (text in document.texts) {
            if (!hitTestText(text, point, zoom)) continue

            val dx = (point.X - text.position.X) * zoom
            val dy = (point.Y - text.position.Y) * zoom
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                bestDistance = distance
                best = text
            }
        }
        return best
    }

    /**
     * Попадание точки тапа в прямоугольник надписи.
     *
     * Прямоугольник задаётся в ЭКРАННЫХ пикселях вокруг позиции надписи
     * (ширина ≈ длина строки × размер шрифта × 0.6, высота ≈ размер шрифта × 1.2),
     * поэтому мировой сдвиг тапа умножается на zoom, а размер шрифта переводится
     * из sp в px через [ToolManager.textPixelScale] (density × fontScale).
     * Для [TextRotation.DEG_90] поворот снимается — проверка идёт в системе координат
     * самой надписи.
     */
    private fun hitTestText(text: CadText, point: Point2, zoom: Double): Boolean {
        val scale = toolManager.textPixelScale.toDouble().coerceAtLeast(0.01)
        val fontPx = text.sizeSp.sp * scale
        val halfWidthPx = text.text.length * fontPx * TEXT_CHAR_WIDTH_FACTOR * 0.5
        val halfHeightPx = fontPx * TEXT_LINE_HEIGHT_FACTOR * 0.5

        // Экранные оси: X — вправо, Y — вниз (мировой Y направлен вверх).
        var dx = (point.X - text.position.X) * zoom
        var dy = -(point.Y - text.position.Y) * zoom

        if (text.rotation == TextRotation.DEG_90) {
            // Обратный поворот на 90° (по часовой) вокруг позиции надписи.
            val tmp = dx
            dx = dy
            dy = -tmp
        }

        return abs(dx) <= halfWidthPx + TEXT_PICK_TOLERANCE_PX &&
            abs(dy) <= halfHeightPx + TEXT_PICK_TOLERANCE_PX
    }
}
