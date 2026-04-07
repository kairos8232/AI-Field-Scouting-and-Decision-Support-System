package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import com.alleyz15.farmtwinai.auth.AuthUser
import com.alleyz15.farmtwinai.domain.model.FarmTwinSnapshot
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.HomeTab
import com.alleyz15.farmtwinai.ui.components.HomeTabBar
import com.alleyz15.farmtwinai.ui.components.InfoCard
import com.alleyz15.farmtwinai.ui.components.MetricRow
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader

@Composable
fun MePanelScreen(
    snapshot: FarmTwinSnapshot,
    authenticatedUser: AuthUser?,
    onBack: (() -> Unit)?,
    onModifyFarm: () -> Unit,
    onAddFarm: () -> Unit,
    onSignOut: () -> Unit,
    isTabBarVisible: Boolean = false,
    onSelectDashboardTab: (() -> Unit)? = null,
    onSelectMeTab: (() -> Unit)? = null,
) {
    val userName = authenticatedUser?.displayName ?: snapshot.user.name
    val userEmail = authenticatedUser?.email ?: "No email available"

    AppScaffold(
        title = "Me",
        subtitle = "Account and farm management",
        onBack = onBack,
        floatingFooter = if (isTabBarVisible && onSelectDashboardTab != null && onSelectMeTab != null) {
            {
                HomeTabBar(
                    selectedTab = HomeTab.ME,
                    onSelectDashboard = onSelectDashboardTab,
                    onSelectMe = onSelectMeTab,
                )
            }
        } else {
            null
        },
    ) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Signed-in account",
                body = "Manage your farm setup anytime, add a new farm draft, or sign out.",
            )
            InfoCard(
                title = userName,
                value = userEmail,
                supporting = snapshot.user.region,
            )
            MetricRow(
                leftTitle = "Current farm",
                leftValue = snapshot.farm.farmName,
                rightTitle = "Crop",
                rightValue = snapshot.farm.cropName,
            )
            MetricRow(
                leftTitle = "Farm size",
                leftValue = snapshot.farm.fieldSize,
                rightTitle = "Planting date",
                rightValue = snapshot.farm.plantingDate,
            )
            DualActionButtons(
                primaryLabel = "Modify Current Farm",
                onPrimary = onModifyFarm,
                secondaryLabel = "Add New Farm",
                onSecondary = onAddFarm,
            )
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign Out")
            }
        }
    }
}
