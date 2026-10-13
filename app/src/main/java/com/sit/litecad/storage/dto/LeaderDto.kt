package com.sit.litecad.storage.dto

import com.google.gson.annotations.SerializedName

data class LeaderDto(
    @SerializedName("id") var id: String = "",
    @SerializedName("kind") var kind: String = "elevation",
    @SerializedName("targetX") var targetX: Double = 0.0,
    @SerializedName("targetY") var targetY: Double = 0.0,
    @SerializedName("textPositionX") var textPositionX: Double = 0.0,
    @SerializedName("textPositionY") var textPositionY: Double = 0.0,
    @SerializedName("text") var text: String = "",
    @SerializedName("color") var color: String = "#FF1565C0"
)
