package com.sit.litecad.pdf

/**
 * Толщины линий для PDF-экспорта по ЕСКД (аналог PdfLineweightTable из ПК-версии).
 * Значения — в миллиметрах. Минимум видимой печати — 0.1 мм.
 */
enum class PenStyle {
    AXIS,
    AXIS_EDGE,
    EDGE,
    EDGE_DASHED,
    EDGE_DOTTED,
    DIMENSION,
    EXTENSION
}

object PdfLineweightTable {

    const val AXIS_MM = 0.125
    const val AXIS_EDGE_MM = 0.125
    const val EDGE_MM = 0.18
    const val EDGE_DASHED_MM = 0.18
    const val EDGE_DOTTED_MM = 0.13
    const val DIMENSION_MM = 0.065
    const val EXTENSION_MM = 0.065

    const val MIN_VISIBLE_PRINT_THICKNESS_MM = 0.1

    private const val MM_PER_INCH = 25.4
    private const val POINTS_PER_INCH = 72.0

    /** Толщина минимума печати в пунктах PDF. */
    val minVisiblePrintThicknessPoints: Double
        get() = MIN_VISIBLE_PRINT_THICKNESS_MM * POINTS_PER_INCH / MM_PER_INCH

    /** Толщина в миллиметрах для стиля. */
    fun toMillimeters(style: PenStyle): Double = when (style) {
        PenStyle.AXIS -> AXIS_MM
        PenStyle.AXIS_EDGE -> AXIS_EDGE_MM
        PenStyle.EDGE -> EDGE_MM
        PenStyle.EDGE_DASHED -> EDGE_DASHED_MM
        PenStyle.EDGE_DOTTED -> EDGE_DOTTED_MM
        PenStyle.DIMENSION -> DIMENSION_MM
        PenStyle.EXTENSION -> EXTENSION_MM
    }

    /** Толщина в пунктах PDF (1 pt = 1/72 дюйма). */
    fun toPoints(style: PenStyle): Double =
        toMillimeters(style) * POINTS_PER_INCH / MM_PER_INCH

    /** Толщина в пунктах PDF с учётом минимума видимой печати. */
    fun toEffectivePoints(style: PenStyle): Double =
        maxOf(toPoints(style), minVisiblePrintThicknessPoints)

    fun mmToPoints(mm: Double): Double = mm * POINTS_PER_INCH / MM_PER_INCH

    fun pointsToMm(points: Double): Double = points * MM_PER_INCH / POINTS_PER_INCH
}
