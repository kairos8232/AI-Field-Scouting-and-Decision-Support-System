package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.domain.model.TimelineDay
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.InfoCard
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader
import com.alleyz15.farmtwinai.ui.components.StatusBadge
import com.alleyz15.farmtwinai.ui.components.TimelineChip
import com.alleyz15.farmtwinai.ui.theme.style

@Composable
fun TimelineScreen(
    days: List<TimelineDay>,
    selectedDay: TimelineDay,
    healthScore: Int,
    onBack: () -> Unit,
    onSelectDay: (Int) -> Unit,
) {
    AppScaffold(title = "Daily Timeline", subtitle = "Planting date to crop cycle", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Expected daily journey",
                body = "The timeline is day-by-day because the core concept is comparing actual progress to the ideal simulation over time.",
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                days.forEach { day ->
                    TimelineChip(
                        title = "Day ${day.dayNumber}",
                        selected = day.dayNumber == selectedDay.dayNumber,
                        onClick = { onSelectDay(day.dayNumber) },
                    )
                }
            }
            StatusBadge(selectedDay.status.style())
            InfoCard("Health score", "$healthScore/100")
            InfoCard("Expected growth range", selectedDay.expectedGrowthRange)
            InfoCard("Expected crop stage", selectedDay.expectedStage)
            InfoCard("Notes", selectedDay.notes)
        }
    }
}
