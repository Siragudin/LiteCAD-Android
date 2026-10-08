package com.sit.litecad.geometry

import com.sit.litecad.math.Geometry2D
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.DirectedEdgeReference
import com.sit.litecad.model.Loop
import com.sit.litecad.model.Polygon
import com.sit.litecad.model.PolygonType
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2

object PolygonBuilder {

    private const val MaxFaceVertices = 10_000
    private const val MinFaceArea = 1e-10

    /**
     * Полная синхронизация граней:
     *  1. удаляет все производные PolygonType.FACE,
     *  2. если рёбер нет — очищает suppressed keys,
     *  3. иначе — перестраивает грани через обход.
     */
    fun syncFaces(document: CadDocument, tolerance: Double = TopologyTolerance.ForMutation) {
        document.polygons.removeAll { it.type == PolygonType.FACE }

        if (document.edges.isEmpty()) {
            document.suppressedFaceGeometryKeys.clear()
            return
        }

        rebuildFaces(document, tolerance)
    }

    private fun rebuildFaces(document: CadDocument, tolerance: Double) {
        val graph = buildGraph(document)
        if (graph.adjacency.isEmpty()) return

        graph.sortOutgoingByAngle(document)

        val boundedContours = enumerateBoundedContours(document, graph, tolerance).toList()
        val parentByContour = buildImmediateParentMap(boundedContours, tolerance)
        val createdIdentityKeys = HashSet<String>()

        for (contour in boundedContours) {
            val outerLoop = createLoopFromDirectedEdges(contour.directedEdges)
            val faceIdentity = FaceIdentity.createLoop(document, outerLoop)

            if (document.suppressedFaceGeometryKeys.contains(faceIdentity)) continue
            if (!createdIdentityKeys.add(faceIdentity)) continue

            val polygon = Polygon(type = PolygonType.FACE)
            polygon.outerLoop.edges.addAll(contour.directedEdges)

            for (candidate in boundedContours) {
                if (candidate === contour) continue
                val parent = parentByContour[candidate]
                if (parent === contour) {
                    val holeLoop = Loop()
                    holeLoop.edges.addAll(candidate.directedEdges)
                    polygon.innerLoops.add(holeLoop)
                }
            }

            document.polygons.add(polygon)
        }
    }

    /**
     * Карта «контур → его непосредственный родитель» (по геометрической идентичности).
     * Используется в ContourContainment.
     */
    fun buildContainmentParentMap(document: CadDocument, tolerance: Double): Map<String, String?> {
        val graph = buildGraph(document)
        if (graph.adjacency.isEmpty()) return emptyMap()

        graph.sortOutgoingByAngle(document)
        val boundedContours = enumerateBoundedContours(document, graph, tolerance).toList()
        val relations = buildContainmentRelations(document, boundedContours, tolerance)

        val parentMap = HashMap<String, String?>()
        for ((child, parent) in relations) {
            parentMap[child] = parent
        }
        return parentMap
    }

    private fun buildImmediateParentMap(
        contours: List<BoundedContour>,
        tolerance: Double
    ): Map<BoundedContour, BoundedContour?> {
        val map = HashMap<BoundedContour, BoundedContour?>()
        for (contour in contours) {
            map[contour] = findImmediateParent(contour, contours, tolerance)
        }
        return map
    }

    private fun buildContainmentRelations(
        document: CadDocument,
        contours: List<BoundedContour>,
        tolerance: Double
    ): List<Pair<String, String?>> {
        val parentByContour = buildImmediateParentMap(contours, tolerance)
        val result = ArrayList<Pair<String, String?>>()

        for (contour in contours) {
            val childIdentity = FaceIdentity.createLoop(
                document,
                createLoopFromDirectedEdges(contour.directedEdges)
            )
            var parentIdentity: String? = null
            val parent = parentByContour[contour]
            if (parent != null) {
                parentIdentity = FaceIdentity.createLoop(
                    document,
                    createLoopFromDirectedEdges(parent.directedEdges)
                )
            }
            result.add(childIdentity to parentIdentity)
        }
        return result
    }

