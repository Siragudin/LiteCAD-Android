package com.sit.litecad.tools

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.ui.canvas.Viewport

interface Tool {

    val currentMeasurement: String
        get() = ""

    // Палец коснулся холста (передаем мировую точку и наш графовый документ чертежа)
    fun onStart(point: Point2, document: CadDocument)

    // Палец двигается
    fun onMove(point: Point2, document: CadDocument)

    // Палец оторвали (фиксация геометрии)
    fun onEnd(document: CadDocument)

    // Отрисовка черновика на холсте
    fun drawPreview(scope: DrawScope, viewport: Viewport)

    // Сброс инструмента
    fun cancel()
}
