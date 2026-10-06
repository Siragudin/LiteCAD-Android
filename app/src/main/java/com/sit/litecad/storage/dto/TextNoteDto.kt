package com.sit.litecad.storage.dto

import com.google.gson.annotations.SerializedName

data class TextNoteDto(
    @SerializedName("id") var id: String = "",
    @SerializedName("kind") var kind: String = "plain",
    @SerializedName("originX") var originX: Double = 0.0,
    @SerializedName("originY") var originY: Double = 0.0,
    @SerializedName("arrowTipX") var arrowTipX: Double? = null,
    @SerializedName("arrowTipY") var arrowTipY: Double? = null,
    @SerializedName("text") var text: String = "",
    @SerializedName("textSize") var textSize: Double = 12.0
)