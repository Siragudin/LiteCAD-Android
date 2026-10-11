package com.sit.litecad.model

/**
 * Снимок состояния документа для undo/redo.
 * Хранит глубокую копию вершин, рёбер, пользовательских полигонов
 * и метаданных заливок/подавлений.
 * PolygonType.FACE в снапшот НЕ попадает — он перестраивается PolygonBuilder-ом.
 */
class DocumentSnapshot private constructor(
    private val vertices: List<CadVertex>,
    private val edges: List<CadEdge>,
    private val axes: List<CadAxis>,
    private val dimensions: List<CadDimension>,
    private val userPolygons: List<Polygon>,
    private val faceFillStyles: Map<String, FaceFillStyle>,
    private val suppressedFaceGeometryKeys: Set<String>
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
            val axesCopy = document.axes.map { axis -> axis.clone() }
            val dimensionsCopy = document.dimensions.map { dimension -> dimension.clone() }
            val userPolygonsCopy = document.polygons
                .filter { it.type != PolygonType.FACE }
                .map { it.clone() }
            val faceFillStylesCopy = document.faceFillStyles
                .mapValues { (_, style) -> style.clone() }
            val suppressedCopy = document.suppressedFaceGeometryKeys.toSet()

            return DocumentSnapshot(
                verticesCopy,
                edgesCopy,
                axesCopy,
                dimensionsCopy,
                userPolygonsCopy,
                faceFillStylesCopy,
                suppressedCopy
            )
        }
    }

    fun restore(document: CadDocument) {
        document.clearTopology()
        document.vertices.addAll(vertices)
        document.edges.addAll(edges)

        document.axes.clear()
        document.axes.addAll(axes.map { it.clone() })

        document.dimensions.clear()
        document.dimensions.addAll(dimensions.map { it.clone() })

        document.polygons.clear()
        document.polygons.addAll(userPolygons.map { it.clone() })

        document.faceFillStyles.clear()
        for ((key, style) in faceFillStyles) {
            document.faceFillStyles[key] = style.clone()
        }

        document.suppressedFaceGeometryKeys.clear()
        document.suppressedFaceGeometryKeys.addAll(suppressedFaceGeometryKeys)

        document.rebuildIndexes()
        com.sit.litecad.geometry.PolygonBuilder.syncFaces(document)
        document.bumpRevision()
    }
}