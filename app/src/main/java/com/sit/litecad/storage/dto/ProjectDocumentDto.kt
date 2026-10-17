package com.sit.litecad.storage.dto

import com.google.gson.annotations.SerializedName

data class ProjectDocumentDto(
    @SerializedName("formatVersion") var formatVersion: Int = 1,
    @SerializedName("linearDisplayUnit") var linearDisplayUnit: String = "Millimeters",
    @SerializedName("vertices") var vertices: MutableList<VertexDto> = mutableListOf(),
    @SerializedName("edges") var edges: MutableList<EdgeDto> = mutableListOf(),
    @SerializedName("userPolygons") var userPolygons: MutableList<PolygonDto> = mutableListOf(),
    @SerializedName("dimensions") var dimensions: MutableList<DimensionDto> = mutableListOf(),
    @SerializedName("axes") var axes: MutableList<AxisDto> = mutableListOf(),
    @SerializedName("axisPoints") var axisPoints: MutableList<AxisPointDto> = mutableListOf(),
    @SerializedName("leaders") var leaders: MutableList<LeaderDto> = mutableListOf(),
    @SerializedName("texts") var texts: MutableList<TextNoteDto> = mutableListOf(),
    @SerializedName("elevationBaseY") var elevationBaseY: Double? = null,
    @SerializedName("suppressedFaceGeometryKeys") var suppressedFaceGeometryKeys: MutableList<String> = mutableListOf(),
    @SerializedName("faceFillStyles") var faceFillStyles: MutableMap<String, FaceFillStyleDto> = mutableMapOf()
)