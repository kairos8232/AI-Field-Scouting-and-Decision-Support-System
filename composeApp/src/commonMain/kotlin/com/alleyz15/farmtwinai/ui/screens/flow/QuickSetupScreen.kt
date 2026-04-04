package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.runtime.Composable
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.LabeledInput
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader

@Composable
fun QuickSetupScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    AppScaffold(title = "Quick Estimate", subtitle = "Fast-start farm setup", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Enter only the essentials",
                body = "The app will later infer defaults from crop type, region, and rough field conditions. For now this path uses mocked defaults after submission.",
            )
            LabeledInput("Crop type", initialValue = "Tomato")
            LabeledInput("Planting date", initialValue = "2026-03-20")
            LabeledInput("Rough field size", initialValue = "Around 2 acres")
            LabeledInput("Approximate location", initialValue = "Kedah")
            DualActionButtons(
                primaryLabel = "Create Quick Farm Twin",
                onPrimary = onContinue,
                secondaryLabel = "Use Suggested Defaults",
                onSecondary = onContinue,
            )
        }
    }
}
