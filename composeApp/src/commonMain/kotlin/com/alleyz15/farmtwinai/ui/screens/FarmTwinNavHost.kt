package com.alleyz15.farmtwinai.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.alleyz15.farmtwinai.navigation.AppDestination
import com.alleyz15.farmtwinai.navigation.AppNavigator
import com.alleyz15.farmtwinai.presentation.FarmTwinAppState
import com.alleyz15.farmtwinai.ui.screens.flow.ActionConfirmationScreen
import com.alleyz15.farmtwinai.ui.screens.flow.AiChatScreen
import com.alleyz15.farmtwinai.ui.screens.flow.AuthScreen
import com.alleyz15.farmtwinai.ui.screens.flow.DashboardScreen
import com.alleyz15.farmtwinai.ui.screens.flow.DocumentSetupScreen
import com.alleyz15.farmtwinai.ui.screens.flow.FarmAddressSetupScreen
import com.alleyz15.farmtwinai.ui.screens.flow.FarmBoundaryDrawScreen
import com.alleyz15.farmtwinai.ui.screens.flow.HistoryScreen
import com.alleyz15.farmtwinai.ui.screens.flow.LotSectionSetupScreen
import com.alleyz15.farmtwinai.ui.screens.flow.LotRecommendationScreen
import com.alleyz15.farmtwinai.ui.screens.flow.MePanelScreen
import com.alleyz15.farmtwinai.ui.screens.flow.KnowledgeBaseScreen
import com.alleyz15.farmtwinai.ui.screens.flow.PolygonInsightsScreen
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

    LaunchedEffect(appState.isAuthenticated, destination) {
        if (
            appState.isAuthenticated &&
            (destination == AppDestination.Welcome || destination is AppDestination.Auth)
        ) {
            navigator.resetTo(AppDestination.Dashboard)
        }
    }

    when (val current = destination) {
        AppDestination.Welcome -> WelcomeScreen(
            onLogin = { navigator.navigate(AppDestination.Auth(isLogin = true)) },
            onSignUp = { navigator.navigate(AppDestination.Auth(isLogin = false)) },
        )
        is AppDestination.Auth -> AuthScreen(
            isLogin = current.isLogin,
            onBack = { navigator.pop() },
            onSwitchMode = { navigator.replace(AppDestination.Auth(isLogin = !current.isLogin)) },
            onSubmit = { modeIsLogin, email, password, displayName ->
                if (modeIsLogin) {
                    appState.signIn(email = email, password = password)
                } else {
                    appState.signUp(
                        email = email,
                        password = password,
                        displayName = displayName.orEmpty(),
                    )
                }
            },
            onGoogleAuth = { appState.signInWithGoogle() },
            onAuthenticated = { user ->
                appState.authenticateAndHydrate(user) { hasSavedFarmConfig ->
                    if (hasSavedFarmConfig) {
                        navigator.resetTo(AppDestination.Dashboard)
                    } else {
                        navigator.navigate(AppDestination.UserSituation)
                    }
                }
            },
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
        AppDestination.FarmMapSetup -> FarmAddressSetupScreen(
            address = appState.farmSetupAddress,
            locationQuery = appState.farmSetupMapQuery,
            searchTrigger = appState.farmSetupSearchTrigger,
            useCurrentLocationTrigger = appState.farmSetupUseCurrentLocationTrigger,
            onAddressChange = appState::updateFarmSetupAddress,
            onSearch = appState::searchFarmSetupAddress,
            onUseCurrentLocation = appState::useCurrentLocationForFarmSetup,
            onBack = {
                appState.cancelAddFarmDraftIfNeeded()
                navigator.pop()
            },
            onContinue = {
                appState.continueToBoundaryDrawing()
                navigator.navigate(AppDestination.FarmBoundaryDraw)
            },
        )
        AppDestination.FarmBoundaryDraw -> FarmBoundaryDrawScreen(
            boundaryPoints = appState.farmBoundaryPoints,
            locationQuery = appState.farmSetupMapQuery,
            searchTrigger = appState.farmSetupSearchTrigger,
            useCurrentLocationTrigger = appState.farmSetupUseCurrentLocationTrigger,
            onBoundaryChanged = appState::updateFarmBoundary,
            onBack = { navigator.pop() },
            onContinue = { navigator.navigate(AppDestination.LotSectionSetup) },
        )
        AppDestination.PolygonInsights -> PolygonInsightsScreen(
            boundaryPoints = appState.farmBoundaryPoints,
            report = appState.polygonInsightsReport,
            isSubmitting = appState.isSubmittingPolygon,
            errorMessage = appState.polygonInsightsError,
            onBack = { navigator.pop() },
            onSubmitPolygon = appState::submitPolygonForInsights,
            onContinue = { navigator.navigate(AppDestination.LotSectionSetup) },
        )
        AppDestination.LotSectionSetup -> LotSectionSetupScreen(
            farmName = appState.farmSetupFarmName,
            locationQuery = appState.farmSetupMapQuery,
            searchTrigger = appState.farmSetupSearchTrigger,
            useCurrentLocationTrigger = appState.farmSetupUseCurrentLocationTrigger,
            boundaryPoints = appState.farmBoundaryPoints,
            totalAreaHa = appState.lotTotalAreaInput,
            lots = appState.lotSections.map { it.points },
            lotCropTypes = appState.lotSections.mapIndexed { index, lot -> index to lot.cropPlan }.toMap(),
            lotPlantingDates = appState.lotSections.mapIndexed { index, lot -> index to (lot.plantingDate ?: "") }.toMap(),
            selectedMode = appState.selectedMode,
            onFarmNameChange = appState::updateFarmSetupFarmName,
            onTotalAreaChange = appState::updateLotTotalAreaInput,
            onLotsChange = { pts ->
                appState.updateLotSections(pts.mapIndexed { idx, p ->
                    val existing = appState.lotSections.getOrNull(idx)
                    com.alleyz15.farmtwinai.domain.model.LotSectionDraft(
                        id = existing?.id ?: "lot-${idx + 1}",
                        name = existing?.name ?: "Lot ${idx + 1}",
                        points = p,
                        cropPlan = existing?.cropPlan ?: "",
                        soilType = existing?.soilType ?: "",
                        waterAvailability = existing?.waterAvailability ?: "",
                        plantingDate = existing?.plantingDate,
                    )
                })
            },
            onLotCropTypeChange = { idx, plan ->
                val existing = appState.lotSections.getOrNull(idx)
                if (existing != null) {
                    val updated = appState.lotSections.toMutableList()
                    updated[idx] = existing.copy(cropPlan = plan)
                    appState.updateLotSections(updated)
                }
            },
            onLotPlantingDateChange = appState::updateLotPlantingDate,
            onBack = { navigator.pop() },
            onContinue = { navigator.navigate(AppDestination.LotRecommendation) },
        )
        AppDestination.LotRecommendation -> {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                appState.fetchEnvironmentalDataForLots()
            }
            LotRecommendationScreen(
                lots = appState.lotSections,
                isFetchingEnvData = appState.isFetchingEnvData,
                isAnalyzing = appState.isAnalyzingLots,
                bestLotId = appState.lotRecommendationBestLotId,
                recommendationReason = appState.lotRecommendationReason,
                errorMessage = appState.lotRecommendationError,
                dataSourceByLotId = appState.lotRecommendationDataSourceByLotId,
                recommendedCropByLotId = appState.lotRecommendationSuggestedCropByLotId,
                onBack = { navigator.pop() },
                onAnalyze = appState::analyzeLotsForRecommendation,
                onFollowAndContinue = {
                appState.completeLotRecommendationAndPersist(followRecommendation = true) {
                    if (it) {
                        navigator.resetTo(AppDestination.Dashboard)
                    }
                }
            },
            onSkipAndContinue = {
                appState.completeLotRecommendationAndPersist(followRecommendation = false) {
                    if (it) {
                        navigator.resetTo(AppDestination.Dashboard)
                    }
                }
            }
        )
        }
        AppDestination.DocumentSetup -> DocumentSetupScreen(
            summary = appState.snapshot.documentSummary,
            onBack = { navigator.pop() },
            onContinue = { navigator.resetTo(AppDestination.Dashboard) },
        )
        AppDestination.QuickSetup -> QuickSetupScreen(
            plantingDate = appState.farmSetupPlantingDate,
            onPlantingDateChange = appState::updateFarmSetupPlantingDate,
            onBack = { navigator.pop() },
            onContinue = { navigator.resetTo(AppDestination.Dashboard) },
        )
        AppDestination.Dashboard -> {
            val pendingTimelineFollowUp = appState.latestPendingTimelineFollowUp()

            LaunchedEffect(appState.authenticatedUser?.userId) {
                if (appState.isAuthenticated && !appState.hasLoadedFarmConfigOnce) {
                    appState.loadFarmConfigFromCloud()
                }
            }

            LaunchedEffect(appState.snapshot.farm.location, appState.farmSetupAddress, appState.farmSetupMapQuery) {
                appState.loadDashboardCurrentWeather()
            }

            DashboardScreen(
                snapshot = appState.snapshot,
                currentTimelineDay = appState.timelineUnlockedMaxDayNumber(),
                selectedMode = appState.selectedMode,
                lotSections = appState.lotSections,
                onOpenTimeline = { currentDay ->
                    appState.openTimelineForDay(currentDay)
                    navigator.navigate(AppDestination.Timeline)
                },
                onOpenChat = { navigator.navigate(AppDestination.AiChat) },
                latestTimelineHealthScore = appState.timelinePhotoAssessmentByDay
                    .maxByOrNull { it.key }
                    ?.value
                    ?.similarityScore,
                pendingFollowUpDayNumber = pendingTimelineFollowUp?.targetDayNumber,
                pendingFollowUpQuestion = pendingTimelineFollowUp?.followUp?.followUpQuestion,
                pendingFollowUpNextAction = pendingTimelineFollowUp?.followUp?.nextBestAction,
                onAcknowledgeFollowUp = { dayNumber -> appState.clearTimelineFollowUpForTimelineDay(dayNumber) },
                isTabBarVisible = true,
                onSelectDashboardTab = { navigator.replace(AppDestination.Dashboard) },
                onSelectMeTab = { navigator.replace(AppDestination.Me) },
                getLotSummary = appState::getOrGenerateCropSummaryForLot,
                weatherNowByLotId = appState.dashboardWeatherByLotId,
                weatherNowFromLocation = appState.dashboardCurrentWeatherNow,
            )
        }
        is AppDestination.ZoneDetail -> ZoneDetailScreen(
            zone = appState.snapshot.zones.first { it.id == current.zoneId },
            onBack = { navigator.pop() },
            onOpenChat = { navigator.navigate(AppDestination.AiChat) },
        )
        AppDestination.Timeline -> TimelineScreen(
            days = appState.snapshot.timeline,
            selectedDay = appState.selectedTimelineDay,
            farmStartDate = appState.snapshot.farm.plantingDate,
            healthScore = appState.snapshot.cropSummary.currentFarmHealthScore,
            stageVisual = appState.timelineStageVisual,
            stageVisualError = appState.timelineStageVisualError,
            isLoadingStageVisual = appState.isLoadingTimelineStageVisual,
            photoAssessment = appState.timelinePhotoAssessment,
            photoAssessmentError = appState.timelinePhotoAssessmentError,
            isAssessingPhoto = appState.isAssessingTimelinePhoto,
            resolvedStatus = appState.timelineStatusForDay(appState.selectedTimelineDay.dayNumber),
            recoveryForecast = appState.recoveryForecastForDay(appState.selectedTimelineDay.dayNumber),
            recommendedActionText = appState.recommendedActionTextForDay(appState.selectedTimelineDay.dayNumber),
            hasAssessmentForSelectedDay = appState.hasAssessmentForDay(appState.selectedTimelineDay.dayNumber),
            unlockedMaxDayNumber = appState.timelineUnlockedMaxDayNumber(),
            cachedPhotoBase64 = appState.timelineUploadByDay[appState.selectedTimelineDay.dayNumber]?.photoBase64,
            cachedPhotoMimeType = appState.timelineUploadByDay[appState.selectedTimelineDay.dayNumber]?.photoMimeType,
            isFarmConfigCacheReady = appState.isFarmConfigCacheReady,
            actionBannerMessage = appState.timelineActionBannerMessage,
            persistentFollowUp = appState.followUpForTimelineDay(appState.selectedTimelineDay.dayNumber),
            onBack = { navigator.pop() },
            onSelectDay = appState::selectTimelineDay,
            onLoadStageVisual = appState::loadTimelineStageVisual,
            onRegenerateStageVisual = appState::regenerateTimelineStageVisual,
            onCacheUploadedPhoto = appState::cacheTimelineUploadedPhoto,
            onComparePhoto = appState::compareTimelinePhoto,
            onClearUploadedPhoto = appState::clearTimelineUploadedPhoto,
            onOpenActionPlan = { navigator.navigate(AppDestination.ActionConfirmation) },
            onOpenChat = { navigator.navigate(AppDestination.AiChat) },
            onConsumeActionBanner = appState::consumeTimelineActionBanner,
            onAcknowledgeFollowUp = { dayNumber -> appState.clearTimelineFollowUpForTimelineDay(dayNumber) },
        )
        AppDestination.AiChat -> AiChatScreen(
            messages = if (appState.aiConversationMessages.isEmpty()) appState.snapshot.chatMessages else appState.aiConversationMessages,
            isSending = appState.isSendingAiConversationMessage,
            errorMessage = appState.aiConversationError,
            onBack = { navigator.pop() },
            onSend = appState::sendAiConversationMessage,
            onNewChat = appState::clearAiConversation,
            onOpenHistory = { navigator.navigate(AppDestination.History) },
            onOpenKnowledgeBase = { navigator.navigate(AppDestination.KnowledgeBase) },
            authenticatedUser = appState.authenticatedUser,
        )
        AppDestination.KnowledgeBase -> KnowledgeBaseScreen(
            results = appState.knowledgeBaseResults,
            isSearching = appState.isSearchingKnowledgeBase,
            errorMessage = appState.knowledgeBaseError,
            provider = appState.knowledgeBaseProvider,
            totalResults = appState.knowledgeBaseTotalResults,
            lastQuery = appState.knowledgeBaseLastQuery,
            onBack = { navigator.pop() },
            onSearch = { query -> appState.searchKnowledgeBase(query) },
        )
        AppDestination.ActionConfirmation -> ActionConfirmationScreen(
            dayNumber = appState.selectedTimelineDay.dayNumber,
            cropName = appState.snapshot.farm.cropName,
            latestAction = appState.recommendedActionTextForDay(appState.selectedTimelineDay.dayNumber),
            primaryRecommendedAction = appState.defaultActionTypeForDay(appState.selectedTimelineDay.dayNumber),
            alternativeActions = appState.recommendedActionTypesForDay(appState.selectedTimelineDay.dayNumber).drop(1),
            recoveryForecast = appState.recoveryForecastForDay(appState.selectedTimelineDay.dayNumber),
            followUp = appState.timelineActionDecisionByDay[appState.selectedTimelineDay.dayNumber]?.followUp,
            onBack = { navigator.pop() },
            onOpenAiChat = { starterPrompt ->
                appState.startAiConversation(starterPrompt)
                navigator.navigate(AppDestination.AiChat)
            },
            onOpenKnowledgeBase = { starterQuery ->
                appState.searchKnowledgeBase(starterQuery)
                navigator.navigate(AppDestination.KnowledgeBase)
            },
            onSubmit = { actionType, actionState ->
                val dayNumber = appState.selectedTimelineDay.dayNumber
                appState.recordTimelineAction(
                    dayNumber = dayNumber,
                    actionType = actionType,
                    actionState = actionState,
                )
                when (actionState) {
                    com.alleyz15.farmtwinai.domain.model.ActionState.DONE -> {
                        navigator.pop()
                    }
                    com.alleyz15.farmtwinai.domain.model.ActionState.NOT_YET -> {
                        val starterPrompt = buildString {
                            append("Day ")
                            append(dayNumber)
                            if (appState.snapshot.farm.cropName.isNotBlank()) {
                                append(" for crop ")
                                append(appState.snapshot.farm.cropName)
                            }
                            append(" action is not done yet: ")
                            append(actionType.name.lowercase().replace('_', ' '))
                            append(". Give me practical step-by-step instructions and precautions for today.")
                        }
                        appState.startAiConversation(starterPrompt)
                        navigator.navigate(AppDestination.AiChat)
                    }
                    com.alleyz15.farmtwinai.domain.model.ActionState.SKIP -> {
                        appState.setTimelineActionBanner("Action skipped for Day $dayNumber. Keep close monitoring and upload next photo to avoid missing deterioration.")
                        navigator.pop()
                    }
                }
            },
        )
        AppDestination.History -> HistoryScreen(
            historyRecords = appState.fieldInsightHistory,
            onLoadHistory = { appState.loadFieldInsightHistory() },
            onContinueChat = { initialPrompt ->
                appState.startAiConversation(initialPrompt)
                navigator.navigate(AppDestination.AiChat)
            },
            onBack = { navigator.pop() },
        )
        AppDestination.Me -> MePanelScreen(
            snapshot = appState.snapshot,
            lotSections = appState.lotSections,
            storedFarms = appState.storedFarms,
            authenticatedUser = appState.authenticatedUser,
            onBack = if (appState.isAuthenticated) null else ({ navigator.pop() }),
            onAddFarm = {
                appState.startAddFarmFlow()
                navigator.navigate(AppDestination.FarmMapSetup)
            },
            onModifyFarm = { navigator.navigate(AppDestination.FarmMapSetup) },
            onSwitchFarm = { farmId ->
                appState.switchToStoredFarm(farmId)
                navigator.replace(AppDestination.Dashboard)
            },
            onDeleteFarm = appState::deleteStoredFarm,
            onDeleteActiveFarm = {
                val deleted = appState.deleteActiveFarm()
                if (deleted) {
                    navigator.replace(AppDestination.Dashboard)
                }
            },
            canDeleteActiveFarm = appState.storedFarms.isNotEmpty(),
            selectedThemePreference = appState.themePreference,
            onThemePreferenceChange = appState::updateThemePreference,
            onSignOut = {
                appState.signOut()
                navigator.resetTo(AppDestination.Welcome)
            },
            isTabBarVisible = true,
            onSelectDashboardTab = { navigator.replace(AppDestination.Dashboard) },
            onSelectMeTab = { navigator.replace(AppDestination.Me) },
        )
    }
}
