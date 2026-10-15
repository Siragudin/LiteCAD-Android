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
 * Иконка инструмента «Удлинить» (Extend).
 * Материал: Material Symbols Outlined, line_end.
 */
@Suppress("CheckReturnValue")
public val LineEnd: ImageVector
  get() {
    if (_lineEnd != null) {
      return _lineEnd!!
    }
    _lineEnd =
      ImageVector.Builder(
          name = "line_end",
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
            moveTo(19.5f, 14.5f)
            quadToRelative(-0.77f, 0f, -1.4f, -0.43f)
            reflectiveQuadTo(17.2f, 13f)
            horizontalLineTo(2f)
            verticalLineTo(11f)
            horizontalLineTo(17.2f)
            quadToRelative(0.28f, -0.65f, 0.9f, -1.08f)
            reflectiveQuadTo(19.5f, 9.5f)
            quadToRelative(1.05f, 0f, 1.78f, 0.72f)
            reflectiveQuadTo(22f, 12f)
            reflectiveQuadToRelative(-0.72f, 1.77f)
            reflectiveQuadTo(19.5f, 14.5f)
            close()
          }
        }
        .build()
    return _lineEnd!!
  }

private var _lineEnd: ImageVector? = null
