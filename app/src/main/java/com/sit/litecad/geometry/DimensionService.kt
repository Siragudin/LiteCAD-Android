package com.sit.litecad.geometry

import com.sit.litecad.math.MathUtils
import com.sit.litecad.model.CadDimension
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.DimensionAnchorType
import java.util.UUID

/**
 * Эквивалент DimensionService из ПК-версии.
 * CRUD и валидация линейных размеров поверх CadDocument.
 */
object DimensionService {

    /**
     * Создаёт размер между двумя существующими вершинами.
     * Возвращает null, если вершины отсутствуют или раскладка вырождена.
     */
    fun create(
        document: CadDocument,
        firstVertexId: UUID,
        secondVertexId: UUID,
        offset: Double,
        tolerance: Double,
        textSize: Double = 8.0,
        firstAnchorType: DimensionAnchorType = DimensionAnchorType.VERTEX,
        secondAnchorType: DimensionAnchorType = DimensionAnchorType.VERTEX
    ): CadDimension? {
        // Якорь может быть вершиной (VERTEX) либо точкой оси (AXIS_POINT).
        val firstPosition = DimensionAnchorResolver.tryResolveAnchorPosition(
            document, firstAnchorType, firstVertexId
        ) ?: return null
        val secondPosition = DimensionAnchorResolver.tryResolveAnchorPosition(
            document, secondAnchorType, secondVertexId
        ) ?: return null

        // Проверяем, что раскладка вообще строится (иначе размер вырожден).
        DimensionGeometry.tryCreateLayout(
            firstPosition, secondPosition, offset, tolerance,
            isOrthogonal = false, orthogonalIsHorizontal = true
        ) ?: return null

        val dimension = CadDimension(
            FirstVertexId = firstVertexId,
            SecondVertexId = secondVertexId,
            FirstAnchorPosition = firstPosition,
            SecondAnchorPosition = secondPosition,
            FirstAnchorType = firstAnchorType,
            SecondAnchorType = secondAnchorType,
            Offset = offset,
            TextSize = textSize
        )
        document.addDimension(dimension)
        return dimension
    }

    fun delete(document: CadDocument, dimensionId: UUID): Boolean {
        val dimension = document.dimensionById(dimensionId) ?: return false
        document.removeDimension(dimension)
        return true
    }

    /**
     * Размер валиден, пока обе вершины существуют и их позиции не изменились
     * относительно снимка anchor-позиций.
     */
    fun isValid(document: CadDocument, dimension: CadDimension, tolerance: Double): Boolean {
        val first = DimensionAnchorResolver.resolveAnchorPosition(
            document, dimension.FirstAnchorType, dimension.FirstVertexId, dimension.FirstAnchorPosition
        )
        val second = DimensionAnchorResolver.resolveAnchorPosition(
            document, dimension.SecondAnchorType, dimension.SecondVertexId, dimension.SecondAnchorPosition
        )

        if (!MathUtils.arePointsEqual(first, dimension.FirstAnchorPosition, tolerance)) return false
        if (!MathUtils.arePointsEqual(second, dimension.SecondAnchorPosition, tolerance)) return false
        return true
    }

    /**
     * Удаляет все невалидные размеры. Возвращает количество удалённых.
     */
    fun removeInvalid(document: CadDocument, tolerance: Double): Int {
        val invalid = document.dimensions.filter { !isValid(document, it, tolerance) }
        for (dimension in invalid) {
            document.removeDimension(dimension)
        }
        return invalid.size
    }
}
