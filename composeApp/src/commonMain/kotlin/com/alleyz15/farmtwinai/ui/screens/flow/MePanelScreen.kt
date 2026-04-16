package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.alleyz15.farmtwinai.ui.theme.isAppDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.auth.AuthUser
import com.alleyz15.farmtwinai.domain.model.FarmTwinSnapshot
import com.alleyz15.farmtwinai.ui.components.AuroraBackground
import com.alleyz15.farmtwinai.ui.components.HomeTab
import com.alleyz15.farmtwinai.ui.components.HomeTabBar
import com.alleyz15.farmtwinai.ui.components.OnboardingAdaptiveWidth
import com.alleyz15.farmtwinai.presentation.ThemePreference
import com.alleyz15.farmtwinai.ui.theme.Mint200

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
    authenticatedUser: AuthUser?,
    onBack: (() -> Unit)?,
    onModifyFarm: () -> Unit,
    onOpenHistory: () -> Unit,
    selectedThemePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    onSignOut: () -> Unit,
    isTabBarVisible: Boolean,
    onSelectDashboardTab: () -> Unit,
    onSelectMeTab: () -> Unit,
) {
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ThemeChoiceChip(
                        label = "System",
                        selected = selectedThemePreference == ThemePreference.SYSTEM,
                        onClick = { onThemePreferenceChange(ThemePreference.SYSTEM) },
                        modifier = Modifier.weight(1f),
                    )
                    ThemeChoiceChip(
                        label = "Light",
                        selected = selectedThemePreference == ThemePreference.LIGHT,
                        onClick = { onThemePreferenceChange(ThemePreference.LIGHT) },
                        modifier = Modifier.weight(1f),
                    )
                    ThemeChoiceChip(
                        label = "Dark",
                        selected = selectedThemePreference == ThemePreference.DARK,
                        onClick = { onThemePreferenceChange(ThemePreference.DARK) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "My Activity & Sync",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                AuroraOptionCard(
                    title = "Cloud History",
                    description = "Review your past field insights, AI photos, and conversations.",
                    onClick = onOpenHistory
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Farm Management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                
                AuroraInfoCard(
                    title = "Active Farm",
                    value = snapshot.farm.farmName,
                    supporting = "Location: ${snapshot.farm.location}\nCrop: ${snapshot.farm.cropName}",
                )

                AuroraOptionCard(
                    title = "Farm Map Setup",
                    description = "Adjust the active polygon and zone configurations for your farm.",
                    onClick = onModifyFarm
                )
                
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

@Composable
private fun ThemeChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = Mint200,
                contentColor = Color.Black,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(text = label, fontWeight = FontWeight.SemiBold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(text = label, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
