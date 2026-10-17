package com.sit.litecad.pdf

import com.sit.litecad.geometry.DimensionAnchorResolver
import com.sit.litecad.geometry.DimensionGeometry
import com.sit.litecad.leaders.LeaderGeometry
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import kotlin.math.max
import kotlin.math.min

/** Точка в миллиметрах на PDF-странице (левый верхний угол = 0,0). */
data class PdfPoint(val xMm: Double, val yMm: Double)

/** Границы в мировых единицах. */
data class WorldBounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY
    val isEmpty: Boolean get() = width <= 1e-9 || height <= 1e-9
}

/**
 * Раскладка PDF-экспорта: границы содержимого, ориентация, масштаб, смещение.
 *
 * Все координаты страницы — в миллиметрах, начало в левом верхнем углу.
 * Мировые координаты чертежа — в единицах модели (обычно мм), Y вверх.
 * При переводе world → page инвертируем Y (page Y растёт вниз).
 *
 * [offsetXMm] и [offsetYMm] включают поле [PdfPageSpec.marginMm], то есть
 * отсчитываются от левого верхнего угла страницы, а не от угла печатной области.
 */
class PdfExportLayout private constructor(
    val pageSpec: PdfPageSpec,
    val worldBounds: WorldBounds,
    val scaleMmPerUnit: Double,
    val offsetXMm: Double,
    val offsetYMm: Double
) {
    /** Мировая точка → точка на странице в мм (левый верх, Y вниз). */
    fun worldToPageMm(point: Point2): PdfPoint {
        val x = offsetXMm + (point.X - worldBounds.minX) * scaleMmPerUnit
        val y = offsetYMm + (worldBounds.maxY - point.Y) * scaleMmPerUnit
        return PdfPoint(x, y)
    }

    companion object {
        /** Padding вокруг содержимого в мировых единицах (как в ПК — 2 мм по умолчанию). */
        private const val DEFAULT_PADDING_MM = 2.0

        /**
         * Вычислить раскладку по документу.
         * @param overrideOrientation если null — ориентация подбирается автоматически.
         */
        fun create(
            document: CadDocument,
            overrideOrientation: PdfPageOrientation? = null,
            marginMm: Double = 10.0
        ): PdfExportLayout {
            val bounds = computeWorldBounds(document)
                ?: WorldBounds(-50.0, -50.0, 50.0, 50.0)

            val orientation = overrideOrientation
                ?: if (bounds.width > bounds.height) PdfPageOrientation.LANDSCAPE
                else PdfPageOrientation.PORTRAIT

            val pageSpec = PdfPageSpec(
                orientation = orientation,
                marginMm = marginMm
            )

            val worldWidth = max(bounds.width, 1e-6)
            val worldHeight = max(bounds.height, 1e-6)

            val scaleX = pageSpec.contentWidthMm / worldWidth
            val scaleY = pageSpec.contentHeightMm / worldHeight
            val scale = min(scaleX, scaleY)

            // Центрирование содержимого в печатной области + отступ от края страницы (marginMm),
            // чтобы worldToPageMm возвращал координаты в мм от левого верхнего угла страницы.
            val usedWidthMm = worldWidth * scale
            val usedHeightMm = worldHeight * scale
            val offsetX = pageSpec.marginMm + (pageSpec.contentWidthMm - usedWidthMm) / 2.0
            val offsetY = pageSpec.marginMm + (pageSpec.contentHeightMm - usedHeightMm) / 2.0

            return PdfExportLayout(pageSpec, bounds, scale, offsetX, offsetY)
        }

        /**
         * Мировые границы содержимого:
         *  - все вершины документа (покрывают и концы рёбер);
         *  - оси;
         *  - размеры (anchor + extension endpoints + dimension line);
         *  - выноски (target, elbow, landing).
         * Если документ пуст — вернуть null.
         */
        private fun computeWorldBounds(document: CadDocument): WorldBounds? {
            var minX = Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE
            var hasAny = false

            fun expand(x: Double, y: Double) {
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
                hasAny = true
            }

            for (v in document.vertices) expand(v.X, v.Y)
            for (axis in document.axes) {
                expand(axis.Start.X, axis.Start.Y)
                expand(axis.End.X, axis.End.Y)
            }

            // Размеры — через DimensionGeometry (порт есть в Android).
            for (dim in document.dimensions) {
                val firstPosition = DimensionAnchorResolver.resolveAnchorPosition(
                    document, dim.FirstAnchorType, dim.FirstVertexId, dim.FirstAnchorPosition
                )
                val secondPosition = DimensionAnchorResolver.resolveAnchorPosition(
                    document, dim.SecondAnchorType, dim.SecondVertexId, dim.SecondAnchorPosition
                )
                val layout = DimensionGeometry.tryCreateLayout(
                    firstPosition, secondPosition, dim.Offset,
                    TopologyTolerance.ForMutation,
                    dim.IsOrthogonal, dim.OrthogonalIsHorizontal
                ) ?: continue
                expand(layout.firstAnchor.X, layout.firstAnchor.Y)
                expand(layout.secondAnchor.X, layout.secondAnchor.Y)
                expand(layout.firstExtensionEnd.X, layout.firstExtensionEnd.Y)
                expand(layout.secondExtensionEnd.X, layout.secondExtensionEnd.Y)
                expand(layout.dimensionLineStart.X, layout.dimensionLineStart.Y)
                expand(layout.dimensionLineEnd.X, layout.dimensionLineEnd.Y)
            }

            // Выноски — через LeaderGeometry.createLayout.
            for (leader in document.leaders) {
                val layout = LeaderGeometry.createLayout(leader.target, leader.textPosition, 1.0)
                expand(layout.target.X, layout.target.Y)
                expand(layout.elbow.X, layout.elbow.Y)
                expand(layout.landingEnd.X, layout.landingEnd.Y)
                expand(layout.chevronLeft.X, layout.chevronLeft.Y)
                expand(layout.chevronRight.X, layout.chevronRight.Y)
            }

            if (!hasAny) return null
            // Небольшой padding в мировых единицах.
            val pad = DEFAULT_PADDING_MM
            return WorldBounds(minX - pad, minY - pad, maxX + pad, maxY + pad)
        }
    }
}
