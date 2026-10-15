package com.sit.litecad.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Иконка инструмента «Выноска» (Leader).
 * Материал: Material Symbols Outlined, arrow_top_left.
 */
@Suppress("CheckReturnValue")
public val ArrowTopLeft: ImageVector
  get() {
    if (_arrowTopLeft != null) {
      return _arrowTopLeft!!
    }
    _arrowTopLeft =
      ImageVector.Builder(
          name = "arrow_top_left",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(17f, 20f)
            verticalLineTo(10f)
            horizontalLineTo(7.83f)
            lineToRelative(3.6f, 3.6f)
            lineToRelative(-1.4f, 1.43f)
            lineTo(4f, 9f)
            lineTo(10f, 3f)
            lineToRelative(1.43f, 1.42f)
            lineTo(7.83f, 8f)
            horizontalLineTo(19f)
            verticalLineTo(20f)
            horizontalLineTo(17f)
            close()
          }
        }
        .build()
    return _arrowTopLeft!!
  }

private var _arrowTopLeft: ImageVector? = null
