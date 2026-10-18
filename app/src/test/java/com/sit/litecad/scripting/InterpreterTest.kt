package com.sit.litecad.scripting

import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.EdgeLineType
import com.sit.litecad.model.PolygonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Юнит-тесты интерпретатора LiteScript ([Interpreter]).
 *
 * Скрипт прогоняется через [Lexer] → [Parser] → [Interpreter]; проверяется получившаяся
 * геометрия в [CadDocument] (рёбра, грани, надписи, заливки), окружение переменных и ошибки.
 */
class InterpreterTest {

    private fun run(source: String, document: CadDocument = CadDocument()): CadDocument {
        val ast = Parser(Lexer(source).tokenize()).parse()
        Interpreter(document).execute(ast)
        return document
    }

    private fun runWithInterpreter(source: String): Pair<CadDocument, Interpreter> {
        val document = CadDocument()
        val interpreter = Interpreter(document)
        interpreter.execute(Parser(Lexer(source).tokenize()).parse())
        return document to interpreter
    }

    // --- Встроенные команды рисования ---

    @Test
    fun line_addsSingleEdge() {
        assertEquals(1, run("line(0, 0, 10, 10)").edges.size)
    }

    @Test
    fun rect_addsFourEdges() {
        assertEquals(4, run("rect(0, 0, 10, 10)").edges.size)
    }

    @Test
    fun circle_addsThirtySixEdges() {
        assertEquals(36, run("circle(0, 0, 10)").edges.size)
    }

    @Test
    fun arc_addsAboutTenEdges() {
        val document = run("arc(0, 0, 10, 0, 90)")
        assertTrue(
            "Ожидалось ~10 рёбер, а получилось ${document.edges.size}",
            document.edges.size in 9..11
        )
    }

    // --- Переменные ---

    @Test
    fun variable_isUsedInCommand() {
        assertEquals(1, run("val x = 5\nline(0, 0, x, 0)").edges.size)
    }

    @Test
    fun environment_isPopulatedWithComputedValues() {
        val (document, interpreter) = runWithInterpreter("val x = 10\nval y = x * 2\nline(0, 0, x, y)")
        assertEquals(Value.Num(10.0), interpreter.getVar("x"))
        assertEquals(Value.Num(20.0), interpreter.getVar("y"))
        assertEquals(1, document.edges.size)
    }

    // --- Циклы ---

    @Test
    fun forLoop_range_isInclusive() {
        assertEquals(5, run("for (i in 0..4) { line(i, 0, i, 10) }").edges.size)
    }

    @Test
    fun forLoop_until_isExclusive() {
        assertEquals(4, run("for (i in 0 until 4) { line(i, 0, i, 10) }").edges.size)
    }

    // --- Условия ---

    @Test
    fun ifTrue_executesThenBranch() {
        assertEquals(1, run("if (true) { line(0, 0, 1, 1) }").edges.size)
    }

    @Test
    fun ifFalse_executesElseBranch() {
        val document = run("if (false) { line(0, 0, 1, 1) } else { line(0, 0, 2, 2) }")
        assertEquals(1, document.edges.size)
    }

    // --- Стиль, текст, заливка ---

    @Test
    fun style_appliesColorThicknessAndLineType() {
        val document = run("style(\"#FF0000\", 2.0, \"dashed\")\nline(0, 0, 1, 1)")
        assertEquals(1, document.edges.size)
        val edge = document.edges[0]
        assertEquals(0xFFFF0000.toInt(), edge.colorArgb)
        assertEquals(2.0, edge.thickness, 1e-9)
        assertEquals(EdgeLineType.DASHED, edge.lineType)
    }

    @Test
    fun text_addsCadText() {
        val document = run("text(10, 20, \"hi\")")
        assertEquals(1, document.texts.size)
        assertEquals("hi", document.texts[0].text)
    }

    @Test
    fun circleAndFill_createFacePolygonAndFillStyle() {
        val document = run("circle(0, 0, 5)\nfill(0, 0, \"#00FF00\")")
        assertEquals(1, document.polygons.count { it.type == PolygonType.FACE })
        assertEquals(1, document.faceFillStyles.size)
        assertEquals(0xFF00FF00.toInt(), document.faceFillStyles.values.first().fillColor)
    }

    // --- Встроенная математика ---

    @Test
    fun sinOfHalfPi_isOne() {
        val (_, interpreter) = runWithInterpreter("val r = sin(Math.PI / 2)")
        val r = interpreter.getVar("r")
        assertTrue(r is Value.Num)
        assertEquals(1.0, (r as Value.Num).value, 1e-9)
    }

