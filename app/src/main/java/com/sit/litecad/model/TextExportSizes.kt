package com.sit.litecad.model

/**
 * Маппинг экранных параметров надписи [CadText] в чертёжные величины
 * для экспорта в PDF (мм на бумаге) и DXF (мм в СК чертежа).
 *
 * Файл лежит в `model/`, потому что нужен обоим модулям экспорта:
 * `pdf/` и `dxf/` намеренно не зависят друг от друга (см. дублирование
 * расчёта габаритов в DxfExporter.characteristicSize).
 *
 * Модель ([CadText], [TextSizeSp], [TextRotation]) не меняется.
 */
object TextExportSizes {

    /**
     * Высота шрифта в мм по ГОСТ 2.304-81 (ряд: 2.5, 3.5, 5, 7, 10…).
     * Маппинг экранных sp на стандартные чертёжные размеры:
     *   12 sp → 3.5 мм
     *   14 sp → 5.0 мм
     *   18 sp → 7.0 мм
     *   24 sp → 10.0 мм
     */
    fun heightMm(sizeSp: TextSizeSp): Float = when (sizeSp) {
        TextSizeSp.SMALL -> 3.5f
        TextSizeSp.NORMAL -> 5.0f
        TextSizeSp.LARGE -> 7.0f
        TextSizeSp.XLARGE -> 10.0f
    }

    /**
     * Угол поворота в градусах для канвы PDF.
     *
     * Оси страницы PDF направлены ВНИЗ (PDF-координаты получаются из мировых
     * через PdfExportLayout.worldToPageMm с инверсией Y), то есть так же, как на
     * экране. Поэтому 270° == -90° — тот же угол, что и в CadRenderer:
     * текст читается СНИЗУ ВВЕРХ.
     */
    fun pdfRotationDeg(rotation: TextRotation): Float =
        if (rotation == TextRotation.DEG_90) 270f else 0f

    /**
     * Угол поворота в градусах для DXF (group code 50).
     *
     * В DXF СК чертежа направлена ВВЕРХ, а угол отсчитывается против часовой
     * стрелки от +X. Чтение СНИЗУ ВВЕРХ даёт здесь 90°, а не 270°.
     * Ровно эта пара значений уже используется в экспорте размеров:
     * PdfExporter.drawDimensionText → -90° для вертикальной размерной линии,
     * DxfExporter.dimensionTextAngleDegrees → +90° для того же случая.
     */
    fun dxfRotationDeg(rotation: TextRotation): Float =
        if (rotation == TextRotation.DEG_90) 90f else 0f
}
