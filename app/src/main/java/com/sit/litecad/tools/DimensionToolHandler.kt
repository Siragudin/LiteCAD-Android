package com.sit.litecad.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sit.litecad.geometry.DimensionGeometry
import com.sit.litecad.geometry.DimensionService
import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.CadVertex
import com.sit.litecad.services.DocumentHistoryService
import com.sit.litecad.services.SnapKind
import com.sit.litecad.services.SnapService
import com.sit.litecad.settings.ToolSettings
import com.sit.litecad.ui.canvas.Camera
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Инструмент «Размер». UX как у дуги:
 * тап в A (снап) → протяжка к B (снап) → отпустил → манипулятор → протяжка offset → отпустил → коммит.
 *
 * Фазы: IDLE → FIRST_SET → DRAGGING_END → END_SET → BENDING → IDLE.
 * Размер хранит Id вершин (не координаты) — следует за геометрией. Вершина создаётся
 * напрямую через [CadDocument.addVertex] только в момент фиксации A или B — так не плодятся
 * висячие вершины и не трогается топология рёбер.
 */
class DimensionToolHandler(
    private val snapService: SnapService,
    private val historyService: DocumentHistoryService,
    private val toolManager: ToolManager
) : Tool {

    private enum class Phase { IDLE, FIRST_SET, DRAGGING_END, END_SET, BENDING }

    private var phase by mutableStateOf(Phase.IDLE)

    private var firstVertexId: UUID? = null
    private var firstAnchor by mutableStateOf(Point2.Zero)
    private var secondVertexId: UUID? = null
    private var secondAnchor by mutableStateOf(Point2.Zero)

    private var cursorPoint by mutableStateOf(Point2.Zero)   // для превью протяжки
    private var offset by mutableStateOf(0.0)
    private var orthogonalIsHorizontal = true

    private var hoverSnapKind by mutableStateOf<SnapKind?>(null)
    private var hoverSnapPoint by mutableStateOf(Point2.Zero)

    private var previewColor by mutableStateOf(Color.Gray)
    private var lastSettings: ToolSettings? = null
    private var draftSettings: ToolSettings? = null

    private var lastZoom = 1.0

    override var currentMeasurement by mutableStateOf("")
        private set

    override fun onStart(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastSettings = settings
        lastZoom = zoom
        when (phase) {
            Phase.IDLE -> {
                // Тап в A: снап обязателен.
                val snap = resolveMeasurementSnap(document, point, zoom) ?: return
                firstVertexId = materializeVertex(document, snap)
                firstAnchor = snap.position
                phase = Phase.FIRST_SET
                currentMeasurement = toolManager.settings.promptDragToDimensionEnd
            }
            Phase.FIRST_SET -> {
                // Начало протяжки к B — фаза перейдёт в DRAGGING_END на первом onMove.
                cursorPoint = point
            }
            Phase.END_SET -> {
                // Начало протяжки манипулятора — фаза перейдёт в BENDING на первом onMove.
                cursorPoint = point
            }
            Phase.DRAGGING_END, Phase.BENDING -> {
                // Не ожидается.
            }
        }
    }

    override fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastSettings = settings
        lastZoom = zoom
        when (phase) {
            Phase.FIRST_SET, Phase.DRAGGING_END -> {
                phase = Phase.DRAGGING_END
                cursorPoint = point
                val snap = resolveMeasurementSnap(document, point, zoom)
                hoverSnapKind = snap?.kind
                hoverSnapPoint = snap?.position ?: point
                previewColor = if (snap != null) Color.Black else Color.Gray
                currentMeasurement = "L: ${MathUtils.distance(firstAnchor, hoverSnapPoint).toInt()}"
            }
            Phase.END_SET, Phase.BENDING -> {
                phase = Phase.BENDING
                if (settings.dim.orthoEnabled) {
                    orthogonalIsHorizontal =
                        DimensionGeometry.resolveOrthogonalIsHorizontal(firstAnchor, secondAnchor, point)
                }
                offset = computeOffset(point, settings)
                currentMeasurement = toolManager.settings.measurementDimOffset.format(abs(offset).toInt())
            }
            Phase.IDLE -> {}
        }
    }

    override fun onEnd(document: CadDocument) {
        when (phase) {
            Phase.FIRST_SET -> {
                // Тап был, но протяжки не было — сброс.
                reset()
            }
            Phase.DRAGGING_END -> {
                // Отпустил с протяжки к B.
                val snap = resolveMeasurementSnap(document, cursorPoint, lastZoom) ?: run {
                    reset()
                    return
                }
                // B не должна совпадать с A.
                if (MathUtils.distance(firstAnchor, snap.position) <= TopologyTolerance.ForMutation) {
                    reset()
                    return
                }
                secondVertexId = materializeVertex(document, snap)
                secondAnchor = snap.position
                phase = Phase.END_SET
                offset = 0.0
                currentMeasurement = toolManager.settings.promptDragDimensionOffset
            }
            Phase.END_SET -> {
                // Отпустил без протяжки манипулятора — сброс.
                reset()
            }
            Phase.BENDING -> {
                // Отпустил после протяжки offset.
                if (abs(offset) <= TopologyTolerance.ForMutation) {
                    reset()
                    return
                }
                val s = lastSettings ?: run {
                    reset()
                    return
                }
                if (s.dim.exactInputEnabled) {
                    // Ждём точный ввод отступа.
                    draftSettings = s
                    toolManager.requestInput(PendingInput.Length(abs(offset)))
                } else {
                    commitDimension(document, s)
                    reset()
                }
            }
            Phase.IDLE -> {}
        }
    }

    override fun onInputSubmitted(value: Double, document: CadDocument) {
        val s = draftSettings ?: lastSettings
        draftSettings = null
        if (s == null) {
            reset()
            return
        }

        // Знак берём из протяжки, величину — из ввода.
        val sign = if (offset >= 0) 1.0 else -1.0
        offset = sign * value
        commitDimension(document, s)
        reset()
    }

    override fun onInputCancelled(document: CadDocument) {
        // Крестик точного ввода — сброс без коммита.
        draftSettings = null
        reset()
    }

    private fun computeOffset(point: Point2, settings: ToolSettings): Double {
        return if (settings.dim.orthoEnabled) {
            DimensionGeometry.computeOrthogonalSignedOffset(
                firstAnchor, secondAnchor, point, orthogonalIsHorizontal
            )
        } else {
            DimensionGeometry.computeSignedOffset(firstAnchor, secondAnchor, point)
        }
    }

    /**
     * Ищет точку снапа, пригодную для размера (Endpoint / Intersection / AxisIntersection /
     * Midpoint / OnEdge). Без побочных эффектов: вершины НЕ создаёт — только находит id
     * существующей (может быть null).
     */
    private fun resolveMeasurementSnap(
        document: CadDocument,
        cursor: Point2,
        zoom: Double
    ): MeasurementSnap? {
        val tolerance = 60.0 / zoom
        val query = snapService.query(document, cursor, tolerance, includeOnEdge = true)
        val best = query.Best.Snap ?: return null
        if (best.Kind !in MEASUREMENT_SNAP_KINDS) return null

        val existingVertexId = if (best.Kind == SnapKind.Endpoint) {
            best.VertexId ?: return null
        } else {
            TopologyService.findVertex(document, best.Position, TopologyTolerance.ForMutation)?.id
        }

        return MeasurementSnap(
            vertexId = existingVertexId,
            position = best.Position,
            kind = best.Kind
        )
    }

    /** Создаёт (или находит) вершину документа только в момент фиксации A или B. */
    private fun materializeVertex(document: CadDocument, snap: MeasurementSnap): UUID {
        snap.vertexId?.let { return it }
        val vertex = CadVertex(X = snap.position.X, Y = snap.position.Y)
        document.addVertex(vertex)
        return vertex.id
    }

    private fun commitDimension(document: CadDocument, settings: ToolSettings) {
        val firstId = firstVertexId ?: return
        val secondId = secondVertexId ?: return

        historyService.record(document)

        val dimension = DimensionService.create(
            document, firstId, secondId, offset, TopologyTolerance.ForMutation, settings.dim.textSize
        ) ?: return

        dimension.ExtensionStyle = settings.dim.extensionStyle
        dimension.IsOrthogonal = settings.dim.orthoEnabled
        dimension.OrthogonalIsHorizontal = orthogonalIsHorizontal
        dimension.colorArgb = settings.dim.color
        dimension.thickness = settings.dim.thickness

        document.bumpRevision()
    }

    private fun reset() {
        phase = Phase.IDLE
        firstVertexId = null
        secondVertexId = null
        firstAnchor = Point2.Zero
        secondAnchor = Point2.Zero
        cursorPoint = Point2.Zero
        offset = 0.0
        orthogonalIsHorizontal = true
        hoverSnapKind = null
        hoverSnapPoint = Point2.Zero
        previewColor = Color.Gray
        currentMeasurement = ""
        lastSettings = null
        draftSettings = null
    }

    override fun drawPreview(scope: DrawScope, camera: Camera) {
        when (phase) {
            Phase.IDLE -> {}
            Phase.FIRST_SET -> {
                // Маркер A.
                scope.drawCircle(toolManager.previewStrokeColor(previewColor), 6f, camera.worldToScreen(firstAnchor))
            }
            Phase.DRAGGING_END -> {
                // Линия A→палец + маркер A + маркер снапа.
                scope.drawLine(
                    color = toolManager.previewStrokeColor(previewColor),
                    start = camera.worldToScreen(firstAnchor),
                    end = camera.worldToScreen(cursorPoint),
                    strokeWidth = 4f
                )
                scope.drawCircle(toolManager.previewStrokeColor(previewColor), 6f, camera.worldToScreen(firstAnchor))
                if (hoverSnapKind != null) {
                    drawSnapMarker(scope, camera.worldToScreen(hoverSnapPoint), hoverSnapKind)
                }
            }
            Phase.END_SET, Phase.BENDING -> {
                // Превью размера без текста (выносные + размерная + засечки).
                val ortho = lastSettings?.dim?.orthoEnabled ?: false
                val layout = DimensionGeometry.tryCreateLayout(
                    firstAnchor, secondAnchor, offset,
                    TopologyTolerance.ForMutation,
                    ortho, orthogonalIsHorizontal
                )
                if (layout != null) {
                    val c = toolManager.previewStrokeColor(previewColor)
                    val t = 2f
                    scope.drawLine(c, camera.worldToScreen(layout.firstAnchor), camera.worldToScreen(layout.firstExtensionEnd), strokeWidth = t)
                    scope.drawLine(c, camera.worldToScreen(layout.secondAnchor), camera.worldToScreen(layout.secondExtensionEnd), strokeWidth = t)
                    scope.drawLine(c, camera.worldToScreen(layout.dimensionLineStart), camera.worldToScreen(layout.dimensionLineEnd), strokeWidth = t * 1.5f)

                    val tickAngle = layout.textAngleRadians + PI / 4.0
                    drawPreviewTick(scope, camera, layout.firstExtensionEnd, tickAngle, 10f, c, t)
                    drawPreviewTick(scope, camera, layout.secondExtensionEnd, tickAngle, 10f, c, t)
                }

                // Манипулятор в середине хорды — только до начала протяжки offset.
                if (phase == Phase.END_SET) {
                    val mid = Point2(
                        (firstAnchor.X + secondAnchor.X) / 2.0,
                        (firstAnchor.Y + secondAnchor.Y) / 2.0
                    )
                    val center = camera.worldToScreen(mid)
                    val radius = 16f
                    scope.drawCircle(Color.Gray, radius, center)
                    scope.drawCircle(Color.Black, radius, center, style = Stroke(width = 3f))
                }

                // Маркер hover-снапа.
                if (hoverSnapKind != null) {
                    drawSnapMarker(scope, camera.worldToScreen(hoverSnapPoint), hoverSnapKind)
                }
            }
        }
    }

    private fun drawPreviewTick(
        scope: DrawScope,
        camera: Camera,
        centerWorld: Point2,
        angleRad: Double,
        halfLengthPx: Float,
        color: Color,
        strokeWidth: Float
    ) {
        val center = camera.worldToScreen(centerWorld)
        val dx = cos(angleRad).toFloat() * halfLengthPx
        val dy = sin(angleRad).toFloat() * halfLengthPx
        scope.drawLine(
            color = color,
            start = Offset(center.x - dx, center.y - dy),
            end = Offset(center.x + dx, center.y + dy),
            strokeWidth = strokeWidth
        )
    }

    private fun drawSnapMarker(scope: DrawScope, center: Offset, kind: SnapKind?) {
        if (kind == null) return
        val color = when (kind) {
            SnapKind.Endpoint -> Color.Green
            SnapKind.Intersection, SnapKind.AxisIntersection -> Color(0xFFFF9800)
            SnapKind.Midpoint -> Color.Cyan
            SnapKind.OnEdge -> Color.Red
            SnapKind.Alignment -> Color.Yellow
        }
        val radius = 14f
        scope.drawCircle(color, radius, center)
        scope.drawCircle(Color.Black, radius + 2f, center, style = Stroke(width = 3f))
    }

    override fun cancel() {
        reset()
    }

    /** Разрешённая точка снапа для размера: id существующей вершины (может быть null), позиция и вид снапа. */
    private data class MeasurementSnap(
        val vertexId: UUID?,
        val position: Point2,
        val kind: SnapKind
    )

    private companion object {
        val MEASUREMENT_SNAP_KINDS = setOf(
            SnapKind.Endpoint,
            SnapKind.Intersection,
            SnapKind.AxisIntersection,
            SnapKind.Midpoint,
            SnapKind.OnEdge
        )
    }
}
