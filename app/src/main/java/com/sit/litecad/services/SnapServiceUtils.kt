package com.sit.litecad.services

import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import java.util.UUID
import kotlin.math.max

internal object SnapServiceUtils {

    fun hashPoint(currentHash: Int, point: Point2): Int {
        var hash = currentHash
        hash = (hash * 31) + point.X.hashCode()
        hash = (hash * 31) + point.Y.hashCode()
        return hash
    }

    fun computeDocumentFingerprint(document: CadDocument): Int {
        var hash = document.vertices.size
        hash = (hash * 31) + document.edges.size

        for (vertex in document.vertices) {
            hash = hashPoint(hash, vertex.position)
        }

        for (edge in document.edges) {
            hash = (hash * 31) + edge.id.hashCode()
            hash = (hash * 31) + edge.startVertexId.hashCode()
            hash = (hash * 31) + edge.endVertexId.hashCode()
        }
        return hash
    }

    fun addUniquePoint(points: ArrayList<Point2>, point: Point2, tolerance: Double) {
        if (points.any { MathUtils.arePointsEqual(it, point, tolerance) }) return
        points.add(point)
    }

    fun quantizeEdgeParameter(start: Point2, end: Point2, point: Point2, tolerance: Double): Long {
        val dx = end.X - start.X
        val dy = end.Y - start.Y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= tolerance * tolerance) {
            return 0L
        }

        val t = ((point.X - start.X) * dx + (point.Y - start.Y) * dy) / lengthSquared
        val scale = 1.0 / max(tolerance, MathUtils.DefaultTolerance)
        return kotlin.math.round(t * scale).toLong()
    }

    fun findIntersectionGroupKey(
        groups: HashMap<String, Pair<Point2, HashSet<UUID>>>,
        intersection: Point2,
        tolerance: Double
    ): String {
        for ((key, group) in groups) {
            if (MathUtils.arePointsEqual(group.first, intersection, tolerance)) {
                return key
            }
        }
        return "${intersection.X}:${intersection.Y}"
    }
}
