package com.sit.litecad

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sit.litecad.ui.screens.EditorScreen
import com.sit.litecad.ui.screens.HomeScreen
import com.sit.litecad.settings.LanguagePreferences
import com.sit.litecad.storage.ProjectStorage

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Применяем сохранённый язык на холодном старте (до первой композиции).
        // Дальше смена языка идёт через AppCompatDelegate без пересоздания Activity.
        val savedLanguage = LanguagePreferences.getLanguage(this)
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(savedLanguage)
        )

        setContent {
            val navController = rememberNavController()


            // --- НАСТРОЙКА ЦВЕТОВ СИСТЕМНЫХ ПАНЕЛЕЙ (Из plane2d) ---
            val window = this.window
            val headerColor = Color(0xFF333333).toArgb() // Темно-серый (под шапку HomeScreen)
            val navBarColor = Color(0xFFF5F5F5).toArgb() // Светло-серый (под фон списка)

            SideEffect {
                // Статус-бар (Верх) -> Делаем иконки светлыми на темном фоне
                window.statusBarColor = headerColor
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

                // Навигационная панель (Низ) -> Делаем иконки темными на светлом фоне
                window.navigationBarColor = navBarColor
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
            }
            // ------------------------------------------------------
            val storage = remember { ProjectStorage(applicationContext) }

            // Навигация с заглушками: пока просто передаем имя проекта между экранами
            NavHost(navController = navController, startDestination = "home") {

                composable("home") {
                    HomeScreen(
                        storage = storage,
                        onOpenProject = { projectName ->
                            navController.navigate("editor/$projectName")
                        }
                    )
                }

                composable("editor/{name}") { backStackEntry ->
                    val projectName = backStackEntry.arguments?.getString("name") ?: "Чертеж"

                    EditorScreen(
                        projectName = projectName,
                        storage = storage,
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
