package com.alleyz15.farmtwinai.ui.components

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

enum class HomeTab {
    DASHBOARD,
    ME,
}

@Composable
fun HomeTabBar(
    selectedTab: HomeTab,
    onSelectDashboard: () -> Unit,
    onSelectMe: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedTab == HomeTab.DASHBOARD,
            onClick = onSelectDashboard,
            label = { Text("Dashboard") },
            icon = {},
        )
        NavigationBarItem(
            selected = selectedTab == HomeTab.ME,
            onClick = onSelectMe,
            label = { Text("Me") },
            icon = {},
        )
    }
}
