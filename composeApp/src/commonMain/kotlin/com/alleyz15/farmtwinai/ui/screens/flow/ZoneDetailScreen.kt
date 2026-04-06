package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.runtime.Composable
import com.alleyz15.farmtwinai.domain.model.ZoneInfo
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.InfoCard
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader
import com.alleyz15.farmtwinai.ui.components.StatusBadge
import com.alleyz15.farmtwinai.ui.theme.style

@Composable
fun ZoneDetailScreen(
    zone: ZoneInfo,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
) {
    AppScaffold(title = zone.zoneName, subtitle = "Zone detail", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "${zone.cropName} performance snapshot",
                body = "Use this screen to compare the simulated target with the mocked actual field condition in the selected zone.",
            )
            StatusBadge(zone.status.style())
            InfoCard("Expected growth range", zone.expectedGrowthRange)
            InfoCard("Actual condition", zone.actualConditionSummary)
            InfoCard("Issue level", zone.issueLevel)
            InfoCard("Suggested next action", zone.suggestedAction)
            DualActionButtons(
                primaryLabel = "Open AI Consultation",
                onPrimary = onOpenChat,
            )
        }
    }
}
