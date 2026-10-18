package com.sit.litecad.scripting

/**
 * Исключение, возникающее при лексическом анализе: неизвестный символ или
 * некорректный литерал (число/строка).
 *
 * @property line номер строки (1-based), в которой обнаружена ошибка.
 * @param message человекочитаемое описание ошибки.
 */
class LexerException(val line: Int, message: String) : Exception(message)

/**
 * Лексер LiteScript: посимвольно разбирает исходный текст скрипта в список лексем [Token].
 *
 * Гарантии:
 * - результат всегда заканчивается лексемой `Token(TokenType.EOF, "", line)`;
 * - [Token.line] — номер строки (1-based, первая строка = 1);
 * - однострочные комментарии `//` игнорируются до конца строки (лексему не порождают),
 *   многострочные `/* */` не поддерживаются;
 * - [Token.lexeme] содержит исходный текст лексемы, а для [TokenType.STRING] — распакованное
 *   содержимое строки без кавычек.
 *
 * @param source исходный текст скрипта.
 */
class Lexer(private val source: String) {

    /** Индекс начала текущей лексемы. */
    private var start = 0

    /** Индекс текущего разбираемого символа. */
    private var current = 0

    /** Текущий номер строки (1-based). */
    private var line = 1

    private val tokens = mutableListOf<Token>()

    /**
     * Выполняет разбор всего исходного текста за один проход.
     *
     * @return список лексем, всегда заканчивающийся [TokenType.EOF].
     * @throws LexerException при неизвестном символе или некорректном числе/строке.
     */
    fun tokenize(): List<Token> {
        while (!isAtEnd()) {
            start = current
            scanToken()
        }
        tokens.add(Token(TokenType.EOF, "", line))
        return tokens
    }

    /**
     * Разбирает одну лексему, начиная с текущего символа.
     * Двухсимвольные операторы проверяются раньше односимвольных.
     */
    private fun scanToken() {
        val c = advance()
        when (c) {
            '(' -> addToken(TokenType.LPAREN)
            ')' -> addToken(TokenType.RPAREN)
            '{' -> addToken(TokenType.LBRACE)
            '}' -> addToken(TokenType.RBRACE)
            ',' -> addToken(TokenType.COMMA)
            ';' -> addToken(TokenType.SEMICOLON)

            '+' -> addToken(TokenType.PLUS)
            '-' -> addToken(TokenType.MINUS)
            '*' -> addToken(TokenType.STAR)

            '!' -> addToken(if (match('=')) TokenType.NEQ else TokenType.NOT)
            '=' -> addToken(if (match('=')) TokenType.EQEQ else TokenType.ASSIGN)
            '<' -> addToken(if (match('=')) TokenType.LTE else TokenType.LT)
            '>' -> addToken(if (match('=')) TokenType.GTE else TokenType.GT)

            '&' -> if (match('&')) addToken(TokenType.AND) else throw unexpected(c)
            '|' -> if (match('|')) addToken(TokenType.OR) else throw unexpected(c)

            '/' -> if (match('/')) skipLineComment() else addToken(TokenType.SLASH)
            '.' -> dot()

            '"' -> string()

            ' ', '\t', '\r' -> Unit
            '\n' -> line++

            else -> when {
                c.isDigit() -> number()
                c == '_' || c.isLetter() -> identifier()
                else -> throw unexpected(c)
            }
        }
    }

    /** Разбирает числовой литерал: целое (`42`) или дробное (`3.14`). */
    private fun number() {
        while (peek().isDigit()) advance()
        if (peek() == '.') {
            when {
                // Дробная часть — только если после точки идёт цифра.
                peekNext().isDigit() -> {
                    advance() // '.'
                    while (peek().isDigit()) advance()
                }
                // '..' — это отдельная лексема RANGE, точку не забираем в число.
                peekNext() == '.' -> Unit
                // Хвостовая точка ('1.') — ошибка.
                else -> throw LexerException(line, "Недопустимое число: ожидалась цифра после '.'")
            }
        }
        addToken(TokenType.NUMBER)
    }

    /** Разбирает идентификатор и сверяет его с полным списком ключевых слов. */
    private fun identifier() {
        while (peek() == '_' || peek().isLetterOrDigit()) advance()
        val text = source.substring(start, current)
        addToken(KEYWORDS[text] ?: TokenType.IDENTIFIER)
    }

    /**
     * Разбирает строковый литерал в двойных кавычках с экранированием `\"` и `\\`.
     * В [Token.lexeme] попадает распакованное содержимое без кавычек.
     */
    private fun string() {
        val stringLine = line
        val sb = StringBuilder()
        while (!isAtEnd()) {
            when (val c = advance()) {
                '"' -> {
                    tokens.add(Token(TokenType.STRING, sb.toString(), stringLine))
                    return
                }
                '\\' -> {
                    if (isAtEnd()) break
                    when (val escaped = advance()) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        // Неизвестная escape-последовательность сохраняется как есть.
                        else -> {
                            sb.append('\\')
                            sb.append(escaped)
                        }
                    }
                }
                '\n' -> throw LexerException(stringLine, "Незакрытая строковая константа")
                else -> sb.append(c)
            }
        }
        throw LexerException(stringLine, "Незакрытая строковая константа")
    }

    /** Разбирает `.`: либо диапазон `..`, либо доступ к члену, либо ошибку ведущей точки. */
    private fun dot() {
        if (match('.')) {
            addToken(TokenType.RANGE)
            return
        }
        if (peek().isDigit()) {
            throw LexerException(line, "Недопустимое число: ведущая точка не поддерживается")
        }
        addToken(TokenType.DOT)
    }

    /** Пропускает однострочный комментарий `// ...` до конца строки (не порождает лексему). */
    private fun skipLineComment() {
        while (!isAtEnd() && peek() != '\n') advance()
    }

    private fun unexpected(c: Char) = LexerException(line, "Неожиданный символ: '$c'")

    private fun isAtEnd(): Boolean = current >= source.length

    private fun advance(): Char = source[current++]

    /** Возвращает текущий символ без потребления или `\u0000` в конце ввода. */
    private fun peek(): Char = if (isAtEnd()) NUL else source[current]

    /** Возвращает следующий за текущим символ или `\u0000`, если его нет. */
    private fun peekNext(): Char = if (current + 1 >= source.length) NUL else source[current + 1]

    /** Потребляет текущий символ, только если он совпадает с [expected]. */
    private fun match(expected: Char): Boolean {
        if (isAtEnd() || source[current] != expected) return false
        current++
        return true
    }

    private fun addToken(type: TokenType) = addToken(type, source.substring(start, current))

    private fun addToken(type: TokenType, lexeme: String) {
        tokens.add(Token(type, lexeme, line))
    }

    private companion object {
        /** Символ-заполнитель для «нет символа». */
        const val NUL = '\u0000'

        /** Карта ключевых слов. Сверка идёт по полному слову, поэтому `untilX`/`trueValue` — идентификаторы. */
        val KEYWORDS: Map<String, TokenType> = mapOf(
            "val" to TokenType.VAL,
            "var" to TokenType.VAR,
            "for" to TokenType.FOR,
            "if" to TokenType.IF,
            "else" to TokenType.ELSE,
            "in" to TokenType.IN,
            "until" to TokenType.UNTIL,
            "true" to TokenType.TRUE,
            "false" to TokenType.FALSE
        )
    }
}
