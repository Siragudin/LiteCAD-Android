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
        // Позиция выноски: X — от graphicOrigin (палец), Y — от measurePoint (снапнутая цель).
        val position = LeaderGeometry.constrainHorizontal(measurePoint, graphicOrigin)

        val leader = Leader(
            kind = LeaderKind.ELEVATION,
            target = position,
            textPosition = position,
            text = ElevationFormatting.format(delta),
            colorArgb = colorArgb
        )
        document.leaders.add(leader)
        return leader
    }
}
