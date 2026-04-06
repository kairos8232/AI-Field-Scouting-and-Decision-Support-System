package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.alleyz15.farmtwinai.auth.AuthUser
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader

@Composable
fun AuthScreen(
    onBack: () -> Unit,
    onAuthSuccess: (AuthUser) -> Unit,
    onUseDemo: () -> Unit,
) {
    val loginMode = "login"
    val signupMode = "signup"

    var mode by remember { mutableStateOf(loginMode) }
    var authMessage by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    AppScaffold(title = "Account Access", subtitle = "Email authentication", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Login or sign up",
                body = "Use your email and password to login or create a new account for this demo.",
            )

            Text(
                text = if (mode == loginMode) "Mode: Login" else "Mode: Sign up",
                style = MaterialTheme.typography.labelLarge,
            )

            OutlinedButton(
                onClick = {
                    mode = if (mode == loginMode) signupMode else loginMode
                    authMessage = null
                },
            ) {
                Text(if (mode == loginMode) "Switch to Sign up" else "Switch to Login")
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                placeholder = { Text("farmer@example.com") },
                singleLine = true,
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                placeholder = { Text("Minimum 6 characters") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )

            if (mode == signupMode) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display name") },
                    placeholder = { Text("How should we call you?") },
                    singleLine = true,
                )
            }

            Button(
                onClick = {
                    authMessage = null

                    val trimmedEmail = email.trim()
                    val trimmedPassword = password.trim()
                    val trimmedDisplayName = displayName.trim()

                    when {
                        trimmedEmail.isBlank() || !trimmedEmail.contains("@") -> {
                            authMessage = "Please enter a valid email address."
                        }

                        trimmedPassword.length < 6 -> {
                            authMessage = "Password must be at least 6 characters."
                        }

                        mode == signupMode && trimmedDisplayName.isBlank() -> {
                            authMessage = "Display name is required for sign up."
                        }

                        else -> {
                            onAuthSuccess(
                                AuthUser(
                                    userId = trimmedEmail.lowercase(),
                                    email = trimmedEmail,
                                    displayName = if (mode == signupMode) trimmedDisplayName else null,
                                    idToken = null,
                                )
                            )
                        }
                    }
                },
            ) {
                Text(if (mode == loginMode) "Login with Email" else "Sign Up with Email")
            }

            if (authMessage != null) {
                Text(
                    text = authMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            DualActionButtons(
                primaryLabel = "Use Demo Mode",
                onPrimary = onUseDemo,
                secondaryLabel = "Back",
                onSecondary = onBack,
            )
        }
    }
}
