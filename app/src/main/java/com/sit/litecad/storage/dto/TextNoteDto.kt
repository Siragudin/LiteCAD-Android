package com.sit.litecad.storage.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO надписи (CadText).
 *
 * Формат общий с ПК-версией: id / origin / text / textSize.
 * Android дополнительно пишет `sizeSp` (экранный размер, sp), `rotation` и `color`;
 * в старых .sit и в файлах ПК-версии этих полей нет — тогда размер выводится из
 * `textSize`, поворот = 0°, цвет = по умолчанию.
 */
data class TextNoteDto(
    @SerializedName("id") var id: String = "",
    @SerializedName("kind") var kind: String = "plain",
    /** Мировая позиция надписи, мм (соответствует CadText.position). */
    @SerializedName("originX") var originX: Double = 0.0,
    @SerializedName("originY") var originY: Double = 0.0,
    @SerializedName("arrowTipX") var arrowTipX: Double? = null,
    @SerializedName("arrowTipY") var arrowTipY: Double? = null,
    @SerializedName("text") var text: String = "",
    /**
     * Размер в мм (ПК-формат). Android пишет сюда sp-значение [com.sit.litecad.model.TextSizeSp]
     * для совместимости, авторитетным для Android остаётся `sizeSp`.
     */
    @SerializedName("textSize") var textSize: Double = 14.0,
    /** Экранный размер шрифта: "small" | "normal" | "large" | "xlarge". Может отсутствовать. */
    @SerializedName("sizeSp") var sizeSp: String? = null,
    /** Поворот на экране: "deg0" | "deg90". Может отсутствовать. */
    @SerializedName("rotation") var rotation: String? = null,
    /** Цвет в формате "#AARRGGBB". */
    @SerializedName("color") var color: String = "#FF222222"
)
