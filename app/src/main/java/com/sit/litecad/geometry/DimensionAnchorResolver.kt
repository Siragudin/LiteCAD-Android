package com.sit.litecad.geometry

import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.DimensionAnchorType
import java.util.UUID

/**
 * Разрешает позицию якоря размера по типу якоря и его Id.
 *
 *  - [DimensionAnchorType.VERTEX] — ищем [com.sit.litecad.model.CadVertex] в документе;
 *  - [DimensionAnchorType.AXIS_POINT] — ищем [com.sit.litecad.model.CadAxisPoint].
 *
 * Если якорь не найден (удалён/сместился), возвращаем переданный [fallback]
 * (снимок anchor-позиции на момент создания размера) — размер продолжает рисоваться.
 */
object DimensionAnchorResolver {

    /** Позиция якоря или [fallback], если якорь не разрешён. Никогда не возвращает null. */
    fun resolveAnchorPosition(
        document: CadDocument,
        anchorType: DimensionAnchorType,
        anchorId: UUID,
        fallback: Point2
    ): Point2 {
        return tryResolveAnchorPosition(document, anchorType, anchorId) ?: fallback
    }

    /** Позиция якоря или null, если якорь не найден. */
    fun tryResolveAnchorPosition(
        document: CadDocument,
        anchorType: DimensionAnchorType,
        anchorId: UUID
    ): Point2? {
        return when (anchorType) {
            DimensionAnchorType.VERTEX -> document.vertexById(anchorId)?.position
            DimensionAnchorType.AXIS_POINT -> document.axisPointById(anchorId)?.position
        }
    }
}
