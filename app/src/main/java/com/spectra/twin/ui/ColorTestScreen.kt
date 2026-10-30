package com.spectra.twin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun ColorTestScreen(onResult: (Boolean) -> Unit, onClose: () -> Unit) {
    val colors = listOf(
        "Чёрный" to Color.Black, "Белый" to Color.White, "Красный" to Color.Red,
        "Зелёный" to Color.Green, "Синий" to Color.Blue, "Серый" to Color(0xFF808080)
    )
    var index by remember { mutableIntStateOf(0) }
    val textColor = if (index == 1 || index == 3 || index == 5) Color.Black else Color.White
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(colors[index].second).safeDrawingPadding().padding(16.dp)) {
        Text("Цвет ${index + 1} / ${colors.size}: ${colors[index].first}", color = textColor,
            style = MaterialTheme.typography.titleLarge)
        Text("Посмотрите, нет ли заметных точек или пятен.", color = textColor,
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Button(onClick = { index = (index + 1).coerceAtMost(colors.lastIndex) },
            enabled = index < colors.lastIndex, modifier = Modifier.fillMaxWidth().testTag("next-color")) {
            Text("Следующий цвет")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onResult(false); onClose() }, modifier = Modifier.weight(1f)) { Text("Проблема") }
            Button(onClick = { onResult(true); onClose() }, enabled = index == colors.lastIndex,
                modifier = Modifier.weight(1f)) { Text("Работает") }
        }
        TextButton(onClick = onClose) { Text("Закрыть", color = textColor) }
    }
}
