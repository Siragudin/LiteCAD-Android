package com.sit.litecad.storage.dto

import com.google.gson.annotations.SerializedName

data class AxisDto(
    @SerializedName("id") var id: String = "",
    @SerializedName("startX") var startX: Double = 0.0,
    @SerializedName("startY") var startY: Double = 0.0,
    @SerializedName("endX") var endX: Double = 0.0,
    @SerializedName("endY") var endY: Double = 0.0
)