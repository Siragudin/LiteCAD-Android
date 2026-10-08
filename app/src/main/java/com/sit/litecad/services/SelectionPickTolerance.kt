package com.sit.litecad.services

/**
 * Порог попадания пальцем для выделения.
 * Задаётся в экранных пикселях, конвертируется в мировые единицы через zoom.
 */
object SelectionPickTolerance {

    /** Радиус попадания в пикселях. ~24dp на типичном экране. */
    const val DEFAULT_PIXELS = 24.0

    /**
     * Экранные пиксели → мировые единицы.
     * zoom > 0, иначе возвращает DEFAULT_PIXELS (на всякий случай).
     */
    fun pixelsToWorld(pixels: Double, zoom: Double): Double {
        if (zoom <= 0.0) return pixels
        return pixels / zoom
    }
}