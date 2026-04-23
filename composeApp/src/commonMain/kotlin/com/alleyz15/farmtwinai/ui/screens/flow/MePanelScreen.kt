package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.alleyz15.farmtwinai.ui.theme.isAppDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.auth.AuthUser
import com.alleyz15.farmtwinai.domain.model.FarmTwinSnapshot
import com.alleyz15.farmtwinai.domain.model.LotSectionDraft
import com.alleyz15.farmtwinai.ui.components.AuroraBackground
import com.alleyz15.farmtwinai.ui.components.HomeTab
import com.alleyz15.farmtwinai.ui.components.HomeTabBar
import com.alleyz15.farmtwinai.ui.components.OnboardingAdaptiveWidth
import com.alleyz15.farmtwinai.presentation.StoredFarm
import com.alleyz15.farmtwinai.presentation.ThemePreference
import com.alleyz15.farmtwinai.ui.theme.Mint200
import kotlin.math.roundToInt

private val ArrowBackIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ArrowBack",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(20f, 11f)
            lineTo(7.83f, 11f)
            lineToRelative(5.59f, -5.59f)
            lineTo(12f, 4f)
            lineToRelative(-8f, 8f)
            lineToRelative(8f, 8f)
            lineToRelative(1.41f, -1.41f)
            lineTo(7.83f, 13f)
            lineTo(20f, 13f)
            close()
        }
    }.build()

@Composable
fun AuroraOptionCard(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val darkTheme = isAppDarkTheme()
    val cardAlpha = if (darkTheme) 0.4f else 0.9f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = cardAlpha)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Mint200, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f))
        }
    }
}

@Composable
fun MePanelScreen(
    snapshot: FarmTwinSnapshot,
    lotSections: List<LotSectionDraft>,
    storedFarms: List<StoredFarm>,
    authenticatedUser: AuthUser?,
    onBack: (() -> Unit)?,
    onAddFarm: () -> Unit,
    onModifyFarm: () -> Unit,
    onSwitchFarm: (String) -> Unit,
    onDeleteFarm: (String) -> Unit,
    onDeleteActiveFarm: () -> Unit,
    canDeleteActiveFarm: Boolean,
    selectedThemePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    onSignOut: () -> Unit,
    isTabBarVisible: Boolean,
    onSelectDashboardTab: () -> Unit,
    onSelectMeTab: () -> Unit,
) {
    var showThemeDropdown by remember { mutableStateOf(false) }
    val themeLabel = when (selectedThemePreference) {
        ThemePreference.SYSTEM -> "System"
        ThemePreference.LIGHT -> "Light"
        ThemePreference.DARK -> "Dark"
    }
    val cropLines = lotSections
        .mapNotNull { lot ->
            val crop = lot.cropPlan.trim()
            if (crop.isEmpty()) null else "${lot.name}: $crop"
        }
    val cropSummaryText = if (cropLines.isNotEmpty()) {
        cropLines.joinToString(separator = "\n")
    } else {
        snapshot.farm.cropName
    }
    val activeFarmCard = StoredFarm(
        id = "__active__",
        farmName = snapshot.farm.farmName,
        address = snapshot.farm.location,
        mapQuery = "",
        totalAreaInput = snapshot.farm.fieldSize,
        mode = snapshot.farm.mode,
        plantingDate = snapshot.farm.plantingDate,
        boundaryPoints = emptyList(),
        lots = lotSections,
    )
    val farmCards = listOf(activeFarmCard) + storedFarms

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        
        OnboardingAdaptiveWidth { maxContentWidth, _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = if (isTabBarVisible) 80.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), CircleShape),
                        ) {
                            Icon(
                                imageVector = ArrowBackIcon,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Profile",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = authenticatedUser?.email ?: snapshot.user.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f),
                        fontWeight = FontWeight.Medium,
                    )

                    Box {
                        OutlinedButton(
                            onClick = { showThemeDropdown = true },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(text = themeLabel, color = MaterialTheme.colorScheme.onBackground)
                        }
                        DropdownMenu(
                            expanded = showThemeDropdown,
                            onDismissRequest = { showThemeDropdown = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("System") },
                                onClick = {
                                    onThemePreferenceChange(ThemePreference.SYSTEM)
                                    showThemeDropdown = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Light") },
                                onClick = {
                                    onThemePreferenceChange(ThemePreference.LIGHT)
                                    showThemeDropdown = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Dark") },
                                onClick = {
                                    onThemePreferenceChange(ThemePreference.DARK)
                                    showThemeDropdown = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Farm Management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                farmCards.forEachIndexed { index, farm ->
                    val isActive = index == 0
                    var revealPx by remember(farm.id) { mutableStateOf(0f) }
                    val density = LocalDensity.current
                    val maxRevealPx = with(density) { 92.dp.toPx() }
                    val canDeleteThisCard = if (isActive) canDeleteActiveFarm else true
                    val farmCropLine = farm.lots
                        .mapNotNull { lot ->
                            val crop = lot.cropPlan.trim()
                            if (crop.isEmpty()) null else "${lot.name}: $crop"
                        }
                    val cropSummary = if (farmCropLine.isNotEmpty()) {
                        farmCropLine.joinToString(separator = "  •  ")
                    } else {
                        "No crop plan"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(),
                    ) {
                        if (canDeleteThisCard && revealPx <= -2f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(92.dp)
                                    .background(Color(0xFFD86A6A), RoundedCornerShape(16.dp))
                                    .clickable {
                                        if (isActive) onDeleteActiveFarm() else onDeleteFarm(farm.id)
                                    }
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(revealPx.roundToInt(), 0) }
                                .pointerInput(canDeleteThisCard) {
                                    if (!canDeleteThisCard) return@pointerInput
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            val next = revealPx + dragAmount
                                            revealPx = next.coerceIn(-maxRevealPx, 0f)
                                        },
                                        onDragEnd = {
                                            revealPx = if (revealPx < -maxRevealPx * 0.45f) -maxRevealPx else 0f
                                        },
                                    )
                                }
                                .clickable {
                                    if (revealPx < -6f) {
                                        revealPx = 0f
                                    } else if (!isActive) {
                                        onSwitchFarm(farm.id)
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = farm.farmName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    if (isActive) {
                                        Box(
                                            modifier = Modifier
                                                .background(Mint200.copy(alpha = 0.26f), RoundedCornerShape(999.dp))
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                        ) {
                                            Text(
                                                text = "Active",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Mint200,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = ">",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }

                                Text(
                                    text = cropSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                                    maxLines = 2,
                                )

                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onAddFarm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Mint200,
                            contentColor = Color.Black,
                        ),
                    ) {
                        Text("Add Farm")
                    }
                    OutlinedButton(
                        onClick = onModifyFarm,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Edit Active Farm", color = MaterialTheme.colorScheme.onBackground)
                    }
                }

                Spacer(modifier = Modifier.weight(1f)) // Push Sign Out to bottom if screen is tall

                if (authenticatedUser != null) {
                    Button(
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), contentColor = MaterialTheme.colorScheme.onBackground),
                    ) {
                        Text("Sign Out")
                    }
                }
            }
        }

        if (isTabBarVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface))
                HomeTabBar(
                    selectedTab = HomeTab.ME,
                    onSelectDashboard = onSelectDashboardTab,
                    onSelectMe = onSelectMeTab,
                )
            }
        }
    }
}
