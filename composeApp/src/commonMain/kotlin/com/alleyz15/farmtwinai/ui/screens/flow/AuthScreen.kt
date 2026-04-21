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
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.alleyz15.farmtwinai.auth.AuthUser
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader

@Composable
fun AuthScreen(
    isLogin: Boolean,
    onBack: () -> Unit,
    onSwitchMode: () -> Unit,
    onAuthSuccess: (AuthUser) -> Unit,
) {
    var authMessage by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    AppScaffold(title = "Account Access", subtitle = "Email authentication", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = if (isLogin) "Login" else "Sign up",
                body = if (isLogin) "Use your email and password to login." else "Create a new account with your email and password.",
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                placeholder = { Text("farmer@example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                placeholder = { Text("Minimum 6 characters") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            if (!isLogin) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display name") },
                    placeholder = { Text("How should we call you?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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

                        !isLogin && trimmedDisplayName.isBlank() -> {
                            authMessage = "Display name is required for sign up."
                        }

                        else -> {
                            onAuthSuccess(
                                AuthUser(
                                    userId = trimmedEmail.lowercase(),
                                    email = trimmedEmail,
                                    displayName = if (!isLogin) trimmedDisplayName else null,
                                    idToken = null,
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isLogin) "Login" else "Sign Up")
            }

            if (authMessage != null) {
                Text(
                    text = authMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedButton(
                onClick = onSwitchMode,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isLogin) "Sign up instead" else "Login instead")
            }
        }
    }
}
