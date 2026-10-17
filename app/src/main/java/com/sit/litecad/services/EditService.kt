package com.sit.litecad.services

import com.sit.litecad.geometry.AxisPointSync
import com.sit.litecad.geometry.PolygonBuilder
import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.DimensionAnchorType
import com.sit.litecad.model.PolygonType
import com.sit.litecad.model.Selection

object EditService {

    /**
     * Удаление выделенного.
     * Возвращает true, если что-то было удалено (нужно для history.record()).
     *
     * Логика 1-в-1 из ПК EditService.Delete:
     *  1. Записать снапшот в историю.
     *  2. Подавить (suppress) выделенные Face-полигоны — они не удаляются,
     *     а запоминаются в suppressedFaceGeometryKeys, чтобы PolygonBuilder не воссоздал.
     *  3. Удалить выделенные рёбра.
     *  4. Удалить выделенные пользовательские полигоны (Wall/Room/Axis).
     *  5. Если были удалены рёбра — инвалидировать подавления + prune vertices.
     *  6. SyncFaces — перестроить грани.
     */
    fun delete(
        document: CadDocument,
        selection: Selection,
        history: DocumentHistoryService
    ): Boolean {
        if (selection.isEmpty) return false

        history.record(document)

        // 1. Suppress выделенных Face
        for (polygon in document.polygons) {
            if (polygon.id !in selection.selectedPolygonIds) continue
            if (polygon.type != PolygonType.FACE) continue
            PolygonBuilder.suppressFaceGeometry(document, polygon)
        }

        // 2. Удаляем выделенные рёбра
        val edgesBefore = document.edges.size
        document.edges.removeAll { it.id in selection.selectedEdgeIds }
        val removedAnyEdge = document.edges.size < edgesBefore

        // 3. Удаляем выделенные оси (вместе с привязанными к ним размерами).
        val removedAxisIds = selection.selectedAxisIds.toSet()
        val axisPointIdsOfRemovedAxes = document.axisPoints
            .filter { it.axisId in removedAxisIds }
            .map { it.id }
            .toSet()
        document.dimensions.removeAll { dim ->
            (dim.FirstAnchorType == DimensionAnchorType.AXIS_POINT &&
                (dim.FirstVertexId in removedAxisIds || dim.FirstVertexId in axisPointIdsOfRemovedAxes)) ||
                (dim.SecondAnchorType == DimensionAnchorType.AXIS_POINT &&
                    (dim.SecondVertexId in removedAxisIds || dim.SecondVertexId in axisPointIdsOfRemovedAxes))
        }

        val axesBefore = document.axes.size
        document.axes.removeAll { it.id in selection.selectedAxisIds }
        val removedAnyAxis = document.axes.size < axesBefore

        // Синхронизировать индексы после удаления рёбер/осей
        if (removedAnyEdge || removedAnyAxis) {
            document.rebuildIndexes()
        }

        // 3.1 Удаляем выделенные надписи (вне топологии — грани не затрагиваются).
        if (selection.selectedTextIds.isNotEmpty()) {
            val textsToRemove = document.texts.filter { it.id in selection.selectedTextIds }
            for (text in textsToRemove) {
                document.removeText(text)
            }
        }

        // 4. Удаляем выделенные пользовательские полигоны
        document.polygons.removeAll { polygon ->
            polygon.id in selection.selectedPolygonIds && polygon.type != PolygonType.FACE
        }

        // 5. Prune вершин + инвалидация suppression
        if (removedAnyEdge) {
            PolygonBuilder.invalidateSuppressionOnEdgeTopologyChange(document)
            TopologyService.pruneUnusedVertices(document)
        }

        // 6. Перестроить грани
        PolygonBuilder.syncFaces(document)
        // Пересобрать производные точки осей после удаления осей.
        AxisPointSync.sync(document)

        document.bumpRevision()
        return true
    }
}