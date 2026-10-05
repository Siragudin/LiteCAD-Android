package com.sit.litecad.ui.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.sit.litecad.math.Point2

class Viewport {
    // Реактивные стейты — Compose автоматически перерисует холст, если они изменятся
    var zoom by mutableStateOf(1.0)
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
        val worldY = (screenOffset.y - panY) / zoom
        return Point2(worldX, worldY)
    }

    /**
     * Конвертация: Мировые миллиметры CAD (Point2) -> Экранные пиксели Android (Offset)
     * Используется графическим холстом при отрисовке линий
     */
    fun worldToScreen(worldPoint: Point2): Offset {
        val screenX = (worldPoint.X * zoom) + panX
        val screenY = (worldPoint.Y * zoom) + panY
        return Offset(screenX.toFloat(), screenY.toFloat())
    }

    /**
     * Метод для изменения панорамирования (сдвига камеры) через Offset (из жестов)
     */
    fun addPan(delta: Offset) {
        panX += delta.x
        panY += delta.y
    }

    fun toCamera(): Camera = Camera(zoom, panX, panY)
}
