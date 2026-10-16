package com.sit.litecad.ui.canvas

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sit.litecad.model.CadDocument
import com.sit.litecad.tools.ToolManager

@Composable
fun CadMagnifier(
    viewport: Viewport,
    document: CadDocument,
    toolManager: ToolManager,
    canvasSize: IntSize,
    cursorPosition: Offset,
    isGridVisible: Boolean,
    isDarkTheme: Boolean,
    visible: Boolean,
    textMeasurer: TextMeasurer
) {
    if (!visible || !cursorPosition.isSpecified || canvasSize.width == 0) return

    val density = LocalDensity.current
    val magSizeDp = 140.dp
    val magSizePx = with(density) { magSizeDp.toPx() }
    val gap = with(density) { 70.dp.toPx() }

    val targetX = cursorPosition.x - (magSizePx / 2)
    val targetY = cursorPosition.y - magSizePx - gap

    val clampedX = targetX.coerceIn(0f, (canvasSize.width - magSizePx).coerceAtLeast(0f))
    val clampedY = targetY.coerceIn(0f, (canvasSize.height - magSizePx).coerceAtLeast(0f))

    val animatedPos by animateOffsetAsState(
        targetValue = Offset(clampedX, clampedY),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "MagnifierMove"
    )

    Box(
        modifier = Modifier
            .offset(
                x = with(density) { animatedPos.x.toDp() },
                y = with(density) { animatedPos.y.toDp() }
            )
            .size(magSizeDp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, Color(0xFF2196F3), CircleShape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val magZoom = viewport.zoom * 2.0
            val worldTarget = viewport.screenToWorld(cursorPosition)

            val magPanX = (size.width / 2.0) - (worldTarget.X * magZoom)
            val magPanY = (size.height / 2.0) + (worldTarget.Y * magZoom)

            val magCamera = Camera(zoom = magZoom, panX = magPanX, panY = magPanY)

            drawCadScene(
                camera = magCamera,
                document = document,
                toolManager = toolManager,
                isGridVisible = isGridVisible,
                isDarkTheme = isDarkTheme,
                textMeasurer = textMeasurer
            )

            val cx = size.width / 2f
            val cy = size.height / 2f
            drawLine(Color.Red, Offset(cx - 15f, cy), Offset(cx + 15f, cy), 2f)
            drawLine(Color.Red, Offset(cx, cy - 15f), Offset(cx, cy + 15f), 2f)
            drawCircle(Color.Red.copy(alpha = 0.3f), 3f, Offset(cx, cy))
        }
    }
}