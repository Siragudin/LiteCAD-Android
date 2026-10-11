package com.sit.litecad.storage.dto

import com.google.gson.annotations.SerializedName

data class AxisDto(
    @SerializedName("id") var id: String = "",
    @SerializedName("startX") var startX: Double = 0.0,
    @SerializedName("startY") var startY: Double = 0.0,
    @SerializedName("endX") var endX: Double = 0.0,
    @SerializedName("endY") var endY: Double = 0.0,
    @SerializedName("color") var color: String = "#FF1565C0",
    @SerializedName("thickness") var thickness: Double = 1.5
)