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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import com.alleyz15.farmtwinai.ui.components.AuroraBackground
import com.alleyz15.farmtwinai.ui.components.OnboardingAdaptiveWidth
import com.alleyz15.farmtwinai.ui.theme.CardDark
import com.alleyz15.farmtwinai.ui.theme.Leaf400
import com.alleyz15.farmtwinai.ui.theme.Mint200
import com.alleyz15.farmtwinai.ui.theme.Sand100
import com.alleyz15.farmtwinai.auth.AuthUser
import farmtwinai.composeapp.generated.resources.Res
import farmtwinai.composeapp.generated.resources.ic_farm_bg_dark
import farmtwinai.composeapp.generated.resources.ic_farm_bg_light
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.Image
import com.alleyz15.farmtwinai.ui.theme.isAppDarkTheme
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height

@Composable
fun AuthScreen(
    isLogin: Boolean,
    onBack: () -> Unit,
    onSwitchMode: () -> Unit,
    onSubmit: suspend (isLogin: Boolean, email: String, password: String, displayName: String?) -> AuthUser,
    onGoogleAuth: (suspend () -> AuthUser)? = null,
    onAuthenticated: (AuthUser) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var authMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
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
        AuroraBackground()
        
        val darkTheme = isAppDarkTheme()
        val bgResource = if (darkTheme) Res.drawable.ic_farm_bg_dark else Res.drawable.ic_farm_bg_light
        val overlayTone = if (darkTheme) Color(0xFF0C1911) else MaterialTheme.colorScheme.surface
        
        Image(
            painter = painterResource(bgResource),
            contentDescription = "Farm Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            overlayTone.copy(alpha = if (darkTheme) 0.5f else 0.35f),
                            overlayTone.copy(alpha = if (darkTheme) 0.9f else 0.72f),
                            overlayTone
                        )
                    )
                )
        )

        OnboardingAdaptiveWidth { maxContentWidth, _ ->
            AnimatedVisibility(
                visible = showCard,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(400),
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300),
                ) + fadeOut(animationSpec = tween(200)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = maxContentWidth)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        val authFieldColors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedLabelColor = Mint200,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.74f),
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.42f),
                            cursorColor = Leaf400,
                            focusedBorderColor = Leaf400,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.32f),
                        )

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            IconButton(
                                onClick = { pendingBack = true },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            ) {
                                Icon(
                                    imageVector = ArrowBackIcon,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                )
                            }

                            Text(
                                text = if (isLogin) "Login" else "Sign Up",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        Text(
                            text = if (isLogin) {
                                "Welcome back. Enter your account details to continue."
                            } else {
                                "Create your account to start building your digital farm workflow."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                            modifier = Modifier.padding(bottom = 8.dp)
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
                                if (isSubmitting) {
                                    return@Button
                                }
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
                                    else -> {
                                        scope.launch {
                                            isSubmitting = true
                                            runCatching {
                                                onSubmit(
                                                    isLogin,
                                                    trimmedEmail,
                                                    trimmedPassword,
                                                    if (isLogin) null else trimmedDisplayName,
                                                )
                                            }.onSuccess { user ->
                                                authMessage = null
                                                onAuthenticated(user)
                                            }.onFailure { error ->
                                                authMessage = error.message ?: "Unable to authenticate. Please try again."
                                            }
                                            isSubmitting = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp),
                            enabled = !isSubmitting,
                        ) {
                            Text(
                                text = if (isSubmitting) "Please wait..." else if (isLogin) "Login" else "Create Account",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (isSubmitting) {
                                    return@OutlinedButton
                                }

                                val googleHandler = onGoogleAuth
                                if (googleHandler == null) {
                                    authMessage = "Google Sign-In is not configured yet."
                                    return@OutlinedButton
                                }

                                authMessage = null
                                scope.launch {
                                    isSubmitting = true
                                    runCatching {
                                        googleHandler()
                                    }.onSuccess { user ->
                                        authMessage = null
                                        onAuthenticated(user)
                                    }.onFailure { error ->
                                        authMessage = error.message ?: "Google Sign-In failed. Please try again."
                                    }
                                    isSubmitting = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled = !isSubmitting && onGoogleAuth != null,
                        ) {
                            Text(
                                if (onGoogleAuth == null) {
                                    "Google Sign-In (Coming soon)"
                                } else if (isLogin) {
                                    "Continue with Google"
                                } else {
                                    "Sign up with Google"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
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
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled = !isSubmitting,
                        ) {
                            Text(if (isLogin) "Sign Up instead" else "Login instead", style = MaterialTheme.typography.titleMedium)
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
