package com.kleadingsolutions.app.feature.login.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Simple Compose LoginScreen that binds to a LoginViewModel (state + events).
 *
 * Adds deterministic test tags so JVM Compose UI tests can query nodes reliably.
 */
@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onRememberChanged: (Boolean) -> Unit,
    onLoginClicked: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = uiState.email,
            onValueChange = onEmailChanged,
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emailField")
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChanged,
            label = { Text("Password") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("passwordField")
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row {
                Checkbox(
                    checked = uiState.rememberMe,
                    onCheckedChange = onRememberChanged,
                    modifier = Modifier.testTag("rememberCheckbox")
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Remember me")
            }
            Button(
                onClick = onLoginClicked,
                enabled = uiState.isLoginEnabled(),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("loginButton")
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Login")
            }
        }
        uiState.errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = msg,
                color = MaterialTheme.colors.error,
                modifier = Modifier.testTag("errorText")
            )
        }
    }
}