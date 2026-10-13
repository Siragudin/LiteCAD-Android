package com.sit.litecad.leaders

import com.sit.litecad.math.Point2

/**
 * Раскладка выноски: все размеры — в экранных пикселях, делятся на zoom.
 * Стойка идёт вверх (в мировых координатах), полка — в сторону [side].
 */
data class LeaderLayout(
    val target: Point2,
    val elbow: Point2,
    val landingEnd: Point2,
    val textPosition: Point2,
    val baseStart: Point2,
    val baseEnd: Point2,
    val chevronLeft: Point2,
    val chevronRight: Point2,
    val side: Int
)

object LeaderGeometry {

    const val BASE_HALF_LENGTH_SCREEN = 30.0
    const val CHEVRON_HEIGHT_SCREEN = 40.0
    const val STEM_HEIGHT_SCREEN = 140.0
    const val SHELF_LENGTH_SCREEN = 180.0
    const val TEXT_HEIGHT_SCREEN = 60.0

    /**
     * Собрать геометрию выноски.
     * @param target точка на чертеже (цель)
     * @param textPosition позиция полки (используется только для определения стороны)
     * @param zoom масштаб (пиксели → мировые единицы через 1/zoom)
     */
    fun createLayout(target: Point2, textPosition: Point2, zoom: Double): LeaderLayout {
        val world = 1.0 / zoom.coerceAtLeast(1e-6)
        val baseHalf = BASE_HALF_LENGTH_SCREEN * world
        val chevronHeight = CHEVRON_HEIGHT_SCREEN * world
        val stemHeight = STEM_HEIGHT_SCREEN * world
        val shelfLength = SHELF_LENGTH_SCREEN * world
        val side = resolveSide(target, textPosition)

        // Стойка вверх: в мировых координатах +Y (как в ПК-версии).
        val elbow = Point2(target.X, target.Y + stemHeight)
        val landing = Point2(target.X + side * shelfLength, elbow.Y)
        val text = Point2((elbow.X + landing.X) * 0.5, elbow.Y)

        return LeaderLayout(
            target = target,
            elbow = elbow,
            landingEnd = landing,
            textPosition = text,
            baseStart = Point2(target.X - baseHalf, target.Y),
            baseEnd = Point2(target.X + baseHalf, target.Y),
            chevronLeft = Point2(target.X - baseHalf, target.Y + chevronHeight),
            chevronRight = Point2(target.X + baseHalf, target.Y + chevronHeight),
            side = side
        )
    }

    /** Полка фиксируется на Y цели — протяжка идёт строго горизонтально. */
    fun constrainHorizontal(measurePoint: Point2, placement: Point2): Point2 =
        Point2(placement.X, measurePoint.Y)

    /** Сторона полки: палец левее цели → -1, иначе +1. */
    fun resolveSide(measurePoint: Point2, placement: Point2): Int =
        if (placement.X < measurePoint.X - 1e-9) -1 else 1

    /** Все отрезки для отрисовки и hit-теста. */
    fun getSegments(layout: LeaderLayout): List<Pair<Point2, Point2>> = listOf(
        layout.baseStart to layout.baseEnd,
        layout.target to layout.chevronLeft,
        layout.target to layout.chevronRight,
        layout.target to layout.elbow,
        layout.elbow to layout.landingEnd
    )
}
