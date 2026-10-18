package com.sit.litecad.scripting

import com.sit.litecad.geometry.EdgeOperations
import com.sit.litecad.geometry.FaceIdentity
import com.sit.litecad.geometry.PolygonBuilder
import com.sit.litecad.geometry.PolygonGeometry
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.CadEdge
import com.sit.litecad.model.CadText
import com.sit.litecad.model.EdgeLineType
import com.sit.litecad.model.FaceFillPattern
import com.sit.litecad.model.FaceFillStyle
import com.sit.litecad.model.Polygon
import com.sit.litecad.model.PolygonType
import com.sit.litecad.model.TextRotation
import com.sit.litecad.model.TextSizeSp
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Значение времени выполнения LiteScript.
 *
 * Используется собственный `sealed`-тип (а не `Any`), чтобы вычисление выражений было
 * типобезопасным и явным: [Num] — число (все арифметические операции сводятся к `Double`),
 * [Str] — строка, [Bool] — логическое, [Null] — «пусто».
 */
sealed class Value {
    /** Числовое значение. */
    data class Num(val value: Double) : Value()

    /** Строковое значение. */
    data class Str(val value: String) : Value()

    /** Логическое значение. */
    data class Bool(val value: Boolean) : Value()

    /** «Пустое» значение (результат инструкций рисования, отсутствие значения). */
    object Null : Value()
}

/**
 * Исключение времени выполнения LiteScript.
 *
 * Бросается только на «жёсткие» ошибки: превышение лимита итераций, вызов неизвестной функции,
 * оператор `..` вне цикла `for`. Все «мягкие» ошибки (неизвестная переменная, деление на ноль)
 * не роняют скрипт — вместо этого возвращается значение по умолчанию.
 */
class ScriptRuntimeException(message: String) : Exception(message)

/**
 * Интерпретатор AST LiteScript: выполняет инструкции [Stmt] и наполняет [document] геометрией.
 *
 * Один экземпляр рассчитан на один прогон одного скрипта в один [CadDocument]. Повторный вызов
 * [execute] безопасен: состояние (окружение, стиль, счётчик итераций, отложенные заливки)
 * сбрасывается в начале прогона.
 *
 * Особенности семантики (упрощения для генерации кода ИИ):
 * - `val` и `var` не различаются — immutability не проверяется, чтобы не блокировать код от ИИ;
 * - присваивание неизвестному имени не падает, а просто создаёт переменную;
 * - неизвестная переменная/функция-член вычисляются в 0.0 (кроме неизвестной функции — она бросает).
 *
 * @param document целевой документ, в который добавляется геометрия.
 */
class Interpreter(private val document: CadDocument) {

    /** Текущий стиль, применяемый к добавляемой геометрии. */
    private var template: CadEdge = CadEdge.createStyleTemplate()

    /** Окружение: имя переменной → значение. */
    private val environment: MutableMap<String, Value> = mutableMapOf()

    /** Заливки, отложенные до финального `syncFaces` (грани ещё не существуют во время обхода). */
    private val pendingFills: MutableList<PendingFill> = mutableListOf()

    /** Счётчик выполненных тел циклов — защита от бесконечных циклов. */
    private var iterations = 0

    /** Текущее положение «карандаша» (moveTo/lineTo/closePath); null — карандаш не поднят. */
    private var lastPolylinePoint: Point2? = null

    /** Начало текущего контура «карандаша» (запоминается в moveTo, используется в closePath). */
    private var polylineStartPoint: Point2? = null

    /**
     * Выполняет список инструкций верхнего уровня.
     *
     * Порядок финализации: обход AST (рисует рёбра, копит [pendingFills]) →
     * один [PolygonBuilder.syncFaces] → применение заливок → [CadDocument.bumpRevision].
     *
     * @throws ScriptRuntimeException при превышении лимита итераций, вызове неизвестной функции
     *   или использовании `..` вне `for`.
     */
    fun execute(statements: List<Stmt>) {
        resetState()
        executeBlock(statements)
        PolygonBuilder.syncFaces(document)
        applyPendingFills()
        document.bumpRevision()
    }

    // --- Обход инструкций ---

    private fun resetState() {
        environment.clear()
        pendingFills.clear()
        iterations = 0
        lastPolylinePoint = null
        polylineStartPoint = null
        template = CadEdge.createStyleTemplate()
    }

    private fun executeBlock(statements: List<Stmt>) {
        for (statement in statements) {
            executeStatement(statement)
        }
    }

