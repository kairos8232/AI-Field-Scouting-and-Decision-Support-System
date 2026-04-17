package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.alleyz15.farmtwinai.ui.theme.isAppDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.domain.model.AppMode
import com.alleyz15.farmtwinai.domain.model.FarmTwinSnapshot
import com.alleyz15.farmtwinai.domain.model.LotSectionDraft
import com.alleyz15.farmtwinai.ui.components.AuroraBackground
import com.alleyz15.farmtwinai.ui.components.HomeTab
import com.alleyz15.farmtwinai.ui.components.HomeTabBar
import com.alleyz15.farmtwinai.ui.components.OnboardingAdaptiveWidth
import com.alleyz15.farmtwinai.ui.theme.Leaf400
import com.alleyz15.farmtwinai.ui.theme.Mint200
import com.alleyz15.farmtwinai.ui.theme.Sand100

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
    getLotSummary: (LotSectionDraft) -> com.alleyz15.farmtwinai.domain.model.CropSummary = { snapshot.cropSummary },
) {
    val darkTheme = isAppDarkTheme()
    val inactiveChipBorder = if (darkTheme) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val inactiveChipBg = if (darkTheme) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    val summaryCardBg = if (darkTheme) Color.Black.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val summaryCardBorder = if (darkTheme) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val statCardBgAlpha = if (darkTheme) 0.45f else 0.82f
    val secondaryButtonBgAlpha = if (darkTheme) 0.55f else 0.9f

    var selectedLotId by remember(lotSections) { mutableStateOf(lotSections.firstOrNull()?.id.orEmpty()) }
    val selectedLot = lotSections.firstOrNull { it.id == selectedLotId } ?: lotSections.firstOrNull()
    val hasMultipleLots = lotSections.size > 1

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()

        OnboardingAdaptiveWidth { maxContentWidth, _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = if (isTabBarVisible) 80.dp else 18.dp),
            ) {
                // Header
                Column {
                    Text(
                        text = "Farm Dashboard",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "${snapshot.farm.farmName} • ${selectedMode.name.replace('_', ' ')}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (lotSections.isNotEmpty()) {
                    Text(
                        text = "Farm lot map view",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (hasMultipleLots) "Tap a lot below to view that lot's details." else "Single-lot setup detected. Showing full lot details.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    LotMapPreview(
                        lots = lotSections,
                        selectedLotId = selectedLot?.id,
                        onLotSelected = { selectedLotId = it },
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (hasMultipleLots) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            lotSections.forEach { lot ->
                                val isSelected = selectedLot?.id == lot.id
                                Box(
                                    modifier = Modifier
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Leaf400 else inactiveChipBorder,
                                            shape = RoundedCornerShape(999.dp),
                                        )
                                        .background(
                                            color = if (isSelected) Leaf400.copy(alpha = 0.2f) else inactiveChipBg,
                                            shape = RoundedCornerShape(999.dp),
                                        )
                                        .clickable { selectedLotId = lot.id }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = lot.name, 
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    selectedLot?.let { lot ->
                        val cropSummary = getLotSummary(lot)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, summaryCardBorder, RoundedCornerShape(14.dp))
                                .background(summaryCardBg, RoundedCornerShape(14.dp))
                                .clickable(onClick = onOpenTimeline)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Text(
                                text = "Crop ${lot.cropPlan} • Soil ${lot.soilType} • Water ${lot.waterAvailability}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = statCardBgAlpha), RoundedCornerShape(14.dp))
                                    .padding(16.dp)
                            ) {
                                Column {
                                    Text("Current day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Day ${cropSummary.currentDay}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = statCardBgAlpha), RoundedCornerShape(14.dp))
                                    .padding(16.dp)
                            ) {
                                Column {
                                    Text("Health score", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${cropSummary.currentFarmHealthScore}/100", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onOpenHistory,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = secondaryButtonBgAlpha), contentColor = MaterialTheme.colorScheme.onBackground),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("View History")
                    }

                    Button(
                        onClick = onOpenChat,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = secondaryButtonBgAlpha), contentColor = MaterialTheme.colorScheme.onBackground),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Open AI Consultation")
                    }
                }
            }
        }

        if (isTabBarVisible && onSelectDashboardTab != null && onSelectMeTab != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                // Background behind tab bar to avoid transparent bleed
                Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface))
                HomeTabBar(
                    selectedTab = HomeTab.DASHBOARD,
                    onSelectDashboard = onSelectDashboardTab,
                    onSelectMe = onSelectMeTab,
                )
            }
        }
    }
}

@Composable
private fun LotMapPreview(
    lots: List<LotSectionDraft>,
    selectedLotId: String?,
    onLotSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = listOf(Leaf400, Mint200, MaterialTheme.colorScheme.onBackground)
    var mapSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .height(220.dp)
            .background(if (isAppDarkTheme()) Color.Black.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
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
                    if (hitLot != null) onLotSelected(hitLot.id)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            lots.forEachIndexed { index, lot ->
                if (lot.points.size < 3) return@forEachIndexed

                val path = Path().apply {
                    val first = lot.points.first()
                    moveTo(first.x * size.width, first.y * size.height)
                    for (point in lot.points.drop(1)) lineTo(point.x * size.width, point.y * size.height)
                    close()
                }

                val isSelected = selectedLotId == null || selectedLotId == lot.id
                val base = palette[index % palette.size]

                drawPath(path = path, color = base.copy(alpha = if (isSelected) 0.38f else 0.18f))
                drawPath(
                    path = path,
                    color = base.copy(alpha = if (isSelected) 0.85f else 0.45f),
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

private fun isPointInsidePolygon(point: Offset, polygon: List<com.alleyz15.farmtwinai.domain.model.FarmPoint>, size: Size): Boolean {
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
