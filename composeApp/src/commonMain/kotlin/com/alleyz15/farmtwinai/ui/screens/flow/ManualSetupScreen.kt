package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.runtime.Composable
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.LabeledInput
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader

@Composable
fun ManualSetupScreen(
    plantingDate: String,
    onPlantingDateChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    AppScaffold(title = "Manual Setup", subtitle = "Flexible farm entry", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Build the farm profile",
                body = "These fields are placeholders for Phase 1. Keep wording flexible so users can continue even if they do not know every value.",
            )
            LabeledInput("Farm name", placeholder = "e.g. Seri Padi Plot A")
            LabeledInput("Crop type", placeholder = "e.g. Tomato")
            LabeledInput(
                label = "Planting date",
                value = plantingDate,
                onValueChange = onPlantingDateChange,
                placeholder = "YYYY-MM-DD",
            )
            LabeledInput("Location", placeholder = "e.g. Kedah")
            LabeledInput("Field size", placeholder = "e.g. 1.8 acres")
            LabeledInput("Irrigation source", placeholder = "e.g. Drip irrigation / I don't know")
            LabeledInput("Sunlight condition", placeholder = "e.g. Full sun / partly shaded")
            LabeledInput("Drainage condition", placeholder = "e.g. Mixed drainage / I don't know")
            LabeledInput("Soil pH if known", placeholder = "e.g. 6.4 / I don't know")
            DualActionButtons(
                primaryLabel = "Create Farm Twin",
                onPrimary = onContinue,
                secondaryLabel = "Use Suggested Demo Values",
                onSecondary = onContinue,
            )
        }
    }
}
