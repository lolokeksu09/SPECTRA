package com.spectra.twin.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable
internal fun CameraAppTest(onResult: (Boolean) -> Unit) {
    var returned by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        returned = true
    }
    Text("Откройте приложение камеры, проверьте изображение и переключите камеры вручную. Вернитесь сюда и укажите результат.",
        color = Muted)
    Button(onClick = {
        try {
            error = null
            launcher.launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        } catch (_: ActivityNotFoundException) {
            error = "Приложение камеры недоступно"
        } catch (_: SecurityException) {
            error = "Не удалось открыть приложение камеры"
        }
    }) { Text("Открыть приложение камеры") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Text("SPECTRA не получает фотографию и не определяет результат автоматически.",
        color = Muted, style = MaterialTheme.typography.bodySmall)
    ManualResultButtons(enabled = returned, onResult = onResult)
}
