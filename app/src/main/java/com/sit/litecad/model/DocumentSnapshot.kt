package com.sit.litecad.model

import java.util.UUID

/**
 * Снимок состояния документа для undo/redo.
 * Хранит глубокую копию вершин и рёбер.
 */
class DocumentSnapshot private constructor(
    private val vertices: List<CadVertex>,
    private val edges: List<CadEdge>
) {
    companion object {
        fun capture(document: CadDocument): DocumentSnapshot {
            val verticesCopy = document.vertices.map { vertex ->
                CadVertex(id = vertex.id, X = vertex.X, Y = vertex.Y)
            }
            val edgesCopy = document.edges.map { edge ->
                CadEdge(
                    id = edge.id,
                    startVertexId = edge.startVertexId,
                    endVertexId = edge.endVertexId,
                    isAxis = edge.isAxis,
                    thickness = edge.thickness,
                    lineType = edge.lineType,
                    orthoAlignment = edge.orthoAlignment,
                    colorArgb = edge.colorArgb
                )
            }
            return DocumentSnapshot(verticesCopy, edgesCopy)
        }
    }

    fun restore(document: CadDocument) {
        document.vertices.clear()
        document.vertices.addAll(vertices)

        document.edges.clear()
        document.edges.addAll(edges)

        document.bumpRevision()
    }
}