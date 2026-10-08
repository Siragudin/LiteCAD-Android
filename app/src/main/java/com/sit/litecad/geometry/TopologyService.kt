package com.sit.litecad.geometry

import com.sit.litecad.math.Geometry2D
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.CadEdge
import com.sit.litecad.model.CadVertex
import java.util.UUID

object TopologyService {

    fun findVertex(document: CadDocument, position: Point2, tolerance: Double): CadVertex? {
        // Линейный поиск. Оптимизация через spatial grid — отдельная задача.
        return document.vertices.find { MathUtils.arePointsEqual(it.position, position, tolerance) }
    }

    fun findOrCreateVertex(document: CadDocument, position: Point2, tolerance: Double): UUID {
        val mutTolerance = TopologyTolerance.ForMutation
        val existing = findVertex(document, position, mutTolerance)
        if (existing != null) {
            return existing.id
        }

        val vertex = CadVertex(X = position.X, Y = position.Y)
        document.addVertex(vertex)
        return vertex.id
    }

    fun getVertex(document: CadDocument, vertexId: UUID): CadVertex {
        return document.vertexById(vertexId)
            ?: throw IllegalStateException("Vertex $vertexId was not found.")
    }

    fun getVertexPosition(document: CadDocument, vertexId: UUID): Point2 {
        val vertex = getVertex(document, vertexId)
        return Point2(vertex.X, vertex.Y)
    }

    fun getEdgeStartPoint(document: CadDocument, edge: CadEdge): Point2 =
        getVertexPosition(document, edge.startVertexId)

    fun getEdgeEndPoint(document: CadDocument, edge: CadEdge): Point2 =
        getVertexPosition(document, edge.endVertexId)

    fun createEdge(document: CadDocument, startVertexId: UUID, endVertexId: UUID, template: CadEdge): CadEdge {
        if (startVertexId == endVertexId) {
            throw IllegalStateException("Cannot create a zero-length edge.")
        }

        val edge = template.copy(
            id = UUID.randomUUID(),
            startVertexId = startVertexId,
            endVertexId = endVertexId
        )
        document.addEdge(edge)
        return edge
    }

    fun deleteEdge(document: CadDocument, edge: CadEdge) {
        document.removeEdge(edge)
    }

    fun splitEdge(document: CadDocument, edge: CadEdge, point: Point2, tolerance: Double) {
        val mutTolerance = TopologyTolerance.ForMutation
        val start = getEdgeStartPoint(document, edge)
        val end = getEdgeEndPoint(document, edge)

        if (!Geometry2D.isPointOnSegmentInterior(point, start, end, mutTolerance)) {
            return
        }

        val splitVertexId = findOrCreateVertex(document, point, mutTolerance)
        val startVertexId = edge.startVertexId
        val endVertexId = edge.endVertexId

        deleteEdge(document, edge)

        createEdge(document, startVertexId, splitVertexId, edge)
        createEdge(document, splitVertexId, endVertexId, edge)

        PolygonBuilder.syncFaces(document)
    }

    fun createEdgeFromPoints(
        document: CadDocument,
        start: Point2,
        end: Point2,
        template: CadEdge,
        tolerance: Double
    ): CadEdge {
        val startVertexId = findOrCreateVertex(document, start, tolerance)
        val endVertexId = findOrCreateVertex(document, end, tolerance)
        return createEdge(document, startVertexId, endVertexId, template)
    }

    fun pruneUnusedVertices(document: CadDocument) {
        val used = mutableSetOf<UUID>()
        for (edge in document.edges) {
            used.add(edge.startVertexId)
            used.add(edge.endVertexId)
        }
        document.vertices.retainAll { used.contains(it.id) }
        document.rebuildIndexes()
    }
}