    private fun executeStatement(statement: Stmt) {
        when (statement) {
            is Stmt.VarDeclaration -> {
                val value = statement.initializer?.let { evaluate(it) } ?: Value.Num(0.0)
                environment[statement.name] = value
            }

            is Stmt.Assignment -> environment[statement.name] = evaluate(statement.value)

            is Stmt.ExpressionStmt -> evaluate(statement.expression)

            is Stmt.Block -> executeBlock(statement.statements)

            is Stmt.IfStmt -> {
                if (isTruthy(evaluate(statement.condition))) {
                    executeBlock(statement.thenBranch)
                } else {
                    executeBlock(statement.elseBranch)
                }
            }

            is Stmt.ForLoop -> executeForLoop(statement)
        }
    }

    private fun executeForLoop(loop: Stmt.ForLoop) {
        val start = toDouble(evaluate(loop.start)).roundToInt()
        val end = toDouble(evaluate(loop.end)).roundToInt()
        var i = start
        while (if (loop.isUntil) i < end else i <= end) {
            ensureIterationBudget()
            environment[loop.variableName] = Value.Num(i.toDouble())
            executeBlock(loop.body)
            i++
        }
    }

    private fun ensureIterationBudget() {
        iterations++
        if (iterations > MAX_ITERATIONS) {
            throw ScriptRuntimeException("Превышен лимит итераций: $MAX_ITERATIONS")
        }
    }

    // --- Вычисление выражений ---

    /** Вычисляет выражение в значение. Доступно из тестов (`internal`). */
    internal fun evaluate(expr: Expr): Value = when (expr) {
        is Expr.NumberLiteral -> Value.Num(expr.value)
        is Expr.StringLiteral -> Value.Str(expr.value)
        is Expr.BooleanLiteral -> Value.Bool(expr.value)
        is Expr.Variable -> lookupVariable(expr.name)
        is Expr.UnaryOp -> evaluateUnary(expr)
        is Expr.BinaryOp -> evaluateBinary(expr)
        is Expr.MemberAccess -> evaluateMemberAccess(expr)
        is Expr.FunctionCall -> evaluateFunctionCall(expr)
    }

    private fun lookupVariable(name: String): Value {
        environment[name]?.let { return it }
        return when (name) {
            "PI" -> Value.Num(PI)
            "E" -> Value.Num(E)
            // Неизвестная переменная — не падаем, отдаём 0.0.
            else -> Value.Num(0.0)
        }
    }

    private fun evaluateUnary(expr: Expr.UnaryOp): Value = when (expr.operator) {
        TokenType.MINUS -> Value.Num(-toDouble(evaluate(expr.operand)))
        TokenType.NOT -> Value.Bool(!isTruthy(evaluate(expr.operand)))
        else -> Value.Num(0.0)
    }

    private fun evaluateBinary(expr: Expr.BinaryOp): Value {
        // Логика — с коротким замыканием, до вычисления правого операнда.
        when (expr.operator) {
            TokenType.AND -> {
                if (!isTruthy(evaluate(expr.left))) return Value.Bool(false)
                return Value.Bool(isTruthy(evaluate(expr.right)))
            }
            TokenType.OR -> {
                if (isTruthy(evaluate(expr.left))) return Value.Bool(true)
                return Value.Bool(isTruthy(evaluate(expr.right)))
            }
            TokenType.RANGE -> throw ScriptRuntimeException("Оператор '..' допустим только в цикле for")
            else -> Unit
        }

        val left = evaluate(expr.left)
        val right = evaluate(expr.right)
        return when (expr.operator) {
            TokenType.PLUS ->
                if (left is Value.Str || right is Value.Str) Value.Str(toString(left) + toString(right))
                else Value.Num(toDouble(left) + toDouble(right))

            TokenType.MINUS -> Value.Num(toDouble(left) - toDouble(right))
            TokenType.STAR -> Value.Num(toDouble(left) * toDouble(right))
            TokenType.SLASH -> Value.Num(toDouble(left) / toDouble(right)) // x/0 → Infinity, не падаем
            TokenType.EQEQ -> Value.Bool(valuesEqual(left, right))
            TokenType.NEQ -> Value.Bool(!valuesEqual(left, right))
            TokenType.LT -> Value.Bool(toDouble(left) < toDouble(right))
            TokenType.LTE -> Value.Bool(toDouble(left) <= toDouble(right))
            TokenType.GT -> Value.Bool(toDouble(left) > toDouble(right))
            TokenType.GTE -> Value.Bool(toDouble(left) >= toDouble(right))
            else -> Value.Null
        }
    }

