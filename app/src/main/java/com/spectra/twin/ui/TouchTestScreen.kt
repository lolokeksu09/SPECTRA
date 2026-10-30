package com.spectra.twin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun DiagnosticsScreen(onTouchTest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Ручные проверки", style = MaterialTheme.typography.headlineSmall)
        Text("Проверяйте работу компонентов самостоятельно.", color = Muted)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Касания экрана", style = MaterialTheme.typography.titleMedium)
                Text("Коснитесь каждой области сетки и проверьте, все ли они реагируют.",
                    style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onTouchTest, modifier = Modifier.testTag("start-touch-test")) { Text("Начать проверку") }
            }
        }
        Text("Результат остаётся на экране только во время проверки и не сохраняется в историю.",
            color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun TouchTestScreen(onClose: () -> Unit) {
    val marked = remember { mutableStateListOf<Int>() }
    BackHandler(onBack = onClose)
    Column(
        modifier = Modifier.fillMaxSize().background(Night).safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Проверка касаний", color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge)
        Text("Коснитесь каждой области отдельно.", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("Отмечено: ${marked.size} / 24", color = Ice,
            style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("touch-progress"))
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(6) { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(4) { column ->
                        val index = row * 4 + column
                        val selected = index in marked
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .background(if (selected) Ice else PanelUp, RoundedCornerShape(8.dp))
                                .border(1.dp, if (selected) Ice else Muted, RoundedCornerShape(8.dp))
                                .semantics {
                                    contentDescription = "Область ${index + 1}: ${if (selected) "отмечена" else "не отмечена"}"
                                }
                                .testTag("touch-cell-$index")
                                .clickable(role = Role.Button) { if (index !in marked) marked.add(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) Text("✓", color = Night, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
        Text(
            if (marked.size == 24) "Все области отмечены. Это не проверка каждого пикселя и краёв за пределами сетки."
            else "Неотмеченная область могла не получить касание. Края за пределами сетки не проверяются.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { marked.clear() }, modifier = Modifier.weight(1f).testTag("touch-reset")) {
                Text("Сбросить")
            }
            Button(onClick = onClose, modifier = Modifier.weight(1f).testTag("touch-close")) { Text("Готово") }
        }
    }
}
