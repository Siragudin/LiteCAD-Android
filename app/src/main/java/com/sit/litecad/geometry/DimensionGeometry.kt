package com.sit.litecad.geometry

import com.sit.litecad.math.Point2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Готовая раскладка линейного размера: точки выносных линий, размерной линии,
 * измеренная длина и угол текста (без самого текста).
 */
data class DimensionLayout(
    val firstAnchor: Point2,
    val secondAnchor: Point2,
    val firstExtensionEnd: Point2,
    val secondExtensionEnd: Point2,
    val dimensionLineStart: Point2,
    val dimensionLineEnd: Point2,
    val measuredDistance: Double,
    val textAngleRadians: Double
)

/**
 * Эквивалент DimensionGeometry из ПК-версии.
 * Чистая геометрия раскладки размера — без модели документа и без текста.
 */
object DimensionGeometry {

    /**
     * Универсальный вход: выбирает aligned или орто-раскладку.
     */
    fun tryCreateLayout(
        firstAnchor: Point2,
        secondAnchor: Point2,
        offset: Double,
        tolerance: Double,
        isOrthogonal: Boolean,
        orthogonalIsHorizontal: Boolean
    ): DimensionLayout? {
        return if (isOrthogonal) {
            if (orthogonalIsHorizontal) {
                tryCreateHorizontalOrthogonalLayout(firstAnchor, secondAnchor, offset, tolerance)
            } else {
                tryCreateVerticalOrthogonalLayout(firstAnchor, secondAnchor, offset, tolerance)
            }
        } else {
            tryCreateAlignedLayout(firstAnchor, secondAnchor, offset, tolerance)
        }
    }

    /**
     * Выровненный размер: выносные линии идут по нормали к измеряемому отрезку.
     */
    fun tryCreateAlignedLayout(
        firstAnchor: Point2,
        secondAnchor: Point2,
        offset: Double,
        tolerance: Double
    ): DimensionLayout? {
        val dx = secondAnchor.X - firstAnchor.X
        val dy = secondAnchor.Y - firstAnchor.Y
        val length = sqrt(dx * dx + dy * dy)
        if (length <= tolerance) return null

        val nx = -dy / length
        val ny = dx / length

        val firstExtensionEnd = Point2(firstAnchor.X + nx * offset, firstAnchor.Y + ny * offset)
        val secondExtensionEnd = Point2(secondAnchor.X + nx * offset, secondAnchor.Y + ny * offset)

        return DimensionLayout(
            firstAnchor = firstAnchor,
            secondAnchor = secondAnchor,
            firstExtensionEnd = firstExtensionEnd,
            secondExtensionEnd = secondExtensionEnd,
            dimensionLineStart = firstExtensionEnd,
            dimensionLineEnd = secondExtensionEnd,
            measuredDistance = length,
            textAngleRadians = atan2(dy, dx)
        )
    }

    /**
     * Горизонтальный орто-размер: измеряет только разницу по X, размерная линия горизонтальна.
     */
    fun tryCreateHorizontalOrthogonalLayout(
        firstAnchor: Point2,
        secondAnchor: Point2,
        offset: Double,
        tolerance: Double
    ): DimensionLayout? {
        val dx = secondAnchor.X - firstAnchor.X
        if (abs(dx) <= tolerance) return null

        val dimensionLineY = (firstAnchor.Y + secondAnchor.Y) / 2.0 + offset
        val firstExtensionEnd = Point2(firstAnchor.X, dimensionLineY)
        val secondExtensionEnd = Point2(secondAnchor.X, dimensionLineY)

        return DimensionLayout(
            firstAnchor = firstAnchor,
            secondAnchor = secondAnchor,
            firstExtensionEnd = firstExtensionEnd,
            secondExtensionEnd = secondExtensionEnd,
            dimensionLineStart = firstExtensionEnd,
            dimensionLineEnd = secondExtensionEnd,
            measuredDistance = abs(dx),
            textAngleRadians = 0.0
        )
    }

    /**
     * Вертикальный орто-размер: измеряет только разницу по Y, размерная линия вертикальна.
     */
    fun tryCreateVerticalOrthogonalLayout(
        firstAnchor: Point2,
        secondAnchor: Point2,
        offset: Double,
        tolerance: Double
    ): DimensionLayout? {
        val dy = secondAnchor.Y - firstAnchor.Y
        if (abs(dy) <= tolerance) return null

        val dimensionLineX = (firstAnchor.X + secondAnchor.X) / 2.0 + offset
        val firstExtensionEnd = Point2(dimensionLineX, firstAnchor.Y)
        val secondExtensionEnd = Point2(dimensionLineX, secondAnchor.Y)

        return DimensionLayout(
            firstAnchor = firstAnchor,
            secondAnchor = secondAnchor,
            firstExtensionEnd = firstExtensionEnd,
            secondExtensionEnd = secondExtensionEnd,
            dimensionLineStart = firstExtensionEnd,
            dimensionLineEnd = secondExtensionEnd,
            measuredDistance = abs(dy),
            textAngleRadians = -PI / 2.0
        )
    }

    /**
     * Знаковое смещение курсора относительно нормали измеряемого отрезка.
     */
    fun computeSignedOffset(firstAnchor: Point2, secondAnchor: Point2, cursor: Point2): Double {
        val dx = secondAnchor.X - firstAnchor.X
        val dy = secondAnchor.Y - firstAnchor.Y
        val length = sqrt(dx * dx + dy * dy)
        if (length <= 0.0) return 0.0

        val nx = -dy / length
        val ny = dx / length
        return (cursor.X - firstAnchor.X) * nx + (cursor.Y - firstAnchor.Y) * ny
    }

    /**
     * Знаковое смещение курсора для орто-режима (по X или Y в зависимости от ориентации).
     */
    fun computeOrthogonalSignedOffset(
        firstAnchor: Point2,
        secondAnchor: Point2,
        cursor: Point2,
        orthogonalIsHorizontal: Boolean
    ): Double {
        return if (orthogonalIsHorizontal) {
            cursor.Y - (firstAnchor.Y + secondAnchor.Y) / 2.0
        } else {
            cursor.X - (firstAnchor.X + secondAnchor.X) / 2.0
        }
    }

    /**
     * Выбор ориентации орто-размера по положению курсора относительно середины отрезка.
     * Перевес по вертикали → горизонтальный размер.
     */
    fun resolveOrthogonalIsHorizontal(
        firstAnchor: Point2,
        secondAnchor: Point2,
        cursor: Point2
    ): Boolean {
        val midX = (firstAnchor.X + secondAnchor.X) / 2.0
        val midY = (firstAnchor.Y + secondAnchor.Y) / 2.0
        return abs(cursor.X - midX) < abs(cursor.Y - midY)
    }
}
