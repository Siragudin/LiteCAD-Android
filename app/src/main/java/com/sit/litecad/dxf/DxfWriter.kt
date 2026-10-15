package com.sit.litecad.dxf

import java.util.Locale
import kotlin.math.abs

/**
 * Низкоуровневый построитель DXF R12 (AC1009) в формате ASCII.
 *
 * Отвечает только за формат файла: групповые коды, секции, строки.
 * Не зависит от модели документа и UI — на вход идут готовые числа/строки.
 *
 * Секции формируются в порядке: HEADER → TABLES → ENTITIES → EOF.
 *
 * В R12 данные группируются парами строк: на первой строке групповой код,
 * на второй — значение. Все числа пишутся с точкой-разделителем (локаль US).
 */
class DxfWriter {

    private val sb = StringBuilder()

    // Содержимое таблиц копится в отдельных буферах: количество записей (group 70
    // в заголовке TABLE) известно только после добавления всех элементов.
    private val ltypeBody = StringBuilder()
    private val layerBody = StringBuilder()
    private var ltypeCount = 0
    private var layerCount = 0

    /** HEADER: $ACADVER = AC1009 (R12), $INSUNITS = 4 (мм). */
    fun beginDocument() {
        group(0, "SECTION")
        group(2, "HEADER")
        group(9, "\$ACADVER")
        group(1, "AC1009")
        group(9, "\$INSUNITS")
        group(70, 4)
        group(0, "ENDSEC")
    }

    /** Открыть секцию TABLES. Записи копятся и выписываются в [endTables]. */
    fun beginTables() {
        group(0, "SECTION")
        group(2, "TABLES")
    }

    /**
     * Добавить определение типа линии (LTYPE).
     * @param pattern шаблон штрихов в единицах чертежа (пустой список — сплошная линия).
     */
    fun addLineType(name: String, description: String, pattern: List<Double>) {
        appendGroup(ltypeBody, 0, "LTYPE")
        appendGroup(ltypeBody, 2, name)
        appendGroup(ltypeBody, 70, 0)
        appendGroup(ltypeBody, 3, description)
        appendGroup(ltypeBody, 72, 65) // 65 = 'A' — выравнивание по всему шаблону
        appendGroup(ltypeBody, 73, pattern.size)
        appendGroup(ltypeBody, 40, pattern.sumOf { abs(it) }) // длина цикла
        for (d in pattern) appendGroup(ltypeBody, 49, d)
        ltypeCount++
    }

    /** Добавить определение слоя (LAYER). */
    fun addLayer(name: String, aciColor: Int, lineTypeName: String) {
        appendGroup(layerBody, 0, "LAYER")
        appendGroup(layerBody, 2, name)
        appendGroup(layerBody, 70, 0)
        appendGroup(layerBody, 62, aciColor)
        appendGroup(layerBody, 6, lineTypeName)
        layerCount++
    }

    /** Закрыть секцию TABLES: выписать таблицы LTYPE и LAYER с подсчитанным количеством. */
    fun endTables() {
        group(0, "TABLE")
        group(2, "LTYPE")
        group(70, ltypeCount)
        sb.append(ltypeBody)

        group(0, "ENDTAB")
        group(0, "TABLE")
        group(2, "LAYER")
        group(70, layerCount)
        sb.append(layerBody)

        group(0, "ENDTAB")
        group(0, "ENDSEC")
    }

    /** Открыть секцию ENTITIES. */
    fun beginEntities() {
        group(0, "SECTION")
        group(2, "ENTITIES")
    }

    /** Добавить отрезок (LINE). Z-координаты — 0. */
    fun addLine(x1: Double, y1: Double, x2: Double, y2: Double, layer: String) {
        group(0, "LINE")
        group(8, layer)
        group(10, x1)
        group(20, y1)
        group(30, 0.0)
        group(11, x2)
        group(21, y2)
        group(31, 0.0)
    }

    /**
     * Добавить однострочный текст (TEXT).
     * Выравнивание — по левому краю (group 72 = 0), поворот в градусах (group 50).
     */
    fun addText(
        x: Double,
        y: Double,
        height: Double,
        text: String,
        layer: String,
        rotationDeg: Double = 0.0
    ) {
        group(0, "TEXT")
        group(8, layer)
        group(10, x)
        group(20, y)
        group(30, 0.0)
        group(40, height)
        group(1, text)
        group(50, rotationDeg)
        group(72, 0)
    }

    /** Закрыть секцию ENTITIES. */
    fun endEntities() {
        group(0, "ENDSEC")
    }

    /** Завершить файл (EOF). */
    fun endDocument() {
        group(0, "EOF")
    }

    /** Готовый текст DXF. */
    fun build(): String = sb.toString()

    // --- внутреннее ---

    private fun appendGroup(target: StringBuilder, code: Int, value: Any) {
        target.append(code).append('\n')
        target.append(formatValue(value)).append('\n')
    }

    private fun group(code: Int, value: Any) = appendGroup(sb, code, value)

    private fun formatValue(value: Any): String = when (value) {
        is Double -> num(value)
        else -> value.toString()
    }

    /** Число с точкой-разделителем (локаль US), без лишних нулей. */
    private fun num(v: Double): String {
        if (v == 0.0 || v.isNaN() || v.isInfinite()) return "0.0"
        var s = String.format(Locale.US, "%.6f", v)
        if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
        return if (s.isEmpty() || s == "-") "0.0" else s
    }
}
