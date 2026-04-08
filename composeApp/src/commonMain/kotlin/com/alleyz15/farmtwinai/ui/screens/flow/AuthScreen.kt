package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.ui.components.OnboardingAdaptiveWidth
import com.alleyz15.farmtwinai.ui.components.OnboardingBackground
import com.alleyz15.farmtwinai.ui.theme.CardDark
import com.alleyz15.farmtwinai.ui.theme.Leaf400
import com.alleyz15.farmtwinai.ui.theme.Mint200
import com.alleyz15.farmtwinai.ui.theme.Sand100
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(
    isLogin: Boolean,
    onBack: () -> Unit,
    onSwitchMode: () -> Unit,
    onContinue: () -> Unit,
) {
    var authMessage by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showCard by remember { mutableStateOf(false) }
    var pendingBack by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showCard = true
    }

    LaunchedEffect(pendingBack) {
        if (pendingBack) {
            showCard = false
            delay(260)
            onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingBackground(overlayAlpha = 0.52f)

        OnboardingAdaptiveWidth { maxContentWidth, _ ->
            AnimatedVisibility(
                visible = showCard,
                enter = fadeIn(animationSpec = tween(320)) +
                    slideInVertically(
                        initialOffsetY = { it / 8 },
                        animationSpec = tween(360),
                    ) +
                    scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(360),
                    ),
                exit = fadeOut(animationSpec = tween(220)) +
                    slideOutVertically(
                        targetOffsetY = { it / 10 },
                        animationSpec = tween(240),
                    ) +
                    scaleOut(
                        targetScale = 0.97f,
                        animationSpec = tween(240),
                    ),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = maxContentWidth)
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = CardDark.copy(alpha = 0.92f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        val authFieldColors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Sand100,
                            unfocusedTextColor = Sand100,
                            focusedLabelColor = Mint200,
                            unfocusedLabelColor = Sand100.copy(alpha = 0.74f),
                            focusedPlaceholderColor = Sand100.copy(alpha = 0.52f),
                            unfocusedPlaceholderColor = Sand100.copy(alpha = 0.42f),
                            cursorColor = Leaf400,
                            focusedBorderColor = Leaf400,
                            unfocusedBorderColor = Sand100.copy(alpha = 0.32f),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { pendingBack = true },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.08f)),
                            ) {
                                Icon(
                                    imageVector = ArrowBackIcon,
                                    contentDescription = "Back",
                                    tint = Sand100,
                                )
                            }
                        }

                        Text(
                            text = if (isLogin) "Login" else "Sign Up",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Sand100,
                        )
                        Text(
                            text = if (isLogin) {
                                "Welcome back. Enter your account details to continue."
                            } else {
                                "Create your account to start building your digital farm workflow."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Sand100.copy(alpha = 0.78f),
                        )

                        if (!isLogin) {
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text("Full Name") },
                                placeholder = { Text("Your full name") },
                                singleLine = true,
                                colors = authFieldColors,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            placeholder = { Text("farmer@example.com") },
                            singleLine = true,
                            colors = authFieldColors,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            placeholder = { Text("Minimum 6 characters") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = authFieldColors,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (!isLogin) {
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text("Confirm Password") },
                                placeholder = { Text("Re-enter your password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                colors = authFieldColors,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Button(
                            onClick = {
                                authMessage = null

                                val trimmedEmail = email.trim()
                                val trimmedPassword = password.trim()
                                val trimmedDisplayName = displayName.trim()
                                val trimmedConfirmPassword = confirmPassword.trim()

                                when {
                                    trimmedEmail.isBlank() || !trimmedEmail.contains("@") -> {
                                        authMessage = "Please enter a valid email address."
                                    }

                                    trimmedPassword.length < 6 -> {
                                        authMessage = "Password must be at least 6 characters."
                                    }

                                    !isLogin && trimmedDisplayName.isBlank() -> {
                                        authMessage = "Full name is required for sign up."
                                    }

                                    !isLogin && trimmedConfirmPassword != trimmedPassword -> {
                                        authMessage = "Passwords do not match."
                                    }

                                    else -> onContinue()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (isLogin) "Login" else "Create Account")
                        }

                        if (authMessage != null) {
                            Text(
                                text = authMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                authMessage = null
                                onSwitchMode()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (isLogin) "Sign Up instead" else "Login instead")
                        }
                    }
                }
            }
        }
    }
}

private val ArrowBackIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ArrowBack",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(20f, 11f)
            lineTo(7.83f, 11f)
            lineToRelative(5.59f, -5.59f)
            lineTo(12f, 4f)
            lineToRelative(-8f, 8f)
            lineToRelative(8f, 8f)
            lineToRelative(1.41f, -1.41f)
            lineTo(7.83f, 13f)
            lineTo(20f, 13f)
            close()
        }
    }.build()