    private fun evaluateMemberAccess(expr: Expr.MemberAccess): Value {
        val targetName = (expr.target as? Expr.Variable)?.name
        if (targetName == "Math" && expr.member == "PI") return Value.Num(PI)
        // TODO: поддержать остальные члены Math и пользовательские объекты.
        return Value.Num(0.0)
    }

    private fun evaluateFunctionCall(expr: Expr.FunctionCall): Value {
        val args = expr.arguments.map { evaluate(it) }
        val name = expr.name
        // Parser склеивает "Math.toRadians" в одно имя — снимаем префикс для совместимости.
        return when (name.removePrefix("Math.")) {
            "line" -> {
                line(numArg(args, 0), numArg(args, 1), numArg(args, 2), numArg(args, 3))
                Value.Null
            }
            "moveTo" -> {
                moveTo(numArg(args, 0), numArg(args, 1))
                Value.Null
            }
            "lineTo" -> {
                lineTo(numArg(args, 0), numArg(args, 1))
                Value.Null
            }
            "closePath" -> {
                closePath()
                Value.Null
            }
            "rect" -> {
                rect(numArg(args, 0), numArg(args, 1), numArg(args, 2), numArg(args, 3))
                Value.Null
            }
            "circle" -> {
                circle(numArg(args, 0), numArg(args, 1), numArg(args, 2))
                Value.Null
            }
            "arc" -> {
                arc(numArg(args, 0), numArg(args, 1), numArg(args, 2), numArg(args, 3), numArg(args, 4))
                Value.Null
            }
            "gear" -> {
                gear(numArg(args, 0), numArg(args, 1), numArg(args, 2), numArg(args, 3), numArg(args, 4).roundToInt())
                Value.Null
            }
            "style" -> {
                style(strArg(args, 0), numArg(args, 1), strArg(args, 2))
                Value.Null
            }
            "text" -> {
                text(numArg(args, 0), numArg(args, 1), strArg(args, 2))
                Value.Null
            }
            "fill" -> {
                fill(numArg(args, 0), numArg(args, 1), strArg(args, 2))
                Value.Null
            }
            "sin" -> Value.Num(sin(numArg(args, 0)))
            "cos" -> Value.Num(cos(numArg(args, 0)))
            "tan" -> Value.Num(tan(numArg(args, 0)))
            "toRadians" -> Value.Num(numArg(args, 0) * PI / 180.0)
            "toDegrees" -> Value.Num(numArg(args, 0) * 180.0 / PI)
            "sqrt" -> Value.Num(sqrt(numArg(args, 0)))
            "abs" -> Value.Num(abs(numArg(args, 0)))
            "min" -> Value.Num(min(numArg(args, 0), numArg(args, 1)))
            "max" -> Value.Num(max(numArg(args, 0), numArg(args, 1)))
            "pow" -> Value.Num(numArg(args, 0).pow(numArg(args, 1)))
            else -> throw ScriptRuntimeException("Неизвестная функция: '$name'")
        }
    }

    // --- Приведение значений ---

    private fun isTruthy(value: Value): Boolean = when (value) {
        is Value.Bool -> value.value
        is Value.Num -> value.value != 0.0
        is Value.Str -> value.value.isNotEmpty()
        Value.Null -> false
    }

    private fun toDouble(value: Value): Double = when (value) {
        is Value.Num -> value.value
        is Value.Bool -> if (value.value) 1.0 else 0.0
        is Value.Str -> value.value.toDoubleOrNull() ?: 0.0
        Value.Null -> 0.0
    }

    private fun toString(value: Value): String = when (value) {
        is Value.Str -> value.value
        is Value.Num -> value.value.toString()
        is Value.Bool -> value.value.toString()
        Value.Null -> ""
    }

    private fun valuesEqual(a: Value, b: Value): Boolean {
        if (a is Value.Num && b is Value.Num) return abs(a.value - b.value) < EPSILON
        if (a is Value.Bool && b is Value.Bool) return a.value == b.value
        if (a is Value.Str && b is Value.Str) return a.value == b.value
        return a == b
    }

    private fun numArg(args: List<Value>, index: Int): Double = toDouble(args.getOrElse(index) { Value.Num(0.0) })

    private fun strArg(args: List<Value>, index: Int): String = toString(args.getOrElse(index) { Value.Str("") })

    /** Читает переменную из окружения — для диагностики и юнит-тестов (`internal`). */
    internal fun getVar(name: String): Value? = environment[name]

    // --- API рисования (вызывается из скрипта) ---

