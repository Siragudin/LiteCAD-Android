package com.sit.litecad.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sit.litecad.geometry.EdgeOperations
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
 * Инструмент «Прямоугольник».
 * UX: протяжка от первого угла до противоположного, по отпусканию фиксируются 4 ребра.
 * Точный ввод: панель с двумя полями (Ширина × Высота).
 *
 * Снап применяется ко ВСЕМ 4 углам: два «ведущих» угла ([startCorner] и [oppositeCorner])
 * снапаются курсором как раньше, а два производных угла «подтягивают» размеры
 * прямоугольника к ближайшей геометрии (см. [snapRectangle]).
 */
class RectangleToolHandler(
    private val snapService: SnapService,
    private val historyService: DocumentHistoryService,
    private val toolManager: ToolManager
) : Tool {

    private var hasStart by mutableStateOf(false)
    private var startCorner by mutableStateOf(Point2.Zero)
    private var oppositeCorner by mutableStateOf(Point2.Zero)

    private var startSnapKind by mutableStateOf<SnapKind?>(null)
    private var endSnapKind by mutableStateOf<SnapKind?>(null)

    private var previewColor by mutableStateOf(Color.Gray)

    /**
     * Прямоугольник после снапа всех 4 углов. Используется и для превью,
     * и при коммите — чтобы то, что видит пользователь, точно совпадало с тем,
     * что будет зафиксировано.
     */
    private var previewRect by mutableStateOf<SnappedRectangle?>(null)

    /** Последний масштаб камеры: нужен для tolerance при снапе производных углов. */
    private var lastZoom = 0.0

    private var lastSettings: ToolSettings? = null

    // Черновик незакоммиченного прямоугольника (пока открыт ввод размеров)
    private var draftSettings: ToolSettings? = null

    override var currentMeasurement by mutableStateOf("")
        private set

    override fun onStart(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastSettings = settings
        lastZoom = zoom
        val resolved = resolveSnap(document, point, null, zoom)
        hasStart = true
        startCorner = resolved
        oppositeCorner = resolved
        startSnapKind = querySnapKind(document, point, zoom)
        endSnapKind = startSnapKind
        previewColor = Color.Gray
        // Пока второй угол не выбран — прямоугольник вырожден (нулевой), превью пустое.
        previewRect = snapRectangle(document, resolved, resolved, zoom, applyDerivedSnap = false)
        currentMeasurement = "Ведите до противоположного угла"
    }

    override fun onMove(point: Point2, document: CadDocument, zoom: Double, settings: ToolSettings) {
        lastSettings = settings
        lastZoom = zoom
        if (!hasStart) return

        val resolved = resolveSnap(document, point, startCorner, zoom)
        oppositeCorner = resolved
        endSnapKind = querySnapKind(document, point, zoom)

        // Считаем снап производных углов здесь, чтобы превью сразу показывало
        // примагниченные углы (те же координаты, что уйдут в коммит).
        val rect = snapRectangle(document, startCorner, resolved, zoom, applyDerivedSnap = true)
        previewRect = rect

        val magnetized = endSnapKind != null || rect.kindB != null || rect.kindD != null
        previewColor = if (magnetized) Color.Black else Color.Gray

        val w = abs(rect.c.X - rect.a.X)
        val h = abs(rect.d.Y - rect.a.Y)
        currentMeasurement = "W: ${w.toInt()}  H: ${h.toInt()}"
    }

    override fun onEnd(document: CadDocument) {
        if (!hasStart) return
        val s = lastSettings
        if (s == null) {
            reset()
            return
        }

        val dx = oppositeCorner.X - startCorner.X
        val dy = oppositeCorner.Y - startCorner.Y
        // Нулевая сторона (строго горизонтальная/вертикальная протяжка) — ничего не коммитим
        if (abs(dx) <= TopologyTolerance.ForMutation || abs(dy) <= TopologyTolerance.ForMutation) {
            reset()
            return
        }

        if (s.exactInputEnabled) {
            // ждём ввод размеров
            draftSettings = s
            toolManager.requestInput(PendingInput.RectangleSize(abs(dx), abs(dy)))
        } else {
            // коммитим сразу
            commitRectangle(document, oppositeCorner, s)
            reset()
        }
    }

    override fun onRectangleInputSubmitted(width: Double, height: Double, document: CadDocument) {
        val s = draftSettings ?: lastSettings
        draftSettings = null

        if (s == null || !hasStart) {
            reset()
            return
        }

        val sx = if (oppositeCorner.X - startCorner.X < 0.0) -1.0 else 1.0
        val sy = if (oppositeCorner.Y - startCorner.Y < 0.0) -1.0 else 1.0
        val newOpposite = Point2(startCorner.X + sx * width, startCorner.Y + sy * height)

        // Точный ввод задаёт размеры явно — производные углы не снапаем,
        // иначе введённые пользователем размеры «поехали» бы.
        commitRectangle(document, newOpposite, s, applyDerivedSnap = false)
        reset()
    }

    override fun onRectangleInputCancelled(document: CadDocument) {
        val s = draftSettings ?: lastSettings
        draftSettings = null

        if (s == null || !hasStart) {
            reset()
            return
        }

        // «принять как нарисовано» — коммит с исходными размерами
        commitRectangle(document, oppositeCorner, s)
        reset()
    }

    private fun commitRectangle(
        document: CadDocument,
        corner: Point2,
        settings: ToolSettings,
        applyDerivedSnap: Boolean = true
    ) {
        val mutTolerance = TopologyTolerance.ForMutation

        // Достраиваем прямоугольник по ведущим углам и снапаем производные.
        val rect = snapRectangle(document, startCorner, corner, lastZoom, applyDerivedSnap)

        val width = abs(rect.c.X - rect.a.X)
        val height = abs(rect.d.Y - rect.a.Y)
        if (width <= mutTolerance || height <= mutTolerance) return

        // один шаг истории на весь прямоугольник
        historyService.record(document)

        val template = CadEdge.createStyleTemplate()
        template.colorArgb = settings.color
        template.thickness = settings.thickness
        template.lineType = settings.lineType

        EdgeOperations.addSegment(document, rect.a, rect.b, template, mutTolerance)
        EdgeOperations.addSegment(document, rect.b, rect.c, template, mutTolerance)
        EdgeOperations.addSegment(document, rect.c, rect.d, template, mutTolerance)
        EdgeOperations.addSegment(document, rect.d, rect.a, template, mutTolerance)
    }

    /**
     * Достраивает прямоугольник по двум ведущим (уже снапнутым) углам [a] и [c]
     * и применяет снап к двум производным углам `b = (c.X, a.Y)` и `d = (a.X, c.Y)`.
     *
     * Прямоугольник полностью описывается четырьмя НЕЗАВИСИМЫМИ рёбрами:
     * `x1 = a.X`, `x2 = c.X`, `y1 = a.Y`, `y2 = c.Y`. Каждое ребро принадлежит ровно
     * одному производному углу:
     *  - угол `b = (x2, y1)` «владеет» рёбрами `x2` и `y1`;
     *  - угол `d = (x1, y2)` «владеет» рёбрами `x1` и `y2`.
     *
     * Поскольку наборы рёбер не пересекаются, производные углы НЕ конфликтуют между
     * собой по осям: каждый найденный снап просто подтягивает «свои» рёбра к геометрии,
     * а форма остаётся строго прямоугольной (меняются только размеры).
     *
     * Если снап найден, его точка ложится РОВНО на соответствующий угол (x/y берутся
     * из снапа целиком). Как следствие, ведущие углы могут слегка сдвинуться по одной
     * оси каждый — это неизбежно, т.к. ребро общее для ведущего и производного углов
     * (например, снап угла `b` по Y сдвигает ребро `y1`, которое принадлежит и `a`).
     *
     * Для OnEdge-снапа смещение по «параллельной» оси близко к нулю (проекция сохраняет
     * координату вдоль линии), поэтому прямоугольник меняется минимально.
     */
    private fun snapRectangle(
        document: CadDocument,
        a: Point2,
        c: Point2,
        zoom: Double,
        applyDerivedSnap: Boolean
    ): SnappedRectangle {
        var x1 = a.X
        var y1 = a.Y
        var x2 = c.X
        var y2 = c.Y

        var kindB: SnapKind? = null
        var kindD: SnapKind? = null

        if (applyDerivedSnap && zoom > 0.0) {
            // «Сырые» производные углы считаем до применения снапов — они опираются
            // на непересекающиеся рёбра, поэтому порядок применения не важен.
            val rawB = Point2(x2, y1)
            val rawD = Point2(x1, y2)

            val snapB = resolveNearbySnap(document, rawB, zoom)
            val snapD = resolveNearbySnap(document, rawD, zoom)

            if (snapB != null) {
                x2 = snapB.first.X
                y1 = snapB.first.Y
                kindB = snapB.second
            }
            if (snapD != null) {
                x1 = snapD.first.X
                y2 = snapD.first.Y
                kindD = snapD.second
            }
        }

        return SnappedRectangle(
            a = Point2(x1, y1),
            b = Point2(x2, y1),
            c = Point2(x2, y2),
            d = Point2(x1, y2),
            kindB = kindB,
            kindD = kindD
        )
    }

    /**
     * Снап для производного угла: возвращает точку привязки и её вид, либо null,
     * если рядом с курсором ничего нет. Направляющие оси намеренно не учитываются
     * (drawingStart = null), чтобы угол не «тянулся» к осям построения.
     */
    private fun resolveNearbySnap(document: CadDocument, cursor: Point2, zoom: Double): Pair<Point2, SnapKind>? {
        val tolerance = 60.0 / zoom
        val query = snapService.query(
            document = document,
            cursor = cursor,
            tolerance = tolerance,
            includeOnEdge = true
        )
        val snap = query.Best.Snap ?: return null
        return Pair(snap.Position, snap.Kind)
    }

    private fun resolveSnap(
        document: CadDocument,
        cursor: Point2,
        drawingStart: Point2?,
        zoom: Double
    ): Point2 {
        val tolerance = 60.0 / zoom
        val query = snapService.query(
            document = document,
            cursor = cursor,
            tolerance = tolerance,
            includeOnEdge = true
        )
        return snapService.resolveDrawingSnap(
            document = document,
            query = query,
            cursor = cursor,
            drawingStart = drawingStart,
            tolerance = tolerance,
            orthoEnabled = false
        )
    }

    private fun querySnapKind(document: CadDocument, cursor: Point2, zoom: Double): SnapKind? {
        val tolerance = 60.0 / zoom
        val query = snapService.query(
            document = document,
            cursor = cursor,
            tolerance = tolerance,
            includeOnEdge = true
        )
        return query.Best.Snap?.Kind
    }

    private fun reset() {
        hasStart = false
        startCorner = Point2.Zero
        oppositeCorner = Point2.Zero
        startSnapKind = null
        endSnapKind = null
        previewColor = Color.Gray
        previewRect = null
        currentMeasurement = ""
        lastSettings = null
        draftSettings = null
    }

    override fun drawPreview(scope: DrawScope, camera: Camera) {
        val rect = previewRect ?: return
        if (!hasStart) return

        val p1 = camera.worldToScreen(rect.a)
        val p2 = camera.worldToScreen(rect.b)
        val p3 = camera.worldToScreen(rect.c)
        val p4 = camera.worldToScreen(rect.d)

        val isSolid = previewColor != Color.Gray
        val stroke = if (isSolid) 6f else 4f
        val effect = if (isSolid) null else PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)

        scope.drawLine(color = previewColor, start = p1, end = p2, strokeWidth = stroke, pathEffect = effect)
        scope.drawLine(color = previewColor, start = p2, end = p3, strokeWidth = stroke, pathEffect = effect)
        scope.drawLine(color = previewColor, start = p3, end = p4, strokeWidth = stroke, pathEffect = effect)
        scope.drawLine(color = previewColor, start = p4, end = p1, strokeWidth = stroke, pathEffect = effect)

        // Маркеры для всех 4 углов (ведущие + производные).
        drawSnapMarker(scope, p1, startSnapKind)
        drawSnapMarker(scope, p2, rect.kindB)
        drawSnapMarker(scope, p3, endSnapKind)
        drawSnapMarker(scope, p4, rect.kindD)
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

    /**
     * Готовый прямоугольник после снапа всех 4 углов.
     * [a] — ведущий стартовый угол, [c] — ведущий противоположный, [b] и [d] — производные.
     */
    private data class SnappedRectangle(
        val a: Point2,
        val b: Point2,
        val c: Point2,
        val d: Point2,
        val kindB: SnapKind? = null,
        val kindD: SnapKind? = null
    )
}
