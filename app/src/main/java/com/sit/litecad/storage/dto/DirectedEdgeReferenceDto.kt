package com.sit.litecad.storage.dto

import com.google.gson.annotations.SerializedName

data class DirectedEdgeReferenceDto(
    @SerializedName("edgeId") var edgeId: String = "",
    @SerializedName("forward") var forward: Boolean = true
)