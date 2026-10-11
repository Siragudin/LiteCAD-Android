package com.sit.litecad.geometry

import com.sit.litecad.math.MathUtils
import com.sit.litecad.model.CadDimension
import com.sit.litecad.model.CadDocument
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
        textSize: Double = 8.0
    ): CadDimension? {
        val first = document.vertexById(firstVertexId) ?: return null
        val second = document.vertexById(secondVertexId) ?: return null

        // Проверяем, что раскладка вообще строится (иначе размер вырожден).
        DimensionGeometry.tryCreateLayout(
            first.position, second.position, offset, tolerance,
            isOrthogonal = false, orthogonalIsHorizontal = true
        ) ?: return null

        val dimension = CadDimension(
            FirstVertexId = firstVertexId,
            SecondVertexId = secondVertexId,
            FirstAnchorPosition = first.position,
            SecondAnchorPosition = second.position,
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
        val first = document.vertexById(dimension.FirstVertexId) ?: return false
        val second = document.vertexById(dimension.SecondVertexId) ?: return false

        if (!MathUtils.arePointsEqual(first.position, dimension.FirstAnchorPosition, tolerance)) return false
        if (!MathUtils.arePointsEqual(second.position, dimension.SecondAnchorPosition, tolerance)) return false
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
