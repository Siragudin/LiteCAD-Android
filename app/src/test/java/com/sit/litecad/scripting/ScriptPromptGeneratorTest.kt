package com.sit.litecad.scripting

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Локальный JVM-тест генератора промпта. Проверяет только чистую функцию [ScriptPromptGenerator.generateMarkdown].
 */
class ScriptPromptGeneratorTest {

    @Test
    fun markdown_containsAllDrawingApiNames() {
        val md = ScriptPromptGenerator.generateMarkdown("Шестерёнка с 20 зубьями")

        for (name in listOf("line", "rect", "circle", "arc", "gear", "style", "text", "fill", "moveTo", "lineTo", "closePath")) {
            assertTrue("Промпт должен упоминать функцию $name", md.contains(name))
        }
    }

    @Test
    fun markdown_containsKeywords() {
        val md = ScriptPromptGenerator.generateMarkdown("что-нибудь")

        for (keyword in listOf("val", "for", "if", "until", "..")) {
            assertTrue("Промпт должен упоминать ключевое слово $keyword", md.contains(keyword))
        }
    }

    @Test
    fun markdown_containsWorkingExample() {
        val md = ScriptPromptGenerator.generateMarkdown("что-нибудь")

        assertTrue("Пример должен использовать gear(", md.contains("gear("))
        assertTrue("Пример должен использовать for (", md.contains("for ("))
    }

    @Test
    fun markdown_containsUserRequestBlock() {
        val md = ScriptPromptGenerator.generateMarkdown("Фланец с 6 отверстиями")

        assertTrue("Промпт должен содержать блок запроса", md.contains("Запрос пользователя:"))
        assertTrue("Промпт должен содержать текст пользователя", md.contains("Фланец с 6 отверстиями"))
    }

    @Test
    fun markdown_usesPlaceholder_whenPromptIsBlank() {
        val md = ScriptPromptGenerator.generateMarkdown("   ")

        assertTrue("Пустой запрос должен заменяться плейсхолдером", md.contains("описание не задано"))
    }

    @Test
    fun markdown_doesNotContainLegacyDsl() {
        val md = ScriptPromptGenerator.generateMarkdown("что-нибудь")

        assertFalse("Старый DSL LINE не должен упоминаться", md.contains("LINE"))
        assertFalse("Старый DSL CIRCLE не должен упоминаться", md.contains("CIRCLE"))
        assertFalse("Старый DSL RECT не должен упоминаться", md.contains("RECT"))
        assertFalse("Старый DSL STYLE не должен упоминаться", md.contains("STYLE"))
    }

    @Test
    fun sampleScripts_areParseable() {
        val samples = listOf(
            ScriptPromptGenerator.SAMPLE_GEAR_SCRIPT,
            ScriptPromptGenerator.SAMPLE_GEAR_FILLED_SCRIPT,
            ScriptPromptGenerator.SAMPLE_FLANGE_SCRIPT,
            ScriptPromptGenerator.SAMPLE_FILL_SCRIPT,
            ScriptPromptGenerator.SAMPLE_POLYLINE_SCRIPT,
            ScriptPromptGenerator.SAMPLE_BOLT_SCRIPT,
            ScriptPromptGenerator.SAMPLE_BOLT_SIDE_SCRIPT,
            ScriptPromptGenerator.SAMPLE_STAR_SCRIPT
        )
        for (sample in samples) {
            // Не должно бросать исключений — примеры в промпте обязаны быть валидным LiteScript.
            Parser(Lexer(sample).tokenize()).parse()
        }
    }

    @Test
    fun markdown_containsComplexShapesGuidance() {
        val md = ScriptPromptGenerator.generateMarkdown("шестерёнка с отверстием")

        assertTrue("Промпт должен объяснять единый контур", md.contains("единым замкнутым контуром"))
        assertTrue("Промпт должен упоминать moveTo", md.contains("moveTo"))
        assertTrue("Промпт должен упоминать lineTo", md.contains("lineTo"))
        assertTrue("Промпт должен упоминать closePath", md.contains("closePath"))
        assertTrue("Промпт должен запрещать наложение фигур", md.contains("Не накладывай"))
    }

    @Test
    fun markdown_containsSpecialCasesGuidance() {
        val md = ScriptPromptGenerator.generateMarkdown("болт M10")

        assertTrue("Промпт должен содержать секцию о спецслучаях", md.contains("Специальные случаи"))
        assertTrue("Промпт должен запрещать смешение видов", md.contains("Не смешивай виды"))
        assertTrue("Промпт должен объяснять рёбра жёсткости", md.contains("Рёбра жёсткости"))
        assertTrue("Промпт должен предупреждать про спирали", md.contains("Спирали"))
    }
}
