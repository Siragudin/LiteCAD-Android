package com.sit.litecad.geometry

import com.sit.litecad.math.Geometry2D
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.AxisPointKind
import com.sit.litecad.model.CadAxisPoint
import com.sit.litecad.model.CadDocument

/**
 * Пересобирает производные точки осей ([CadAxisPoint]) из документа.axes:
 *  - концы каждой оси (END_START / END_END);
 *  - точки пересечения пар осей (INTERSECTION), если они не совпадают
 *    с уже добавленными концами/пересечениями (в пределах [tolerance]).
 *
 * Вызывается после любого изменения состава/геометрии осей, а также после
 * undo/redo, загрузки .sit и восстановления снапшота.
 *
 * Примечание: [CadDocument.axisPointsById] приватна — поэтому список чистится
 * напрямую (document.axisPoints.clear()), точки добавляются через
 * document.addAxisPoint(...), а в конце document.rebuildIndexes() гарантирует
 * согласованность приватного индекса.
 */
object AxisPointSync {

    fun sync(document: CadDocument, tolerance: Double = TopologyTolerance.ForMutation) {
        document.axisPoints.clear()

        // 1. Концы осей.
        for (axis in document.axes) {
            document.addAxisPoint(
                CadAxisPoint(
                    position = Point2(axis.Start.X, axis.Start.Y),
                    kind = AxisPointKind.END_START,
                    axisId = axis.id
                )
            )
            document.addAxisPoint(
                CadAxisPoint(
                    position = Point2(axis.End.X, axis.End.Y),
                    kind = AxisPointKind.END_END,
                    axisId = axis.id
                )
            )
        }

        // 2. Пересечения пар осей.
        val axes = document.axes
        for (i in axes.indices) {
            for (j in i + 1 until axes.size) {
                val a = axes[i]
                val b = axes[j]
                val intersection = Geometry2D.tryGetSegmentIntersection(
                    a.Start, a.End, b.Start, b.End, tolerance
                ) ?: continue

                if (isCoincidentWithExisting(document.axisPoints, intersection, tolerance)) continue

                document.addAxisPoint(
                    CadAxisPoint(
                        position = intersection,
                        kind = AxisPointKind.INTERSECTION,
                        axisId = a.id,
                        otherAxisId = b.id
                    )
                )
            }
        }

        // Индекс перестраивается из списка — приватная map синхронизируется.
        document.rebuildIndexes()
    }

    private fun isCoincidentWithExisting(
        existing: List<CadAxisPoint>,
        point: Point2,
        tolerance: Double
    ): Boolean {
        for (p in existing) {
            if (MathUtils.arePointsEqual(p.position, point, tolerance)) return true
        }
        return false
    }
}
