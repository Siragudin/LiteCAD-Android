package com.sit.litecad.scripting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Юнит-тесты парсера LiteScript ([Parser]).
 *
 * Скрипт прогоняется через реальный [Lexer], затем через [Parser]; проверяется форма AST,
 * приоритеты операторов и корректные ошибки [ParseException].
 */
class ParserTest {

    private fun parse(source: String): List<Stmt> = Parser(Lexer(source).tokenize()).parse()

    private fun parseSingle(source: String): Stmt = parse(source).single()

    private fun expr(source: String): Expr = (parseSingle(source) as Stmt.ExpressionStmt).expression

    private fun assertParseError(source: String, line: Int) {
        val ex = assertThrows(ParseException::class.java) { parse(source) }
        assertEquals("Неверная строка ошибки для: $source", line, ex.line)
    }

    // --- Объявления ---

    @Test
    fun valDeclaration_producesImmutableVarDeclaration() {
        val stmt = parseSingle("val x = 42") as Stmt.VarDeclaration
        assertEquals("x", stmt.name)
        assertEquals(false, stmt.isMutable)
        assertEquals(Expr.NumberLiteral(42.0), stmt.initializer)
    }

    @Test
    fun varDeclaration_isMutable() {
        val stmt = parseSingle("var i = 0") as Stmt.VarDeclaration
        assertEquals("i", stmt.name)
        assertTrue(stmt.isMutable)
        assertEquals(Expr.NumberLiteral(0.0), stmt.initializer)
    }

    @Test
    fun stringLiteral_isParsed() {
        val stmt = parseSingle("val s = \"abc\"") as Stmt.VarDeclaration
        assertEquals(Expr.StringLiteral("abc"), stmt.initializer)
    }

    @Test
    fun booleanLiteral_isParsed() {
        val stmt = parseSingle("val b = true") as Stmt.VarDeclaration
        assertEquals(Expr.BooleanLiteral(true), stmt.initializer)
    }

    // --- Вызовы и доступ к членам ---

    @Test
    fun functionCallStatement_isParsed() {
        val stmt = parseSingle("line(0.0, 0.0, 10.0, 10.0)") as Stmt.ExpressionStmt
        assertEquals(
            Expr.FunctionCall(
                "line",
                listOf(
                    Expr.NumberLiteral(0.0), Expr.NumberLiteral(0.0),
                    Expr.NumberLiteral(10.0), Expr.NumberLiteral(10.0)
                )
            ),
            stmt.expression
        )
    }

    @Test
    fun memberAccessCall_isFoldedIntoFunctionName() {
        assertEquals(
            Expr.FunctionCall("Math.toRadians", listOf(Expr.Variable("x"))),
            expr("Math.toRadians(x)")
        )
    }

    @Test
    fun memberAccess_withoutCall_isMemberAccess() {
        assertEquals(Expr.MemberAccess(Expr.Variable("Math"), "PI"), expr("Math.PI"))
    }

    // --- Циклы и условия ---

    @Test
    fun forLoop_withRange_isInclusive() {
        val stmt = parseSingle("for (i in 0..10) { line(0.0, 0.0, i, 0.0) }") as Stmt.ForLoop
        assertEquals("i", stmt.variableName)
        assertEquals(Expr.NumberLiteral(0.0), stmt.start)
        assertEquals(Expr.NumberLiteral(10.0), stmt.end)
        assertEquals(false, stmt.isUntil)
        assertEquals(1, stmt.body.size)
        assertTrue(stmt.body[0] is Stmt.ExpressionStmt)
    }

    @Test
    fun forLoop_withUntil_isExclusive() {
        val stmt = parseSingle("for (i in 0 until z) { line(0.0, 0.0, i, 0.0) }") as Stmt.ForLoop
        assertEquals("i", stmt.variableName)
        assertEquals(Expr.NumberLiteral(0.0), stmt.start)
        assertEquals(Expr.Variable("z"), stmt.end)
        assertEquals(true, stmt.isUntil)
    }

    @Test
    fun ifElse_bothBranches() {
        val stmt = parseSingle(
            "if (x > 5) { circle(0.0, 0.0, 1.0) } else { rect(0.0, 0.0, 1.0, 1.0) }"
        ) as Stmt.IfStmt
        assertEquals(
            Expr.BinaryOp(Expr.Variable("x"), TokenType.GT, Expr.NumberLiteral(5.0)),
            stmt.condition
        )
        assertEquals(1, stmt.thenBranch.size)
        assertEquals(1, stmt.elseBranch.size)
    }

