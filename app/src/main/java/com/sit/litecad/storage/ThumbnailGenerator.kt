package com.sit.litecad.storage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.EdgeLineType
import kotlin.math.max
import kotlin.math.min

object ThumbnailGenerator {

    private const val SIZE = 640

    fun generate(document: CadDocument): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        if (document.vertices.isEmpty()) return bitmap

        // Границы
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE

        for (v in document.vertices) {
            if (v.X < minX) minX = v.X
            if (v.Y < minY) minY = v.Y
            if (v.X > maxX) maxX = v.X
            if (v.Y > maxY) maxY = v.Y
        }

        val width = maxX - minX
        val height = maxY - minY
        val maxDim = max(width, height)
        if (maxDim <= 0.0) return bitmap

        val scale = (SIZE * 0.8) / maxDim
        val offsetX = (SIZE - width * scale) / 2.0 - minX * scale
        val offsetY = (SIZE - height * scale) / 2.0 - minY * scale

        val paint = Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        for (edge in document.edges) {
            val start = TopologyService.getEdgeStartPoint(document, edge)
            val end = TopologyService.getEdgeEndPoint(document, edge)

            val x1 = (start.X * scale + offsetX).toFloat()
            val y1 = (start.Y * scale + offsetY).toFloat()
            val x2 = (end.X * scale + offsetX).toFloat()
            val y2 = (end.Y * scale + offsetY).toFloat()

            paint.color = if (edge.isAxis) Color.rgb(0x99, 0x00, 0x00) else edge.colorArgb
            paint.strokeWidth = max(2f, edge.thickness.toFloat() * scale.toFloat())
            paint.pathEffect = when (edge.lineType) {
                EdgeLineType.DASHED -> DashPathEffect(floatArrayOf(10f, 5f), 0f)
                EdgeLineType.DOTTED -> DashPathEffect(floatArrayOf(2f, 5f), 0f)
                EdgeLineType.SOLID -> null
            }

            canvas.drawLine(x1, y1, x2, y2, paint)
        }

        return bitmap
    }
}