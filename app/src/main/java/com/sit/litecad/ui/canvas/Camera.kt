package com.sit.litecad.ui.canvas

import androidx.compose.ui.geometry.Offset
import com.sit.litecad.math.Point2

data class Camera(
    val zoom: Double,
    val panX: Double,
    val panY: Double
) {
    fun worldToScreen(p: Point2): Offset =
        Offset((p.X * zoom + panX).toFloat(), (p.Y * zoom + panY).toFloat())

    fun screenToWorld(o: Offset): Point2 =
        Point2((o.x - panX) / zoom, (o.y - panY) / zoom)
}