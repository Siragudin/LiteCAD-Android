package com.sit.litecad.geometry

import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument

/**
 * Производные отношения вложенности между ограниченными контурами.
 * НЕ влияет на создание сущности Face (этим занимается PolygonBuilder.syncFaces).
 * Используется, например, для определения «какой контур чей родитель».
 */
object ContourContainment {

    fun buildParentMap(
        document: CadDocument,
        tolerance: Double = TopologyTolerance.ForMutation
    ): Map<String, String?> =
        PolygonBuilder.buildContainmentParentMap(document, tolerance)
}