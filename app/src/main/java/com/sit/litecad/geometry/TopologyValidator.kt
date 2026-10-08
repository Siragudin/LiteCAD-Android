package com.sit.litecad.geometry

import com.sit.litecad.math.Geometry2D
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.Loop
import com.sit.litecad.model.PolygonType

object TopologyValidator {

    /**
     * Полная валидация документа. Возвращает список ошибок (пустой = ОК).
     */
    fun validate(document: CadDocument, tolerance: Double = TopologyTolerance.ForMutation): List<String> {
        val errors = ArrayList<String>()

        // 1. Рёбра ссылаются на существующие вершины, не вырождены.
        for (edge in document.edges) {
            if (document.vertexById(edge.startVertexId) == null) {
                errors.add("Edge ${edge.id} references missing StartVertexId ${edge.startVertexId}.")
            }
            if (document.vertexById(edge.endVertexId) == null) {
                errors.add("Edge ${edge.id} references missing EndVertexId ${edge.endVertexId}.")
            }
            if (edge.startVertexId == edge.endVertexId) {
                errors.add("Edge ${edge.id} has identical start and end vertex.")
            }
        }

        // 2. Вершины имеют корректные координаты.
        for (vertex in document.vertices) {
            if (vertex.X.isNaN() || vertex.Y.isNaN() || vertex.X.isInfinite() || vertex.Y.isInfinite()) {
                errors.add("Vertex ${vertex.id} has invalid position.")
            }
        }

        // 3. Нет дублирующих рёбер (геометрически совпадающих отрезков).
        validateDuplicateEdges(document, errors, tolerance)

        // 4. Полигоны: контуры непрерывны и замкнуты.
        for (polygon in document.polygons) {
            if (polygon.type == PolygonType.FACE) {
                if (polygon.outerLoop.edges.isEmpty()) {
                    errors.add("Face polygon ${polygon.id} has empty OuterLoop.")
                } else {
                    validateLoop(document, polygon.outerLoop, errors, tolerance, "Face ${polygon.id} OuterLoop")
                }
                for (i in polygon.innerLoops.indices) {
                    validateLoop(
                        document,
                        polygon.innerLoops[i],
                        errors,
                        tolerance,
                        "Face ${polygon.id} InnerLoop[$i]"
                    )
                }
            } else if (polygon.outerLoop.edges.isNotEmpty()) {
                validateLoop(document, polygon.outerLoop, errors, tolerance, "OuterLoop")
                for (i in polygon.innerLoops.indices) {
                    validateLoop(document, polygon.innerLoops[i], errors, tolerance, "InnerLoop[$i]")
                }
            }
        }

        return errors
    }

    fun isValid(document: CadDocument, tolerance: Double = TopologyTolerance.ForMutation): Boolean =
        validate(document, tolerance).isEmpty()

    fun assertValid(document: CadDocument, tolerance: Double = TopologyTolerance.ForMutation) {
        val errors = validate(document, tolerance)
        if (errors.isEmpty()) return
        throw IllegalStateException(
            "Invalid topology:\n" + errors.joinToString("\n")
        )
    }

    private fun validateDuplicateEdges(
        document: CadDocument,
        errors: MutableList<String>,
        tolerance: Double
    ) {
        val edges = document.edges
        for (i in edges.indices) {
            for (j in i + 1 until edges.size) {
                val a = edges[i]
                val b = edges[j]
                val aStart = TopologyService.getEdgeStartPoint(document, a)
                val aEnd = TopologyService.getEdgeEndPoint(document, a)
                val bStart = TopologyService.getEdgeStartPoint(document, b)
                val bEnd = TopologyService.getEdgeEndPoint(document, b)

                if (segmentsEqual(aStart, aEnd, bStart, bEnd, tolerance)) {
                    errors.add("Duplicate edge topology between ${a.id} and ${b.id}.")
                }
            }
        }
    }

    private fun validateLoop(
        document: CadDocument,
        loop: Loop,
        errors: MutableList<String>,
        tolerance: Double,
        label: String
    ) {
        if (loop.edges.isEmpty()) {
            errors.add("$label is empty.")
            return
        }

        var previousEnd: java.util.UUID? = null
        var firstStart: java.util.UUID? = null

        for (i in loop.edges.indices) {
            val ref = loop.edges[i]
            val edge = document.edgeById(ref.edgeId)
            if (edge == null) {
                errors.add("$label references missing edge ${ref.edgeId}.")
                continue
            }

            val startVertexId = if (ref.forward) edge.startVertexId else edge.endVertexId
            val endVertexId = if (ref.forward) edge.endVertexId else edge.startVertexId

            if (document.vertexById(startVertexId) == null ||
                document.vertexById(endVertexId) == null
            ) {
                errors.add("$label edge ${edge.id} references missing vertex.")
                continue
            }

            if (i == 0) {
                firstStart = startVertexId
            } else if (previousEnd != startVertexId) {
                errors.add("$label is not continuous at directed edge index $i.")
            }

            previousEnd = endVertexId
        }

        if (firstStart != null && previousEnd != firstStart) {
            errors.add("$label is not closed.")
        }
    }

    private fun segmentsEqual(
        aStart: Point2,
        aEnd: Point2,
        bStart: Point2,
        bEnd: Point2,
        tolerance: Double
    ): Boolean {
        return (MathUtils.arePointsEqual(aStart, bStart, tolerance) &&
                MathUtils.arePointsEqual(aEnd, bEnd, tolerance)) ||
                (MathUtils.arePointsEqual(aStart, bEnd, tolerance) &&
                        MathUtils.arePointsEqual(aEnd, bStart, tolerance))
    }
}