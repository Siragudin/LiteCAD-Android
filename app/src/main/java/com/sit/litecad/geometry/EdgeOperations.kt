package com.sit.litecad.geometry

import com.sit.litecad.math.Geometry2D
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.CadEdge
import kotlin.collections.sortWith
import kotlin.math.abs

object EdgeOperations {
    private const val ParameterTolerance = 1e-6

    /**
     * Фундаментальный метод добавления линии в топологический граф CAD (Аналог AddSegment с ПК)
     */
    fun addSegment(document: CadDocument, start: Point2, end: Point2, template: CadEdge, tolerance: Double) {
        val mutTolerance = TopologyTolerance.ForMutation

        // 1. Защита от линий нулевой длины
        if (MathUtils.distance(start, end) <= mutTolerance) {
            return
        }

        // 2. Если линия полностью совпадает с существующей — просто обновляем её стиль
        if (tryReplaceCoincidentEdge(document, start, end, template, mutTolerance)) {
            return
        }

        // 3. Удаляем коллинеарные перекрытия, чтобы ЧПУ станок не делал двойных проходов
        removeCollinearOverlaps(document, start, end, mutTolerance)

        // Массив точек, на которые разобьется наша новая линия при пересечениях
        val breakpoints = mutableListOf(start, end)

        // 4. Цикл поиска пересечений со всеми существующими ребрами чертежа
        for (edge in document.edges.toList()) {
            val edgeStart = TopologyService.getEdgeStartPoint(document, edge)
            val edgeEnd = TopologyService.getEdgeEndPoint(document, edge)

            // Проверяем геометрическое пересечение отрезков
            val intersection = Geometry2D.tryGetSegmentIntersection(start, end, edgeStart, edgeEnd, mutTolerance)
            if (intersection != null) {
                // Если пересечение внутри нашей новой линии — добавляем точку разбиения
                if (Geometry2D.isPointOnSegmentInterior(intersection, start, end, mutTolerance)) {
                    addUniquePoint(breakpoints, intersection, mutTolerance)
                }

                // Если пересечение внутри существующего ребра — бьем существующее ребро на две части
                if (Geometry2D.isPointOnSegmentInterior(intersection, edgeStart, edgeEnd, mutTolerance)) {
                    trySplitEdge(document, edge, intersection, mutTolerance)
                }
            }

            // Проверяем, не попали ли концы нашей линии на тело существующего ребра
            if (Geometry2D.isPointOnSegmentInterior(start, edgeStart, edgeEnd, mutTolerance)) {
                trySplitEdge(document, edge, start, mutTolerance)
            }

            if (Geometry2D.isPointOnSegmentInterior(end, edgeStart, edgeEnd, mutTolerance)) {
                trySplitEdge(document, edge, end, mutTolerance)
            }
        }

        // 5. Сортируем все точки разбиения строго вдоль направления вектора линии
        sortPointsAlongSegment(breakpoints, start, end, mutTolerance)

        // 6. Собираем итоговые чистые ребра топологии из отсортированных сегментов
        for (i in 0 until breakpoints.size - 1) {
            val segmentStart = breakpoints[i]
            val segmentEnd = breakpoints[i + 1]

            if (MathUtils.distance(segmentStart, segmentEnd) <= mutTolerance) {
                continue
            }

            if (hasCoincidentEdge(document, segmentStart, segmentEnd, mutTolerance)) {
                continue
            }

            // Финальное создание ребра и его регистрация в графе уникальных вершин документов
            TopologyService.createEdgeFromPoints(document, segmentStart, segmentEnd, template, mutTolerance)
        }
    }

    private fun trySplitEdge(document: CadDocument, edge: CadEdge, point: Point2, tolerance: Double) {
        if (!document.edges.contains(edge)) {
            return
        }
        TopologyService.splitEdge(document, edge, point, tolerance)
    }

    private fun tryReplaceCoincidentEdge(document: CadDocument, start: Point2, end: Point2, template: CadEdge, tolerance: Double): Boolean {
        for (edge in document.edges.toList()) {
            val edgeStart = TopologyService.getEdgeStartPoint(document, edge)
            val edgeEnd = TopologyService.getEdgeEndPoint(document, edge)

            if (!isSameSegment(start, end, edgeStart, edgeEnd, tolerance)) {
                continue
            }

            // Применяем новый стиль ребра
            applyTemplate(edge, template)
            val startVertexId = TopologyService.findOrCreateVertex(document, start, tolerance)
            val endVertexId = TopologyService.findOrCreateVertex(document, end, tolerance)

            edge.startVertexId = startVertexId
            edge.endVertexId = endVertexId
            edge.orthoAlignment = template.orthoAlignment
            return true
        }
        return false
    }

