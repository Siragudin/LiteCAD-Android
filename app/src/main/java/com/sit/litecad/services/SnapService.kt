package com.sit.litecad.services

import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.math.Geometry2D
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.AxisPointKind
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.OrthoAlignment
import java.util.UUID
import kotlin.math.abs

class SnapService {

    private var cache: DocumentSnapCache? = null

    fun invalidateCache() {
        cache = null
    }

    // Главный поисковый запрос привязок (Query из C#)
    fun query(
        document: CadDocument,
        cursor: Point2,
        tolerance: Double,
        excludeEdgeId: UUID? = null,
        includeOnEdge: Boolean = false
    ): SnapQueryResult {
        val candidates = buildGeometricCandidates(document, cursor, tolerance, excludeEdgeId, includeOnEdge)

        val nearby = candidates
            .filter { it.Distance <= tolerance }
            .sortedWith(compareBy<SnapCandidate> { getKindPriority(it.Identity.Kind) }.thenBy { it.Distance })

        val best = if (nearby.isNotEmpty()) nearby.first().Snap else null

        val visiblePairs = deduplicateByPosition(
            nearby.map { Pair(it.Snap, it.Identity) },
            tolerance
        )
        val visible = visiblePairs.map { it.first }

        return SnapQueryResult(SnapResult(best, best != null), visible)
    }

    // Разрешение точки черчения с учетом Ortho и направляющих осей
    fun resolveDrawingSnap(
        document: CadDocument,
        cursor: Point2,
        drawingStart: Point2?,
        tolerance: Double,
        orthoEnabled: Boolean,
        includeOnEdge: Boolean
    ): Point2 {
        val q = query(document, cursor, tolerance, includeOnEdge = includeOnEdge)
        return resolveDrawingSnap(document, q, cursor, drawingStart, tolerance, orthoEnabled)
    }

    fun resolveDrawingSnap(
        document: CadDocument,
        query: SnapQueryResult,
        cursor: Point2,
        drawingStart: Point2?,
        tolerance: Double,
        orthoEnabled: Boolean
    ): Point2 {
        var resolved = query.Best.resolve(cursor)

        if (drawingStart == null) {
            return resolved
        }

        if (orthoEnabled) {
            resolved = Geometry2D.applyOrtho(drawingStart, resolved)
        }

        val alignedResult = trySnapDrawingAlignment(document, drawingStart, resolved, tolerance)
        if (alignedResult != null) {
            return alignedResult
        }

        if (orthoEnabled) {
            resolved = Geometry2D.applyOrtho(drawingStart, query.Best.resolve(cursor))
        }

        return resolved
    }

    fun findBestSnap(
        document: CadDocument,
        cursor: Point2,
        tolerance: Double,
        excludeEdgeId: UUID? = null,
        includeOnEdge: Boolean = true
    ): SnapResult = query(document, cursor, tolerance, excludeEdgeId, includeOnEdge).Best

    fun getVisibleSnaps(document: CadDocument, cursor: Point2, tolerance: Double): List<SnapPoint> =
        query(document, cursor, tolerance, includeOnEdge = false).Visible

    // Алгоритмы работы с кэшем документа
    private fun buildGeometricCandidates(
        document: CadDocument,
        cursor: Point2,
        tolerance: Double,
        excludeEdgeId: UUID?,
        includeOnEdge: Boolean
    ): List<SnapCandidate> {
        val currentCache = getOrBuildCache(document)
        val candidates = ArrayList<Pair<SnapPoint, SnapIdentity>>(currentCache.StaticCandidates)

        if (includeOnEdge) {
            addOnEdgeCandidates(document, cursor, tolerance, excludeEdgeId, candidates)
        }

        return resolveDistances(candidates, cursor)
    }

    private fun getOrBuildCache(document: CadDocument): DocumentSnapCache {
        val fingerprint = SnapServiceUtils.computeDocumentFingerprint(document)
        val existingCache = cache
        if (existingCache != null && existingCache.Fingerprint == fingerprint) {
            return existingCache
        }

        val newCache = buildDocumentSnapCache(document, TopologyTolerance.ForMutation, fingerprint)
        cache = newCache
        return newCache
    }