    @Test
    fun evaluate_computesArithmeticPrecedence() {
        val interpreter = Interpreter(CadDocument())
        val expr = (Parser(Lexer("1 + 2 * 3").tokenize()).parse().single() as Stmt.ExpressionStmt).expression
        assertEquals(Value.Num(7.0), interpreter.evaluate(expr))
    }

    // --- Ошибки времени выполнения ---

    @Test
    fun exceedingIterationLimit_throws() {
        assertThrows(ScriptRuntimeException::class.java) {
            run("for (i in 0 until 1000001) { line(0, 0, 1, 1) }")
        }
    }

    @Test
    fun unknownFunction_throws() {
        assertThrows(ScriptRuntimeException::class.java) {
            run("foo(1, 2, 3)")
        }
    }

    // --- Повторный прогон ---

    @Test
    fun execute_canBeCalledTwice_withoutBreaking() {
        val document = CadDocument()
        val interpreter = Interpreter(document)
        val ast = Parser(Lexer("val x = 5\nline(0, 0, x, x)").tokenize()).parse()

        interpreter.execute(ast)
        interpreter.execute(ast)

        assertEquals(Value.Num(5.0), interpreter.getVar("x"))
        // Совпадающая линия не дублируется, окружение пересобрано.
        assertEquals(1, document.edges.size)
    }

    // --- «Карандаш»: moveTo / lineTo / closePath ---

    @Test
    fun moveToAndLineTo_addSegments() {
        assertEquals(2, run("moveTo(0, 0)\nlineTo(10, 0)\nlineTo(10, 10)").edges.size)
    }

    @Test
    fun lineToWithoutMoveTo_throws() {
        assertThrows(ScriptRuntimeException::class.java) {
            run("lineTo(1, 1)")
        }
    }

    @Test
    fun closePath_addsClosingSegment() {
        // Треугольник: два lineTo + замыкающий сегмент = 3 различных ребра.
        // (На одном lineTo замыкающий сегмент совпал бы с уже нарисованным — движок дедуплицирует рёбра.)
        assertEquals(3, run("moveTo(0, 0)\nlineTo(10, 0)\nlineTo(10, 10)\nclosePath()").edges.size)
    }

    @Test
    fun closePath_isNoOp_afterCloseOrWithoutMoveTo() {
        // Повторный closePath после замыкания — no-op, не падает.
        assertEquals(3, run("moveTo(0, 0)\nlineTo(10, 0)\nlineTo(10, 10)\nclosePath()\nclosePath()").edges.size)
        // closePath без moveTo — тоже no-op.
        assertEquals(0, run("closePath()").edges.size)
    }

    @Test
    fun polylineLoop_buildsClosedContour_andFillFindsFace() {
        val source = """
            val n = 6
            val r = 50.0
            val step = 360.0 / n
            moveTo(r, 0)
            for (i in 1 until n) {
                val a = toRadians(i * step)
                lineTo(r * cos(a), r * sin(a))
            }
            closePath()
            fill(0, 0, "#FF0000")
        """.trimIndent()
        val document = run(source)
        assertEquals(6, document.edges.size)
        assertEquals(1, document.polygons.count { it.type == PolygonType.FACE })
        assertEquals(1, document.faceFillStyles.size)
    }

    @Test
    fun execute_resetsPolylineState() {
        val document = CadDocument()
        val interpreter = Interpreter(document)
        interpreter.execute(Parser(Lexer("moveTo(0, 0)\nlineTo(10, 10)").tokenize()).parse())

        // Второй прогон без moveTo: состояние «карандаша» сброшено → lineTo бросает.
        assertThrows(ScriptRuntimeException::class.java) {
            interpreter.execute(Parser(Lexer("lineTo(1, 1)").tokenize()).parse())
        }
    }

    // --- Шестерёнка (gear) ---

    @Test
    fun gear_buildsToothedContour() {
        val edges = run("gear(0, 0, 100, 80, 12)").edges.size
        assertTrue("gear должен давать больше рёбер, чем circle (> 36), а получилось $edges", edges > 36)
        assertTrue("Ожидалось > 100 рёбер зубчатого профиля, а получилось $edges", edges > 100)
        assertEquals("Число рёбер должно быть кратно числу зубьев", 0, edges % 12)
    }

    @Test
    fun gear_withTooFewTeeth_addsNothing() {
        assertEquals(0, run("gear(0, 0, 100, 80, 2)").edges.size)
    }

    @Test
    fun gear_withInnerRadiusNotSmaller_addsNothing() {
        assertEquals(0, run("gear(0, 0, 100, 120, 12)").edges.size)
    }
}