    private fun removeCollinearOverlaps(document: CadDocument, start: Point2, end: Point2, tolerance: Double) {
        for (edge in document.edges.toList()) {
            val edgeStart = TopologyService.getEdgeStartPoint(document, edge)
            val edgeEnd = TopologyService.getEdgeEndPoint(document, edge)

            val overlap = Geometry2D.tryGetCollinearSegmentOverlap(start, end, edgeStart, edgeEnd, tolerance) ?: continue

            if (isSameSegment(start, end, edgeStart, edgeEnd, tolerance)) {
                continue
            }

            trimEdgeOverlap(document, edge, overlap.start, overlap.end, tolerance)
        }
    }

    private fun trimEdgeOverlap(document: CadDocument, edge: CadEdge, overlapStart: Point2, overlapEnd: Point2, tolerance: Double) {
        val edgeStart = TopologyService.getEdgeStartPoint(document, edge)
        val edgeEnd = TopologyService.getEdgeEndPoint(document, edge)
        val template = edge.cloneGeometry()
        val startVertexId = edge.startVertexId
        val endVertexId = edge.endVertexId

        TopologyService.deleteEdge(document, edge)

        var tStart = Geometry2D.getSegmentParameter(overlapStart, edgeStart, edgeEnd, tolerance)
        var tEnd = Geometry2D.getSegmentParameter(overlapEnd, edgeStart, edgeEnd, tolerance)
        if (tStart > tEnd) {
            val temp = tStart
            tStart = tEnd
            tEnd = temp
        }

        if (tStart > ParameterTolerance) {
            val beforeEndVertexId = TopologyService.findOrCreateVertex(document, overlapStart, tolerance)
            if (MathUtils.distance(edgeStart, overlapStart) > tolerance) {
                TopologyService.createEdge(document, startVertexId, beforeEndVertexId, template)
            }
        }

        if (tEnd < 1.0 - ParameterTolerance) {
            val afterStartVertexId = TopologyService.findOrCreateVertex(document, overlapEnd, tolerance)
            if (MathUtils.distance(overlapEnd, edgeEnd) > tolerance) {
                TopologyService.createEdge(document, afterStartVertexId, endVertexId, template)
            }
        }
    }

    private fun hasCoincidentEdge(document: CadDocument, start: Point2, end: Point2, tolerance: Double): Boolean {
        for (edge in document.edges) {
            val edgeStart = TopologyService.getEdgeStartPoint(document, edge)
            val edgeEnd = TopologyService.getEdgeEndPoint(document, edge)
            if (isSameSegment(start, end, edgeStart, edgeEnd, tolerance)) {
                return true
            }
        }
        return false
    }

    private fun isSameSegment(aStart: Point2, aEnd: Point2, bStart: Point2, bEnd: Point2, tolerance: Double): Boolean {
        return (MathUtils.arePointsEqual(aStart, bStart, tolerance) && MathUtils.arePointsEqual(aEnd, bEnd, tolerance)) ||
                (MathUtils.arePointsEqual(aStart, bEnd, tolerance) && MathUtils.arePointsEqual(aEnd, bStart, tolerance))
    }

    private fun applyTemplate(edge: CadEdge, template: CadEdge) {
        edge.colorArgb = template.colorArgb
        edge.thickness = template.thickness
        edge.lineType = template.lineType
        edge.isAxis = template.isAxis
        edge.orthoAlignment = template.orthoAlignment
    }

    private fun addUniquePoint(points: MutableList<Point2>, point: Point2, tolerance: Double) {
        if (points.any { MathUtils.arePointsEqual(it, point, tolerance) }) return
        points.add(point)
    }

    private fun sortPointsAlongSegment(points: MutableList<Point2>, start: Point2, end: Point2, tolerance: Double) {
        val dx = end.X - start.X
        val dy = end.Y - start.Y
        points.sortWith { a, b ->
            val ta = if (abs(dx) >= abs(dy)) {
                (a.X - start.X) / if (dx == 0.0) tolerance else dx
            } else {
                (a.Y - start.Y) / if (dy == 0.0) tolerance else dy
            }
            val tb = if (abs(dx) >= abs(dy)) {
                (b.X - start.X) / if (dx == 0.0) tolerance else dx
            } else {
                (b.Y - start.Y) / if (dy == 0.0) tolerance else dy
            }
            ta.compareTo(tb)
        }
    }

}