    private fun buildDocumentSnapCache(
        document: CadDocument,
        tolerance: Double,
        fingerprint: Int
    ): DocumentSnapCache {
        val raw = collectStaticCandidates(document, tolerance)
        val canonical = canonicalizeCandidates(document, raw, tolerance)
        val byIdentity = deduplicateCandidates(canonical)
        val staticCandidates = deduplicateByPosition(byIdentity, tolerance)

        return DocumentSnapCache(
            Fingerprint = fingerprint,
            StaticCandidates = staticCandidates,
            AlignmentPoints = buildAlignmentReferencePoints(document, tolerance),
            VerticalAlignmentPoints = buildVerticalAlignmentReferencePoints(document, tolerance)
        )
    }

    private fun collectStaticCandidates(
        document: CadDocument,
        tolerance: Double
    ): List<Pair<SnapPoint, SnapIdentity>> {
        val candidates = ArrayList<Pair<SnapPoint, SnapIdentity>>()

        for (vertex in document.vertices) {
            candidates.add(
                Pair(
                    SnapPoint(vertex.position, SnapKind.Endpoint, VertexId = vertex.id),
                    SnapIdentity.forVertex(vertex.id)
                )
            )
        }

        for (edge in document.edges) {
            val start = TopologyService.getEdgeStartPoint(document, edge)
            val end = TopologyService.getEdgeEndPoint(document, edge)
            val midpoint = Point2((start.X + end.X) / 2.0, (start.Y + end.Y) / 2.0)

            candidates.add(
                Pair(
                    SnapPoint(midpoint, SnapKind.Midpoint, edge.id),
                    SnapIdentity.forMidpoint(edge.id)
                )
            )
        }

        // Точки осей (концы + пересечения) теперь приходят из document.axisPoints
        // (перестраиваются AxisPointSync). Вершины добавлены выше — при совпадении
        // позиций deduplicateByPosition оставит именно вершину (приоритет равный, первый побеждает).
        for (point in document.axisPoints) {
            when (point.kind) {
                AxisPointKind.END_START -> candidates.add(
                    Pair(
                        SnapPoint(point.position, SnapKind.Endpoint, AxisId = point.axisId),
                        SnapIdentity.forAxisEndpoint(point.axisId, isStart = true)
                    )
                )
                AxisPointKind.END_END -> candidates.add(
                    Pair(
                        SnapPoint(point.position, SnapKind.Endpoint, AxisId = point.axisId),
                        SnapIdentity.forAxisEndpoint(point.axisId, isStart = false)
                    )
                )
                AxisPointKind.INTERSECTION -> {
                    val otherId = point.otherAxisId
                    val identity = if (otherId != null) {
                        SnapIdentity.forIntersection(listOf(point.axisId, otherId))
                    } else {
                        SnapIdentity.forIntersection(listOf(point.axisId))
                    }
                    candidates.add(
                        Pair(
                            SnapPoint(point.position, SnapKind.Intersection, AxisId = point.axisId),
                            identity
                        )
                    )
                }
            }
        }

        addLinearContactCandidates(document, candidates, tolerance)
        return candidates
    }

    private fun addOnEdgeCandidates(
        document: CadDocument,
        cursor: Point2,
        tolerance: Double,
        excludeEdgeId: UUID?,
        candidates: ArrayList<Pair<SnapPoint, SnapIdentity>>
    ) {
        for (edge in document.edges) {
            if (excludeEdgeId != null && edge.id == excludeEdgeId) {
                continue
            }

            val start = TopologyService.getEdgeStartPoint(document, edge)
            val end = TopologyService.getEdgeEndPoint(document, edge)
            val projResult = Geometry2D.tryProjectPointOnSegment(cursor, start, end, tolerance)

            if (projResult == null || projResult.distance > tolerance ||
                !Geometry2D.isPointOnSegmentInterior(projResult.projection, start, end, tolerance)) {
                continue
            }

            val parameterKey = SnapServiceUtils.quantizeEdgeParameter(start, end, projResult.projection, tolerance)
            candidates.add(
                Pair(
                    SnapPoint(projResult.projection, SnapKind.OnEdge, edge.id),
                    SnapIdentity.forOnEdge(edge.id, parameterKey)
                )
            )
        }

        for (axis in document.axes) {
            val start = axis.Start
            val end = axis.End
            val projResult = Geometry2D.tryProjectPointOnSegment(cursor, start, end, tolerance) ?: continue
            if (projResult.distance > tolerance) continue
            if (!Geometry2D.isPointOnSegmentInterior(projResult.projection, start, end, tolerance)) continue

            val parameterKey = SnapServiceUtils.quantizeEdgeParameter(start, end, projResult.projection, tolerance)
            candidates.add(
                Pair(
                    SnapPoint(projResult.projection, SnapKind.OnEdge, AxisId = axis.id),
                    SnapIdentity.forOnAxis(axis.id, parameterKey)
                )
            )
        }
    }

