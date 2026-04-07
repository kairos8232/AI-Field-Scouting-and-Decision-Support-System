package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    onBack: () -> Unit,
    onSelectDay: (Int) -> Unit,
) {
    val selectedIndex = days.indexOfFirst { it.dayNumber == selectedDay.dayNumber }.coerceAtLeast(0)
    val hasPrevious = selectedIndex > 0
    val hasNext = selectedIndex in 0 until days.lastIndex

    AppScaffold(
        title = "Daily Timeline",
        subtitle = "Planting date to crop cycle",
        onBack = onBack,
        floatingFooter = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                days.forEach { day ->
                    val isSelected = day.dayNumber == selectedDay.dayNumber
                    
                    Box(
                        modifier = Modifier
                            .width(280.dp)
                            .clickable { onSelectDay(day.dayNumber) }
                    ) {
                        InfoCard(
                            title = "Day ${day.dayNumber}",
                            value = "${day.expectedStage} • ${day.expectedGrowthRange}",
                            supporting = day.notes,
                            modifier = if (isSelected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                            } else Modifier
                        )
                    }
                }
            }
        }
    ) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Expected daily journey",
                body = "Use bottom controls to move across stages quickly.",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Image Placeholder",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(selectedDay.status.style())
        }
    }
}
