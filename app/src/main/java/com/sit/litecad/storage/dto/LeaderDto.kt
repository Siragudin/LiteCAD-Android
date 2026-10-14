package com.sit.litecad.storage.dto

import com.google.gson.annotations.SerializedName

data class LeaderDto(
    @SerializedName("id") var id: String = "",
    @SerializedName("kind") var kind: String = "elevation",
    @SerializedName("targetX") var targetX: Double = 0.0,
    @SerializedName("targetY") var targetY: Double = 0.0,
    /**
     * Позиция полки выноски.
     * ПК-версия пишет `textX` / `textY` — это основной формат (совместимость проектов).
     * Android до этой правки писал `textPositionX` / `textPositionY` — читаем их как
     * alternate, чтобы старые Android-проекты тоже открывались.
     */
    @SerializedName(value = "textX", alternate = ["textPositionX"])
    var textX: Double = 0.0,
    @SerializedName(value = "textY", alternate = ["textPositionY"])
    var textY: Double = 0.0,
    @SerializedName("text") var text: String = "",
    @SerializedName("color") var color: String = "#FF1565C0"
)