    private fun addLinearContactCandidates(
        document: CadDocument,
        candidates: ArrayList<Pair<SnapPoint, SnapIdentity>>,
        tolerance: Double
    ) {
        val edgeEdgeGroups = HashMap<String, Pair<Point2, HashSet<UUID>>>()

        if (document.edges.isNotEmpty()) {
            val edgeGrid = buildEdgeSpatialGrid(document, tolerance)
            for ((outerIndex, innerIndex) in edgeGrid.getOrderedPairIndices()) {
                val edgeA = document.edges[outerIndex]
                val edgeB = document.edges[innerIndex]

                for (contact in collectSegmentContactPoints(
                    edgeGrid.getStart(outerIndex),
                    edgeGrid.getEnd(outerIndex),
                    edgeGrid.getStart(innerIndex),
                    edgeGrid.getEnd(innerIndex),
                    tolerance
                )) {
                    if (TopologyService.findVertex(document, contact, tolerance) != null) {
                        continue
                    }

                    val groupKey = SnapServiceUtils.findIntersectionGroupKey(edgeEdgeGroups, contact, tolerance)
                    var group = edgeEdgeGroups[groupKey]
                    if (group == null) {
                        group = Pair(contact, HashSet())
                        edgeEdgeGroups[groupKey] = group
                    }

                    group.second.add(edgeA.id)
                    group.second.add(edgeB.id)
                }
            }
        }

        for (group in edgeEdgeGroups.values) {
            candidates.add(
                Pair(
                    SnapPoint(group.first, SnapKind.Intersection),
                    SnapIdentity.forIntersection(group.second)
                )
            )
        }
    }

    private fun collectSegmentContactPoints(
        aStart: Point2, aEnd: Point2,
        bStart: Point2, bEnd: Point2,
        tolerance: Double
    ): List<Point2> {
        val contacts = ArrayList<Point2>()

        val intersection = Geometry2D.tryGetSegmentIntersection(aStart, aEnd, bStart, bEnd, tolerance)
        if (intersection != null) {
            addUniqueContactPoint(contacts, intersection, tolerance)
        }

        tryAddEndpointOnSegmentContact(aStart, aEnd, aStart, contacts, tolerance)
        tryAddEndpointOnSegmentContact(aStart, aEnd, aEnd, contacts, tolerance)
        tryAddEndpointOnSegmentContact(bStart, bEnd, bStart, contacts, tolerance)
        tryAddEndpointOnSegmentContact(bStart, bEnd, bEnd, contacts, tolerance)

        return contacts
    }

    private fun tryAddEndpointOnSegmentContact(
        hostStart: Point2,
        hostEnd: Point2,
        endpoint: Point2,
        contacts: ArrayList<Point2>,
        tolerance: Double
    ) {
        if (MathUtils.distance(hostStart, hostEnd) <= tolerance) {
            if (MathUtils.arePointsEqual(endpoint, hostStart, tolerance)) {
                addUniqueContactPoint(contacts, hostStart, tolerance)
            }
            return
        }

        val projResult = Geometry2D.tryProjectPointOnSegment(endpoint, hostStart, hostEnd, tolerance)
        if (projResult == null || projResult.distance > tolerance) {
            return
        }

        addUniqueContactPoint(contacts, projResult.projection, tolerance)
    }

