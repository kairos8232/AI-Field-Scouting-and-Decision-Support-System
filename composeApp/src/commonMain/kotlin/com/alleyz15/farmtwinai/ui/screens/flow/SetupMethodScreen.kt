package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.runtime.Composable
import com.alleyz15.farmtwinai.domain.model.SetupMethod
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.OptionCard
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader

@Composable
fun SetupMethodScreen(
    onBack: () -> Unit,
    onMethodSelected: (SetupMethod) -> Unit,
) {
    AppScaffold(title = "Farm Setup", subtitle = "Choose a setup method", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "How do you want to create the farm twin?",
                body = "Start by mapping your farm boundary, then split it into lots and fill per-lot details. Documents and quick estimate are still available.",
            )
            OptionCard(
                title = "Upload land / soil documents",
                description = "Use land info, soil reports, or a farm sketch to prefill setup details later.",
                onClick = { onMethodSelected(SetupMethod.DOCUMENT) },
            )
            OptionCard(
                title = "Manual setup",
                description = "Enter farm details directly with flexible fields and 'I don't know' style support.",
                onClick = { onMethodSelected(SetupMethod.MANUAL) },
            )
            OptionCard(
                title = "Quick estimate setup",
                description = "Start with a minimal form and mocked defaults for a fast demo path.",
                onClick = { onMethodSelected(SetupMethod.QUICK_ESTIMATE) },
            )
        }
    }
}
