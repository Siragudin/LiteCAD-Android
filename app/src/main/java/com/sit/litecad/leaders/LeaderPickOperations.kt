package com.sit.litecad.leaders

import com.sit.litecad.math.Geometry2D
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.Leader

/**
 * Hit-тест выносок: точка — по полке, стойке или тексту.
 */
object LeaderPickOperations {

    fun tryPickAt(
        document: CadDocument,
        world: Point2,
        tolerance: Double,
        zoom: Double
    ): Leader? {
        var closest: Leader? = null
        var closestDistance = tolerance

        for (leader in document.leaders) {
            val d = pickDistance(world, leader, tolerance, zoom) ?: continue
            if (d > closestDistance) continue
            closest = leader
            closestDistance = d
        }
        return closest
    }

    private fun pickDistance(world: Point2, leader: Leader, tolerance: Double, zoom: Double): Double? {
        val layout = LeaderGeometry.createLayout(leader.target, leader.textPosition, zoom)
        var best: Double? = null
        for ((a, b) in LeaderGeometry.getSegments(layout)) {
            val d = Geometry2D.tryHitTestSegment(world, a, b, tolerance) ?: continue
            if (best == null || d < best!!) best = d
        }
        val textDist = MathUtils.distance(world, layout.textPosition)
        if (textDist <= maxOf(tolerance, 8.0)) {
            if (best == null || textDist < best!!) best = textDist
        }
        return best
    }
}
