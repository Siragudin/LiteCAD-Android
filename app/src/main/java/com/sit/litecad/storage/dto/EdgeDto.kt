package com.sit.litecad.storage.dto

import com.google.gson.annotations.SerializedName

data class EdgeDto(
    @SerializedName("id") var id: String = "",
    @SerializedName("startVertexId") var startVertexId: String = "",
    @SerializedName("endVertexId") var endVertexId: String = "",
    @SerializedName("isAxis") var isAxis: Boolean = false,
    @SerializedName("color") var color: String = "#FF222222",
    @SerializedName("thickness") var thickness: Double = 1.5,
    @SerializedName("lineType") var lineType: String = "solid",
    @SerializedName("orthoAlignment") var orthoAlignment: String = "none"
)