    /** Линия из точки (x1, y1) в точку (x2, y2). */
    fun line(x1: Double, y1: Double, x2: Double, y2: Double) {
        addSegment(Point2(x1, y1), Point2(x2, y2))
    }

    /** Скрытая функция для скриптов (в UI не выводится): ставит «карандаш» в точку (x, y) и запоминает старт контура; не рисует. */
    fun moveTo(x: Double, y: Double) {
        val point = Point2(x, y)
        lastPolylinePoint = point
        polylineStartPoint = point
    }

    /** Скрытая функция для скриптов (в UI не выводится): ведёт отрезок из текущего положения «карандаша» в (x, y) и сдвигает его; без moveTo бросает ScriptRuntimeException. */
    fun lineTo(x: Double, y: Double) {
        val current = lastPolylinePoint
            ?: throw ScriptRuntimeException("lineTo() без предварительного moveTo()")
        val next = Point2(x, y)
        addSegment(current, next)
        lastPolylinePoint = next
    }

    /** Скрытая функция для скриптов (в UI не выводится): замыкает текущий контур отрезком в стартовую точку и сбрасывает «карандаш»; без moveTo или при совпадении точек — no-op. */
    fun closePath() {
        val current = lastPolylinePoint
        val start = polylineStartPoint
        if (current != null && start != null && current != start) {
            addSegment(current, start)
        }
        lastPolylinePoint = null
        polylineStartPoint = null
    }

    /** Прямоугольник по двум противоположным углам (x1, y1) и (x2, y2). */
    fun rect(x1: Double, y1: Double, x2: Double, y2: Double) {
        val a = Point2(x1, y1)
        val b = Point2(x2, y1)
        val c = Point2(x2, y2)
        val d = Point2(x1, y2)
        addSegment(a, b)
        addSegment(b, c)
        addSegment(c, d)
        addSegment(d, a)
    }

    /** Окружность с центром (cx, cy) и радиусом r (36 сегментов по 10°). */
    fun circle(cx: Double, cy: Double, r: Double) {
        if (r <= TOLERANCE) return
        val step = (2.0 * PI) / CIRCLE_SEGMENTS
        var previous = Point2(cx + r, cy)
        for (i in 1..CIRCLE_SEGMENTS) {
            val angle = step * i
            val next = Point2(cx + r * cos(angle), cy + r * sin(angle))
            addSegment(previous, next)
            previous = next
        }
    }

    /**
     * Дуга с центром (cx, cy), радиусом r и углами [startDeg]..[endDeg] в градусах.
     * Строится цепочкой отрезков с шагом ~10° (по аналогии с [circle]). Если
     * `startDeg > endDeg`, границы меняются местами.
     */
    fun arc(cx: Double, cy: Double, r: Double, startDeg: Double, endDeg: Double) {
        if (r <= TOLERANCE) return
        val from = min(startDeg, endDeg)
        val to = max(startDeg, endDeg)
        val sweepDeg = to - from
        if (sweepDeg <= 0.0) return
        val steps = max(1, (sweepDeg / ARC_STEP_DEGREES).roundToInt())
        val startRad = Math.toRadians(from)
        val stepRad = Math.toRadians(sweepDeg / steps)
        var previous = Point2(cx + r * cos(startRad), cy + r * sin(startRad))
        for (i in 1..steps) {
            val angle = startRad + stepRad * i
            val next = Point2(cx + r * cos(angle), cy + r * sin(angle))
            addSegment(previous, next)
            previous = next
        }
    }

    /**
     * Строит зубчатый контур шестерёнки с трапециевидными зубами: [teeth] зубьев между
     * [innerRadius] (впадины) и [outerRadius] (вершины). Не эвольвента, но визуально узнаваемая
     * шестерёнка.
     *
     * `teeth < 3`, `outerRadius <= TOLERANCE` или `innerRadius >= outerRadius` → no-op: некорректные
     * параметры пропускаем, чтобы не рисовать вырождённый/самопересекающийся контур (fallback к
     * `outerRadius * 0.8` сознательно отвергнут ради предсказуемости результата).
     */
    fun gear(cx: Double, cy: Double, outerRadius: Double, innerRadius: Double, teeth: Int) {
        if (teeth < 3 || outerRadius <= TOLERANCE || innerRadius >= outerRadius) return

        val step = (2.0 * PI) / teeth
        val gapHalf = step * 0.10      // половина ширины впадины
        val baseHalf = step * 0.20     // половина основания зуба (после подъёма)
        val topHalf = step * 0.14      // половина вершины зуба
        val valleyHalf = step / 2.0 - gapHalf

        // Профиль одного зуба: смещение (доля step) от оси зуба и «это вершина?».
        // Впадина -> подъём -> дуга вершины -> спуск; впадины на innerRadius, остальное — outerRadius.
        val profile = listOf(
            -valleyHalf to false,
            -baseHalf to true,
            -topHalf to true,
            -topHalf / 2.0 to true,
            0.0 to true,
            topHalf / 2.0 to true,
            topHalf to true,
            baseHalf to true,
            valleyHalf to false
        )

        val points = ArrayList<Point2>(profile.size * teeth)
        for (tooth in 0 until teeth) {
            val axis = tooth * step
            for ((offset, onTop) in profile) {
                val angle = axis + offset
                val radius = if (onTop) outerRadius else innerRadius
                points.add(Point2(cx + radius * cos(angle), cy + radius * sin(angle)))
            }
        }

        // Соединяем точки по порядку и замыкаем контур (последняя -> первая).
        for (i in points.indices) {
            addSegment(points[i], points[(i + 1) % points.size])
        }
    }

