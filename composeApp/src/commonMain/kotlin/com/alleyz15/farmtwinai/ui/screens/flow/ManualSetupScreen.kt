package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.runtime.Composable
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.LabeledInput
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader

@Composable
fun ManualSetupScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    AppScaffold(title = "Manual Setup", subtitle = "Flexible farm entry", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Build the farm profile",
                body = "These fields are placeholders for Phase 1. Keep wording flexible so users can continue even if they do not know every value.",
            )
            LabeledInput("Farm name", initialValue = "Seri Padi Plot A")
            LabeledInput("Crop type", initialValue = "Tomato")
            LabeledInput("Planting date", initialValue = "2026-03-20")
            LabeledInput("Location", initialValue = "Pendang, Kedah")
            LabeledInput("Field size", initialValue = "1.8 acres")
            LabeledInput("Irrigation source", initialValue = "Drip irrigation / I don't know")
            LabeledInput("Sunlight condition", initialValue = "Full sun / partly shaded")
            LabeledInput("Drainage condition", initialValue = "Mixed drainage / I don't know")
            LabeledInput("Soil pH if known", initialValue = "6.4 / I don't know")
            DualActionButtons(
                primaryLabel = "Create Farm Twin",
                onPrimary = onContinue,
                secondaryLabel = "Use Suggested Demo Values",
                onSecondary = onContinue,
            )
        }
    }
}
