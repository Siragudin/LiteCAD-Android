package com.sit.litecad.storage.dto

import com.google.gson.annotations.SerializedName

data class SitManifestDto(
    @SerializedName("format") var format: String = "LiteCad",
    @SerializedName("version") var version: Int = 1,
    @SerializedName("projectName") var projectName: String? = null,
    @SerializedName("modifiedUtc") var modifiedUtc: String? = null
)