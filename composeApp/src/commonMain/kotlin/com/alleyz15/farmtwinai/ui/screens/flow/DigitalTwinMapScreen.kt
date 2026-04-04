package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.domain.model.ZoneInfo
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader
import com.alleyz15.farmtwinai.ui.components.StatusBadge
import com.alleyz15.farmtwinai.ui.theme.style

@Composable
fun DigitalTwinMapScreen(
    zones: List<ZoneInfo>,
    onBack: () -> Unit,
    onZoneSelected: (String) -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    AppScaffold(title = "Digital Twin Map", subtitle = "Zone-based field view", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Zone monitoring overview",
                body = "This simplified 2D grid represents the farm twin. Each zone shows crop health and suitability using mocked status data.",
            )
            zones.chunked(2).forEach { rowZones ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowZones.forEach { zone ->
                        val status = zone.status.style()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(status.container)
                                .clickable { onZoneSelected(zone.id) }
                                .padding(14.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(zone.zoneName, style = MaterialTheme.typography.titleMedium, color = status.content)
                                Text(zone.cropName, style = MaterialTheme.typography.bodyMedium, color = status.content)
                                Text("Suitability: ${zone.suitability.name.lowercase().replaceFirstChar { it.uppercase() }}", color = status.content)
                                StatusBadge(status = status)
                            }
                        }
                    }
                }
            }
            DualActionButtons(
                primaryLabel = "Open Timeline",
                onPrimary = onOpenTimeline,
                secondaryLabel = "Open History",
                onSecondary = onOpenHistory,
            )
        }
    }
}
