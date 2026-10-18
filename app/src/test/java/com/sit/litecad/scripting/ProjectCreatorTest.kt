package com.sit.litecad.scripting

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Юнит-тесты обработки ошибок [ProjectCreator.createFromScriptDetailed].
 *
 * Покрываются только «ранние» ветки — пустой скрипт и ошибки лексера/парсера/рантайма, — которые
 * возвращают результат раньше, чем дело доходит до [com.sit.litecad.storage.ProjectStorage], поэтому
 * реальный `Context` не нужен. В качестве заглушки берётся [Application]: сборка тестов идёт с
 * `unitTests.isReturnDefaultValues = true`, а его методы в этих ветках всё равно не вызываются.
 * Успешный путь создания проекта покрыт на уровне движка ([InterpreterTest]).
 */
class ProjectCreatorTest {

    private val context = Application()

    @Test
    fun blankScript_returnsEmptyScript() = runBlocking {
        val result = ProjectCreator.createFromScriptDetailed(context, "проект", "   ")

        assertTrue(
            "Пустой скрипт должен возвращать EmptyScript, а получено $result",
            result is ScriptCreationResult.EmptyScript
        )
    }

    @Test
    fun invalidSyntax_returnsParserFailureWithLine() = runBlocking {
        val result = ProjectCreator.createFromScriptDetailed(context, "проект", "val = 1")

        val failure = result as ScriptCreationResult.ParseFailure
        assertEquals(ScriptCreationResult.Kind.PARSER, failure.kind)
        assertEquals(1, failure.line)
    }

    @Test
    fun unknownFunction_returnsRuntimeFailure() = runBlocking {
        val result = ProjectCreator.createFromScriptDetailed(context, "проект", "foo()")

        val failure = result as ScriptCreationResult.ParseFailure
        assertEquals(ScriptCreationResult.Kind.RUNTIME, failure.kind)
        assertEquals(-1, failure.line)
    }
}