    @Test
    fun if_withoutElse_hasEmptyElseBranch() {
        val stmt = parseSingle("if (x > 5) { circle(0.0, 0.0, 1.0) }") as Stmt.IfStmt
        assertTrue(stmt.elseBranch.isEmpty())
    }

    @Test
    fun ifElseIf_nestsIfStatementInElseBranch() {
        val stmt = parseSingle(
            "if (a) { circle(0.0, 0.0, 1.0) } else if (b) { circle(0.0, 0.0, 2.0) }"
        ) as Stmt.IfStmt
        assertEquals(1, stmt.elseBranch.size)
        assertTrue(stmt.elseBranch[0] is Stmt.IfStmt)
    }

    // --- Присваивание ---

    @Test
    fun assignment_isParsed() {
        val stmt = parseSingle("i = i + 1") as Stmt.Assignment
        assertEquals("i", stmt.name)
        assertEquals(
            Expr.BinaryOp(Expr.Variable("i"), TokenType.PLUS, Expr.NumberLiteral(1.0)),
            stmt.value
        )
    }

    @Test
    fun semicolons_areOptional() {
        val stmts = parse("x = 1; y = 2")
        assertEquals(2, stmts.size)
        assertEquals("x", (stmts[0] as Stmt.Assignment).name)
        assertEquals("y", (stmts[1] as Stmt.Assignment).name)
    }

    // --- Приоритеты операторов ---

    @Test
    fun arithmetic_precedence() {
        assertEquals(
            Expr.BinaryOp(
                Expr.NumberLiteral(1.0), TokenType.PLUS,
                Expr.BinaryOp(Expr.NumberLiteral(2.0), TokenType.STAR, Expr.NumberLiteral(3.0))
            ),
            expr("1 + 2 * 3")
        )
    }

    @Test
    fun unary_bindsTighterThanMultiplication() {
        assertEquals(
            Expr.BinaryOp(
                Expr.UnaryOp(TokenType.MINUS, Expr.Variable("x")), TokenType.STAR, Expr.Variable("y")
            ),
            expr("-x * y")
        )
    }

    @Test
    fun logical_operatorPrecedence() {
        assertEquals(
            Expr.BinaryOp(
                Expr.BinaryOp(Expr.Variable("a"), TokenType.AND, Expr.Variable("b")),
                TokenType.OR,
                Expr.Variable("c")
            ),
            expr("a && b || c")
        )
    }

    @Test
    fun equality_isLowerPrecedenceThanComparison() {
        assertEquals(
            Expr.BinaryOp(
                Expr.BinaryOp(Expr.NumberLiteral(1.0), TokenType.LT, Expr.NumberLiteral(2.0)),
                TokenType.EQEQ,
                Expr.BooleanLiteral(true)
            ),
            expr("1 < 2 == true")
        )
    }

    // --- Блоки и пустой ввод ---

    @Test
    fun topLevelBlock_isSingleBlockStatement() {
        val stmts = parse("{ val a = 1 val b = 2 }")
        assertEquals(1, stmts.size)
        val block = stmts[0] as Stmt.Block
        assertEquals(2, block.statements.size)
        assertTrue(block.statements[0] is Stmt.VarDeclaration)
        assertTrue(block.statements[1] is Stmt.VarDeclaration)
    }

    @Test
    fun emptyInput_producesNoStatements() {
        assertTrue(parse("").isEmpty())
    }

    // --- Ошибки ---

    @Test
    fun varDeclaration_withoutName_throws() {
        assertParseError("val = 1", 1)
    }

    @Test
    fun forLoop_withoutIn_throws() {
        assertParseError("for (i 0..10) {}", 1)
    }

    @Test
    fun if_withoutParens_throws() {
        assertParseError("if x > 5 {}", 1)
    }

    @Test
    fun functionCall_withoutClosingParen_throws() {
        assertParseError("line(1, 2", 1)
    }

    @Test
    fun elseWithoutIf_throws() {
        assertParseError("else { }", 1)
    }

    @Test
    fun error_onSecondLine_reportsCorrectLine() {
        assertParseError("val a = 1\nval = 2", 2)
    }
}
