package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.runtime.Composable
import com.alleyz15.farmtwinai.auth.AuthUser
import com.alleyz15.farmtwinai.domain.model.FarmTwinSnapshot
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.HomeTab
import com.alleyz15.farmtwinai.ui.components.HomeTabBar
import com.alleyz15.farmtwinai.ui.components.InfoCard
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
    isTabBarVisible: Boolean,
    onSelectDashboardTab: () -> Unit,
    onSelectMeTab: () -> Unit,
) {
    AppScaffold(
        title = "Profile",
        subtitle = authenticatedUser?.email ?: snapshot.user.name,
        onBack = onBack,
        floatingFooter = if (isTabBarVisible) {
            {
                HomeTabBar(
                    selectedTab = HomeTab.ME,
                    onSelectDashboard = onSelectDashboardTab,
                    onSelectMe = onSelectMeTab,
                )
            }
        } else null,
    ) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Account and farm",
                body = "Manage the active farm setup and account access from here.",
            )
            InfoCard(
                title = "Current Farm",
                value = snapshot.farm.farmName,
                supporting = "Location: ${snapshot.farm.location}\nCrop: ${snapshot.farm.cropName}",
            )
            androidx.compose.material3.OutlinedButton(onClick = onModifyFarm) {
                androidx.compose.material3.Text("Modify Farm Map")
            }
            androidx.compose.material3.OutlinedButton(onClick = onAddFarm) {
                androidx.compose.material3.Text("Add New Farm")
            }
            if (authenticatedUser != null) {
                androidx.compose.material3.OutlinedButton(onClick = onSignOut) {
                    androidx.compose.material3.Text("Sign Out")
                }
            }
        }
    }
}
