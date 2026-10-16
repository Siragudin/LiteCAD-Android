package com.sit.litecad.ui.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.sit.litecad.math.Point2

class Viewport {
    // Реактивные стейты — Compose автоматически перерисует холст, если они изменятся
    // Дефолтный зум 0.2: при ширине экрана ~1000px видно ~5000 единиц мира (~5 м).
    var zoom by mutableStateOf(0.2)
    var panX by mutableStateOf(0.0)
    var panY by mutableStateOf(0.0)

    // Лимиты для зума, чтобы чертеж не исчезал из вида
    val minZoom = 0.01
    val maxZoom = 50.0

    /**
     * Конвертация: Экранные пиксели Android (Offset) -> Мировые миллиметры CAD (Point2)
     */
    fun screenToWorld(screenOffset: Offset): Point2 {
        val worldX = (screenOffset.x - panX) / zoom
        val worldY = (panY - screenOffset.y) / zoom
        return Point2(worldX, worldY)
    }

    /**
     * Конвертация: Мировые миллиметры CAD (Point2) -> Экранные пиксели Android (Offset)
     * Используется графическим холстом при отрисовке линий
     */
    fun worldToScreen(worldPoint: Point2): Offset {
        val screenX = (worldPoint.X * zoom) + panX
        val screenY = (-worldPoint.Y * zoom) + panY
        return Offset(screenX.toFloat(), screenY.toFloat())
    }

    /**
     * Метод для изменения панорамирования (сдвига камеры) через Offset (из жестов)
     */
    fun addPan(delta: Offset) {
        panX += delta.x
        panY += delta.y
    }

    /**
     * Поставить мировую точку (0,0) в геометрический центр экрана.
     * Вызывается один раз при первом получении размера канваса.
     */
    fun centerOn(sizeWidthPx: Int, sizeHeightPx: Int) {
        panX = sizeWidthPx / 2.0
        panY = sizeHeightPx / 2.0
    }

    fun toCamera(): Camera = Camera(zoom, panX, panY)
}
