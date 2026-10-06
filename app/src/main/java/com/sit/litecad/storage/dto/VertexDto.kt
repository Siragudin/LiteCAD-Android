package com.sit.litecad.storage.dto

import com.google.gson.annotations.SerializedName

data class VertexDto(
    @SerializedName("id") var id: String = "",
    @SerializedName("x") var x: Double = 0.0,
    @SerializedName("y") var y: Double = 0.0
)