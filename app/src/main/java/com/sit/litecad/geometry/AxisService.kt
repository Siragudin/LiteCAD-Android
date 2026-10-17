package com.sit.litecad.geometry

import com.sit.litecad.math.Geometry2D
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadAxis
import com.sit.litecad.model.CadDocument
import java.util.UUID

/**
 * Эквивалент AxisService из ПК-версии.
 * Создаёт ось с объединением коллинеарных существующих осей.
 */
object AxisService {

    fun create(
        document: CadDocument,
        start: Point2,
        end: Point2,
        tolerance: Double = TopologyTolerance.ForMutation
    ): CadAxis? {
        if (MathUtils.distance(start, end) <= tolerance) return null

        var mergedStart = start
        var mergedEnd = end
        val axesToRemove = mutableSetOf<UUID>()
        var changed = true

        while (changed) {
            changed = false
            for (existing in document.axes) {
                if (existing.id in axesToRemove) continue
                val union = Geometry2D.tryGetCollinearSegmentsUnion(
                    mergedStart, mergedEnd,
                    existing.Start, existing.End,
                    tolerance
                ) ?: continue
                mergedStart = union.start
                mergedEnd = union.end
                axesToRemove.add(existing.id)
                changed = true
            }
        }

        if (axesToRemove.isNotEmpty()) {
            // Удалять после цикла — иначе ConcurrentModification.
            document.axes.removeAll { it.id in axesToRemove }
            // Синхронизация индекса после удаления осей.
            document.rebuildIndexes()
        }

        if (MathUtils.distance(mergedStart, mergedEnd) <= tolerance) {
            // Оси всё равно удалились (слияние выродилось) — пересобрать точки осей.
            AxisPointSync.sync(document)
            return null
        }

        val axis = CadAxis(Start = mergedStart, End = mergedEnd)
        document.addAxis(axis)
        return axis
    }

    fun delete(document: CadDocument, axisId: UUID): Boolean {
        val axis = document.axisById(axisId) ?: return false
        document.removeAxis(axis)
        // Пересобрать производные точки осей после удаления.
        AxisPointSync.sync(document)
        return true
    }

    fun find(document: CadDocument, axisId: UUID): CadAxis? = document.axisById(axisId)
}
