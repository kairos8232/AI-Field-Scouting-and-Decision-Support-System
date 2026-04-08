package com.alleyz15.farmtwinai.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.alleyz15.farmtwinai.navigation.AppDestination
import com.alleyz15.farmtwinai.navigation.AppNavigator
import com.alleyz15.farmtwinai.presentation.FarmTwinAppState
import com.alleyz15.farmtwinai.ui.screens.flow.ActionConfirmationScreen
import com.alleyz15.farmtwinai.ui.screens.flow.AiChatScreen
import com.alleyz15.farmtwinai.ui.screens.flow.AuthScreen
import com.alleyz15.farmtwinai.ui.screens.flow.DashboardScreen
import com.alleyz15.farmtwinai.ui.screens.flow.DocumentSetupScreen
import com.alleyz15.farmtwinai.ui.screens.flow.FarmMapSetupScreen
import com.alleyz15.farmtwinai.ui.screens.flow.HistoryScreen
import com.alleyz15.farmtwinai.ui.screens.flow.LotSectionSetupScreen
import com.alleyz15.farmtwinai.ui.screens.flow.MePanelScreen
import com.alleyz15.farmtwinai.ui.screens.flow.QuickSetupScreen
import com.alleyz15.farmtwinai.ui.screens.flow.SetupMethodScreen
import com.alleyz15.farmtwinai.ui.screens.flow.TimelineScreen
import com.alleyz15.farmtwinai.ui.screens.flow.UserSituationScreen
import com.alleyz15.farmtwinai.ui.screens.flow.WelcomeScreen
import com.alleyz15.farmtwinai.ui.screens.flow.ZoneDetailScreen

@Composable
fun FarmTwinNavHost(
    navigator: AppNavigator,
    appState: FarmTwinAppState,
) {
    val destination by navigator.currentDestination

    when (val current = destination) {
        AppDestination.Welcome -> WelcomeScreen(
            onLogin = { navigator.navigate(AppDestination.Auth(isLogin = true)) },
            onSignUp = { navigator.navigate(AppDestination.Auth(isLogin = false)) },
        )
        is AppDestination.Auth -> AuthScreen(
            isLogin = current.isLogin,
            onBack = { navigator.pop() },
            onSwitchMode = { navigator.replace(AppDestination.Auth(isLogin = !current.isLogin)) },
            onContinue = { navigator.navigate(AppDestination.UserSituation) },
        )
        AppDestination.UserSituation -> UserSituationScreen(
            onBack = { navigator.pop() },
            onSituationSelected = { mode ->
                appState.setMode(mode)
                navigator.navigate(AppDestination.FarmMapSetup)
            },
        )
        AppDestination.SetupMethod -> SetupMethodScreen(
            onBack = { navigator.pop() },
            onMethodSelected = { method ->
                appState.setSetupMethod(method)
                when (method) {
                    com.alleyz15.farmtwinai.domain.model.SetupMethod.MANUAL -> navigator.navigate(AppDestination.ManualSetup)
                    com.alleyz15.farmtwinai.domain.model.SetupMethod.DOCUMENT -> navigator.navigate(AppDestination.DocumentSetup)
                    com.alleyz15.farmtwinai.domain.model.SetupMethod.QUICK_ESTIMATE -> navigator.navigate(AppDestination.QuickSetup)
                }
            },
        )
        AppDestination.ManualSetup,
        AppDestination.FarmMapSetup -> FarmMapSetupScreen(
            boundaryPoints = appState.farmBoundaryPoints,
            onBoundaryChanged = appState::updateFarmBoundary,
            onBack = { navigator.pop() },
            onContinue = { navigator.navigate(AppDestination.LotSectionSetup) },
        )
        AppDestination.LotSectionSetup -> LotSectionSetupScreen(
            boundaryPoints = appState.farmBoundaryPoints,
            initialSections = appState.lotSections,
            onSaveSections = appState::updateLotSections,
            onBack = { navigator.pop() },
            onContinue = { navigator.resetTo(AppDestination.Dashboard) },
        )
        AppDestination.DocumentSetup -> DocumentSetupScreen(
            summary = appState.snapshot.documentSummary,
            onBack = { navigator.pop() },
            onContinue = { navigator.resetTo(AppDestination.Dashboard) },
        )
        AppDestination.QuickSetup -> QuickSetupScreen(
            onBack = { navigator.pop() },
            onContinue = { navigator.resetTo(AppDestination.Dashboard) },
        )
        AppDestination.Dashboard -> DashboardScreen(
            snapshot = appState.snapshot,
            selectedMode = appState.selectedMode,
            lotSections = appState.lotSections,
            onOpenTimeline = { navigator.navigate(AppDestination.Timeline) },
            onOpenChat = { navigator.navigate(AppDestination.AiChat) },
            onOpenHistory = { navigator.navigate(AppDestination.History) },
            isTabBarVisible = appState.isAuthenticated,
            onSelectDashboardTab = { navigator.replace(AppDestination.Dashboard) },
            onSelectMeTab = { navigator.replace(AppDestination.Me) },
        )
        is AppDestination.ZoneDetail -> ZoneDetailScreen(
            zone = appState.snapshot.zones.first { it.id == current.zoneId },
            onBack = { navigator.pop() },
            onOpenChat = { navigator.navigate(AppDestination.AiChat) },
        )
        AppDestination.Timeline -> TimelineScreen(
            days = appState.snapshot.timeline,
            selectedDay = appState.selectedTimelineDay,
            onBack = { navigator.pop() },
            onSelectDay = appState::selectTimelineDay,
        )
        AppDestination.AiChat -> AiChatScreen(
            messages = appState.snapshot.chatMessages,
            onBack = { navigator.pop() },
            onConfirmAction = { navigator.navigate(AppDestination.ActionConfirmation) },
            authenticatedUser = appState.authenticatedUser,
        )
        AppDestination.ActionConfirmation -> ActionConfirmationScreen(
            latestAction = appState.snapshot.cropSummary.latestRecommendation,
            onBack = { navigator.pop() },
            onSubmit = { actionType, actionState ->
                appState.recordAction(actionType, actionState)
                navigator.navigate(AppDestination.History)
            },
        )
        AppDestination.History -> HistoryScreen(
            snapshot = appState.snapshot,
            onBack = { navigator.pop() },
        )
        AppDestination.Me -> MePanelScreen(
            snapshot = appState.snapshot,
            authenticatedUser = appState.authenticatedUser,
            onBack = if (appState.isAuthenticated) null else ({ navigator.pop() }),
            onModifyFarm = { navigator.navigate(AppDestination.FarmMapSetup) },
            onAddFarm = {
                appState.prepareNewFarmDraft()
                navigator.navigate(AppDestination.FarmMapSetup)
            },
            onSignOut = {
                appState.signOut()
                navigator.resetTo(AppDestination.Welcome)
            },
            isTabBarVisible = appState.isAuthenticated,
            onSelectDashboardTab = { navigator.replace(AppDestination.Dashboard) },
            onSelectMeTab = { navigator.replace(AppDestination.Me) },
        )
    }
}
