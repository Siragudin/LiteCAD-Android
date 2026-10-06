package com.sit.litecad.storage.dto

import com.google.gson.annotations.SerializedName

data class FaceFillStyleDto(
    @SerializedName("fillColor") var fillColor: String = "#FFF0F0F0",
    @SerializedName("fillPattern") var fillPattern: String = "solid"
)