    private fun findImmediateParent(
        contour: BoundedContour,
        contours: List<BoundedContour>,
        tolerance: Double
    ): BoundedContour? {
        var parent: BoundedContour? = null
        var parentArea = Double.MAX_VALUE

        for (candidate in contours) {
            if (candidate === contour) continue
            if (abs(candidate.signedArea) <= abs(contour.signedArea)) continue
            if (!isContourInsideContour(contour, candidate, tolerance)) continue

            val candidateArea = abs(candidate.signedArea)
            if (candidateArea < parentArea) {
                parent = candidate
                parentArea = candidateArea
            }
        }
        return parent
    }

    private fun enumerateBoundedContours(
        document: CadDocument,
        graph: Graph,
        tolerance: Double
    ): Sequence<BoundedContour> = sequence {
        val visited = HashSet<WalkKey>()

        for (from in graph.adjacency.keys.toList()) {
            for (neighbor in graph.getNeighbors(from)) {
                val start = WalkKey(from, neighbor.vertexId, neighbor.edgeId)
                if (visited.contains(start)) continue

                val contour = walkBoundedContour(document, graph, from, neighbor, visited)
                if (contour == null || contour.edgeIds.size < 3) continue

                contour.signedArea = MathUtils.signedPolygonArea(contour.vertices)
                if (contour.signedArea <= MinFaceArea) continue

                yield(contour)
            }
        }
    }

    private fun walkBoundedContour(
        document: CadDocument,
        graph: Graph,
        startFrom: UUID,
        startEdge: Neighbor,
        visited: HashSet<WalkKey>
    ): BoundedContour? {
        val contour = BoundedContour()
        val vertices = ArrayList<Point2>()
        val walkKeys = ArrayList<WalkKey>()

        var from = startFrom
        var to = startEdge.vertexId
        var edgeId = startEdge.edgeId

        while (true) {
            val key = WalkKey(from, to, edgeId)
            if (visited.contains(key) || walkKeys.contains(key)) return null

            walkKeys.add(key)
            contour.edgeIds.add(edgeId)
            vertices.add(TopologyService.getVertexPosition(document, from))

            val directedRef = tryGetDirectedReference(document, edgeId, from, to) ?: return null
            contour.directedEdges.add(directedRef)

            if (vertices.size > MaxFaceVertices) return null

            val next = graph.nextFaceEdge(to, from, edgeId) ?: return null
            from = to
            to = next.vertexId
            edgeId = next.edgeId

            if (from == startFrom && to == startEdge.vertexId && edgeId == startEdge.edgeId) {
                for (walkKey in walkKeys) visited.add(walkKey)
                contour.vertices.addAll(vertices)
                return contour
            }
        }
    }

    private fun tryGetDirectedReference(
        document: CadDocument,
        edgeId: UUID,
        fromVertexId: UUID,
        toVertexId: UUID
    ): DirectedEdgeReference? {
        val edge = document.edgeById(edgeId) ?: return null
        if (edge.startVertexId == fromVertexId && edge.endVertexId == toVertexId) {
            return DirectedEdgeReference(edgeId, forward = true)
        }
        if (edge.endVertexId == fromVertexId && edge.startVertexId == toVertexId) {
            return DirectedEdgeReference(edgeId, forward = false)
        }
        return null
    }

    private fun isContourInsideContour(inner: BoundedContour, outer: BoundedContour, tolerance: Double): Boolean {
        // Если есть общие рёбра — не считается вложенным (сосед по границе).
        if (inner.edgeIds.any { outer.edgeIds.contains(it) }) return false

        for (vertex in inner.vertices) {
            if (!isPointStrictlyInsideContour(vertex, outer.vertices, tolerance)) return false
        }
        return true
    }

    private fun isPointStrictlyInsideContour(
        point: Point2,
        contour: List<Point2>,
        tolerance: Double
    ): Boolean {
        if (!MathUtils.pointInPolygon(point, contour)) return false

        // Не на вершине
        for (vertex in contour) {
            if (MathUtils.arePointsEqual(point, vertex, tolerance)) return false
        }

        // Не на ребре
        for (i in contour.indices) {
            val start = contour[i]
            val end = contour[(i + 1) % contour.size]
            val proj = Geometry2D.tryProjectPointOnSegment(point, start, end, tolerance)
            if (proj != null && proj.distance <= tolerance) return false
        }
        return true
    }

