package com.sit.litecad

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sit.litecad.ui.screens.EditorScreen
import com.sit.litecad.ui.screens.HelpScreen
import com.sit.litecad.ui.screens.HomeScreen
import com.sit.litecad.settings.LanguagePreferences
import com.sit.litecad.storage.ProjectStorage

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Включаем отображение от края до края (Edge-to-Edge) для Android 10+ (SDK 29+)
        // Это автоматически убирает необходимость менять цвета баров напрямую через устаревшие методы window
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        // Применяем сохранённый язык на холодном старте (до первой композиции).
        // Дальше смена языка идёт через AppCompatDelegate без пересоздания Activity.
        val savedLanguage = LanguagePreferences.getLanguage(this)
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(savedLanguage)
        )

        setContent {
            val navController = rememberNavController()

            // --- СОВРЕМЕННАЯ НАСТРОЙКА ЦВЕТОВ СИСТЕМНЫХ ПАНЕЛЕЙ ---
            val view = androidx.compose.ui.platform.LocalView.current
            val window = this.window

            SideEffect {
                // Управляем только контрастностью иконок (светлые/темные)
                val insetsController = WindowCompat.getInsetsController(window, view)

                // Статус-бар (Верх) -> Иконки будут БЕЛЫМИ (isAppearanceLightStatusBars = false)
                insetsController.isAppearanceLightStatusBars = false

                // Навигационная панель (Низ) -> Иконки будут ТЕМНЫМИ (isAppearanceLightNavigationBars = true)
                insetsController.isAppearanceLightNavigationBars = true
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
                        },
                        onOpenHelp = {
                            navController.navigate("help")
                        }
                    )
                }

                composable("help") {
                    HelpScreen(
                        onBack = {
                            navController.popBackStack()
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
