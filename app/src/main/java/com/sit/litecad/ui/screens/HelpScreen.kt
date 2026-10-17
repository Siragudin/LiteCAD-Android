package com.sit.litecad.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sit.litecad.R
import com.sit.litecad.ui.icons.ArrowMenuOpen
import com.sit.litecad.ui.icons.ArrowTopLeft
import com.sit.litecad.ui.icons.LineEnd
import com.sit.litecad.ui.icons.TabDuplicate

// Палитра в стиле главного экрана (HomeScreen), чтобы «Справочник» выглядел родным.
private val HelpHeaderColor = Color(0xFF383838)
private val HelpBodyColor = Color(0xFF6E6E6E)
private val HelpCardColor = Color(0xFFD9D9D9)
private val HelpCardTitleColor = Color(0xFF1C1B1F)
private val HelpCardBodyColor = Color(0xFF4A4A4A)

/** Данные одного инструмента для списка-аккордеона: иконка, название и инструкция. */
private data class HelpTool(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val description: String
)

/**
 * Экран «Справочник инструментов» — гайд для новых пользователей.
 *
 * Список построен как аккордеон: в каждой карточке сверху название инструмента и его
 * иконка, а по тапу под ней плавно раскрывается инструкция (список сдвигается вниз).
 * Открыт может быть только один пункт: тап по уже открытому закрывает его,
 * тап по другому — закрывает текущий и открывает новый.
 *
 * Инструкции повторяют реальное поведение инструментов (см. ToolManager и *ToolHandler).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    // Какой инструмент раскрыт сейчас. null — все пункты свёрнуты.
    var expandedId by remember { mutableStateOf<String?>(null) }

    // Порядок: сначала навигация и выделение, затем построение, затем редактирование.
    val tools = listOf(
        HelpTool("hand", Icons.Default.PanTool, stringResource(R.string.tool_hand), stringResource(R.string.help_tool_hand)),
        HelpTool("select", Icons.Default.AdsClick, stringResource(R.string.tool_select), stringResource(R.string.help_tool_select)),
        HelpTool("line", Icons.Default.Edit, stringResource(R.string.tool_line), stringResource(R.string.help_tool_line)),
        HelpTool("axis", Icons.Default.HorizontalRule, stringResource(R.string.tool_axis), stringResource(R.string.help_tool_axis)),
        HelpTool("rect", Icons.Default.Rectangle, stringResource(R.string.tool_rect), stringResource(R.string.help_tool_rect)),
        HelpTool("circle", Icons.Default.Circle, stringResource(R.string.tool_circle), stringResource(R.string.help_tool_circle)),
        HelpTool("sector", Icons.Default.PieChart, stringResource(R.string.tool_sector), stringResource(R.string.help_tool_sector)),
        HelpTool("arc", Icons.Default.ChangeHistory, stringResource(R.string.tool_arc), stringResource(R.string.help_tool_arc)),
        HelpTool("text", Icons.Default.TextFields, stringResource(R.string.tool_text), stringResource(R.string.help_tool_text)),
        HelpTool("dim", Icons.Default.Straighten, stringResource(R.string.tool_dim), stringResource(R.string.help_tool_dim)),
        HelpTool("leader", ArrowTopLeft, stringResource(R.string.tool_leader), stringResource(R.string.help_tool_leader)),
        HelpTool("fill", Icons.Default.FormatColorFill, stringResource(R.string.tool_fill), stringResource(R.string.help_tool_fill)),
        HelpTool("move", Icons.Default.OpenWith, stringResource(R.string.tool_move), stringResource(R.string.help_tool_move)),
        HelpTool("rotate", Icons.AutoMirrored.Filled.RotateRight, stringResource(R.string.tool_rotate), stringResource(R.string.help_tool_rotate)),
        HelpTool("mirror", Icons.Default.Flip, stringResource(R.string.tool_mirror), stringResource(R.string.help_tool_mirror)),
        HelpTool("stretch", ArrowMenuOpen, stringResource(R.string.tool_stretch), stringResource(R.string.help_tool_stretch)),
        HelpTool("offset", TabDuplicate, stringResource(R.string.tool_offset), stringResource(R.string.help_tool_offset)),
        HelpTool("extend", LineEnd, stringResource(R.string.tool_extend), stringResource(R.string.help_tool_extend)),
        HelpTool("erase", Icons.Default.AutoFixOff, stringResource(R.string.tool_eraser), stringResource(R.string.help_tool_eraser))
    )

    Scaffold(
        containerColor = HelpBodyColor,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HelpHeaderColor,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { WelcomeHeader() }
            items(tools, key = { it.id }) { tool ->
                HelpToolCard(
                    tool = tool,
                    expanded = expandedId == tool.id,
                    onClick = {
                        // Тап по открытому — закрыть; по другому — открыть только его.
                        expandedId = if (expandedId == tool.id) null else tool.id
                    }
                )
            }
        }
    }
}

/** Приветственный блок в самом верху списка. */
@Composable
private fun WelcomeHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.help_welcome_title),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.help_welcome_message),
            color = Color(0xFFEDEDED),
            fontSize = 14.sp
        )
    }
}

/**
 * Карточка-аккордеон одного инструмента.
 *
 * Верхняя строка (иконка + название + стрелка) всегда видна и кликабельна.
 * Инструкция появляется под ней через [AnimatedVisibility]; [animateContentSize]
 * на карточке обеспечивает плавный «разъезд» списка вниз.
 */
@Composable
private fun HelpToolCard(
    tool: HelpTool,
    expanded: Boolean,
    onClick: () -> Unit
) {
    // Стрелка разворачивается вниз при раскрытии.
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "helpArrowRotation"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HelpCardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = HelpHeaderColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = tool.title,
                    color = HelpCardTitleColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = HelpCardBodyColor,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(arrowRotation)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Text(
                    text = tool.description,
                    color = HelpCardBodyColor,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 12.dp, start = 40.dp)
                )
            }
        }
    }
}
