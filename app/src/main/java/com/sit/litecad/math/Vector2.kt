package com.sit.litecad.math

import kotlin.math.sqrt

/**
 * Класс двухмерного вектора. Полный аналог C# структуры Vector2.
 * Свойства X, Y и Length пишутся с заглавной буквы для 100% совместимости с алгоритмами ПК.
 */
data class Vector2(
    val X: Double,
    val Y: Double
) {
    // Эквивалент public double Length => Math.Sqrt(X * X + Y * Y);
    val Length: Double
        get() = sqrt(X * X + Y * Y)

    companion object {
        // Эквивалент public static Vector2 Zero => new(0, 0);
        val Zero = Vector2(0.0, 0.0)
    }

    // Оператор: Вектор + Вектор = Вектор (public static Vector2 operator +(Vector2 a, Vector2 b))
    operator fun plus(b: Vector2): Vector2 = Vector2(this.X + b.X, this.Y + b.Y)

    // Оператор: Вектор - Вектор = Вектор (public static Vector2 operator -(Vector2 a, Vector2 b))
    operator fun minus(b: Vector2): Vector2 = Vector2(this.X - b.X, this.Y - b.Y)

    // Оператор: Вектор * Скаляр = Вектор (public static Vector2 operator *(Vector2 v, double scalar))
    operator fun times(scalar: Double): Vector2 = Vector2(this.X * scalar, this.Y * scalar)

    /**
     * Расчет скалярного произведения векторов (Dot Product).
     * Необходим для CAD-вычислений (проекции, Ortho-режимы).
     */
    fun dot(other: Vector2): Double = this.X * other.X + this.Y * other.Y
}
