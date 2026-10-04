package com.sit.litecad.math

/**
 * Точка чертежа. Полный аналог C# структуры PointF.
 * Свойства X и Y пишутся с Большой буквы для полной совместимости с кодом ПК-версии.
 */
data class Point2(
    val X: Double,
    val Y: Double
) {
    companion object {
        // Эквивалент public static PointF Zero => new(0, 0);
        val Zero = Point2(0.0, 0.0)
    }

    // Оператор: Точка + Вектор = Точка (public static PointF operator +(PointF a, Vector2 v))
    operator fun plus(v: Vector2): Point2 = Point2(this.X + v.X, this.Y + v.Y)

    // Оператор: Точка - Точка = Точка (public static PointF operator -(PointF a, PointF b))
    operator fun minus(b: Point2): Point2 = Point2(this.X - b.X, this.Y - b.Y)

    // Оператор: Точка - Вектор = Вектор (public static Vector2 operator -(PointF a, Vector2 v))
    operator fun minus(v: Vector2): Vector2 = Vector2(this.X - v.X, this.Y - v.Y)

    /**
     * Полезные утилиты для совместимости с алгоритмами ядра
     */
    fun distanceTo(other: Point2): Double = MathUtils.distance(this, other)
}
