package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.runtime.Composable
import com.alleyz15.farmtwinai.domain.model.AppMode
import com.alleyz15.farmtwinai.domain.model.FarmTwinSnapshot
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.InfoCard
import com.alleyz15.farmtwinai.ui.components.MetricRow
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader

@Composable
fun DashboardScreen(
    snapshot: FarmTwinSnapshot,
    selectedMode: AppMode,
    onOpenMap: () -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    AppScaffold(
        title = "Farm Dashboard",
        subtitle = "${snapshot.farm.farmName} • ${selectedMode.name.replace('_', ' ')}",
    ) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Expected vs actual overview",
                body = "This dashboard communicates the digital twin concept: track the simulated ideal journey and highlight deviations that need attention.",
            )
            MetricRow(
                leftTitle = "Crop",
                leftValue = snapshot.farm.cropName,
                rightTitle = "Current day",
                rightValue = "Day ${snapshot.cropSummary.currentDay}",
            )
            MetricRow(
                leftTitle = "Expected growth",
                leftValue = snapshot.cropSummary.expectedGrowthRange,
                rightTitle = "Health score",
                rightValue = "${snapshot.cropSummary.currentFarmHealthScore}/100",
            )
            MetricRow(
                leftTitle = "Urgent zones",
                leftValue = snapshot.cropSummary.urgentZones.toString(),
                rightTitle = "Stage",
                rightValue = snapshot.cropSummary.expectedStage,
            )
            InfoCard(
                title = "Latest recommendation",
                value = snapshot.cropSummary.latestRecommendation,
                supporting = "AI consultation is triggered when actual field conditions diverge from the expected model.",
            )
            DualActionButtons(
                primaryLabel = "Open Digital Twin Map",
                onPrimary = onOpenMap,
                secondaryLabel = "Open AI Chat",
                onSecondary = onOpenChat,
            )
            DualActionButtons(
                primaryLabel = "Open Daily Timeline",
                onPrimary = onOpenTimeline,
                secondaryLabel = "View History",
                onSecondary = onOpenHistory,
            )
        }
    }
}
