package com.sit.litecad.settings

import com.sit.litecad.R

/**
 * Палитра цветов заливки граней.
 *
 * Значения заданы как RGB — точно как в ПК-версии (FaceFillService.ParsePaletteTag).
 * ARGB = 0xFF000000 | RGB (alpha непрозрачная). Не храните ARGB литералы «вручную» —
 * используйте [FillColorTable.argb] / [FillColorTable.entries].
 */
object FillColorTable {

    // --- RGB-значения из ПК-версии ---
    /** Светло-серый (по умолчанию). ПК: Color.FromRgb(0xF0, 0xF0, 0xF0). */
    const val RGB_LIGHT_GRAY = 0xF0F0F0

    /** Чёрный. ПК: Color.FromRgb(0x22, 0x22, 0x22). */
    const val RGB_BLACK = 0x222222

    /** Серый. ПК: Color.FromRgb(0x66, 0x66, 0x66). */
    const val RGB_GRAY = 0x666666

    /** Синий. ПК: Colors.Blue (0x0000FF). */
    const val RGB_BLUE = 0x0000FF

    /** Красный. ПК: Colors.Red (0xFF0000). */
    const val RGB_RED = 0xFF0000

    /** Зелёный. ПК: Colors.Green (0x008000). */
    const val RGB_GREEN = 0x008000

    /** Цвет по умолчанию (ARGB, непрозрачный светло-серый). */
    val defaultArgb: Int = argb(RGB_LIGHT_GRAY)

    /** RGB → ARGB (alpha = 0xFF). */
    fun argb(rgb: Int): Int = 0xFF000000.toInt() or (rgb and 0xFFFFFF)

    /** Один элемент палитры: RGB-значение и ресурс имени. */
    data class Entry(val rgb: Int, val nameRes: Int) {
        val argb: Int get() = FillColorTable.argb(rgb)
    }

    /** Палитра в порядке отображения. */
    val entries: List<Entry> = listOf(
        Entry(RGB_LIGHT_GRAY, R.string.fill_color_light_gray),
        Entry(RGB_BLACK, R.string.fill_color_black),
        Entry(RGB_GRAY, R.string.fill_color_gray),
        Entry(RGB_BLUE, R.string.fill_color_blue),
        Entry(RGB_RED, R.string.fill_color_red),
        Entry(RGB_GREEN, R.string.fill_color_green)
    )

    /** Ресурс имени для ARGB-значения (для кнопки панели). */
    fun nameResFor(argb: Int): Int =
        entries.firstOrNull { it.argb == argb }?.nameRes ?: R.string.fill_color_light_gray
}
