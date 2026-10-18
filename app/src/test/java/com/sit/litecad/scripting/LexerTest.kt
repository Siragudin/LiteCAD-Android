package com.sit.litecad.scripting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Юнит-тесты лексера LiteScript ([Lexer]).
 *
 * Проверяют разбор чисел, идентификаторов vs ключевых слов, операторов, строк,
 * комментариев, отслеживание номеров строк, а также ошибочные ситуации ([LexerException]).
 */
class LexerTest {

    private fun lex(source: String): List<Token> = Lexer(source).tokenize()

    private fun types(source: String): List<TokenType> = lex(source).map { it.type }

    @Test
    fun emptyInput_producesSingleEof() {
        val tokens = lex("")
        assertEquals(listOf(TokenType.EOF), tokens.map { it.type })
        assertEquals("", tokens.single().lexeme)
        assertEquals(1, tokens.single().line)
    }

    @Test
    fun numbers_integerAndDecimal() {
        val tokens = lex("42 3.14 0.5")
        assertEquals(
            listOf(TokenType.NUMBER, TokenType.NUMBER, TokenType.NUMBER, TokenType.EOF),
            tokens.map { it.type }
        )
        assertEquals(listOf("42", "3.14", "0.5"), tokens.dropLast(1).map { it.lexeme })
    }

    @Test
    fun identifiers_vsKeywords_fullWordMatching() {
        val tokens = lex("line untilX val until true trueValue in IN")
        assertEquals(
            listOf(
                TokenType.IDENTIFIER, TokenType.IDENTIFIER, TokenType.VAL, TokenType.UNTIL,
                TokenType.TRUE, TokenType.IDENTIFIER, TokenType.IN, TokenType.IDENTIFIER,
                TokenType.EOF
            ),
            tokens.map { it.type }
        )
        // Полнословная сверка: untilX/trueValue/IN — идентификаторы, а не ключевые слова/операторы.
        assertEquals("untilX", tokens[1].lexeme)
        assertEquals("trueValue", tokens[5].lexeme)
        assertEquals("IN", tokens[7].lexeme)
    }

    @Test
    fun twoCharOperators_areNotSplit() {
        val tokens = lex("== != <= >= && || ..")
        assertEquals(
            listOf(
                TokenType.EQEQ, TokenType.NEQ, TokenType.LTE, TokenType.GTE,
                TokenType.AND, TokenType.OR, TokenType.RANGE, TokenType.EOF
            ),
            tokens.map { it.type }
        )
        // '==' не распадается на два '='.
        assertEquals("==", tokens[0].lexeme)
        assertEquals("..", tokens[6].lexeme)
    }

    @Test
    fun singleCharOperators() {
        assertEquals(
            listOf(
                TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH,
                TokenType.ASSIGN, TokenType.LT, TokenType.GT, TokenType.NOT,
                TokenType.DOT, TokenType.EOF
            ),
            types("+ - * / = < > ! .")
        )
    }

    @Test
    fun rangeOperator_afterNumber_isNotSwallowedAsDecimalPoint() {
        assertEquals(
            listOf(TokenType.NUMBER, TokenType.RANGE, TokenType.NUMBER, TokenType.EOF),
            types("0..10")
        )
    }

    @Test
    fun strings_plainAndEscaped() {
        val plain = lex("\"abc\"").first()
        assertEquals(TokenType.STRING, plain.type)
        assertEquals("abc", plain.lexeme)

        val quote = lex("\"a\\\"b\"").first()
        assertEquals(TokenType.STRING, quote.type)
        assertEquals("a\"b", quote.lexeme)

        val backslash = lex("\"a\\\\b\"").first()
        assertEquals(TokenType.STRING, backslash.type)
        assertEquals("a\\b", backslash.lexeme)
    }

    @Test
    fun comments_areSkippedToEndOfLine() {
        val tokens = lex("line // комментарий до конца строки\nrect")
        assertEquals(
            listOf(TokenType.IDENTIFIER, TokenType.IDENTIFIER, TokenType.EOF),
            tokens.map { it.type }
        )
        assertEquals("line", tokens[0].lexeme)
        assertEquals("rect", tokens[1].lexeme)
        assertEquals(1, tokens[0].line)
        assertEquals(2, tokens[1].line)
    }

    @Test
    fun lineNumbers_areTrackedAcrossLines() {
        val tokens = lex("val a = 1\nval b = 2\nline(0, 0, 1, 1)")
        assertEquals(TokenType.VAL, tokens[0].type)
        assertEquals(1, tokens[0].line)

        assertEquals(2, tokens.first { it.lexeme == "b" }.line)
        assertEquals(3, tokens.first { it.lexeme == "line" }.line)
        assertEquals(3, tokens.last().line) // EOF на последней строке
    }

    @Test
    fun bracesCommasSemicolons_areLexed() {
        assertEquals(
            listOf(
                TokenType.LPAREN, TokenType.RPAREN, TokenType.LBRACE, TokenType.RBRACE,
                TokenType.COMMA, TokenType.SEMICOLON, TokenType.EOF
            ),
            types("( ) { } , ;")
        )
    }

    @Test
    fun unterminatedString_throwsWithLine() {
        val ex = assertThrows(LexerException::class.java) { lex("\"abc") }
        assertEquals(1, ex.line)
    }

    @Test
    fun unterminatedString_onSecondLine_throwsWithCorrectLine() {
        val ex = assertThrows(LexerException::class.java) { lex("val a = 1\n\"abc") }
        assertEquals(2, ex.line)
    }

    @Test
    fun leadingDotNumber_throwsWithLine() {
        val ex = assertThrows(LexerException::class.java) { lex(".5") }
        assertEquals(1, ex.line)
    }

    @Test
    fun trailingDotNumber_throwsWithLine() {
        val ex = assertThrows(LexerException::class.java) { lex("1.") }
        assertEquals(1, ex.line)
    }

    @Test
    fun unknownCharacter_throwsWithMessageAndLine() {
        val ex = assertThrows(LexerException::class.java) { lex("\n$") }
        assertEquals(2, ex.line)
        assertEquals("Неожиданный символ: '$'", ex.message)
    }
}
