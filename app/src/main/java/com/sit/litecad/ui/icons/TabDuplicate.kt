package com.sit.litecad.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val TabDuplicate: ImageVector
  get() {
    if (_tabDuplicate != null) {
      return _tabDuplicate!!
    }
    _tabDuplicate =
      ImageVector.Builder(
          name = "tab_duplicate",
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
            moveTo(8f, 18f)
            quadTo(7.18f, 18f, 6.59f, 17.41f)
            reflectiveQuadTo(6f, 16f)
            verticalLineTo(4f)
            quadTo(6f, 3.17f, 6.59f, 2.59f)
            reflectiveQuadTo(8f, 2f)
            horizontalLineTo(20f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(22f, 4f)
            verticalLineTo(16f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(20f, 18f)
            horizontalLineTo(8f)
            close()
            moveTo(8f, 16f)
            horizontalLineTo(20f)
            verticalLineTo(8f)
            horizontalLineTo(13f)
            verticalLineTo(4f)
            horizontalLineTo(8f)
            verticalLineTo(16f)
            close()
            moveTo(8f, 4f)
            verticalLineTo(16f)
            verticalLineTo(4f)
            close()
            moveTo(6f, 22f)
            verticalLineTo(20f)
            horizontalLineTo(8f)
            verticalLineToRelative(2f)
            horizontalLineTo(6f)
            close()
            moveTo(4f, 6f)
            horizontalLineTo(2f)
            quadTo(2f, 5.18f, 2.59f, 4.59f)
            reflectiveQuadTo(4f, 4f)
            verticalLineTo(6f)
            close()
            moveToRelative(6f, 16f)
            verticalLineTo(20f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            horizontalLineTo(10f)
            close()
            moveToRelative(4f, 0f)
            verticalLineTo(20f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            horizontalLineTo(14f)
            close()
            moveTo(4f, 20f)
            verticalLineToRelative(2f)
            quadTo(3.18f, 22f, 2.59f, 21.41f)
            reflectiveQuadTo(2f, 20f)
            horizontalLineTo(4f)
            close()
            moveTo(2f, 18f)
            verticalLineTo(16f)
            horizontalLineTo(4f)
            verticalLineToRelative(2f)
            horizontalLineTo(2f)
            close()
            moveTo(2f, 14f)
            verticalLineTo(12f)
            horizontalLineTo(4f)
            verticalLineToRelative(2f)
            horizontalLineTo(2f)
            close()
            moveTo(2f, 10f)
            verticalLineTo(8f)
            horizontalLineTo(4f)
            verticalLineToRelative(2f)
            horizontalLineTo(2f)
            close()
            moveTo(18f, 20f)
            horizontalLineToRelative(2f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(18f, 22f)
            verticalLineTo(20f)
            close()
          }
        }
        .build()
    return _tabDuplicate!!
  }

private var _tabDuplicate: ImageVector? = null
