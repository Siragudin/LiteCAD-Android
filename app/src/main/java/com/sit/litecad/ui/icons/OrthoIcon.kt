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
 * Иконка кнопки «Ortho».
 * Две перпендикулярные стрелки из общего угла в левом нижнем углу:
 * горизонтальная (слева направо) и вертикальная (снизу вверх), обе со стрелочными наконечниками.
 * Классический стиль CAD-иконки Ortho. Цвет задаётся tint'ом из [androidx.compose.material3.Icon].
 */
@Suppress("CheckReturnValue")
public val OrthoIcon: ImageVector
  get() {
    if (_orthoIcon != null) {
      return _orthoIcon!!
    }
    _orthoIcon =
      ImageVector.Builder(
          name = "ortho",
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
            // Горизонтальная стрелка: из угла слева направо.
            moveTo(5f, 17f)
            horizontalLineTo(20f)
            verticalLineTo(19f)
            horizontalLineTo(5f)
            close()
            // Наконечник горизонтальной стрелки (вправо).
            moveTo(22f, 18f)
            lineTo(18f, 21f)
            lineTo(18f, 15f)
            close()
            // Вертикальная стрелка: из угла снизу вверх.
            moveTo(5f, 5f)
            horizontalLineTo(7f)
            verticalLineTo(19f)
            horizontalLineTo(5f)
            close()
            // Наконечник вертикальной стрелки (вверх).
            moveTo(6f, 3f)
            lineTo(9f, 7f)
            lineTo(3f, 7f)
            close()
          }
        }
        .build()
    return _orthoIcon!!
  }

private var _orthoIcon: ImageVector? = null
