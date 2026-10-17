package com.sit.litecad.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sit.litecad.geometry.ArcGeometry
import com.sit.litecad.geometry.EdgeOperations
import com.sit.litecad.geometry.PolygonBuilder
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.CadEdge
import com.sit.litecad.services.DocumentHistoryService
import com.sit.litecad.services.SnapKind
import com.sit.litecad.services.SnapService
import com.sit.litecad.settings.ToolSettings
import com.sit.litecad.ui.canvas.Camera
import kotlin.math.abs

/**
 * Инструмент «Дуга». Три опорных значения: start → end → sagitta (высота).
 * Первые две точки — тапом/протяжкой; высота — протяжкой манипулятора в середине хорды.
 *
 * Снап включён (includeOnEdge = true при выборе точек). Ortho нет (как в ПК).
 * Дуга строится цепочкой рёбер через [EdgeOperations.addSegmentInternal]
 * (шаг 10° + остаток), грани перестраиваются один раз в конце.
 */
class ArcToolHandler(
    private val snapService: SnapService,
    private val historyService: DocumentHistoryService,
    private val toolManager: ToolManager
) : Tool {

    private enum class Phase { IDLE, START_SET, END_SET, BENDING }

    private var phase by mutableStateOf(Phase.IDLE)

    private var startPoint by mutableStateOf(Point2.Zero)
    private var endPoint by mutableStateOf(Point2.Zero)
    private var bendPoint by mutableStateOf(Point2.Zero)     // текущая вершина (тянется пальцем)
    private var cursorPoint by mutableStateOf(Point2.Zero)   // курсор на фазе выбора конца

    private var startSnapKind by mutableStateOf<SnapKind?>(null)
    private var endSnapKind by mutableStateOf<SnapKind?>(null)
    private var previewColor by mutableStateOf(Color.Gray)

    private var lastSettings: ToolSettings? = null
    private var draftSettings: ToolSettings? = null
    private var lastSagitta by mutableStateOf(0.0)           // для точного ввода (знак)

    override var currentMeasurement by mutableStateOf("")
        private set

    override val previewMeasurements: List<PreviewMeasurement>
        get() = if (phase == Phase.BENDING) {
            listOf(PreviewMeasurement("H", "${abs(lastSagitta).toInt()} мм"))
        } else {
            emptyList()
        }

    override fun onStart(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastSettings = settings

        when (phase) {
            Phase.IDLE -> {
                val (snapped, kind) = resolveSnap(document, point, zoom)
                startPoint = snapped
                cursorPoint = snapped
                startSnapKind = kind
                endSnapKind = null
                previewColor = Color.Gray
                phase = Phase.START_SET
                currentMeasurement = toolManager.settings.promptPickArcEnd
            }

            Phase.START_SET -> {
                val (snapped, kind) = resolveSnap(document, point, zoom)
                if (MathUtils.distance(startPoint, snapped) <= TopologyTolerance.ForMutation) {
                    reset()
                    return
                }
                endPoint = snapped
                endSnapKind = kind
                phase = Phase.END_SET
                currentMeasurement = toolManager.settings.promptDragHeight
            }

            Phase.END_SET, Phase.BENDING -> {
                // Ждём onMove/onEnd (протяжка манипулятора высоты).
            }
        }
    }

    override fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastSettings = settings

        when (phase) {
            Phase.START_SET -> {
                val (snapped, kind) = resolveSnap(document, point, zoom)
                cursorPoint = snapped
                endSnapKind = kind
                currentMeasurement = "L: ${MathUtils.distance(startPoint, snapped).toInt()}"
            }

            Phase.END_SET -> {
                // Начали тянуть после второй точки — задаём высоту.
                phase = Phase.BENDING
                updateBend(document, point, zoom)
            }

            Phase.BENDING -> updateBend(document, point, zoom)

            Phase.IDLE -> {}
        }
    }

    /** Пересчёт вершины (bendPoint) и подписанной высоты по текущему положению пальца. */
    private fun updateBend(document: CadDocument, point: Point2, zoom: Double) {
        // Снап без тел рёбер (includeOnEdge = false) — не тянем вершину к телу ребра.
        val tolerance = 60.0 / zoom
        val query = snapService.query(document, point, tolerance, includeOnEdge = false)
        val snapped = query.Best.Snap?.Position ?: point
        bendPoint = snapped
        lastSagitta = ArcGeometry.tryGetSignedSagitta(startPoint, endPoint, bendPoint) ?: 0.0
        previewColor = if (query.Best.Snap != null) Color.Black else Color.Gray
        currentMeasurement = "H: ${abs(lastSagitta).toInt()}"
    }

    override fun onEnd(document: CadDocument) {
        when (phase) {
            Phase.START_SET -> {
                // Тап (уже обработан в onStart) или завершение протяжки от первой точки.
                // Если палец реально ушёл от startPoint — фиксируем конец.
                if (MathUtils.distance(startPoint, cursorPoint) > TopologyTolerance.ForMutation) {
                    endPoint = cursorPoint
                    // endSnapKind уже установлен в onMove (снап вокруг cursorPoint).
                    phase = Phase.END_SET
                    currentMeasurement = toolManager.settings.promptDragHeight
                }
            }

            Phase.END_SET -> {
                // Тап второй точки без протяжки — ждём протяжку высоты или точный ввод.
            }

            Phase.BENDING -> {
                val s = lastSettings
                if (s == null) {
                    reset()
                    return
                }

                if (abs(lastSagitta) <= TopologyTolerance.ForMutation) {
                    // Высота нулевая — дуга вырождена.
                    reset()
                    return
                }

                if (s.arc.exactInputEnabled) {
                    draftSettings = s
                    toolManager.requestInput(PendingInput.Length(abs(lastSagitta)))
                } else {
                    commitArc(document)
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
        lastSettings = s

        // Знак берём из протяжки, величину — из введённого значения.
        val sign = if (lastSagitta >= 0.0) 1 else -1
        val signedSagitta = sign * value

        val bp = ArcGeometry.tryCreateBendPoint(startPoint, endPoint, signedSagitta)
        if (bp == null) {
            reset()
            return
        }
        bendPoint = bp
        lastSagitta = signedSagitta

        commitArc(document)
        reset()
    }

    override fun onInputCancelled(document: CadDocument) {
        // Полная отмена — ничего не коммитим.
        draftSettings = null
        reset()
    }

    private fun commitArc(document: CadDocument) {
        val s = lastSettings ?: return

        val chord = MathUtils.distance(startPoint, endPoint)
        if (chord <= TopologyTolerance.ForMutation) return

        val build = ArcGeometry.tryBuildArc(startPoint, endPoint, bendPoint) ?: return
        if (build.radius < chord / 2.0) return

        val points = ArcGeometry.computeOpenArcPoints(build.center, build.radius, build.startAngleRad, build.sweepRad)
        if (points.size < 2) return

        historyService.record(document)

        val template = CadEdge.createStyleTemplate()
        template.colorArgb = s.arc.color
        template.thickness = s.arc.thickness
        template.lineType = s.arc.lineType

        val mutTolerance = TopologyTolerance.ForMutation
        for (i in 0 until points.size - 1) {
            EdgeOperations.addSegmentInternal(document, points[i], points[i + 1], template, mutTolerance)
        }

        PolygonBuilder.syncFaces(document)
        document.bumpRevision()
    }

    private fun resolveSnap(document: CadDocument, cursor: Point2, zoom: Double): Pair<Point2, SnapKind?> {
        val tolerance = 60.0 / zoom
        val query = snapService.query(document, cursor, tolerance, includeOnEdge = true)
        val best = query.Best.Snap
        return (best?.Position ?: cursor) to best?.Kind
    }

    private fun reset() {
        phase = Phase.IDLE
        startPoint = Point2.Zero
        endPoint = Point2.Zero
        bendPoint = Point2.Zero
        cursorPoint = Point2.Zero
        startSnapKind = null
        endSnapKind = null
        previewColor = Color.Gray
        currentMeasurement = ""
        lastSettings = null
        draftSettings = null
        lastSagitta = 0.0
    }

    override fun drawPreview(scope: DrawScope, camera: Camera) {
        when (phase) {
            Phase.IDLE -> {}

            Phase.START_SET -> {
                val p1 = camera.worldToScreen(startPoint)
                val p2 = camera.worldToScreen(cursorPoint)
                scope.drawLine(
                    color = toolManager.previewStrokeColor(if (endSnapKind != null) Color.Black else Color.Gray),
                    start = p1,
                    end = p2,
                    strokeWidth = 4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                )
                drawSnapMarker(scope, p2, endSnapKind)
            }

            Phase.END_SET -> {
                drawChord(scope, camera)
                drawHandle(scope, camera)
            }

            Phase.BENDING -> {
                drawChord(scope, camera)

                val build = ArcGeometry.tryBuildArc(startPoint, endPoint, bendPoint)
                if (build != null) {
                    val points = ArcGeometry.computeOpenArcPoints(
                        build.center, build.radius, build.startAngleRad, build.sweepRad
                    )
                    for (i in 0 until points.size - 1) {
                        scope.drawLine(
                            color = toolManager.previewStrokeColor(previewColor),
                            start = camera.worldToScreen(points[i]),
                            end = camera.worldToScreen(points[i + 1]),
                            strokeWidth = 6f
                        )
                    }
                }
                drawHandle(scope, camera)
            }
        }

        drawSnapMarker(scope, camera.worldToScreen(startPoint), startSnapKind)
        drawSnapMarker(scope, camera.worldToScreen(endPoint), endSnapKind)
    }

    /** Хорда (пунктир) между start и end. */
    private fun drawChord(scope: DrawScope, camera: Camera) {
        val p1 = camera.worldToScreen(startPoint)
        val p2 = camera.worldToScreen(endPoint)
        scope.drawLine(
            color = toolManager.previewStrokeColor(Color.Gray),
            start = p1,
            end = p2,
            strokeWidth = 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
        )
    }

    /** Манипулятор-кружок в середине хорды. */
    private fun drawHandle(scope: DrawScope, camera: Camera) {
        val mid = Point2((startPoint.X + endPoint.X) / 2.0, (startPoint.Y + endPoint.Y) / 2.0)
        val c = camera.worldToScreen(mid)
        val radius = 16f
        scope.drawCircle(Color.Gray, radius, c)
        scope.drawCircle(Color.Black, radius, c, style = Stroke(width = 3f))
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
}
