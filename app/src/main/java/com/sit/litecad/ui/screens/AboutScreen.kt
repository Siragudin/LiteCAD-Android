package com.sit.litecad.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sit.litecad.R

/**
 * Компактное окно «О приложении».
 *
 * Это НЕ отдельный экран: диалог маленького размера (окно подстраивается под
 * содержимое), показывается поверх HomeScreen — так же, как панель настроек.
 * Закрывается кнопкой OK, тапом вне окна или системной кнопкой «Назад»
 * (всё через [onDismiss]).
 *
 * Оформление — в стиле главного экрана: тёмная карточка, белый текст.
 *
 * Версия берётся из строкового ресурса [R.string.about_version]: BuildConfig в проекте
 * не генерируется (в build.gradle.kts не включён buildFeatures.buildConfig), а
 * versionName в defaultConfig равен "1.0" и не совпадает с отображаемой версией.
 */
@Composable
fun AboutScreen(onDismiss: () -> Unit) {
    val windowColor = Color(0xFF383838)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = windowColor,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Название приложения — главный элемент окна.
                Text(
                    text = stringResource(R.string.app_name),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.about_version),
                    color = Color(0xFFCFCFCF),
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.about_developed_by),
                    color = Color(0xFFE0E0E0),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))

                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFCFCFCF))
                ) {
                    Text(stringResource(R.string.button_ok))
                }
            }
        }
    }
}
