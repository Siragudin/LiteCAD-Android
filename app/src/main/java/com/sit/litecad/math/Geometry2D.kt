package com.sit.litecad.math

import com.sit.litecad.model.OrthoAlignment
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sqrt

object Geometry2D {

    class ProjectionResult(val projection: Point2, val distance: Double)
    class OverlapResult(val start: Point2, val end: Point2)

    fun tryGetSegmentIntersection(
        a1: Point2, a2: Point2,
        b1: Point2, b2: Point2,
        tolerance: Double = MathUtils.DefaultTolerance
    ): Point2? {
        val d1X = a2.X - a1.X
        val d1Y = a2.Y - a1.Y
        val d2X = b2.X - b1.X
        val d2Y = b2.Y - b1.Y
        val denominator = d1X * d2Y - d1Y * d2X

        if (abs(denominator) < tolerance) return null

        val t = ((b1.X - a1.X) * d2Y - (b1.Y - a1.Y) * d2X) / denominator
        val u = ((b1.X - a1.X) * d1Y - (b1.Y - a1.Y) * d1X) / denominator

        if (t < -tolerance || t > 1 + tolerance || u < -tolerance || u > 1 + tolerance) {
            return null
        }

        return Point2(a1.X + t * d1X, a1.Y + t * d1Y)
    }

    fun tryProjectPointOnSegment(
        point: Point2,
        segmentStart: Point2,
        segmentEnd: Point2,
        tolerance: Double = MathUtils.DefaultTolerance
    ): ProjectionResult? {
        val dx = segmentEnd.X - segmentStart.X
        val dy = segmentEnd.Y - segmentStart.Y
        val lengthSquared = dx * dx + dy * dy

        if (lengthSquared < tolerance * tolerance) return null

        var t = ((point.X - segmentStart.X) * dx + (point.Y - segmentStart.Y) * dy) / lengthSquared
        t = MathUtils.clamp(t, 0.0, 1.0)

        val projection = Point2(segmentStart.X + t * dx, segmentStart.Y + t * dy)
        val distance = MathUtils.distance(point, projection)
        return ProjectionResult(projection, distance)
    }

    fun isPointOnSegmentInterior(
        point: Point2,
        segmentStart: Point2,
        segmentEnd: Point2,
        tolerance: Double
    ): Boolean {
        val projResult = tryProjectPointOnSegment(point, segmentStart, segmentEnd, tolerance) ?: return false
        if (projResult.distance > tolerance) return false

        return !MathUtils.arePointsEqual(point, segmentStart, tolerance) &&
                !MathUtils.arePointsEqual(point, segmentEnd, tolerance)
    }

    fun getOrthoAlignment(start: Point2, end: Point2, tolerance: Double): OrthoAlignment {
        if (abs(start.Y - end.Y) <= tolerance) return OrthoAlignment.HORIZONTAL
        if (abs(start.X - end.X) <= tolerance) return OrthoAlignment.VERTICAL
        return OrthoAlignment.NONE
    }

    fun applyOrtho(start: Point2, target: Point2): Point2 {
        val dx = abs(target.X - start.X)
        val dy = abs(target.Y - start.Y)
        return if (dx >= dy) Point2(target.X, start.Y) else Point2(start.X, target.Y)
    }

    fun areDirectionsParallel(
        dx1: Double, dy1: Double, length1: Double,
        dx2: Double, dy2: Double, length2: Double,
        tolerance: Double
    ): Boolean {
        if (length1 <= tolerance || length2 <= tolerance) return false

        val cross = abs(dx1 * dy2 - dy1 * dx2)
        val sinAngleMax = min(0.2, tolerance / min(length1, length2))
        return cross <= sinAngleMax * length1 * length2
    }

    fun distanceToLine(
        point: Point2,
        lineStart: Point2,
        lineEnd: Point2,
        tolerance: Double = MathUtils.DefaultTolerance
    ): Double {
        val dx = lineEnd.X - lineStart.X
        val dy = lineEnd.Y - lineStart.Y
        val length = sqrt(dx * dx + dy * dy)
        if (length <= tolerance) {
            return MathUtils.distance(point, lineStart)
        }
        return abs((point.X - lineStart.X) * dy - (point.Y - lineStart.Y) * dx) / length
    }

    fun getSegmentParameter(point: Point2, segmentStart: Point2, segmentEnd: Point2, tolerance: Double): Double {
        val dx = segmentEnd.X - segmentStart.X
        val dy = segmentEnd.Y - segmentStart.Y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= tolerance * tolerance) return 0.0

        return ((point.X - segmentStart.X) * dx + (point.Y - segmentStart.Y) * dy) / lengthSquared
    }

    fun getSegmentPoint(segmentStart: Point2, segmentEnd: Point2, parameter: Double): Point2 {
        val dx = segmentEnd.X - segmentStart.X
        val dy = segmentEnd.Y - segmentStart.Y
        return Point2(segmentStart.X + dx * parameter, segmentStart.Y + dy * parameter)
    }

    fun areSegmentsCollinear(a1: Point2, a2: Point2, b1: Point2, b2: Point2, tolerance: Double): Boolean {
        val lengthA = MathUtils.distance(a1, a2)
        val lengthB = MathUtils.distance(b1, b2)
        if (lengthA <= tolerance || lengthB <= tolerance) return false

        if (!areDirectionsParallel(
                a2.X - a1.X, a2.Y - a1.Y, lengthA,
                b2.X - b1.X, b2.Y - b1.Y, lengthB,
                tolerance
            )) return false

        return distanceToLine(b1, a1, a2, tolerance) <= tolerance &&
                distanceToLine(b2, a1, a2, tolerance) <= tolerance
    }

    fun tryGetCollinearSegmentOverlap(
        newStart: Point2, newEnd: Point2,
        existingStart: Point2, existingEnd: Point2,
        tolerance: Double
    ): OverlapResult? {
        if (!areSegmentsCollinear(newStart, newEnd, existingStart, existingEnd, tolerance)) return null

        val tExistingStart = getSegmentParameter(existingStart, newStart, newEnd, tolerance)
        val tExistingEnd = getSegmentParameter(existingEnd, newStart, newEnd, tolerance)
        val tMin = min(tExistingStart, tExistingEnd)
        val tMax = max(tExistingStart, tExistingEnd)

        val overlapMin = max(0.0, tMin)
        val overlapMax = min(1.0, tMax)

        val lengthNew = MathUtils.distance(newStart, newEnd)
        if (overlapMax - overlapMin <= tolerance / max(lengthNew, tolerance)) return null

        val overlapStart = getSegmentPoint(newStart, newEnd, overlapMin)
        val overlapEnd = getSegmentPoint(newStart, newEnd, overlapMax)
        return OverlapResult(overlapStart, overlapEnd)
    }
}
