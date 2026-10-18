package com.sit.litecad.scripting

/**
 * Исключение, возникающее при синтаксическом разборе: неожиданный токен,
 * пропущенная скобка, отсутствие имени переменной и т.п.
 *
 * @property line номер строки (1-based), в которой обнаружена ошибка.
 * @param message человекочитаемое описание ошибки.
 */
class ParseException(val line: Int, message: String) : Exception(message)

/**
 * Парсер LiteScript: рекурсивный спуск по списку лексем [Token] в дерево инструкций [Stmt].
 *
 * @param tokens лексемы от [Lexer]; должны заканчиваться [TokenType.EOF].
 */
class Parser(private val tokens: List<Token>) {

    /** Индекс текущего токена. */
    private var current = 0

    /**
     * Разбирает весь поток лексем в список инструкций верхнего уровня.
     *
     * @return список инструкций ([Stmt]); для пустого ввода — пустой список.
     * @throws ParseException при синтаксической ошибке или отсутствии завершающего [TokenType.EOF].
     */
    fun parse(): List<Stmt> {
        if (tokens.isEmpty() || tokens.last().type != TokenType.EOF) {
            throw ParseException(tokens.lastOrNull()?.line ?: 1, "Поток лексем должен заканчиваться EOF")
        }
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            statements.add(statement())
        }
        return statements
    }

    // --- Инструкции ---

    /** Разбирает одну инструкцию верхнего уровня (или вложенную в блок). */
    private fun statement(): Stmt = when {
        match(TokenType.VAL) -> varDeclaration(isMutable = false)
        match(TokenType.VAR) -> varDeclaration(isMutable = true)
        check(TokenType.FOR) -> forLoop()
        check(TokenType.IF) -> ifStatement()
        check(TokenType.LBRACE) -> block()
        // Присваивание: IDENTIFIER '=' (но не '==').
        check(TokenType.IDENTIFIER) && peekNext().type == TokenType.ASSIGN -> assignment()
        else -> expressionStatement()
    }

    /** Объявление переменной: `val`/`var` уже потреблён вызывающим. */
    private fun varDeclaration(isMutable: Boolean): Stmt {
        val name = consume(TokenType.IDENTIFIER, "Ожидалось имя переменной").lexeme
        var initializer: Expr? = null
        if (match(TokenType.ASSIGN)) {
            initializer = expression()
        }
        match(TokenType.SEMICOLON) // точка с запятой необязательна
        return Stmt.VarDeclaration(name, initializer, isMutable)
    }

    /** Присваивание существующей переменной: `name = value`. */
    private fun assignment(): Stmt {
        val name = consume(TokenType.IDENTIFIER, "Ожидалось имя переменной").lexeme
        consume(TokenType.ASSIGN, "Ожидалось '=' в присваивании")
        val value = expression()
        match(TokenType.SEMICOLON) // точка с запятой необязательна
        return Stmt.Assignment(name, value)
    }

    /** Цикл: `for (i in start..end) { ... }` или `for (i in start until end) { ... }`. */
    private fun forLoop(): Stmt {
        consume(TokenType.FOR, "Ожидалось 'for'")
        consume(TokenType.LPAREN, "Ожидалось '(' после for")
        val variableName = consume(TokenType.IDENTIFIER, "Ожидалось имя переменной").lexeme
        consume(TokenType.IN, "Ожидалось 'in' в цикле for")
        // Границы диапазона не должны поглощать разделитель '..' как бинарный оператор.
        val start = expression(allowRange = false)
        val isUntil = when {
            match(TokenType.RANGE) -> false
            match(TokenType.UNTIL) -> true
            else -> throw error(peek(), "Ожидалось '..' или 'until' в цикле for")
        }
        val end = expression(allowRange = false)
        consume(TokenType.RPAREN, "Ожидалось ')' после диапазона")
        return Stmt.ForLoop(variableName, start, end, isUntil, blockStatements())
    }

    /** Условие: `if (cond) { ... } [else { ... } | else if (...) { ... }]`. */
    private fun ifStatement(): Stmt {
        consume(TokenType.IF, "Ожидалось 'if'")
        consume(TokenType.LPAREN, "Ожидалось '(' после if")
        val condition = expression()
        consume(TokenType.RPAREN, "Ожидалось ')' после условия")
        val thenBranch = blockStatements()
        val elseBranch = if (match(TokenType.ELSE)) {
            // `else if` — рекурсивный if, обёрнутый в одноэлементный список ветки else.
            if (check(TokenType.IF)) listOf(ifStatement()) else blockStatements()
        } else {
            emptyList()
        }
        return Stmt.IfStmt(condition, thenBranch, elseBranch)
    }

    /** Блок `{ ... }` как отдельная инструкция. */
    private fun block(): Stmt = Stmt.Block(blockStatements())

    /** Разбирает `{ statement* }` и возвращает список инструкций внутри. */
    private fun blockStatements(): List<Stmt> {
        consume(TokenType.LBRACE, "Ожидалось '{'")
        val statements = mutableListOf<Stmt>()
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            statements.add(statement())
        }
        consume(TokenType.RBRACE, "Ожидалось '}'")
        return statements
    }

    /** Инструкция-выражение (например, вызов функции рисования). */
    private fun expressionStatement(): Stmt {
        val expr = expression()
        match(TokenType.SEMICOLON) // точка с запятой необязательна
        return Stmt.ExpressionStmt(expr)
    }

    // --- Выражения (по убыванию приоритета) ---

    /** Точка входа для выражений; [allowRange] = false отключает бинарный оператор `..` (для `for`). */
    private fun expression(allowRange: Boolean = true): Expr = logicOr(allowRange)

    private fun logicOr(allowRange: Boolean): Expr {
        var expr = logicAnd(allowRange)
        while (match(TokenType.OR)) {
            expr = Expr.BinaryOp(expr, TokenType.OR, logicAnd(allowRange))
        }
        return expr
    }

    private fun logicAnd(allowRange: Boolean): Expr {
        var expr = equality(allowRange)
        while (match(TokenType.AND)) {
            expr = Expr.BinaryOp(expr, TokenType.AND, equality(allowRange))
        }
        return expr
    }

    private fun equality(allowRange: Boolean): Expr {
        var expr = comparison(allowRange)
        while (match(TokenType.EQEQ, TokenType.NEQ)) {
            val operator = previous().type
            expr = Expr.BinaryOp(expr, operator, comparison(allowRange))
        }
        return expr
    }

    private fun comparison(allowRange: Boolean): Expr {
        var expr = range(allowRange)
        while (match(TokenType.LT, TokenType.LTE, TokenType.GT, TokenType.GTE)) {
            val operator = previous().type
            expr = Expr.BinaryOp(expr, operator, range(allowRange))
        }
        return expr
    }

    private fun range(allowRange: Boolean): Expr {
        val left = term()
        if (allowRange && match(TokenType.RANGE)) {
            return Expr.BinaryOp(left, TokenType.RANGE, term())
        }
        return left
    }

    private fun term(): Expr {
        var expr = factor()
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val operator = previous().type
            expr = Expr.BinaryOp(expr, operator, factor())
        }
        return expr
    }

    private fun factor(): Expr {
        var expr = unary()
        while (match(TokenType.STAR, TokenType.SLASH)) {
            val operator = previous().type
            expr = Expr.BinaryOp(expr, operator, unary())
        }
        return expr
    }

    private fun unary(): Expr {
        if (match(TokenType.MINUS, TokenType.NOT)) {
            val operator = previous().type
            return Expr.UnaryOp(operator, unary())
        }
        return postfix()
    }

    /** Доступ к членам и вызовы: `Math.toRadians(x)`, `a.b.c`. */
    private fun postfix(): Expr {
        var expr = primary()
        while (match(TokenType.DOT)) {
            val member = consume(TokenType.IDENTIFIER, "Ожидалось имя члена после '.'").lexeme
            expr = if (check(TokenType.LPAREN)) {
                // `target.member(...)` — склеиваем имя через точку в одно имя функции.
                val name = if (expr is Expr.Variable) "${expr.name}.$member" else member
                Expr.FunctionCall(name, arguments())
            } else {
                Expr.MemberAccess(expr, member)
            }
        }
        return expr
    }

    private fun primary(): Expr = when {
        match(TokenType.NUMBER) -> Expr.NumberLiteral(previous().lexeme.toDouble())
        match(TokenType.STRING) -> Expr.StringLiteral(previous().lexeme)
        match(TokenType.TRUE) -> Expr.BooleanLiteral(true)
        match(TokenType.FALSE) -> Expr.BooleanLiteral(false)
        match(TokenType.IDENTIFIER) -> {
            val name = previous().lexeme
            if (check(TokenType.LPAREN)) Expr.FunctionCall(name, arguments()) else Expr.Variable(name)
        }
        match(TokenType.LPAREN) -> {
            val expr = expression()
            consume(TokenType.RPAREN, "Ожидалось ')' после выражения")
            expr
        }
        else -> throw error(peek(), "Неожиданный токен: '${describe(peek())}'")
    }

    /** Список аргументов вызова: `( expr (, expr)* )`. */
    private fun arguments(): List<Expr> {
        consume(TokenType.LPAREN, "Ожидалось '('")
        val args = mutableListOf<Expr>()
        if (!check(TokenType.RPAREN)) {
            do {
                args.add(expression())
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RPAREN, "Ожидалось ')' после аргументов")
        return args
    }

    // --- Примитивы работы с потоком лексем ---

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    private fun peek(): Token = tokens.getOrElse(current) { syntheticEof() }

    private fun peekNext(): Token = tokens.getOrElse(current + 1) { syntheticEof() }

    private fun previous(): Token = tokens[current - 1]

    private fun check(type: TokenType): Boolean = peek().type == type

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw error(peek(), message)
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun error(token: Token, message: String): ParseException = ParseException(token.line, message)

    private fun describe(token: Token): String = if (token.lexeme.isEmpty()) token.type.name else token.lexeme

    private fun syntheticEof(): Token = Token(TokenType.EOF, "", tokens.lastOrNull()?.line ?: 1)
}
