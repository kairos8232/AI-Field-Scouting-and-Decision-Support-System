package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.domain.model.AppMode
import com.alleyz15.farmtwinai.domain.model.FarmTwinSnapshot
import com.alleyz15.farmtwinai.domain.model.LotSectionDraft
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.HomeTab
import com.alleyz15.farmtwinai.ui.components.HomeTabBar
import com.alleyz15.farmtwinai.ui.components.InfoCard
import com.alleyz15.farmtwinai.ui.components.MetricRow
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader

@Composable
fun DashboardScreen(
    snapshot: FarmTwinSnapshot,
    selectedMode: AppMode,
    lotSections: List<LotSectionDraft>,
    onOpenTimeline: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenHistory: () -> Unit,
    isTabBarVisible: Boolean = false,
    onSelectDashboardTab: (() -> Unit)? = null,
    onSelectMeTab: (() -> Unit)? = null,
) {
    var selectedLotId by remember(lotSections) {
        mutableStateOf(lotSections.firstOrNull()?.id.orEmpty())
    }
    val selectedLot = lotSections.firstOrNull { it.id == selectedLotId } ?: lotSections.firstOrNull()
    val hasMultipleLots = lotSections.size > 1

    AppScaffold(
        title = "Farm Dashboard",
        subtitle = "${snapshot.farm.farmName} • ${selectedMode.name.replace('_', ' ')}",
        floatingFooter = if (isTabBarVisible && onSelectDashboardTab != null && onSelectMeTab != null) {
            {
                HomeTabBar(
                    selectedTab = HomeTab.DASHBOARD,
                    onSelectDashboard = onSelectDashboardTab,
                    onSelectMe = onSelectMeTab,
                )
            }
        } else {
            null
        },
    ) { _ ->
        ScreenColumn {
            if (lotSections.isNotEmpty()) {
                SectionHeader(
                    title = "Farm lot map view",
                    body = if (hasMultipleLots) {
                        "Tap a lot below to view that lot's details."
                    } else {
                        "Single-lot setup detected. Showing full lot details."
                    },
                )
                LotMapPreview(
                    lots = lotSections,
                    selectedLotId = selectedLot?.id,
                    onLotSelected = { selectedLotId = it },
                )

                if (hasMultipleLots) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        lotSections.forEach { lot ->
                            val isSelected = selectedLot?.id == lot.id
                            Box(
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(999.dp),
                                    )
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(999.dp),
                                    )
                                    .clickable { selectedLotId = lot.id }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(text = lot.name, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                selectedLot?.let { lot ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                            .clickable(onClick = onOpenTimeline)
                            .padding(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (hasMultipleLots) "${lot.name} details" else "Whole lot details",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Crop: ${lot.cropPlan}",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                text = "Soil: ${lot.soilType} • Water: ${lot.waterAvailability} • Points: ${lot.points.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    MetricRow(
                        leftTitle = "Current day",
                        leftValue = "Day ${snapshot.cropSummary.currentDay}",
                        rightTitle = "Health score",
                        rightValue = "${snapshot.cropSummary.currentFarmHealthScore}/100",
                    )
                    MetricRow(
                        leftTitle = "Expected growth",
                        leftValue = snapshot.cropSummary.expectedGrowthRange,
                        rightTitle = "Stage",
                        rightValue = snapshot.cropSummary.expectedStage,
                    )
                    InfoCard(
                        title = "Lot recommendation",
                        value = snapshot.cropSummary.latestRecommendation,
                        supporting = if (hasMultipleLots) {
                            "Focused on ${lot.name}. Urgent zones: ${snapshot.cropSummary.urgentZones}"
                        } else {
                            "Single-crop farm overview. Urgent zones: ${snapshot.cropSummary.urgentZones}"
                        },
                    )
                }
            }
            DualActionButtons(
                primaryLabel = "Open AI Chat",
                onPrimary = onOpenChat,
            )
            DualActionButtons(
                primaryLabel = "View History",
                onPrimary = onOpenHistory,
            )
        }
    }
}

@Composable
private fun LotMapPreview(
    lots: List<LotSectionDraft>,
    selectedLotId: String?,
    onLotSelected: (String) -> Unit,
) {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
    )
    var mapSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(12.dp)
            .onSizeChanged { mapSize = it }
            .pointerInput(lots, mapSize) {
                detectTapGestures { tapOffset ->
                    if (mapSize.width <= 0 || mapSize.height <= 0) return@detectTapGestures
                    val hitLot = lots.lastOrNull { lot ->
                        lot.points.size >= 3 && isPointInsidePolygon(
                            point = tapOffset,
                            polygon = lot.points,
                            size = Size(mapSize.width.toFloat(), mapSize.height.toFloat()),
                        )
                    }
                    if (hitLot != null) {
                        onLotSelected(hitLot.id)
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(196.dp)) {
            lots.forEachIndexed { index, lot ->
                if (lot.points.size < 3) return@forEachIndexed

                val path = Path().apply {
                    val first = lot.points.first()
                    moveTo(first.x * size.width, first.y * size.height)
                    for (point in lot.points.drop(1)) {
                        lineTo(point.x * size.width, point.y * size.height)
                    }
                    close()
                }

                val isSelected = selectedLotId == null || selectedLotId == lot.id
                val base = palette[index % palette.size]

                drawPath(
                    path = path,
                    color = base.copy(alpha = if (isSelected) 0.38f else 0.18f),
                )
                drawPath(
                    path = path,
                    color = base.copy(alpha = if (isSelected) 0.95f else 0.45f),
                    style = Stroke(width = if (isSelected) 4f else 2f),
                )

                val centroid = lot.points
                    .map { Offset(it.x * size.width, it.y * size.height) }
                    .reduce { acc, offset -> Offset(acc.x + offset.x, acc.y + offset.y) }
                    .let { Offset(it.x / lot.points.size, it.y / lot.points.size) }
                drawCircle(
                    color = base.copy(alpha = if (isSelected) 0.95f else 0.5f),
                    radius = if (isSelected) 5f else 3.5f,
                    center = centroid,
                )
            }
        }
    }
}

private fun isPointInsidePolygon(
    point: Offset,
    polygon: List<com.alleyz15.farmtwinai.domain.model.FarmPoint>,
    size: Size,
): Boolean {
    var inside = false
    var j = polygon.size - 1

    for (i in polygon.indices) {
        val xi = polygon[i].x * size.width
        val yi = polygon[i].y * size.height
        val xj = polygon[j].x * size.width
        val yj = polygon[j].y * size.height

        val intersects = ((yi > point.y) != (yj > point.y)) &&
            (point.x < (xj - xi) * (point.y - yi) / ((yj - yi).takeIf { it != 0f } ?: 0.0001f) + xi)
        if (intersects) inside = !inside
        j = i
    }

    return inside
}
