package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.runtime.Composable
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.LabeledInput
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader

@Composable
fun AuthScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    AppScaffold(title = "Account Access", subtitle = "Placeholder authentication", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Login or sign up",
                body = "Use simple placeholder fields for the hackathon demo. No real authentication is connected yet.",
            )
            LabeledInput("Email", placeholder = "farmer@example.com")
            LabeledInput("Password", placeholder = "Enter password")
            LabeledInput("Display name", placeholder = "Optional for sign up")
            DualActionButtons(
                primaryLabel = "Continue",
                onPrimary = onContinue,
                secondaryLabel = "Use Demo Credentials",
                onSecondary = onContinue,
            )
        }
    }
}
