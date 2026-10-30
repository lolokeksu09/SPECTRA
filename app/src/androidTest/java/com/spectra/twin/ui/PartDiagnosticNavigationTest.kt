package com.spectra.twin.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spectra.twin.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PartDiagnosticNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun returnsToSelectedPartAfterOpeningItsCheck() {
        compose.onNodeWithText("Выбрать деталь из списка").performScrollTo().performClick()
        compose.onNodeWithText("Экран").performClick()
        compose.onNodeWithTag("part-test-touch").performScrollTo().performClick()
        compose.onNodeWithText("Проверка касаний").assertIsDisplayed()
        compose.onNodeWithText("Проблема").performClick()
        compose.onNodeWithTag("part-test-touch").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Касания экрана · результат пользователя: Есть проблема").performScrollTo().assertIsDisplayed()
    }

    @Test fun cameraCardOpensCameraSectionOfPassport() {
        compose.onNodeWithText("Выбрать деталь из списка").performScrollTo().performClick()
        compose.onNodeWithText("Верхняя задняя камера").performClick()
        compose.onNodeWithTag("open-camera-passport").performScrollTo().performClick()
        compose.onNodeWithText("Камеры, доступные Android").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("passport-show-all").assertIsDisplayed()
    }
}
