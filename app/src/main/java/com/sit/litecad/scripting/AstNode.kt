package com.sit.litecad.scripting

/**
 * Узел выражения — то, что вычисляется и имеет значение.
 */
sealed class Expr {
    /** Числовой литерал, например `42` или `3.14`. */
    data class NumberLiteral(val value: Double) : Expr()

    /** Строковый литерал, например `"Готово"` (значение уже распаковано лексером). */
    data class StringLiteral(val value: String) : Expr()

    /** Булев литерал: `true` или `false`. */
    data class BooleanLiteral(val value: Boolean) : Expr()

    /** Ссылка на переменную по имени. */
    data class Variable(val name: String) : Expr()

    /** Унарная операция `operator operand`, например `-x` или `!flag`. */
    data class UnaryOp(val operator: TokenType, val operand: Expr) : Expr()

    /** Бинарная операция `left <operator> right`: арифметика, сравнение, логика или диапазон `..`. */
    data class BinaryOp(val left: Expr, val operator: TokenType, val right: Expr) : Expr()

    /** Вызов функции, например `line(0, 0, 10, 10)`. */
    data class FunctionCall(val name: String, val arguments: List<Expr>) : Expr()

    /** Доступ к члену объекта, например `Math.toRadians` (target — обычно `Variable("Math")`). */
    data class MemberAccess(val target: Expr, val member: String) : Expr()
}

/**
 * Узел инструкции — то, что выполняется интерпретатором.
 */
sealed class Stmt {
    /** Объявление переменной: `val name = initializer` / `var name = initializer`. */
    data class VarDeclaration(
        val name: String,
        val initializer: Expr?,
        val isMutable: Boolean
    ) : Stmt()

    /** Присваивание уже существующей переменной: `name = value`. */
    data class Assignment(
        val name: String,
        val value: Expr
    ) : Stmt()

    /**
     * Цикл по диапазону: `for (i in start..end) { body }` или `for (i in start until end) { body }`.
     * При [isUntil] == true конечная граница не включается (`0 until 10`), иначе включается (`0..10`).
     */
    data class ForLoop(
        val variableName: String,
        val start: Expr,
        val end: Expr,
        val isUntil: Boolean,
        val body: List<Stmt>
    ) : Stmt()

    /** Условие: `if (condition) { thenBranch } else { elseBranch }`. */
    data class IfStmt(
        val condition: Expr,
        val thenBranch: List<Stmt>,
        val elseBranch: List<Stmt>
    ) : Stmt()

    /** Инструкция-выражение (например, вызов функции рисования). */
    data class ExpressionStmt(val expression: Expr) : Stmt()

    /** Блок инструкций `{ ... }` — отдельная инструкция, содержащая список вложенных инструкций. */
    data class Block(val statements: List<Stmt>) : Stmt()
}
