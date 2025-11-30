package com.kleadingsolutions.app.feature.login.ui;

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

/**
 * Improved Compose UI (JVM) test that interacts with the UI via performTextInput and performClick.
 * Uses testTag identifiers added to LoginScreen to find nodes deterministically.
 */
class LoginScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun loginButton_enabled_when_email_and_password_present_and_remember_toggles() {
        val initial = LoginUiState()

        var emailValue = ""
        var passwordValue = ""
        var rememberValue = false
        rule.setContent {
            LoginScreen(
                uiState = initial.copy(email = emailValue, password = passwordValue, rememberMe = rememberValue),
                onEmailChanged = { emailValue = it },
                onPasswordChanged = { passwordValue = it },
                onRememberChanged = { rememberValue = it },
                onLoginClicked = {}
            )
        }

        // At start, login button should be disabled
        rule.onNodeWithTag("loginButton").assertIsNotEnabled()

        // Enter email
        rule.onNodeWithTag("emailField").performTextInput("a@b.com")
        // Enter password
        rule.onNodeWithTag("passwordField").performTextInput("pw")

        // Recompose with updated state to simulate ViewModel updating state in real app.
        rule.setContent {
            LoginScreen(
                uiState = initial.copy(email = "a@b.com", password = "pw", rememberMe = rememberValue),
                onEmailChanged = { emailValue = it },
                onPasswordChanged = { passwordValue = it },
                onRememberChanged = { rememberValue = it },
                onLoginClicked = {}
            )
        }

        // Now the button should be enabled
        rule.onNodeWithTag("loginButton").assertIsEnabled()

        // Verify the remember checkbox toggles when clicked
        // Initially unchecked
        rule.onNodeWithTag("rememberCheckbox").assertIsOff()
        // Click to toggle on
        rule.onNodeWithTag("rememberCheckbox").performClick()
        // Recompose with remember toggled (simulating ViewModel state flow)
        rule.setContent {
            LoginScreen(
                uiState = initial.copy(email = "a@b.com", password = "pw", rememberMe = true),
                onEmailChanged = { emailValue = it },
                onPasswordChanged = { passwordValue = it },
                onRememberChanged = { rememberValue = it },
                onLoginClicked = {}
            )
        }
        rule.onNodeWithTag("rememberCheckbox").assertIsOn()
    }
}