package com.sit.litecad.storage.dto

import com.google.gson.annotations.SerializedName

data class DimensionDto(
    @SerializedName("id") var id: String = "",
    @SerializedName("firstVertexId") var firstVertexId: String = "",
    @SerializedName("secondVertexId") var secondVertexId: String = "",
    @SerializedName("firstAnchorX") var firstAnchorX: Double = 0.0,
    @SerializedName("firstAnchorY") var firstAnchorY: Double = 0.0,
    @SerializedName("secondAnchorX") var secondAnchorX: Double = 0.0,
    @SerializedName("secondAnchorY") var secondAnchorY: Double = 0.0,
    @SerializedName("offset") var offset: Double = 0.0,
    @SerializedName("extensionStyle") var extensionStyle: String = "full",
    @SerializedName("isOrthogonal") var isOrthogonal: Boolean = false,
    @SerializedName("orthogonalIsHorizontal") var orthogonalIsHorizontal: Boolean = false,
    @SerializedName("textSize") var textSize: Double? = null
)