    private fun addUniqueContactPoint(contacts: ArrayList<Point2>, point: Point2, tolerance: Double) {
        if (contacts.any { MathUtils.arePointsEqual(it, point, tolerance) }) return
        contacts.add(point)
    }

    // Поиск направляющих осей
    fun trySnapDrawingAlignment(
        document: CadDocument,
        start: Point2,
        target: Point2,
        tolerance: Double
    ): Point2? {
        val resolvedTolerance = tolerance
        val alignment = Geometry2D.getOrthoAlignment(start, target, resolvedTolerance)
        if (alignment == OrthoAlignment.NONE) {
            return null
        }

        val currentCache = getOrBuildCache(document)
        val referencePoints = if (alignment == OrthoAlignment.HORIZONTAL) {
            currentCache.AlignmentPoints
        } else {
            currentCache.VerticalAlignmentPoints
        }

        var bestDelta = Double.MAX_VALUE
        var snappedEnd = target

        for (point in referencePoints) {
            if (MathUtils.arePointsEqual(point, start, resolvedTolerance)) {
                continue
            }

            if (alignment == OrthoAlignment.HORIZONTAL) {
                val xDelta = abs(target.X - point.X)
                if (xDelta > resolvedTolerance || xDelta >= bestDelta) {
                    continue
                }
                snappedEnd = Point2(point.X, start.Y)
                bestDelta = xDelta
            } else {
                val yDelta = abs(target.Y - point.Y)
                if (yDelta > resolvedTolerance || yDelta >= bestDelta) {
                    continue
                }
                snappedEnd = Point2(start.X, point.Y)
                bestDelta = yDelta
            }
        }

        return if (bestDelta < Double.MAX_VALUE) snappedEnd else null
    }

    fun findVisibleDrawingAlignmentSnap(
        document: CadDocument,
        start: Point2,
        target: Point2,
        tolerance: Double
    ): SnapPoint? {
        val aligned = trySnapDrawingAlignment(document, start, target, tolerance) ?: return null
        return SnapPoint(aligned, SnapKind.Alignment)
    }

    private fun buildAlignmentReferencePoints(document: CadDocument, tolerance: Double): List<Point2> {
        val points = ArrayList<Point2>()

        for (vertex in document.vertices) {
            SnapServiceUtils.addUniquePoint(points, vertex.position, tolerance)
        }

        for (edge in document.edges) {
            val start = TopologyService.getEdgeStartPoint(document, edge)
            val end = TopologyService.getEdgeEndPoint(document, edge)
            val midpoint = Point2((start.X + end.X) / 2.0, (start.Y + end.Y) / 2.0)

            if (TopologyService.findVertex(document, midpoint, tolerance) == null) {
                SnapServiceUtils.addUniquePoint(points, midpoint, tolerance)
            }
        }

        if (document.edges.isNotEmpty()) {
            val edgeGrid = buildEdgeSpatialGrid(document, tolerance)
            for ((outerIndex, innerIndex) in edgeGrid.getOrderedPairIndices()) {
                for (contact in collectSegmentContactPoints(
                    edgeGrid.getStart(outerIndex),
                    edgeGrid.getEnd(outerIndex),
                    edgeGrid.getStart(innerIndex),
                    edgeGrid.getEnd(innerIndex),
                    tolerance
                )) {
                    if (TopologyService.findVertex(document, contact, tolerance) != null) {
                        continue
                    }
                    SnapServiceUtils.addUniquePoint(points, contact, tolerance)
                }
            }
        }

        return points
    }

    private fun buildEdgeSpatialGrid(document: CadDocument, tolerance: Double): SnapSegmentSpatialGrid {
        val segments = document.edges.map { edge ->
            Triple(
                edge.id,
                TopologyService.getEdgeStartPoint(document, edge),
                TopologyService.getEdgeEndPoint(document, edge)
            )
        }
        return SnapSegmentSpatialGrid.fromSegments(segments, tolerance)
    }