    /**
     * Подавить грань: запомнить её геометрический ключ, чтобы Builder её не воссоздал.
     */
    fun suppressFaceGeometry(document: CadDocument, polygon: Polygon) {
        document.suppressedFaceGeometryKeys.add(FaceIdentity.create(document, polygon))
    }

    /**
     * Инвалидация подавлений при изменении топологии.
     */
    fun invalidateSuppressionOnEdgeTopologyChange(document: CadDocument) {
        document.suppressedFaceGeometryKeys.clear()
    }

    private fun buildGraph(document: CadDocument): Graph {
        val graph = Graph()
        for (edge in document.edges) {
            if (edge.startVertexId == edge.endVertexId) continue
            graph.addEdge(edge.startVertexId, edge.endVertexId, edge.id)
        }
        return graph
    }

    private fun createLoopFromDirectedEdges(edges: List<DirectedEdgeReference>): Loop {
        val loop = Loop()
        loop.edges.addAll(edges)
        return loop
    }

    // --- helper types ---

    private data class Neighbor(val vertexId: UUID, val edgeId: UUID)
    private data class WalkKey(val from: UUID, val to: UUID, val edgeId: UUID)

    private class BoundedContour {
        val edgeIds = ArrayList<UUID>()
        val directedEdges = ArrayList<DirectedEdgeReference>()
        val vertices = ArrayList<Point2>()
        var signedArea = 0.0
    }

    private class Graph {
        val adjacency = HashMap<UUID, MutableList<Neighbor>>()

        fun addEdge(from: UUID, to: UUID, edgeId: UUID) {
            ensureVertex(from)
            ensureVertex(to)
            adjacency[from]!!.add(Neighbor(to, edgeId))
            adjacency[to]!!.add(Neighbor(from, edgeId))
        }

        private fun ensureVertex(vertexId: UUID) {
            if (!adjacency.containsKey(vertexId)) {
                adjacency[vertexId] = ArrayList()
            }
        }

        fun getNeighbors(vertexId: UUID): List<Neighbor> =
            adjacency[vertexId] ?: emptyList()

        /**
         * Сортирует исходящие рёбра каждой вершины по углу против часовой стрелки от +X.
         * Это критично для next-edge rotation.
         */
        fun sortOutgoingByAngle(document: CadDocument) {
            for ((originId, neighbors) in adjacency) {
                val origin = TopologyService.getVertexPosition(document, originId)
                neighbors.sortWith(Comparator { a, b ->
                    val aPos = TopologyService.getVertexPosition(document, a.vertexId)
                    val bPos = TopologyService.getVertexPosition(document, b.vertexId)
                    val angleA = atan2(aPos.Y - origin.Y, aPos.X - origin.X)
                    val angleB = atan2(bPos.Y - origin.Y, bPos.X - origin.X)
                    val cmp = angleA.compareTo(angleB)
                    if (cmp != 0) cmp else a.edgeId.compareTo(b.edgeId)
                })
            }
        }

        /**
         * Next-edge rotation: из соседей вершины vertexId находим входящее ребро
         * (incomingFrom, incomingEdgeId) и возвращаем предыдущее по сортировке.
         * Это «поворот налево» вокруг вершины, дающий обход грани.
         */
        fun nextFaceEdge(vertexId: UUID, incomingFrom: UUID, incomingEdgeId: UUID): Neighbor? {
            val neighbors = adjacency[vertexId] ?: return null
            if (neighbors.isEmpty()) return null

            var index = -1
            for (i in neighbors.indices) {
                val n = neighbors[i]
                if (n.vertexId == incomingFrom && n.edgeId == incomingEdgeId) {
                    index = i
                    break
                }
            }
            if (index < 0) return null

            val previous = (index - 1 + neighbors.size) % neighbors.size
            return neighbors[previous]
        }
    }
}