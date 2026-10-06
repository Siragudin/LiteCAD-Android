package com.sit.litecad.storage.dto

import com.google.gson.annotations.SerializedName

data class LeaderDto(
    @SerializedName("id") var id: String = "",
    @SerializedName("kind") var kind: String = "text",
    @SerializedName("targetX") var targetX: Double = 0.0,
    @SerializedName("targetY") var targetY: Double = 0.0,
    @SerializedName("textX") var textX: Double = 0.0,
    @SerializedName("textY") var textY: Double = 0.0,
    @SerializedName("text") var text: String = ""
)