    private fun buildVerticalAlignmentReferencePoints(document: CadDocument, tolerance: Double): List<Point2> {
        val points = ArrayList<Point2>()

        for (edge in document.edges) {
            val start = TopologyService.getEdgeStartPoint(document, edge)
            val end = TopologyService.getEdgeEndPoint(document, edge)
            if (Geometry2D.getOrthoAlignment(start, end, tolerance) != OrthoAlignment.VERTICAL) {
                continue
            }

            val midpoint = Point2((start.X + end.X) / 2.0, (start.Y + end.Y) / 2.0)
            for (point in arrayOf(start, end, midpoint)) {
                val vertex = TopologyService.findVertex(document, point, tolerance)
                SnapServiceUtils.addUniquePoint(points, vertex?.position ?: point, tolerance)
            }
        }

        return points
    }

    private fun canonicalizeCandidates(
        document: CadDocument,
        candidates: List<Pair<SnapPoint, SnapIdentity>>,
        tolerance: Double
    ): List<Pair<SnapPoint, SnapIdentity>> {
        val canonical = ArrayList<Pair<SnapPoint, SnapIdentity>>(candidates.size)

        for ((snap, identity) in candidates) {
            val vertex = TopologyService.findVertex(document, snap.Position, tolerance)
            if (vertex != null) {
                canonical.add(
                    Pair(
                        SnapPoint(vertex.position, SnapKind.Endpoint, VertexId = vertex.id),
                        SnapIdentity.forVertex(vertex.id)
                    )
                )
                continue
            }
            canonical.add(Pair(snap, identity))
        }

        return canonical
    }

    private fun deduplicateCandidates(
        candidates: List<Pair<SnapPoint, SnapIdentity>>
    ): List<Pair<SnapPoint, SnapIdentity>> {
        val bestByIdentity = HashMap<SnapIdentity, Pair<SnapPoint, SnapIdentity>>()

        for (candidate in candidates) {
            val existing = bestByIdentity[candidate.second]
            if (existing == null || getKindPriority(candidate.second.Kind) > getKindPriority(existing.second.Kind)) {
                bestByIdentity[candidate.second] = candidate
            }
        }
        return bestByIdentity.values.toList()
    }

    private fun deduplicateByPosition(
        candidates: List<Pair<SnapPoint, SnapIdentity>>,
        tolerance: Double
    ): List<Pair<SnapPoint, SnapIdentity>> {
        val bestByPosition = ArrayList<Pair<SnapPoint, SnapIdentity>>()

        for (candidate in candidates) {
            var existingIndex = -1
            for (i in bestByPosition.indices) {
                if (MathUtils.arePointsEqual(bestByPosition[i].first.Position, candidate.first.Position, tolerance)) {
                    existingIndex = i
                    break
                }
            }

            if (existingIndex == -1) {
                bestByPosition.add(candidate)
            } else {
                val existing = bestByPosition[existingIndex]
                if (shouldReplace(candidate, existing)) {
                    bestByPosition[existingIndex] = candidate
                }
            }
        }
        return bestByPosition
    }

    private fun shouldReplace(
        candidate: Pair<SnapPoint, SnapIdentity>,
        existing: Pair<SnapPoint, SnapIdentity>
    ): Boolean {
        val candidatePriority = getKindPriority(candidate.second.Kind)
        val existingPriority = getKindPriority(existing.second.Kind)
        if (candidatePriority != existingPriority) {
            return candidatePriority > existingPriority
        }
        return false
    }

    private fun resolveDistances(
        candidates: List<Pair<SnapPoint, SnapIdentity>>,
        cursor: Point2
    ): List<SnapCandidate> {
        return candidates.map { candidate ->
            SnapCandidate(
                candidate.first,
                candidate.second,
                MathUtils.distance(cursor, candidate.first.Position)
            )
        }
    }

    private fun getKindPriority(kind: SnapKind): Int = when (kind) {
        SnapKind.Endpoint -> 0
        SnapKind.Intersection, SnapKind.AxisIntersection -> 1
        SnapKind.Midpoint -> 2
        SnapKind.OnEdge -> 3
        SnapKind.Alignment -> 4
    }
}