package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.runtime.Composable
import com.alleyz15.farmtwinai.domain.model.AppMode
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.OptionCard
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader

@Composable
fun UserSituationScreen(
    onBack: () -> Unit,
    onSituationSelected: (AppMode) -> Unit,
) {
    AppScaffold(title = "Your Situation", subtitle = "Choose the starting mode", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "How are you starting?",
                body = "Phase 1 supports planning mode, live monitoring mode, and a direct demo shortcut.",
            )
            OptionCard(
                title = "I have not planted yet",
                description = "Start in planning mode and set up the field before planting.",
                onClick = { onSituationSelected(AppMode.PLANNING) },
            )
            OptionCard(
                title = "I already planted",
                description = "Start in live monitoring mode and compare actual progress against the expected simulation.",
                onClick = { onSituationSelected(AppMode.LIVE_MONITORING) },
            )
            OptionCard(
                title = "Try demo flow",
                description = "Skip setup and load the mocked dashboard immediately.",
                onClick = { onSituationSelected(AppMode.DEMO) },
            )
        }
    }
}
