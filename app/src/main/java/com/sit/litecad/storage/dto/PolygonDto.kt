package com.sit.litecad.storage.dto

import com.google.gson.annotations.SerializedName

data class PolygonDto(
    @SerializedName("type") var type: String = "face",
    @SerializedName("outerLoop") var outerLoop: MutableList<DirectedEdgeReferenceDto> = mutableListOf(),
    @SerializedName("innerLoops") var innerLoops: MutableList<MutableList<DirectedEdgeReferenceDto>> = mutableListOf()
)