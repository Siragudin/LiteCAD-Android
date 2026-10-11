package com.sit.litecad.geometry

import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Геометрия дуги, заданной двумя точками (start, end) и «высотой» (sagitta) —
 * расстоянием от середины хорды до дуги, со знаком (сторона изгиба).
 *
 * Нормаль к хорде: rotate90(CCW) от направления start→end, единичная.
 * Положительная sagitta — изгиб в сторону этой нормали (слева от start→end).
 */
object ArcGeometry {

    private const val EPS = 1e-9

    /**
     * Signed sagitta: расстояние от середины хорды [start-end] до точки [bendPoint],
     * со знаком. Знак определяет сторону изгиба (по/против часовой относительно
     * направления start→end).
     */
    fun tryGetSignedSagitta(start: Point2, end: Point2, bendPoint: Point2): Double? {
        val chord = MathUtils.distance(start, end)
        if (chord <= EPS) return null

        val nx = -(end.Y - start.Y) / chord
        val ny = (end.X - start.X) / chord
        val midX = (start.X + end.X) / 2.0
        val midY = (start.Y + end.Y) / 2.0

        return (bendPoint.X - midX) * nx + (bendPoint.Y - midY) * ny
    }

    /**
     * Радиус дуги по длине хорды и высоте (sagitta) со знаком.
     * R = (chord² / 4 + sagitta²) / (2 * |sagitta|).
     * null — если |sagitta| слишком мала или дуга вырождена.
     */
    fun tryComputeRadiusFromSignedSagitta(chord: Double, signedSagitta: Double): Double? {
        val s = abs(signedSagitta)
        if (s <= EPS) return null
        return (chord * chord / 4.0 + s * s) / (2.0 * s)
    }

    /**
     * Точка на дуге (вершина стрелки) — середина хорды + нормаль * sagitta.
     * Знак sagitta задаёт сторону нормали.
     */
    fun tryCreateBendPoint(start: Point2, end: Point2, signedSagitta: Double): Point2? {
        val chord = MathUtils.distance(start, end)
        if (chord <= EPS) return null

        val nx = -(end.Y - start.Y) / chord
        val ny = (end.X - start.X) / chord
        val midX = (start.X + end.X) / 2.0
        val midY = (start.Y + end.Y) / 2.0

        return Point2(midX + nx * signedSagitta, midY + ny * signedSagitta)
    }

    /**
     * Центр дуги, начальный угол (рад), развёртка (рад, со знаком) —
     * чтобы нарисовать дугу от start к end через bendPoint.
     * Возвращает null, если геометрия вырождена.
     */
    fun tryBuildArc(
        start: Point2,
        end: Point2,
        bendPoint: Point2
    ): ArcBuildResult? {
        val chord = MathUtils.distance(start, end)
        if (chord <= EPS) return null

        val signedSagitta = tryGetSignedSagitta(start, end, bendPoint) ?: return null
        val radius = tryComputeRadiusFromSignedSagitta(chord, signedSagitta) ?: return null

        val nx = -(end.Y - start.Y) / chord
        val ny = (end.X - start.X) / chord
        val midX = (start.X + end.X) / 2.0
        val midY = (start.Y + end.Y) / 2.0

        // Центр — на нормали от середины хорды; при |sagitta| = R (полуокружность) центр = середина хорды.
        val sgn = if (signedSagitta >= 0.0) 1.0 else -1.0
        val center = Point2(
            midX - nx * (radius - abs(signedSagitta)) * sgn,
            midY - ny * (radius - abs(signedSagitta)) * sgn
        )

        val startAngle = atan2(start.Y - center.Y, start.X - center.X)
        val endAngle = atan2(end.Y - center.Y, end.X - center.X)
        val bendAngle = atan2(bendPoint.Y - center.Y, bendPoint.X - center.X)

        val sweep = signedSweepThrough(startAngle, endAngle, bendAngle)

        return ArcBuildResult(center, radius, startAngle, sweep)
    }

    /**
     * Точки дуги от startAngle до startAngle+sweepRad, шаг 10° + остаток.
     * Первая точка — ровно start, последняя — ровно end.
     */
    fun computeOpenArcPoints(
        center: Point2,
        radius: Double,
        startAngleRad: Double,
        sweepRad: Double
    ): List<Point2> {
        val points = mutableListOf<Point2>()
        points.add(pointOnCircle(center, radius, startAngleRad))

        val endAngle = startAngleRad + sweepRad
        val step = PI / 18.0
        val stepSigned = if (sweepRad >= 0.0) step else -step

        var a = startAngleRad
        while (true) {
            a += stepSigned
            if (stepSigned > 0.0 && a >= endAngle) break
            if (stepSigned < 0.0 && a <= endAngle) break
            points.add(pointOnCircle(center, radius, a))
        }

        points.add(pointOnCircle(center, radius, endAngle))
        return points
    }

    /**
     * Выбор знака развёртки: дуга от [startAngle] должна проходить через [throughAngle]
     * и закончиться в [endAngle]. Возвращает ближайшую по модулю из двух развёрток,
     * накрывающую [throughAngle].
     */
    private fun signedSweepThrough(startAngle: Double, endAngle: Double, throughAngle: Double): Double {
        val twoPi = 2.0 * PI

        var ccwEnd = (endAngle - startAngle) % twoPi
        if (ccwEnd < 0.0) ccwEnd += twoPi

        var ccwThrough = (throughAngle - startAngle) % twoPi
        if (ccwThrough < 0.0) ccwThrough += twoPi

        // Если при движении против часовой от start мы встречаем bend раньше end —
        // дуга идёт CCW (положительная развёртка), иначе — CW (отрицательная).
        return if (ccwThrough <= ccwEnd) ccwEnd else ccwEnd - twoPi
    }

    private fun pointOnCircle(center: Point2, radius: Double, angleRad: Double): Point2 =
        Point2(center.X + radius * cos(angleRad), center.Y + radius * sin(angleRad))
}

data class ArcBuildResult(
    val center: Point2,
    val radius: Double,
    val startAngleRad: Double,
    val sweepRad: Double
)
