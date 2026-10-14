package com.sit.litecad.tools

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.settings.ToolSettings
import com.sit.litecad.ui.canvas.Camera

interface Tool {

    val currentMeasurement: String
        get() = ""

    /** Значения превью для второго ряда топбара. Пусто — инструмент ничего не показывает. */
    val previewMeasurements: List<PreviewMeasurement>
        get() = emptyList()

    fun onInputSubmitted(value: Double, document: CadDocument) {}
    fun onInputCancelled(document: CadDocument) {}
    fun onRectangleInputSubmitted(width: Double, height: Double, document: CadDocument) {}
    fun onRectangleInputCancelled(document: CadDocument) {}
    fun onSectorAngleSubmitted(angle: Double, document: CadDocument) {}
    fun onSectorAngleCancelled(document: CadDocument) {}
    fun onMoveInputSubmitted(value: Double, document: CadDocument) {}
    fun onMoveInputCancelled(document: CadDocument) {}
    fun onRotationAngleSubmitted(value: Double, document: CadDocument) {}
    fun onRotationAngleCancelled(document: CadDocument) {}
    fun onOffsetInputSubmitted(value: Double, document: CadDocument) {}
    fun onOffsetInputCancelled(document: CadDocument) {}
    fun onStart(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings)
    fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings)
    fun onEnd(document: CadDocument)
    fun drawPreview(scope: DrawScope, camera: Camera)
    fun cancel()
}
