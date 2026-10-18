package com.sit.litecad.scripting

import android.content.Context
import com.sit.litecad.model.CadDocument
import com.sit.litecad.storage.ProjectStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Результат создания проекта из скрипта.
 *
 * В отличие от булева результата [ProjectCreator.createFromScript], позволяет UI показать
 * пользователю точную причину неудачи — тип ошибки ([ParseFailure.kind]) и номер строки
 * ([ParseFailure.line]), а не молчаливое «ничего не произошло».
 */
sealed class ScriptCreationResult {

    /** Источник ошибки создания скрипта. */
    enum class Kind { LEXER, PARSER, RUNTIME, IO }

    /** Скрипт успешно обработан, документ построен и проект сохранён. */
    data class Success(val document: CadDocument) : ScriptCreationResult()

    /**
     * Ошибка обработки скрипта или сохранения проекта.
     *
     * @property line номер строки (1-based) для лексических и синтаксических ошибок; `-1`, если неизвестен.
     * @property message человекочитаемое описание ошибки.
     * @property kind источник ошибки (см. [Kind]).
     */
    data class ParseFailure(val line: Int, val message: String, val kind: Kind) : ScriptCreationResult()

    /** Скрипт пуст (или содержит только пробельные символы). */
    data object EmptyScript : ScriptCreationResult()
}

/**
 * Фасад генерации проекта из скрипта.
 *
 * Полностью изолирован от UI: принимает текст скрипта, прогоняет его через конвейер
 * [Lexer] → [Parser] → [Interpreter] (строит новый [CadDocument]) и сохраняет результат
 * в `.sit` файл через [ProjectStorage]. Тяжёлые операции выполняются на [Dispatchers.IO].
 */
object ProjectCreator {

    /**
     * Создаёт новый проект из текста скрипта и сохраняет его.
     *
     * Тонкая обёртка над [createFromScriptDetailed] для текущего UI: возвращает `true`
     * только при [ScriptCreationResult.Success]. Постепенно мигрируйте на детальный вариант,
     * чтобы показывать пользователю причину ошибки.
     *
     * @param context контекст приложения (нужен для файлового хранилища).
     * @param projectName имя проекта (имя `.sit` файла).
     * @param scriptText текст скрипта.
     * @param onProgress прогресс в диапазоне `0f..1f` (обработка скрипта + сохранение).
     * @return `true`, если проект успешно создан и сохранён, иначе `false`.
     */
    suspend fun createFromScript(
        context: Context,
        projectName: String,
        scriptText: String,
        onProgress: (Float) -> Unit = {},
    ): Boolean =
        createFromScriptDetailed(context, projectName, scriptText, onProgress) is ScriptCreationResult.Success

    /**
     * Создаёт новый проект из текста скрипта и возвращает детальный результат с типом и строкой ошибки.
     *
     * Прогресс отдаётся в диапазоне `0f..1f` (`0f` в начале, `PARSE_PROGRESS_WEIGHT` после разбора
     * скрипта, `1f` после сохранения). При любой ошибке `onProgress(1f)` не вызывается.
     *
     * @param context контекст приложения (нужен для файлового хранилища).
     * @param projectName имя проекта (имя `.sit` файла).
     * @param scriptText текст скрипта.
     * @param onProgress прогресс в диапазоне `0f..1f`.
     * @return [ScriptCreationResult] — успех, пустой скрипт или ошибка с типом и строкой.
     */
    suspend fun createFromScriptDetailed(
        context: Context,
        projectName: String,
        scriptText: String,
        onProgress: (Float) -> Unit = {},
    ): ScriptCreationResult = withContext(Dispatchers.IO) {
        // Пустой скрипт — выходим до создания документа и запуска прогресса.
        if (scriptText.isBlank()) return@withContext ScriptCreationResult.EmptyScript

        try {
            onProgress(0f)

            val document = CadDocument()

            // Конвейер нового движка: лексер -> парсер -> интерпретатор.
            val tokens = Lexer(scriptText).tokenize()
            val ast = Parser(tokens).parse()
            Interpreter(document).execute(ast)

            // Обработка скрипта занимает 0f..0.9f, остаток отведён на запись проекта.
            onProgress(PARSE_PROGRESS_WEIGHT)

            val storage = ProjectStorage(context)
            // Превью пока не генерируем — его можно добавить позже (thumbnail = null).
            storage.saveProject(document, projectName, thumbnail = null)

            onProgress(1f)
            ScriptCreationResult.Success(document)
        } catch (e: LexerException) {
            // Ожидаемая ошибка ввода — логи не засоряем стектрейсом.
            ScriptCreationResult.ParseFailure(
                line = e.line,
                message = e.message ?: "Ошибка лексического анализа",
                kind = ScriptCreationResult.Kind.LEXER
            )
        } catch (e: ParseException) {
            // Ожидаемая ошибка ввода — логи не засоряем стектрейсом.
            ScriptCreationResult.ParseFailure(
                line = e.line,
                message = e.message ?: "Ошибка синтаксиса",
                kind = ScriptCreationResult.Kind.PARSER
            )
        } catch (e: ScriptRuntimeException) {
            // Ожидаемая ошибка ввода — логи не засоряем стектрейсом.
            // У ScriptRuntimeException нет номера строки.
            ScriptCreationResult.ParseFailure(
                line = -1,
                message = e.message ?: "Ошибка выполнения скрипта",
                kind = ScriptCreationResult.Kind.RUNTIME
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ScriptCreationResult.ParseFailure(
                line = -1,
                message = e.message ?: "Не удалось создать проект",
                kind = ScriptCreationResult.Kind.IO
            )
        }
    }

    /** Доля прогресса, отведённая на обработку скрипта (остаток — на запись проекта). */
    private const val PARSE_PROGRESS_WEIGHT = 0.9f
}
