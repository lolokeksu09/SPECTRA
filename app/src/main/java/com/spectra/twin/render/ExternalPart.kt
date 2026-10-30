package com.spectra.twin.render

/** Only names from bundled SPECTRA assets are mapped; unknown objects stay unselected. */
enum class ExternalPart(val title: String, val description: String) {
    SCREEN("Экран", "Отображает изображение и реагирует на касания."),
    SELFIE("Фронтальная камера", "Камера в верхней части экрана."),
    MAIN_CAMERA("Основная камера", "Основная задняя камера телефона."),
    ULTRAWIDE("Сверхширокоугольная камера", "Задняя камера для широкого угла и макросъёмки."),
    FLICKER("Датчик мерцания", "Помогает камере учитывать мерцание освещения."),
    FLASH("Вспышка", "Подсвечивает сцену при съёмке."),
    USB("USB-C", "Разъём для зарядки и передачи данных."),
    SIM("SIM-лоток", "Лоток для SIM-карт."),
    SPEAKER("Динамик", "Воспроизводит звук."),
    MICROPHONE("Микрофон", "Принимает звук."),
    POWER("Кнопка питания", "Включает экран и управляет питанием."),
    VOLUME("Клавиши громкости", "Регулируют громкость."),
    SMART("Боковая клавиша", "Дополнительная клавиша; действие зависит от прошивки."),
    BACK("Задняя крышка", "Внешняя поверхность корпуса."),
    FRAME("Рамка", "Боковая часть корпуса телефона.");

    companion object {
        fun fromNode(name: String?): ExternalPart? {
            val n = name?.lowercase() ?: return null
            return when {
                n.contains("front_punch_hole") -> SELFIE
                n.contains("front_glass") -> SCREEN
                n.contains("primary_") || n.contains("main_hairline") -> MAIN_CAMERA
                n.contains("ultrawide") || n.contains("lower_optic_tick") ||
                    (n.take(3).toIntOrNull()?.let { it in 25..33 } == true && n.contains("bottom_")) -> ULTRAWIDE
                n.contains("flicker_sensor") || n.contains("sensor_machined") ||
                    n.contains("top_right_") -> FLICKER
                n.contains("flash_") || n.contains("lower_right_") || n.contains("_led_") -> FLASH
                n.contains("usb") -> USB
                n.contains("sim_") -> SIM
                n.contains("speaker_") || n.contains("earpiece_grille") -> SPEAKER
                n.contains("mic_") -> MICROPHONE
                n.contains("power_button") -> POWER
                n.contains("volume_button") -> VOLUME
                n.contains("smart_key") -> SMART
                n.contains("engraved_legion") -> BACK
                n.contains("chassis") || n.contains("perimeter_trim") -> FRAME
                else -> null
            }
        }
    }
}