    /**
     * Обновляет текущий стиль последующей геометрии.
     *
     * @param hexColor цвет `#RRGGBB` или `#AARRGGBB`; невалидный — игнорируется (текущий сохраняется).
     * @param thickness толщина линии (в экранных px при zoom=1).
     * @param lineType тип линии: `"solid"`, `"dashed"`, `"dotted"`; неизвестный — игнорируется.
     */
    fun style(hexColor: String, thickness: Double, lineType: String) {
        parseColor(hexColor)?.let { template.colorArgb = it }
        if (thickness > 0.0) template.thickness = thickness
        template.lineType = when (lineType.lowercase()) {
            "solid" -> EdgeLineType.SOLID
            "dashed" -> EdgeLineType.DASHED
            "dotted" -> EdgeLineType.DOTTED
            else -> template.lineType
        }
    }

    /** Добавляет надпись [value] в мировой точке (x, y) со стилем по умолчанию. */
    fun text(x: Double, y: Double, value: String) {
        document.addText(
            CadText(
                position = Point2(x, y),
                text = value,
                sizeSp = TextSizeSp.NORMAL,
                rotation = TextRotation.DEG_0,
                colorArgb = DEFAULT_TEXT_COLOR
            )
        )
    }

    /**
     * Откладывает заливку грани, содержащей точку (x, y), цветом [hexColor].
     * Применяется после [PolygonBuilder.syncFaces] в конце прогона (грани должны уже существовать).
     */
    fun fill(x: Double, y: Double, hexColor: String) {
        val argb = parseColor(hexColor) ?: return
        pendingFills.add(PendingFill(Point2(x, y), argb))
    }

    /** Применяет отложенные заливки к уже построенным граням FACE. */
    private fun applyPendingFills() {
        for (fill in pendingFills) {
            val polygon = findFaceContaining(fill.point) ?: continue
            document.faceFillStyles[FaceIdentity.create(document, polygon)] =
                FaceFillStyle(fillColor = fill.argb, fillPattern = FaceFillPattern.SOLID)
        }
    }

    /** Ищет наименьшую по площади грань FACE, содержащую [point]. */
    private fun findFaceContaining(point: Point2): Polygon? =
        document.polygons
            .filter { it.type == PolygonType.FACE && it.outerLoop.edges.size >= 3 }
            .filter { PolygonGeometry.containsPoint(document, it, point) }
            .minByOrNull { PolygonGeometry.getArea(document, it) }

    /** Разбирает `#RRGGBB`/`#AARRGGBB` в ARGB-`Int`; `null`, если формат не распознан. */
    private fun parseColor(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        return when (hex.length) {
            6 -> hex.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
            8 -> hex.toLongOrNull(16)?.toInt()
            else -> null
        }
    }

    /** Добавляет одно ребро без перестроения граней (syncFaces вызывается один раз в конце). */
    private fun addSegment(start: Point2, end: Point2) {
        EdgeOperations.addSegmentInternal(document, start, end, template, TOLERANCE)
    }

    /** Отложенная заливка: точка и цвет. */
    private data class PendingFill(val point: Point2, val argb: Int)

    private companion object {
        const val TOLERANCE = TopologyTolerance.ForMutation
        const val CIRCLE_SEGMENTS = 36
        const val ARC_STEP_DEGREES = 10.0
        const val MAX_ITERATIONS = 1_000_000
        const val EPSILON = 1e-9
        const val DEFAULT_TEXT_COLOR = 0xFF222222.toInt()
    }
}
