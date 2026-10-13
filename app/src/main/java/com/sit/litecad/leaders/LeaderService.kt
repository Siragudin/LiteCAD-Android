package com.sit.litecad.leaders

import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.Leader
import com.sit.litecad.model.LeaderKind

/**
 * Создание и удаление выносок. Изолировано: работает только с CadDocument.
 */
object LeaderService {

    /**
     * Создать выноску отметки высоты.
     * Если elevationBaseY не задан — задать его как target.Y (текст будет "0").
     */
    fun createElevation(
        document: CadDocument,
        measurePoint: Point2,
        graphicOrigin: Point2,
        colorArgb: Int
    ): Leader {
        if (document.elevationBaseY == null) {
            document.elevationBaseY = measurePoint.Y
        }
        val delta = ElevationFormatting.computeDelta(document.elevationBaseY!!, measurePoint.Y)
        val constrained = LeaderGeometry.constrainHorizontal(measurePoint, graphicOrigin)
        val side = LeaderGeometry.resolveSide(measurePoint, constrained)
        val sideHint = Point2(constrained.X + if (side >= 0) 1.0 else -1.0, constrained.Y)

        val leader = Leader(
            kind = LeaderKind.ELEVATION,
            target = constrained,
            textPosition = sideHint,
            text = ElevationFormatting.format(delta),
            colorArgb = colorArgb
        )
        document.leaders.add(leader)
        return leader
    }
}
