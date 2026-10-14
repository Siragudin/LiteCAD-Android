package com.sit.litecad.pdf

/** Формат листа PDF. Пока только A4. */
enum class PdfPaperFormat(val widthMm: Double, val heightMm: Double) {
    A4(210.0, 297.0)
}

/** Ориентация листа. */
enum class PdfPageOrientation { PORTRAIT, LANDSCAPE }

/**
 * Параметры листа PDF: формат, ориентация, поля.
 * Поля по умолчанию 10 мм, как в ПК-версии.
 */
data class PdfPageSpec(
    val format: PdfPaperFormat = PdfPaperFormat.A4,
    val orientation: PdfPageOrientation = PdfPageOrientation.PORTRAIT,
    val marginMm: Double = 10.0
) {
    /** Ширина страницы в миллиметрах с учётом ориентации. */
    val pageWidthMm: Double
        get() = if (orientation == PdfPageOrientation.LANDSCAPE) format.heightMm else format.widthMm

    /** Высота страницы в миллиметрах с учётом ориентации. */
    val pageHeightMm: Double
        get() = if (orientation == PdfPageOrientation.LANDSCAPE) format.widthMm else format.heightMm

    /** Ширина печатной области в миллиметрах (без полей). */
    val contentWidthMm: Double get() = pageWidthMm - marginMm * 2

    /** Высота печатной области в миллиметрах (без полей). */
    val contentHeightMm: Double get() = pageHeightMm - marginMm * 2

    companion object {
        /** A4 с авто-ориентацией по соотношению сторон чертежа. */
        fun autoOrientation(worldWidth: Double, worldHeight: Double): PdfPageSpec =
            if (worldWidth > worldHeight) {
                PdfPageSpec(orientation = PdfPageOrientation.LANDSCAPE)
            } else {
                PdfPageSpec(orientation = PdfPageOrientation.PORTRAIT)
            }
    }
}
