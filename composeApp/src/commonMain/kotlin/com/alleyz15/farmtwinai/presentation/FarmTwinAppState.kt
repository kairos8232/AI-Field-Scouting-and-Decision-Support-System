package com.alleyz15.farmtwinai.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alleyz15.farmtwinai.auth.AuthUser
import com.alleyz15.farmtwinai.auth.GoogleAuthProvider
import com.alleyz15.farmtwinai.data.analysis.FieldInsightsRepository
import com.alleyz15.farmtwinai.data.auth.AuthRepository
import com.alleyz15.farmtwinai.data.farm.FarmConfigDraft
import com.alleyz15.farmtwinai.data.farm.FarmConfigFarmEntry
import com.alleyz15.farmtwinai.data.farm.FarmConfigRemote
import com.alleyz15.farmtwinai.data.farm.FarmConfigRepository
import com.alleyz15.farmtwinai.data.farm.TimelinePhotoCacheEntry
import com.alleyz15.farmtwinai.data.farm.TimelineStageVisualCacheEntry
import com.alleyz15.farmtwinai.data.farm.TimelinePhotoAssessmentCacheEntry
import com.alleyz15.farmtwinai.data.farm.TimelineCacheStore
import com.alleyz15.farmtwinai.data.farm.TimelineActionDecisionCacheEntry
import com.alleyz15.farmtwinai.data.farm.TimelineInsightCacheEntry
import com.alleyz15.farmtwinai.data.farm.createTimelineCacheStore
import com.alleyz15.farmtwinai.data.mock.MockFarmTwinRepository
import com.alleyz15.farmtwinai.domain.model.AiChatContext
import com.alleyz15.farmtwinai.domain.model.ActionState
import com.alleyz15.farmtwinai.domain.model.ActionType
import com.alleyz15.farmtwinai.domain.model.ActionTrackerFollowUp
import com.alleyz15.farmtwinai.domain.model.AppMode
import com.alleyz15.farmtwinai.domain.model.ChatMessage
import com.alleyz15.farmtwinai.domain.model.FieldInsightReport
import com.alleyz15.farmtwinai.domain.model.FarmPoint
import com.alleyz15.farmtwinai.domain.model.FarmProfile
import com.alleyz15.farmtwinai.domain.model.FarmTwinSnapshot
import com.alleyz15.farmtwinai.domain.model.ForecastConfidenceTier
import com.alleyz15.farmtwinai.domain.model.LotSectionDraft
import com.alleyz15.farmtwinai.domain.model.KnowledgeBaseResult
import com.alleyz15.farmtwinai.domain.model.MessageSender
import com.alleyz15.farmtwinai.domain.model.RecoveryTrend
import com.alleyz15.farmtwinai.domain.model.SetupMethod
import com.alleyz15.farmtwinai.domain.model.TimelinePhotoAssessment
import com.alleyz15.farmtwinai.domain.model.TimelineRecoveryForecast
import com.alleyz15.farmtwinai.domain.model.TimelineStageVisual
import com.alleyz15.farmtwinai.domain.model.TimelineStatus
import com.alleyz15.farmtwinai.domain.model.ZoneInfo
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

data class TimelineUploadCache(
    val photoBase64: String,
    val photoMimeType: String,
    val updatedAtEpochMs: Long,
)

data class TimelineActionDecision(
    val actionType: ActionType,
    val state: ActionState,
    val updatedAtEpochMs: Long,
    val followUp: ActionTrackerFollowUp? = null,
)

data class PendingTimelineFollowUp(
    val actionDayNumber: Int,
    val targetDayNumber: Int,
    val followUp: ActionTrackerFollowUp,
)

data class StoredFarm(
    val id: String,
    val farmName: String,
    val address: String,
    val mapQuery: String,
    val totalAreaInput: String,
    val mode: AppMode,
    val plantingDate: String = "",
    val createdAtEpochMs: Long = 0L,
    val boundaryPoints: List<FarmPoint>,
    val lots: List<LotSectionDraft>,
)

class FarmTwinAppState(
    repository: MockFarmTwinRepository,
    private val fieldInsightsRepository: FieldInsightsRepository,
    private val authRepository: AuthRepository,
    private val farmConfigRepository: FarmConfigRepository,
    private val googleAuthProvider: GoogleAuthProvider,
    private val timelineCacheStore: TimelineCacheStore = createTimelineCacheStore(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val cacheJson = Json { ignoreUnknownKeys = true }
    private var cacheUpdateSequence = 1L
    private var lastUpdatedTimelineStageVisualDay: Int? = null
    private var timelineCacheSyncInFlight = false
    private var timelineCacheSyncRequested = false

    var authenticatedUser by mutableStateOf<AuthUser?>(null)
        private set

    var fieldInsightHistory by mutableStateOf<List<com.alleyz15.farmtwinai.domain.model.FieldInsightHistoryRecord>?>(null)
        private set
        
    fun loadFieldInsightHistory() {
        scope.launch {
            try {
                fieldInsightHistory = fieldInsightsRepository.getHistory(authenticatedUser?.userId)
            } catch (e: Exception) {
                fieldInsightHistory = emptyList()
            }
        }
    }

    var snapshot by mutableStateOf(repository.loadSnapshot())
        private set

    var selectedMode by mutableStateOf(snapshot.farm.mode)
        private set

    var selectedSetupMethod by mutableStateOf(SetupMethod.MANUAL)
        private set

    var selectedZoneId by mutableStateOf(snapshot.zones.first().id)
        private set

    var selectedTimelineDay by mutableStateOf(snapshot.timeline.minByOrNull { it.dayNumber } ?: snapshot.timeline.last())
        private set

    var farmSetupAddress by mutableStateOf("")
        private set

    var farmSetupFarmName by mutableStateOf("")
        private set

    var farmSetupMapQuery by mutableStateOf("")
        private set

    var farmSetupPlantingDate by mutableStateOf(snapshot.farm.plantingDate)
        private set

    var farmSetupSearchTrigger by mutableStateOf(0)
        private set

    var farmSetupUseCurrentLocationTrigger by mutableStateOf(0)
        private set

    var isFarmMapFrozen by mutableStateOf(false)
        private set

    var farmBoundaryPoints by mutableStateOf(
        listOf(
            FarmPoint(0.50f, 0.25f),
            FarmPoint(0.85f, 0.75f),
            FarmPoint(0.15f, 0.75f),
        )
    )
        private set

    var lotSections by mutableStateOf(
        listOf(
            LotSectionDraft(
                id = "lot-1",
                name = "Lot 1",
                points = farmBoundaryPoints,
                cropPlan = "",
                soilType = "",
                waterAvailability = "",
            )
        )
    )
        private set

    var lotTotalAreaInput by mutableStateOf(snapshot.farm.fieldSize)
        private set

    var polygonInsightsReport by mutableStateOf<FieldInsightReport?>(null)
        private set

    var polygonInsightsError by mutableStateOf<String?>(null)
        private set

    var isSubmittingPolygon by mutableStateOf(false)
        private set

    var isAnalyzingLots by mutableStateOf(false)
        private set

    var lotRecommendationBestLotId by mutableStateOf<String?>(null)
        private set

    var lotRecommendationReason by mutableStateOf<String?>(null)
        private set

    var lotRecommendationError by mutableStateOf<String?>(null)
        private set

    var lotRecommendationDataSourceByLotId by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var lotRecommendationSuggestedCropByLotId by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var isFarmConfigSyncing by mutableStateOf(false)
        private set

    var hasLoadedFarmConfigOnce by mutableStateOf(false)
        private set

    var farmConfigSyncError by mutableStateOf<String?>(null)
        private set

    var isFetchingEnvData by mutableStateOf(false)
        private set

    var lotReports by mutableStateOf<Map<String, FieldInsightReport>>(emptyMap())
        private set

    var dashboardCurrentWeatherNow by mutableStateOf<String?>(null)
        private set

    var isLoadingDashboardCurrentWeather by mutableStateOf(false)
        private set

    val dashboardWeatherByLotId: Map<String, String>
        get() = lotReports.mapValues { (_, report) ->
            weatherLabelForReport(report)
        }

    var timelineStageVisual by mutableStateOf<TimelineStageVisual?>(null)
        private set

    var timelineStageVisualByDay by mutableStateOf<Map<Int, TimelineStageVisual>>(emptyMap())
        private set

    var isLoadingTimelineStageVisual by mutableStateOf(false)
        private set

    var timelineLoadingStageVisualDays by mutableStateOf<Set<Int>>(emptySet())
        private set

    var timelineStageVisualError by mutableStateOf<String?>(null)
        private set

    var timelineStageVisualErrorByDay by mutableStateOf<Map<Int, String>>(emptyMap())
        private set

    var timelinePhotoAssessment by mutableStateOf<TimelinePhotoAssessment?>(null)
        private set

    var timelinePhotoAssessmentByDay by mutableStateOf<Map<Int, TimelinePhotoAssessment>>(emptyMap())
        private set

    var isAssessingTimelinePhoto by mutableStateOf(false)
        private set

    var timelinePhotoAssessmentError by mutableStateOf<String?>(null)
        private set

    var timelinePhotoAssessmentErrorByDay by mutableStateOf<Map<Int, String>>(emptyMap())
        private set

    var timelineUploadByDay by mutableStateOf<Map<Int, TimelineUploadCache>>(emptyMap())
        private set

    var timelineDynamicStatusByDay by mutableStateOf<Map<Int, TimelineStatus>>(emptyMap())
    var timelineSuggestedActionByDay by mutableStateOf<Map<Int, String>>(emptyMap())
    var timelineRecoveryForecastByDay by mutableStateOf<Map<Int, TimelineRecoveryForecast>>(emptyMap())
        private set

    var timelineActionDecisionByDay by mutableStateOf<Map<Int, TimelineActionDecision>>(emptyMap())
        private set

    var timelineActionBannerMessage by mutableStateOf<String?>(null)
        private set

    var storedFarms by mutableStateOf<List<StoredFarm>>(emptyList())
        private set

    private var isAddFarmDraftActive by mutableStateOf(false)
    private var pendingActiveFarmBeforeAdd: StoredFarm? = null
    private var pendingSnapshotFarmBeforeAdd: FarmProfile? = null

    var aiConversationMessages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set

    var isSendingAiConversationMessage by mutableStateOf(false)
        private set

    var aiConversationError by mutableStateOf<String?>(null)
        private set

    var aiConversationProvider by mutableStateOf<String?>(null)
        private set

    var knowledgeBaseResults by mutableStateOf<List<KnowledgeBaseResult>>(emptyList())
        private set

    var knowledgeBaseLastQuery by mutableStateOf("")
        private set

    var knowledgeBaseTotalResults by mutableStateOf(0)
        private set

    var knowledgeBaseProvider by mutableStateOf<String?>(null)
        private set

    var isSearchingKnowledgeBase by mutableStateOf(false)
        private set

    var knowledgeBaseError by mutableStateOf<String?>(null)
        private set

    var themePreference by mutableStateOf(ThemePreference.SYSTEM)
        private set

    private var aiConversationMessageIndex = 0

    init {
        authenticatedUser = authRepository.getSavedSession()
        restoreTimelineCacheFromLocalStore()
    }

    val isAuthenticated: Boolean
        get() = authenticatedUser != null

    val isFarmConfigCacheReady: Boolean
        get() = !isAuthenticated || hasLoadedFarmConfigOnce

    fun authenticateUser(user: AuthUser) {
        authenticatedUser = user
        authRepository.saveSession(user)
    }

    fun authenticateAndHydrate(
        user: AuthUser,
        onReady: (hasSavedFarmConfig: Boolean) -> Unit,
    ) {
        authenticatedUser = user
        authRepository.saveSession(user)
        isFarmConfigSyncing = true
        farmConfigSyncError = null

        scope.launch {
            val remote = runCatching {
                farmConfigRepository.fetchLatestFarmConfig(user.userId)
            }.onFailure { error ->
                farmConfigSyncError = error.message ?: "Unable to load farm setup from cloud."
            }.getOrNull()

            if (remote != null) {
                applyRemoteFarmConfig(remote)
            }

            hasLoadedFarmConfigOnce = true
            isFarmConfigSyncing = false
            onReady(remote != null)
        }
    }

    fun signOut() {
        authenticatedUser = null
        authRepository.clearSession()
        isFarmConfigSyncing = false
        farmConfigSyncError = null
        hasLoadedFarmConfigOnce = false
        timelineCacheStore.clearCacheJson()
    }

    suspend fun signIn(email: String, password: String): AuthUser {
        return authRepository.signIn(email = email, password = password)
    }

    suspend fun signUp(email: String, password: String, displayName: String): AuthUser {
        return authRepository.signUp(
            email = email,
            password = password,
            displayName = displayName,
        )
    }

    suspend fun signInWithGoogle(): AuthUser {
        val authorizationCode = googleAuthProvider.signIn()
            ?: throw IllegalStateException("Google Sign-In was cancelled or did not return a code.")
        return authRepository.signInWithGoogle(authorizationCode)
    }

    fun setMode(mode: AppMode) {
        selectedMode = mode
    }

    fun updateThemePreference(preference: ThemePreference) {
        themePreference = preference
    }

    fun setSetupMethod(method: SetupMethod) {
        selectedSetupMethod = method
    }

    fun selectZone(zoneId: String) {
        selectedZoneId = zoneId
    }

    fun selectTimelineDay(dayNumber: Int) {
        snapshot.timeline.firstOrNull { it.dayNumber == dayNumber }?.let {
            selectedTimelineDay = it
            isLoadingTimelineStageVisual = dayNumber in timelineLoadingStageVisualDays
            timelineStageVisual = timelineStageVisualByDay[dayNumber]
            timelineStageVisualError = timelineStageVisualErrorByDay[dayNumber]
            timelinePhotoAssessment = timelinePhotoAssessmentByDay[dayNumber]
            timelinePhotoAssessmentError = timelinePhotoAssessmentErrorByDay[dayNumber]
        }
    }

    fun loadTimelineStageVisual(dayNumber: Int, expectedStage: String) {
        timelineStageVisualByDay[dayNumber]?.let {
            timelineStageVisual = it
            timelineStageVisualError = timelineStageVisualErrorByDay[dayNumber]
            return
        }

        if (isFarmConfigSyncing) {
            return
        }

        if (dayNumber in timelineLoadingStageVisualDays) return
        timelineLoadingStageVisualDays = timelineLoadingStageVisualDays + dayNumber
        isLoadingTimelineStageVisual = selectedTimelineDay.dayNumber in timelineLoadingStageVisualDays
        timelineStageVisualError = null

        scope.launch {
            runCatching {
                withTimeout(45000) {
                    fieldInsightsRepository.generateTimelineStageVisual(
                        dayNumber = dayNumber,
                        expectedStage = expectedStage,
                        cropName = snapshot.farm.cropName,
                    )
                }
            }.onSuccess { visual ->
                    timelineStageVisualByDay = timelineStageVisualByDay + (dayNumber to visual)
                    lastUpdatedTimelineStageVisualDay = dayNumber
                    timelineStageVisualErrorByDay = timelineStageVisualErrorByDay - dayNumber
                timelineStageVisual = visual
                    persistTimelineCacheToCloudIfAuthenticated()
            }.onFailure { error ->
                    val message = error.message ?: "Unable to generate expected plant image."
                    timelineStageVisualErrorByDay = timelineStageVisualErrorByDay + (dayNumber to message)
                    timelineStageVisualError = message
            }
            timelineLoadingStageVisualDays = timelineLoadingStageVisualDays - dayNumber
            isLoadingTimelineStageVisual = selectedTimelineDay.dayNumber in timelineLoadingStageVisualDays
        }
    }

    fun regenerateTimelineStageVisual(dayNumber: Int, expectedStage: String) {
        timelineStageVisualByDay = timelineStageVisualByDay - dayNumber
        timelineStageVisualErrorByDay = timelineStageVisualErrorByDay - dayNumber
        if (lastUpdatedTimelineStageVisualDay == dayNumber) {
            lastUpdatedTimelineStageVisualDay = null
        }
        if (selectedTimelineDay.dayNumber == dayNumber) {
            timelineStageVisual = null
            timelineStageVisualError = null
        }

        // Sync cache removal first so stale cloud image cannot reappear after app restart
        // if fresh generation fails or user closes the app early.
        persistTimelineCacheToCloudIfAuthenticated()

        loadTimelineStageVisual(dayNumber = dayNumber, expectedStage = expectedStage)
    }

        fun cacheTimelineUploadedPhoto(dayNumber: Int, photoBase64: String, photoMimeType: String) {
            val cleaned = photoBase64.substringAfter("base64,").trim()
            if (cleaned.isBlank()) return

            timelineUploadByDay = timelineUploadByDay + (
                dayNumber to TimelineUploadCache(
                    photoBase64 = cleaned,
                    photoMimeType = photoMimeType,
                    updatedAtEpochMs = currentEpochMs(),
                )
            )
            persistTimelineCacheToCloudIfAuthenticated()
        }

    fun compareTimelinePhoto(
        dayNumber: Int,
        expectedStage: String,
        photoBase64: String,
        photoMimeType: String,
        userMarkedSimilar: Boolean?,
    ) {
        if (isAssessingTimelinePhoto) return
        val cleaned = photoBase64.substringAfter("base64,").trim()
        if (cleaned.isBlank()) {
            timelinePhotoAssessmentError = "Please provide a valid photo before comparison."
            return
        }

        cacheTimelineUploadedPhoto(dayNumber, cleaned, photoMimeType)

        isAssessingTimelinePhoto = true
        timelinePhotoAssessmentError = null

        scope.launch {
            runCatching {
                fieldInsightsRepository.assessTimelinePhoto(
                    dayNumber = dayNumber,
                    expectedStage = expectedStage,
                    cropName = snapshot.farm.cropName,
                    photoBase64 = cleaned,
                    photoMimeType = photoMimeType,
                    userMarkedSimilar = userMarkedSimilar,
                    userId = authenticatedUser?.userId,
                )
            }.onSuccess { assessment ->
                    timelinePhotoAssessmentByDay = timelinePhotoAssessmentByDay + (dayNumber to assessment)
                    timelinePhotoAssessmentErrorByDay = timelinePhotoAssessmentErrorByDay - dayNumber
                    timelineDynamicStatusByDay = timelineDynamicStatusByDay + (
                        dayNumber to deriveTimelineStatus(assessment, userMarkedSimilar)
                    )
                timelineSuggestedActionByDay = timelineSuggestedActionByDay + (dayNumber to assessment.recommendation)
                computeRecoveryForecast(dayNumber)?.let { forecast ->
                    timelineRecoveryForecastByDay = timelineRecoveryForecastByDay + (dayNumber to forecast)
                }
                timelinePhotoAssessment = assessment
                    persistTimelineCacheToCloudIfAuthenticated()
            }.onFailure { error ->
                    val message = error.message ?: "Unable to compare plant photo right now."
                    timelinePhotoAssessmentErrorByDay = timelinePhotoAssessmentErrorByDay + (dayNumber to message)
                    timelinePhotoAssessmentError = message
            }
            isAssessingTimelinePhoto = false
        }
    }

    fun startAiConversation(initialPrompt: String) {
        aiConversationMessages = emptyList()
        aiConversationError = null
        aiConversationProvider = null

        val firstPrompt = initialPrompt.trim()
        if (firstPrompt.isNotEmpty()) {
            sendAiConversationMessage(firstPrompt)
        }
    }

    fun sendAiConversationMessage(rawMessage: String) {
        if (isSendingAiConversationMessage) return

        val cleanMessage = rawMessage.trim()
        if (cleanMessage.isEmpty()) return

        val historyBeforeSend = aiConversationMessages.takeLast(12)

        aiConversationMessages = aiConversationMessages +
            ChatMessage(
                id = nextAiConversationMessageId(),
                sender = MessageSender.USER,
                content = cleanMessage,
                timestamp = "Now",
            )

        aiConversationError = null
        isSendingAiConversationMessage = true

        scope.launch {
            runCatching {
                withTimeout(45000) {
                    fieldInsightsRepository.consultAiChat(
                        message = cleanMessage,
                        history = historyBeforeSend,
                        userId = authenticatedUser?.userId,
                        context = AiChatContext(
                            farmName = snapshot.farm.farmName,
                            cropName = snapshot.farm.cropName,
                            mode = selectedMode.name,
                            latestRecommendation = snapshot.cropSummary.latestRecommendation,
                        ),
                    )
                }
            }.onSuccess { reply ->
                aiConversationProvider = reply.provider
                aiConversationMessages = aiConversationMessages +
                    ChatMessage(
                        id = nextAiConversationMessageId(),
                        sender = MessageSender.ASSISTANT,
                        content = reply.reply,
                        timestamp = "Now",
                    )
            }.onFailure { error ->
                aiConversationError = null
                aiConversationProvider = "gemini-offline-fallback"
                aiConversationMessages = aiConversationMessages +
                    ChatMessage(
                        id = nextAiConversationMessageId(),
                        sender = MessageSender.ASSISTANT,
                        content = buildAiUnavailableFallbackReply(cleanMessage),
                        timestamp = "Now",
                    )
            }

            isSendingAiConversationMessage = false
        }
    }

    fun searchKnowledgeBase(rawQuery: String, pageSize: Int = 5) {
        val cleanQuery = rawQuery.trim()
        if (cleanQuery.isEmpty() || isSearchingKnowledgeBase) return

        isSearchingKnowledgeBase = true
        knowledgeBaseError = null

        scope.launch {
            runCatching {
                fieldInsightsRepository.queryKnowledgeBase(
                    query = cleanQuery,
                    userId = authenticatedUser?.userId,
                    pageSize = pageSize,
                )
            }.onSuccess { reply ->
                knowledgeBaseLastQuery = reply.query
                knowledgeBaseResults = reply.results
                knowledgeBaseTotalResults = reply.totalResults
                knowledgeBaseProvider = reply.provider
            }.onFailure { error ->
                knowledgeBaseError = error.message ?: "Unable to search knowledge base right now."
                knowledgeBaseResults = emptyList()
                knowledgeBaseTotalResults = 0
                knowledgeBaseProvider = null
            }

            isSearchingKnowledgeBase = false
        }
    }

    fun clearKnowledgeBaseSearch() {
        knowledgeBaseResults = emptyList()
        knowledgeBaseLastQuery = ""
        knowledgeBaseTotalResults = 0
        knowledgeBaseProvider = null
        knowledgeBaseError = null
    }

    fun updateFarmBoundary(points: List<FarmPoint>) {
        farmBoundaryPoints = points
        polygonInsightsReport = null
        polygonInsightsError = null
        clearLotRecommendationState()
        // Step 2 defines the farm boundary; initialize Step 3 from that exact shape.
        lotSections = if (points.size >= 3) {
            listOf(
                LotSectionDraft(
                    id = "lot-1",
                    name = "Lot 1",
                    points = points,
                    cropPlan = "",
                    soilType = "",
                    waterAvailability = "",
                )
            )
        } else {
            emptyList()
        }
    }

    fun updateFarmSetupAddress(value: String) {
        farmSetupAddress = value
        if (value.trim().isEmpty()) {
            // Avoid carrying old geocode query when user clears the field.
            farmSetupMapQuery = ""
            farmSetupSearchTrigger = 0
        }
    }

    fun updateFarmSetupFarmName(value: String) {
        farmSetupFarmName = value
        val trimmed = value.trim()
        if (trimmed.isNotEmpty()) {
            snapshot = snapshot.copy(
                farm = snapshot.farm.copy(farmName = trimmed),
            )
        }
    }

    fun updateFarmSetupPlantingDate(value: String) {
        farmSetupPlantingDate = value
        snapshot = snapshot.copy(
            farm = snapshot.farm.copy(
                plantingDate = value.trim(),
            ),
        )
    }

    fun searchFarmSetupAddress() {
        val query = farmSetupAddress.trim()
        if (query.isBlank()) return
        farmSetupMapQuery = query
        farmSetupSearchTrigger += 1
    }

    fun useCurrentLocationForFarmSetup() {
        farmSetupUseCurrentLocationTrigger += 1
        updateFarmBoundary(emptyList())
    }

    fun continueToBoundaryDrawing() {
        val latestQuery = farmSetupAddress.trim()
        if (latestQuery.isNotEmpty()) {
            val locationChanged = latestQuery != farmSetupMapQuery
            if (locationChanged) {
                // Resolve the typed address only when it changed.
                // Otherwise keep user's latest manual pan/zoom from Step 1.
                farmSetupMapQuery = latestQuery
                farmSetupSearchTrigger += 1
            }

            // If Step 1 location changed, previously drawn Step 2/3 geometry is no longer valid.
            if (locationChanged) {
                updateFarmBoundary(emptyList())
            }
        } else {
            // No typed location: do not replay stale query in Step 2.
            farmSetupMapQuery = ""
            farmSetupSearchTrigger = 0
        }
        isFarmMapFrozen = true
    }

    fun updateLotSections(sections: List<LotSectionDraft>) {
        lotSections = sections
        clearLotRecommendationState()
    }

    fun updateLotPlantingDate(index: Int, value: String) {
        if (index in lotSections.indices) {
            val updated = lotSections.toMutableList()
            updated[index] = updated[index].copy(plantingDate = value)
            lotSections = updated
            clearLotRecommendationState()
        }
    }

    fun updateLotTotalAreaInput(value: String) {
        lotTotalAreaInput = value
        clearLotRecommendationState()
    }

    fun prepareNewFarmDraft() {
        val defaultBoundary = defaultFarmBoundary()
        val todayPlantingDate = currentIsoDate()
        farmBoundaryPoints = defaultBoundary
        farmSetupFarmName = ""
        farmSetupAddress = ""
        farmSetupMapQuery = farmSetupAddress
        farmSetupPlantingDate = todayPlantingDate
        farmSetupSearchTrigger = 0
        farmSetupUseCurrentLocationTrigger = 0
        isFarmMapFrozen = false
        lotTotalAreaInput = snapshot.farm.fieldSize
        snapshot = snapshot.copy(
            farm = snapshot.farm.copy(
                plantingDate = todayPlantingDate,
            ),
        )
        lotSections = listOf(
            LotSectionDraft(
                id = "lot-1",
                name = "Lot 1",
                points = defaultBoundary,
                cropPlan = "",
                soilType = "",
                waterAvailability = "",
            )
        )
        selectedSetupMethod = SetupMethod.MANUAL
        polygonInsightsReport = null
        polygonInsightsError = null
        clearLotRecommendationState()
    }

    fun startAddFarmFlow() {
        pendingActiveFarmBeforeAdd = currentFarmAsStored()
        pendingSnapshotFarmBeforeAdd = snapshot.farm
        isAddFarmDraftActive = true
        prepareNewFarmDraft()
    }

    fun cancelAddFarmDraftIfNeeded(): Boolean {
        if (!isAddFarmDraftActive) return false

        val previous = pendingActiveFarmBeforeAdd
        if (previous != null) {
            farmSetupFarmName = previous.farmName
            farmSetupAddress = previous.address
            farmSetupMapQuery = previous.mapQuery
            farmSetupPlantingDate = previous.plantingDate
            lotTotalAreaInput = previous.totalAreaInput
            selectedMode = previous.mode
            if (previous.boundaryPoints.size >= 3) {
                farmBoundaryPoints = previous.boundaryPoints
            }
            if (previous.lots.isNotEmpty()) {
                lotSections = previous.lots
            }
        }

        pendingSnapshotFarmBeforeAdd?.let { previousFarm ->
            snapshot = snapshot.copy(farm = previousFarm)
        }

        isAddFarmDraftActive = false
        pendingActiveFarmBeforeAdd = null
        pendingSnapshotFarmBeforeAdd = null
        return true
    }

    fun switchToStoredFarm(farmId: String) {
        val target = storedFarms.firstOrNull { it.id == farmId } ?: return

        currentFarmAsStored()?.let { current ->
            if (current.id != target.id) {
                storeFarmIfUnique(current, exceptId = target.id)
            }
        }

        farmSetupFarmName = target.farmName
        farmSetupAddress = target.address
        farmSetupMapQuery = target.mapQuery
        farmSetupPlantingDate = target.plantingDate
        lotTotalAreaInput = target.totalAreaInput
        selectedMode = target.mode

        if (target.boundaryPoints.size >= 3) {
            farmBoundaryPoints = target.boundaryPoints
        }
        if (target.lots.isNotEmpty()) {
            lotSections = target.lots
        }

        val primaryCrop = target.lots.firstOrNull()?.cropPlan?.trim().orEmpty()
        snapshot = snapshot.copy(
            farm = snapshot.farm.copy(
                farmName = target.farmName.ifBlank { snapshot.farm.farmName },
                cropName = if (primaryCrop.isNotBlank()) primaryCrop else snapshot.farm.cropName,
                location = target.address.ifBlank { snapshot.farm.location },
                fieldSize = target.totalAreaInput.ifBlank { snapshot.farm.fieldSize },
                mode = target.mode,
                plantingDate = target.plantingDate.ifBlank { snapshot.farm.plantingDate },
            ),
        )

        storedFarms = storedFarms.filterNot { it.id == target.id }

        timelineStageVisual = null
        timelineStageVisualByDay = emptyMap()
        timelineStageVisualError = null
        timelineStageVisualErrorByDay = emptyMap()
        timelinePhotoAssessment = null
        timelinePhotoAssessmentByDay = emptyMap()
        timelinePhotoAssessmentError = null
        timelinePhotoAssessmentErrorByDay = emptyMap()
        timelineUploadByDay = emptyMap()
        timelineDynamicStatusByDay = emptyMap()
        timelineActionDecisionByDay = emptyMap()
        timelineSuggestedActionByDay = emptyMap()
        timelineRecoveryForecastByDay = emptyMap()
        persistTimelineCacheToLocal()

        val userId = authenticatedUser?.userId
        if (!userId.isNullOrBlank()) {
            scope.launch {
                runCatching {
                    farmConfigRepository.upsertFarmConfig(buildFarmConfigDraft(userId))
                }
            }
        }
    }

    fun deleteStoredFarm(farmId: String) {
        if (farmId.isBlank()) return
        storedFarms = storedFarms.filterNot { it.id == farmId }
        persistFarmConfigSilently()
    }

    fun deleteActiveFarm(): Boolean {
        val currentId = currentFarmAsStored()?.id ?: return false
        val nextFarm = storedFarms.firstOrNull() ?: return false

        switchToStoredFarm(nextFarm.id)
        storedFarms = storedFarms.filterNot { it.id == currentId }
        persistFarmConfigSilently()
        return true
    }

    fun fetchEnvironmentalDataForLots() {
        if (isFetchingEnvData) return
        val needsEnvData = lotSections.any { it.soilType.isBlank() || it.waterAvailability.isBlank() }
        if (!needsEnvData) return

        isFetchingEnvData = true
        scope.launch {
            runCatching {
                val totalFarmAreaHa = parseAreaInputToHectares(lotTotalAreaInput)
                val boundaryArea = polygonArea(farmBoundaryPoints)
                val reports = mutableMapOf<String, FieldInsightReport>()
                
                lotSections = lotSections.map { lot ->
                    if (lot.points.size >= 3) {
                        val lotAreaHa = if (totalFarmAreaHa != null && boundaryArea > 0.0f) {
                            val ratio = (polygonArea(lot.points) / boundaryArea).coerceIn(0.0f, 1.0f)
                            totalFarmAreaHa * ratio
                        } else null
                        
                        val targetCrops = listOf(lot.cropPlan.trim()).filter { it.isNotEmpty() }
                        val report = fieldInsightsRepository.analyzePolygon(
                            points = lot.points,
                            targetCrops = targetCrops,
                            totalFarmAreaHectares = totalFarmAreaHa,
                            lotAreaHectares = lotAreaHa,
                        )
                        reports[lot.id] = report
                        
                        lot.copy(
                            soilType = inferSoilType(report.summary.soilMoistureMean, report.summary.ndviMean, report.summary.centroidLat, report.summary.centroidLng),
                            waterAvailability = inferWaterAvailability(report.summary.rainfallMm7d, report.summary.soilMoistureMean, report.summary.centroidLat, report.summary.centroidLng)
                        )
                    } else lot
                }
                
                // Update data source labels for the UI
                val sources = mutableMapOf<String, String>()
                reports.forEach { (lotId, report) ->
                    val earthSource = if (report.summary.sourceVerified && report.summary.source == "earth-engine-live") {
                        "Earth: live (verified)"
                    } else {
                        "Earth: unverified"
                    }
                    val aiSource = "Gemini: ${report.provider}"
                    sources[lotId] = "$earthSource | $aiSource"
                }
                lotRecommendationDataSourceByLotId = sources
                lotReports = reports
            }.onFailure { e ->
                println("FarmTwinAppState: Failed to fetch environmental data: $e")
                e.printStackTrace()
                lotRecommendationError = "Failed to fetch data: ${e.message}"
            }
            isFetchingEnvData = false
        }
    }

    fun loadDashboardCurrentWeather() {
        val location = farmSetupAddress.trim()
            .ifBlank { farmSetupMapQuery.trim() }
            .ifBlank { snapshot.farm.location.trim() }
        val centroid = lotReports.values.firstOrNull()?.summary
        if ((location.isBlank() && centroid == null) || isLoadingDashboardCurrentWeather) return

        isLoadingDashboardCurrentWeather = true
        scope.launch {
            runCatching {
                fieldInsightsRepository.getCurrentWeatherNow(
                    location = location,
                    latitude = centroid?.centroidLat,
                    longitude = centroid?.centroidLng,
                )
            }.onSuccess { weather ->
                dashboardCurrentWeatherNow = "${weather.condition} ${weather.temperatureC.toInt()} C"
            }.onFailure {
                dashboardCurrentWeatherNow = null
            }
            isLoadingDashboardCurrentWeather = false
        }
    }

    fun analyzeLotsForRecommendation() {
        if (isAnalyzingLots) return
        if (lotSections.isEmpty()) {
            lotRecommendationError = "No lots available for analysis."
            return
        }

        val requestedCrops = lotSections.map { it.cropPlan.trim() }.filter { it.isNotEmpty() }
        if (requestedCrops.isEmpty()) {
            lotRecommendationError = "Please assign crops to lots before analysis."
            return
        }

        isAnalyzingLots = true
        lotRecommendationError = null
        lotRecommendationBestLotId = null
        lotRecommendationReason = null
        lotRecommendationSuggestedCropByLotId = emptyMap()

        scope.launch {
            runCatching {
                val analyzed = lotSections.map { lot ->
                    if (lot.points.size < 3) {
                        AnalyzedLot(
                            lot = lot,
                            score = -1.0,
                            reason = "Lot boundary is incomplete.",
                            dataSource = "Unavailable",
                        )
                    } else {
                        val report = lotReports[lot.id]
                        if (report != null) {
                            val score = scoreLotForCrop(lot.cropPlan, report)
                            val reason = lotReason(lot.cropPlan, report)
                            val earthSource = if (report.summary.sourceVerified && report.summary.source == "earth-engine-live") {
                                "Earth: live (verified)"
                            } else {
                                "Earth: unverified"
                            }
                            val aiSource = "Gemini: ${report.provider}"
                            AnalyzedLot(
                                lot = lot,
                                score = score,
                                reason = reason,
                                dataSource = "$earthSource | $aiSource",
                                report = report,
                            )
                        } else {
                           AnalyzedLot(
                                lot = lot,
                                score = -1.0,
                                reason = "Environmental data missing.",
                                dataSource = "Unavailable",
                            )
                        }
                    }
                }

                // Removed the `lotSections = analyzed.map { it.lot }` because env data is already set in fetch
                analyzed
            }.onSuccess { analyzed ->
                val validLots = analyzed.filter { it.score >= 0 && it.report != null }
                if (validLots.size != analyzed.size) {
                    lotRecommendationError = "Unable to analyze all lots. Ensure each lot has a valid boundary."
                } else {
                    val assignment = recommendCropAssignment(validLots, requestedCrops)
                    lotRecommendationSuggestedCropByLotId = assignment.cropByLotId
                    lotRecommendationBestLotId = null

                    val changedLots = validLots.filter { analyzedLot ->
                        val suggested = assignment.cropByLotId[analyzedLot.lot.id].orEmpty()
                        analyzedLot.lot.cropPlan.trim().lowercase() != suggested.trim().lowercase()
                    }

                    if (changedLots.isEmpty()) {
                        lotRecommendationReason = "Current crop-to-lot setup is already optimal based on Earth Engine + Gemini scoring."
                    } else {
                        val swapsSummary = changedLots.joinToString(separator = " | ") { analyzedLot ->
                            val suggested = assignment.cropByLotId[analyzedLot.lot.id].orEmpty()
                            "${analyzedLot.lot.name}: ${analyzedLot.lot.cropPlan} -> $suggested"
                        }
                        lotRecommendationReason = "Recommended crop reassignment: $swapsSummary"
                    }
                }
            }.onFailure { error ->
                lotRecommendationError = error.message ?: "Failed to analyze lots right now."
            }

            isAnalyzingLots = false
        }
    }

    fun finalizeLotRecommendation(followRecommendation: Boolean) {
        if (followRecommendation) {
            val suggestedByLotId = lotRecommendationSuggestedCropByLotId
            if (suggestedByLotId.isNotEmpty()) {
                lotSections = lotSections.map { lot ->
                    suggestedByLotId[lot.id]?.let { suggested ->
                        lot.copy(cropPlan = suggested)
                    } ?: lot
                }
            }
        }

        // Guarantee that lots have some data for dashboard presentation if skipped or failed
        lotSections = lotSections.map { lot ->
            lot.copy(
                soilType = lot.soilType.trim().ifEmpty { "Silty Loam (Assumed)" },
                waterAvailability = lot.waterAvailability.trim().ifEmpty { "Medium (Assumed)" }
            )
        }

        clearLotRecommendationState()
    }

    fun completeLotRecommendationAndPersist(
        followRecommendation: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        val userId = authenticatedUser?.userId
        isFarmConfigSyncing = true
        farmConfigSyncError = null
        lotRecommendationError = null

        scope.launch {
            if (isFetchingEnvData) {
                // Wait for the data to finish fetching before persisting if needed, 
                // but actually it's fine just to wait a bit or we can just proceed because it updates state.
            }
            val needsEnvData = lotSections.any { it.soilType.isBlank() || it.waterAvailability.isBlank() }
            if (needsEnvData && !isFetchingEnvData) {
                // Do inline fetch just in case
                runCatching {
                    val totalFarmAreaHa = parseAreaInputToHectares(lotTotalAreaInput)
                    val boundaryArea = polygonArea(farmBoundaryPoints)
                    lotSections = lotSections.map { lot ->
                        if (lot.points.size >= 3) {
                            val lotAreaHa = if (totalFarmAreaHa != null && boundaryArea > 0.0f) {
                                val ratio = (polygonArea(lot.points) / boundaryArea).coerceIn(0.0f, 1.0f)
                                totalFarmAreaHa * ratio
                            } else null
                            
                            val targetCrops = listOf(lot.cropPlan.trim()).filter { it.isNotEmpty() }
                            val report = fieldInsightsRepository.analyzePolygon(
                                points = lot.points,
                                targetCrops = targetCrops,
                                totalFarmAreaHectares = totalFarmAreaHa,
                                lotAreaHectares = lotAreaHa,
                            )
                            lot.copy(
                                soilType = inferSoilType(report.summary.soilMoistureMean, report.summary.ndviMean, report.summary.centroidLat, report.summary.centroidLng),
                                waterAvailability = inferWaterAvailability(report.summary.rainfallMm7d, report.summary.soilMoistureMean, report.summary.centroidLat, report.summary.centroidLng)
                            )
                        } else lot
                    }
                }
            }

            finalizeLotRecommendation(followRecommendation = followRecommendation)

            if (isAddFarmDraftActive) {
                pendingActiveFarmBeforeAdd?.let { previous ->
                    storeFarmIfUnique(previous)
                }
                isAddFarmDraftActive = false
                pendingActiveFarmBeforeAdd = null
                pendingSnapshotFarmBeforeAdd = null
            }

            if (userId.isNullOrBlank()) {
                isFarmConfigSyncing = false
                onComplete(true)
                return@launch
            }

            val draft = buildFarmConfigDraft(userId)
            val success = runCatching {
                farmConfigRepository.upsertFarmConfig(draft)
                val latest = farmConfigRepository.fetchLatestFarmConfig(userId)
                if (latest != null) {
                    applyRemoteFarmConfig(latest)
                }
            }.fold(
                onSuccess = { true },
                onFailure = {
                    farmConfigSyncError = it.message ?: "Unable to sync farm setup to cloud."
                    lotRecommendationError = farmConfigSyncError
                    false
                },
            )

            isFarmConfigSyncing = false
            onComplete(success)
        }
    }

    fun loadFarmConfigFromCloud(force: Boolean = false) {
        val userId = authenticatedUser?.userId ?: return
        if (!force && isFarmConfigSyncing) return

        isFarmConfigSyncing = true
        farmConfigSyncError = null

        scope.launch {
            runCatching {
                farmConfigRepository.fetchLatestFarmConfig(userId)
            }.onSuccess { remote ->
                if (remote != null) {
                    applyRemoteFarmConfig(remote)
                }
            }.onFailure { error ->
                farmConfigSyncError = error.message ?: "Unable to load farm setup from cloud."
            }

            hasLoadedFarmConfigOnce = true
            isFarmConfigSyncing = false
        }
    }

    fun submitPolygonForInsights() {
        if (farmBoundaryPoints.size < 3 || isSubmittingPolygon) return

        isSubmittingPolygon = true
        polygonInsightsError = null
        scope.launch {
            runCatching {
                fieldInsightsRepository.analyzePolygon(
                    points = farmBoundaryPoints,
                    totalFarmAreaHectares = parseAreaInputToHectares(lotTotalAreaInput),
                )
            }.onSuccess { report ->
                polygonInsightsReport = report
            }.onFailure { error ->
                polygonInsightsError = error.message ?: "Unable to analyze polygon right now."
            }
            isSubmittingPolygon = false
        }
    }

    fun currentZone(): ZoneInfo {
        return snapshot.zones.first { it.id == selectedZoneId }
    }

    fun getOrGenerateCropSummaryForLot(lot: LotSectionDraft): com.alleyz15.farmtwinai.domain.model.CropSummary {
        if (lot.cropSummary != null) return lot.cropSummary

        val base = snapshot.cropSummary
        if (lot.cropPlan.isBlank()) return base
        
        val seed = (lot.cropPlan.hashCode() + lot.id.hashCode()).let { kotlin.math.abs(it) }
        
        val exactDays = calculateMockDaysPassed(lot.plantingDate)
        val mockDay = if (lot.plantingDate?.isNotBlank() == true) {
            exactDays.coerceAtLeast(0)
        } else {
            timelineUnlockedMaxDayNumber().coerceAtLeast(1)
        }
        
        val scoreOffset = (seed % 15) - 7
        val mockScore = (base.currentFarmHealthScore + scoreOffset).coerceIn(40, 100)
        
        val stageChoices = listOf("Seedling", "Vegetative start", "Vegetative expansion", "Flowering", "Fruiting", "Maturation")
        val stageIndex = ((seed / 10) % stageChoices.size)
        val expectedStage = stageChoices[stageIndex]
        
        val growthLow = (seed % 20) + 10
        val growthHigh = growthLow + (seed % 15) + 5
        
        val recommendations = listOf(
            "Inspect drainage in lower zones.",
            "Monitor nutrient response after next feed.",
            "Schedule supplementary watering tomorrow.",
            "Current moisture aligns with ideal curve.",
            "Check for early pest damage at edges."
        )
        val recIndex = ((seed / 100) % recommendations.size)

        return com.alleyz15.farmtwinai.domain.model.CropSummary(
            currentDay = mockDay,
            currentFarmHealthScore = mockScore,
            expectedGrowthRange = "$growthLow% - $growthHigh%",
            expectedStage = expectedStage,
            urgentZones = seed % 3,
            latestRecommendation = recommendations[recIndex],
        )
    }

    fun recordAction(actionType: ActionType, actionState: ActionState) {
        val summary = when (actionState) {
            ActionState.DONE -> "Action marked done; timeline flagged for follow-up simulation."
            ActionState.NOT_YET -> "Action is still pending; reminder remains active."
            ActionState.SKIP -> "Action skipped; no re-simulation requested."
        }
        appendActionRecord(
            actionType = actionType,
            actionState = actionState,
            dayLabel = "Day ${snapshot.cropSummary.currentDay}",
            summary = summary,
        )
    }

    fun recordTimelineAction(dayNumber: Int, actionType: ActionType, actionState: ActionState) {
        timelineActionDecisionByDay = timelineActionDecisionByDay + (
            dayNumber to TimelineActionDecision(
                actionType = actionType,
                state = actionState,
                updatedAtEpochMs = currentEpochMs(),
            )
        )
        timelineSuggestedActionByDay = timelineSuggestedActionByDay + (
            dayNumber to recommendedActionTextForDay(dayNumber)
        )
        computeRecoveryForecast(dayNumber)?.let { forecast ->
            timelineRecoveryForecastByDay = timelineRecoveryForecastByDay + (dayNumber to forecast)
        }
        persistTimelineCacheToCloudIfAuthenticated()

        val forecast = recoveryForecastForDay(dayNumber)
        val summary = buildString {
            append(
                when (actionState) {
                    ActionState.DONE -> "Farmer applied this action."
                    ActionState.NOT_YET -> "Farmer has not applied this action yet."
                    ActionState.SKIP -> "Farmer skipped this action."
                }
            )
            if (forecast != null) {
                append(" Trend ${forecast.trend.name.lowercase()}.")
                append(" ETA ${forecast.etaDaysMin}-${forecast.etaDaysMax} days.")
                append(" Confidence ${forecast.confidencePercent}%.")
            }
        }

        appendActionRecord(
            actionType = actionType,
            actionState = actionState,
            dayLabel = "Day $dayNumber",
            summary = summary,
        )

        val userId = authenticatedUser?.userId
        if (!userId.isNullOrBlank()) {
            scope.launch {
                runCatching {
                    fieldInsightsRepository.logTimelineAction(
                        userId = userId,
                        dayNumber = dayNumber,
                        actionType = actionType,
                        actionState = actionState,
                        summary = summary,
                        cropName = snapshot.farm.cropName,
                    )
                }

                if (actionState == ActionState.DONE) {
                    runCatching {
                        fieldInsightsRepository.trackActionFollowUp(
                            userId = userId,
                            dayNumber = dayNumber,
                            cropName = snapshot.farm.cropName,
                            issueType = defaultActionIssueType(actionType),
                            actionTaken = actionType.name.lowercase().replace('_', ' '),
                            note = summary,
                        )
                    }.onSuccess { followUp ->
                        // Store follow-up with action decision for persistent timeline display
                        timelineActionDecisionByDay = timelineActionDecisionByDay + (
                            dayNumber to (timelineActionDecisionByDay[dayNumber] ?: TimelineActionDecision(
                                actionType = actionType,
                                state = actionState,
                                updatedAtEpochMs = currentEpochMs(),
                            )).copy(followUp = followUp)
                        )
                        persistTimelineCacheToCloudIfAuthenticated()
                        val banner = buildString {
                            if (followUp.followUpQuestion.isNotBlank()) {
                                append("Follow-up: ")
                                append(followUp.followUpQuestion)
                            }
                            if (followUp.nextBestAction.isNotBlank()) {
                                if (isNotBlank()) append("  ")
                                append("Next: ")
                                append(followUp.nextBestAction)
                            }
                        }.ifBlank {
                            "Follow-up generated. Re-check within 24 hours and upload the next photo."
                        }
                        // Keep persistent card only; avoid duplicate transient banner.
                    }.onFailure {
                        // Keep Timeline clean; persistent follow-up card is primary surface.
                    }
                }
            }
        }
    }

    private fun defaultActionIssueType(actionType: ActionType): String {
        return when (actionType) {
            ActionType.WATERED -> "water stress"
            ActionType.IMPROVED_DRAINAGE -> "drainage issue"
            ActionType.ADJUSTED_FERTILIZER -> "nutrient imbalance"
            ActionType.APPLIED_PESTICIDE_FUNGICIDE -> "pest or disease pressure"
            ActionType.PRUNED_AFFECTED_LEAVES -> "leaf damage"
            ActionType.MONITORED_ONLY -> "monitoring only"
            ActionType.REPLANTED -> "severe plant loss"
        }
    }

    fun setTimelineActionBanner(message: String?) {
        timelineActionBannerMessage = message?.trim().takeUnless { it.isNullOrBlank() }
    }

    fun consumeTimelineActionBanner() {
        timelineActionBannerMessage = null
    }

    fun clearTimelineActionFollowUp(dayNumber: Int) {
        val current = timelineActionDecisionByDay[dayNumber] ?: return
        if (current.followUp == null) return
        timelineActionDecisionByDay = timelineActionDecisionByDay + (dayNumber to current.copy(followUp = null))
        persistTimelineCacheToCloudIfAuthenticated()
    }

    private fun appendActionRecord(
        actionType: ActionType,
        actionState: ActionState,
        dayLabel: String,
        summary: String,
    ) {
        snapshot = snapshot.copy(
            actionRecords = listOf(
                com.alleyz15.farmtwinai.domain.model.ActionRecord(
                    id = "action-${snapshot.actionRecords.size + 1}",
                    actionType = actionType,
                    state = actionState,
                    dayLabel = dayLabel,
                    resultSummary = summary,
                )
            ) + snapshot.actionRecords
        )
    }

    private fun calculateMockDaysPassed(plantingDate: String?): Int {
        if (plantingDate.isNullOrBlank()) return 0
        val plantedEpochDay = isoDateToEpochDay(plantingDate) ?: return 0
        val todayEpochDay = isoDateToEpochDay(currentIsoDate()) ?: return 0
        return (todayEpochDay - plantedEpochDay).toInt()
    }

    private fun isoDateToEpochDay(value: String): Long? {
        val parts = value.split("-")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null

        var y = year.toLong()
        val m = month.toLong()
        val d = day.toLong()
        y -= if (m <= 2L) 1L else 0L
        val era = if (y >= 0L) y / 400L else (y - 399L) / 400L
        val yoe = y - era * 400L
        val mp = m + if (m > 2L) -3L else 9L
        val doy = (153L * mp + 2L) / 5L + d - 1L
        val doe = yoe * 365L + yoe / 4L - yoe / 100L + doy
        return era * 146097L + doe - 719468L
    }

    private fun timelineDateCappedMaxDay(minTimelineDay: Int, maxTimelineDay: Int): Int {
        val plantingDate = snapshot.farm.plantingDate.trim().ifBlank { farmSetupPlantingDate.trim() }
        if (plantingDate.isBlank()) return minTimelineDay

        val elapsedDays = calculateMockDaysPassed(plantingDate).coerceAtLeast(0)
        val cappedByCalendar = minTimelineDay + elapsedDays
        return cappedByCalendar.coerceIn(minTimelineDay, maxTimelineDay)
    }

    private fun nextAiConversationMessageId(): String {
        aiConversationMessageIndex += 1
        return "live-msg-$aiConversationMessageIndex"
    }

    private fun defaultFarmBoundary(): List<FarmPoint> {
        return listOf(
            FarmPoint(0.50f, 0.25f),
            FarmPoint(0.85f, 0.75f),
            FarmPoint(0.15f, 0.75f),
        )
    }

    private fun currentFarmAsStored(): StoredFarm? {
        val farmName = farmSetupFarmName.trim().ifBlank { snapshot.farm.farmName.trim() }
        val mapQuery = farmSetupMapQuery.trim()
        val address = farmSetupAddress.trim().ifBlank { mapQuery }
        val totalArea = lotTotalAreaInput.trim().ifBlank { snapshot.farm.fieldSize.trim() }
        val boundary = if (farmBoundaryPoints.size >= 3) farmBoundaryPoints else lotSections.firstOrNull()?.points.orEmpty()
        val lotsCopy = lotSections.map { lot -> lot.copy(points = lot.points.toList()) }
        val farmId = farmIdentity(farmName = farmName, address = address, mapQuery = mapQuery, lots = lotsCopy)

        if (farmName.isBlank() || lotsCopy.isEmpty()) return null

        return StoredFarm(
            id = farmId,
            farmName = farmName,
            address = address,
            mapQuery = mapQuery,
            totalAreaInput = totalArea,
            mode = selectedMode,
            plantingDate = farmSetupPlantingDate.trim().ifBlank { snapshot.farm.plantingDate.trim() },
            createdAtEpochMs = resolveFarmCreatedAtEpochMs(farmId),
            boundaryPoints = boundary,
            lots = lotsCopy,
        )
    }

    private fun resolveFarmCreatedAtEpochMs(farmId: String): Long {
        val existing = storedFarms.firstOrNull { it.id == farmId }?.createdAtEpochMs ?: 0L
        return if (existing > 0L) existing else currentWallClockEpochMs()
    }

    private fun storeFarmIfUnique(farm: StoredFarm, exceptId: String? = null) {
        if (!isStoredFarmReady(farm)) return
        if (farm.id == exceptId) return
        if (storedFarms.any { it.id == farm.id }) return
        storedFarms = listOf(farm) + storedFarms.filterNot { it.id == exceptId }
    }

    private fun isStoredFarmReady(farm: StoredFarm): Boolean {
        if (farm.farmName.isBlank()) return false
        if (farm.lots.isEmpty()) return false
        return farm.lots.all { lot ->
            lot.points.size >= 3 &&
                lot.cropPlan.trim().isNotEmpty()
        }
    }

    private fun farmIdentity(
        farmName: String,
        address: String,
        mapQuery: String,
        lots: List<LotSectionDraft>,
    ): String {
        val cropFingerprint = lots
            .map { lot -> "${lot.name.trim().lowercase()}:${lot.cropPlan.trim().lowercase()}" }
            .sorted()
            .joinToString("|")
        return "${farmName.trim().lowercase()}::${address.trim().lowercase()}::${mapQuery.trim().lowercase()}::${lots.size}::${cropFingerprint}"
    }

    private fun keepPointInsideBoundary(candidate: FarmPoint, boundaryPoints: List<FarmPoint>): FarmPoint {
        if (boundaryPoints.size < 3 || isPointInsidePolygon(candidate, boundaryPoints)) return candidate

        val center = polygonCentroid(boundaryPoints)
        var t = 1.0f
        while (t > 0.0f) {
            val trial = FarmPoint(
                x = center.x + (candidate.x - center.x) * t,
                y = center.y + (candidate.y - center.y) * t,
            )
            if (isPointInsidePolygon(trial, boundaryPoints)) return trial
            t -= 0.05f
        }

        return center
    }

    private fun isPointInsidePolygon(point: FarmPoint, polygon: List<FarmPoint>): Boolean {
        if (polygon.size < 3) return true

        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            val intersects = ((pi.y > point.y) != (pj.y > point.y)) &&
                (point.x < (pj.x - pi.x) * (point.y - pi.y) / ((pj.y - pi.y).takeIf { it != 0f } ?: 0.000001f) + pi.x)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    private fun polygonCentroid(points: List<FarmPoint>): FarmPoint {
        if (points.isEmpty()) return FarmPoint(0.5f, 0.5f)
        val x = points.sumOf { it.x.toDouble() } / points.size
        val y = points.sumOf { it.y.toDouble() } / points.size
        return FarmPoint(x.toFloat(), y.toFloat())
    }

    private fun polygonArea(points: List<FarmPoint>): Float {
        if (points.size < 3) return 0f
        var sum = 0f
        for (i in points.indices) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            sum += p1.x * p2.y - p2.x * p1.y
        }
        return kotlin.math.abs(sum) * 0.5f
    }

    private fun parseAreaInputToHectares(raw: String): Double? {
        val normalized = raw.trim().lowercase()
        if (normalized.isEmpty()) return null

        val value = Regex("""([0-9]+(?:\\.[0-9]+)?)""")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?: return null

        val isAcre = normalized.contains("acre") || normalized.contains("ac")
        return if (isAcre) value * 0.40468564224 else value
    }

    private fun buildFarmConfigDraft(
        userId: String,
    ): FarmConfigDraft {
        val activeFarm = currentFarmAsStored()
        val prioritizedPhotoDays = buildList {
            add(selectedTimelineDay.dayNumber)
            timelinePhotoAssessmentByDay.keys.sortedDescending().forEach { add(it) }
            timelineUploadByDay.keys.sortedDescending().forEach { add(it) }
        }.distinct().take(7)

        val photoCache = prioritizedPhotoDays.mapNotNull { dayNumber ->
            val upload = timelineUploadByDay[dayNumber] ?: return@mapNotNull null
            TimelinePhotoCacheEntry(
                dayNumber = dayNumber,
                photoBase64 = upload.photoBase64,
                photoMimeType = upload.photoMimeType,
                updatedAtEpochMs = upload.updatedAtEpochMs,
            )
        }

        val prioritizedDays = buildList {
            lastUpdatedTimelineStageVisualDay?.let { add(it) }
            add(selectedTimelineDay.dayNumber)
            timelineStageVisualByDay.keys.sortedDescending().forEach { add(it) }
        }.distinct().take(14)

        val stageCache = prioritizedDays.mapNotNull { dayNumber ->
            val visual = timelineStageVisualByDay[dayNumber] ?: return@mapNotNull null
            TimelineStageVisualCacheEntry(
                dayNumber = dayNumber,
                title = visual.title,
                description = visual.description,
                imageDataUrl = visual.imageDataUrl,
                provider = visual.provider,
                updatedAtEpochMs = currentEpochMs(),
            )
        }

        val assessmentCache = timelinePhotoAssessmentByDay.map { (dayNumber, assessment) ->
            TimelinePhotoAssessmentCacheEntry(
                dayNumber = dayNumber,
                expectedStage = assessment.expectedStage,
                cropName = assessment.cropName,
                similarityScore = assessment.similarityScore,
                isSimilar = assessment.isSimilar,
                observedStage = assessment.observedStage,
                recommendation = assessment.recommendation,
                rationale = assessment.rationale,
                provider = assessment.provider,
                updatedAtEpochMs = currentEpochMs(),
            )
        }

        val actionDecisionCache = timelineActionDecisionByDay.mapNotNull { (dayNumber, decision) ->
            val followUp = decision.followUp ?: return@mapNotNull null
            TimelineActionDecisionCacheEntry(
                dayNumber = dayNumber,
                actionType = decision.actionType,
                state = decision.state,
                updatedAtEpochMs = decision.updatedAtEpochMs,
                nextBestAction = followUp.nextBestAction,
                followUpQuestion = followUp.followUpQuestion,
                confidence = followUp.confidence,
                riskLevel = followUp.riskLevel,
                provider = followUp.provider,
            )
        }

        val timelineInsightCache = (timelineSuggestedActionByDay.keys + timelineRecoveryForecastByDay.keys)
            .sortedDescending()
            .mapNotNull { dayNumber ->
            val recommendation = timelineSuggestedActionByDay[dayNumber].orEmpty()
            val forecast = timelineRecoveryForecastByDay[dayNumber]
            if (recommendation.isBlank() && forecast == null) return@mapNotNull null

            val effectiveForecast = forecast ?: TimelineRecoveryForecast(
                sourceDayNumber = dayNumber,
                trend = RecoveryTrend.UNKNOWN,
                etaDaysMin = 1,
                etaDaysMax = 1,
                confidencePercent = 0,
                confidenceTier = ForecastConfidenceTier.LOW,
                isUrgent = false,
            )
            TimelineInsightCacheEntry(
                dayNumber = dayNumber,
                recommendedActionText = recommendation,
                timelineStatus = timelineDynamicStatusByDay[dayNumber],
                sourceDayNumber = effectiveForecast.sourceDayNumber,
                trend = effectiveForecast.trend,
                etaDaysMin = effectiveForecast.etaDaysMin,
                etaDaysMax = effectiveForecast.etaDaysMax,
                confidencePercent = effectiveForecast.confidencePercent,
                confidenceTier = effectiveForecast.confidenceTier,
                isUrgent = effectiveForecast.isUrgent,
                updatedAtEpochMs = currentEpochMs(),
            )
        }

        val farmsForSync = buildList {
            if (activeFarm != null) {
                add(activeFarm)
            }
            addAll(storedFarms)
        }
            .distinctBy { it.id }
            .map { farm ->
                FarmConfigFarmEntry(
                    id = farm.id,
                    farmName = farm.farmName,
                    address = farm.address,
                    mapQuery = farm.mapQuery,
                    totalAreaInput = farm.totalAreaInput,
                    mode = farm.mode,
                    plantingDate = farm.plantingDate,
                    createdAtEpochMs = farm.createdAtEpochMs,
                    boundaryPoints = farm.boundaryPoints,
                    lots = farm.lots,
                )
            }

        return FarmConfigDraft(
            userId = userId,
            activeFarmId = activeFarm?.id.orEmpty(),
            farms = farmsForSync,
            farmName = activeFarm?.farmName ?: farmSetupFarmName.trim(),
            address = activeFarm?.address ?: farmSetupAddress.trim(),
            mapQuery = activeFarm?.mapQuery ?: farmSetupMapQuery.trim(),
            totalAreaInput = activeFarm?.totalAreaInput ?: lotTotalAreaInput.trim(),
            mode = activeFarm?.mode ?: selectedMode,
            plantingDate = activeFarm?.plantingDate ?: farmSetupPlantingDate.trim(),
            boundaryPoints = activeFarm?.boundaryPoints ?: farmBoundaryPoints,
            lots = activeFarm?.lots ?: lotSections,
            timelinePhotoCache = photoCache,
            timelineStageVisualCache = stageCache,
            timelineAssessmentCache = assessmentCache,
            timelineActionDecisionCache = actionDecisionCache,
            timelineInsightCache = timelineInsightCache,
        )
    }

    private fun applyRemoteFarmConfig(remote: FarmConfigRemote) {
        val previousFarm = currentFarmAsStored()
        val remoteActiveId = remote.activeFarmId.ifBlank { remote.farms.firstOrNull()?.id.orEmpty() }
        val resolvedActive = remote.farms.firstOrNull { it.id == remoteActiveId } ?: remote.farms.firstOrNull()
        val inactiveRemoteFarms = remote.farms.filterNot { it.id == resolvedActive?.id }

        if (inactiveRemoteFarms.isNotEmpty()) {
            storedFarms = inactiveRemoteFarms.map { farm ->
                StoredFarm(
                    id = farm.id,
                    farmName = farm.farmName,
                    address = farm.address,
                    mapQuery = farm.mapQuery,
                    totalAreaInput = farm.totalAreaInput,
                    mode = farm.mode,
                    plantingDate = farm.plantingDate,
                    createdAtEpochMs = farm.createdAtEpochMs,
                    boundaryPoints = farm.boundaryPoints,
                    lots = farm.lots,
                )
            }
        }

        if (!resolvedActive?.farmName.isNullOrBlank()) {
            farmSetupFarmName = resolvedActive.farmName
        } else if (remote.farmName.isNotBlank()) {
            farmSetupFarmName = remote.farmName
        }

        if (!resolvedActive?.address.isNullOrBlank()) {
            farmSetupAddress = resolvedActive.address
        } else if (remote.address.isNotBlank()) {
            farmSetupAddress = remote.address
        }

        if (!resolvedActive?.mapQuery.isNullOrBlank()) {
            farmSetupMapQuery = resolvedActive.mapQuery
        } else if (remote.mapQuery.isNotBlank()) {
            farmSetupMapQuery = remote.mapQuery
        }

        if (!resolvedActive?.plantingDate.isNullOrBlank()) {
            farmSetupPlantingDate = resolvedActive.plantingDate
        } else if (remote.plantingDate.isNotBlank()) {
            farmSetupPlantingDate = remote.plantingDate
        }

        if (!resolvedActive?.totalAreaInput.isNullOrBlank()) {
            lotTotalAreaInput = resolvedActive.totalAreaInput
        } else if (remote.totalAreaInput.isNotBlank()) {
            lotTotalAreaInput = remote.totalAreaInput
        }

        selectedMode = resolvedActive?.mode ?: remote.mode

        val activeBoundary = resolvedActive?.boundaryPoints ?: remote.boundaryPoints
        if (activeBoundary.size >= 3) {
            farmBoundaryPoints = activeBoundary
        }

        val activeLots = resolvedActive?.lots ?: remote.lots
        if (activeLots.isNotEmpty()) {
            lotSections = activeLots
        }

        val primaryCrop = activeLots.firstOrNull()?.cropPlan?.trim().orEmpty()
        snapshot = snapshot.copy(
            farm = snapshot.farm.copy(
                farmName = (resolvedActive?.farmName ?: remote.farmName).ifBlank { snapshot.farm.farmName },
                cropName = if (primaryCrop.isNotBlank()) primaryCrop else snapshot.farm.cropName,
                location = (resolvedActive?.address ?: remote.address).ifBlank { snapshot.farm.location },
                fieldSize = (resolvedActive?.totalAreaInput ?: remote.totalAreaInput).ifBlank { snapshot.farm.fieldSize },
                mode = resolvedActive?.mode ?: remote.mode,
                plantingDate = (resolvedActive?.plantingDate ?: remote.plantingDate).ifBlank { snapshot.farm.plantingDate },
            ),
        )

        if (remote.timelinePhotoCache.isNotEmpty()) {
            val remoteUploads = remote.timelinePhotoCache.associate { entry ->
                entry.dayNumber to TimelineUploadCache(
                    photoBase64 = entry.photoBase64,
                    photoMimeType = entry.photoMimeType,
                    updatedAtEpochMs = entry.updatedAtEpochMs,
                )
            }
            timelineUploadByDay = mergeUploadsByRecency(
                local = timelineUploadByDay,
                remote = remoteUploads,
            )
        }

        if (remote.timelineStageVisualCache.isNotEmpty()) {
            val remoteStages = remote.timelineStageVisualCache.associate { entry ->
                entry.dayNumber to TimelineStageVisual(
                    dayNumber = entry.dayNumber,
                    expectedStage = "",
                    cropName = snapshot.farm.cropName,
                    title = entry.title,
                    description = entry.description,
                    imageDataUrl = entry.imageDataUrl,
                    prompt = "",
                    provider = entry.provider,
                )
            }
            timelineStageVisualByDay = remoteStages + timelineStageVisualByDay
        }

        if (remote.timelineAssessmentCache.isNotEmpty()) {
            val remoteAssessments = remote.timelineAssessmentCache.associate { entry ->
                entry.dayNumber to TimelinePhotoAssessment(
                    dayNumber = entry.dayNumber,
                    expectedStage = entry.expectedStage,
                    cropName = entry.cropName,
                    similarityScore = entry.similarityScore,
                    isSimilar = entry.isSimilar,
                    observedStage = entry.observedStage,
                    recommendation = entry.recommendation,
                    rationale = entry.rationale,
                    provider = entry.provider,
                )
            }
            timelinePhotoAssessmentByDay = remoteAssessments + timelinePhotoAssessmentByDay
        }

        if (remote.timelineActionDecisionCache.isNotEmpty()) {
            val remoteDecisions = remote.timelineActionDecisionCache.associate { entry ->
                entry.dayNumber to TimelineActionDecision(
                    actionType = entry.actionType,
                    state = entry.state,
                    updatedAtEpochMs = entry.updatedAtEpochMs,
                    followUp = ActionTrackerFollowUp(
                        nextBestAction = entry.nextBestAction,
                        followUpQuestion = entry.followUpQuestion,
                        confidence = entry.confidence,
                        riskLevel = entry.riskLevel,
                        provider = entry.provider,
                    ),
                )
            }
            timelineActionDecisionByDay = mergeActionDecisionsByRecency(
                local = timelineActionDecisionByDay,
                remote = remoteDecisions,
            )
        }

        if (remote.timelineInsightCache.isNotEmpty()) {
            val remoteSuggested = remote.timelineInsightCache.associate { entry ->
                entry.dayNumber to entry.recommendedActionText
            }
            val remoteForecasts = remote.timelineInsightCache.associate { entry ->
                entry.dayNumber to TimelineRecoveryForecast(
                    sourceDayNumber = entry.sourceDayNumber,
                    trend = entry.trend,
                    etaDaysMin = entry.etaDaysMin,
                    etaDaysMax = entry.etaDaysMax,
                    confidencePercent = entry.confidencePercent,
                    confidenceTier = entry.confidenceTier,
                    isUrgent = entry.isUrgent,
                )
            }
            val remoteStatuses = remote.timelineInsightCache
                .mapNotNull { entry -> entry.timelineStatus?.let { entry.dayNumber to it } }
                .toMap()
            timelineSuggestedActionByDay = remoteSuggested + timelineSuggestedActionByDay
            timelineRecoveryForecastByDay = remoteForecasts + timelineRecoveryForecastByDay
            timelineDynamicStatusByDay = remoteStatuses + timelineDynamicStatusByDay
        }

        val selectedDayNumber = selectedTimelineDay.dayNumber
        timelineStageVisual = timelineStageVisualByDay[selectedDayNumber]
        timelinePhotoAssessment = timelinePhotoAssessmentByDay[selectedDayNumber]
        timelineStageVisualError = timelineStageVisualErrorByDay[selectedDayNumber]
        timelinePhotoAssessmentError = timelinePhotoAssessmentErrorByDay[selectedDayNumber]
        refreshSnapshotCurrentDayFromTimeline()
        persistTimelineCacheToLocal()

        val activeId = farmIdentity(
            farmName = farmSetupFarmName.ifBlank { snapshot.farm.farmName },
            address = farmSetupAddress.ifBlank { snapshot.farm.location },
            mapQuery = farmSetupMapQuery.ifBlank { farmSetupAddress.ifBlank { snapshot.farm.location } },
            lots = lotSections,
        )
        if (previousFarm != null && previousFarm.id != activeId) {
            storeFarmIfUnique(previousFarm, exceptId = activeId)
        }
    }

    private fun persistTimelineCacheToCloudIfAuthenticated() {
        persistTimelineCacheToLocal()

        val userId = authenticatedUser?.userId ?: return
        val effectiveFarmName = farmSetupFarmName.trim().ifBlank { snapshot.farm.farmName.trim() }
        if (effectiveFarmName.isBlank() || lotSections.isEmpty()) return

        timelineCacheSyncRequested = true
        if (timelineCacheSyncInFlight) return
        timelineCacheSyncInFlight = true

        scope.launch {
            try {
                while (timelineCacheSyncRequested) {
                    timelineCacheSyncRequested = false
                    syncTimelineCacheDraftWithRetry(userId)
                }
            } finally {
                timelineCacheSyncInFlight = false
                if (timelineCacheSyncRequested) {
                    persistTimelineCacheToCloudIfAuthenticated()
                }
            }
        }
    }

    private suspend fun syncTimelineCacheDraftWithRetry(userId: String) {
        runCatching {
            farmConfigRepository.upsertFarmConfig(buildFarmConfigDraft(userId))
        }.onFailure { error ->
            farmConfigSyncError = error.message ?: "Unable to sync timeline media cache to cloud."
        }
    }

    private fun restoreTimelineCacheFromLocalStore() {
        val raw = timelineCacheStore.readCacheJson() ?: return
        val root = runCatching { cacheJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return

        val cachedUserId = root["userId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val currentUserId = authenticatedUser?.userId.orEmpty()
        if (cachedUserId.isNotBlank() && currentUserId.isNotBlank() && cachedUserId != currentUserId) {
            return
        }

        val photos = root["timelinePhotoCache"]?.jsonArray?.mapNotNull { node ->
            val obj = node.jsonObject
            val dayNumber = obj["dayNumber"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val photoBase64 = obj["photoBase64"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (photoBase64.isBlank()) return@mapNotNull null
            dayNumber to TimelineUploadCache(
                photoBase64 = photoBase64,
                photoMimeType = obj["photoMimeType"]?.jsonPrimitive?.contentOrNull ?: "image/jpeg",
                updatedAtEpochMs = obj["updatedAtEpochMs"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }.orEmpty().toMap()

        val visuals = root["timelineStageVisualCache"]?.jsonArray?.mapNotNull { node ->
            val obj = node.jsonObject
            val dayNumber = obj["dayNumber"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val imageDataUrl = obj["imageDataUrl"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (imageDataUrl.isBlank()) return@mapNotNull null
            dayNumber to TimelineStageVisual(
                dayNumber = dayNumber,
                expectedStage = obj["expectedStage"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                cropName = obj["cropName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                imageDataUrl = imageDataUrl,
                prompt = "",
                provider = obj["provider"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }.orEmpty().toMap()

        val assessments = root["timelineAssessmentCache"]?.jsonArray?.mapNotNull { node ->
            val obj = node.jsonObject
            val dayNumber = obj["dayNumber"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val recommendation = obj["recommendation"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val rationale = obj["rationale"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (recommendation.isBlank() && rationale.isBlank()) return@mapNotNull null
            dayNumber to TimelinePhotoAssessment(
                dayNumber = dayNumber,
                expectedStage = obj["expectedStage"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                cropName = obj["cropName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                similarityScore = obj["similarityScore"]?.jsonPrimitive?.intOrNull ?: 0,
                isSimilar = obj["isSimilar"]?.jsonPrimitive?.booleanOrNull ?: false,
                observedStage = obj["observedStage"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                recommendation = recommendation,
                rationale = rationale,
                provider = obj["provider"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }.orEmpty().toMap()

        val decisions = root["timelineActionDecisionCache"]?.jsonArray?.mapNotNull { node ->
            val obj = node.jsonObject
            val dayNumber = obj["dayNumber"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val actionTypeRaw = obj["actionType"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val stateRaw = obj["state"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val actionType = runCatching { ActionType.valueOf(actionTypeRaw) }.getOrNull() ?: return@mapNotNull null
            val state = runCatching { ActionState.valueOf(stateRaw) }.getOrNull() ?: return@mapNotNull null
            val followUpObj = obj["followUp"]?.jsonObject
            val followUp = followUpObj?.let {
                ActionTrackerFollowUp(
                    nextBestAction = it["nextBestAction"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    followUpQuestion = it["followUpQuestion"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    confidence = it["confidence"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0,
                    riskLevel = it["riskLevel"]?.jsonPrimitive?.contentOrNull ?: "unknown",
                    provider = it["provider"]?.jsonPrimitive?.contentOrNull ?: "agent-action-tracker-v1",
                )
            }
            dayNumber to TimelineActionDecision(
                actionType = actionType,
                state = state,
                updatedAtEpochMs = obj["updatedAtEpochMs"]?.jsonPrimitive?.longOrNull ?: 0L,
                followUp = followUp,
            )
        }.orEmpty().toMap()

        val insights = root["timelineInsightCache"]?.jsonArray?.mapNotNull { node ->
            val obj = node.jsonObject
            val dayNumber = obj["dayNumber"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val recommendedAction = obj["recommendedActionText"]?.jsonPrimitive?.contentOrNull.orEmpty()

            val trend = obj["trend"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { RecoveryTrend.valueOf(it) }.getOrNull() }
            val confidenceTier = obj["confidenceTier"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { ForecastConfidenceTier.valueOf(it) }.getOrNull() }
            val forecast = if (trend != null && confidenceTier != null) {
                TimelineRecoveryForecast(
                    sourceDayNumber = obj["sourceDayNumber"]?.jsonPrimitive?.intOrNull ?: dayNumber,
                    trend = trend,
                    etaDaysMin = obj["etaDaysMin"]?.jsonPrimitive?.intOrNull ?: 1,
                    etaDaysMax = obj["etaDaysMax"]?.jsonPrimitive?.intOrNull ?: 1,
                    confidencePercent = obj["confidencePercent"]?.jsonPrimitive?.intOrNull ?: 0,
                    confidenceTier = confidenceTier,
                    isUrgent = obj["isUrgent"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
            } else {
                null
            }

            if (recommendedAction.isBlank() && forecast == null) {
                null
            } else {
                val status = obj["timelineStatus"]?.jsonPrimitive?.contentOrNull
                    ?.let { runCatching { TimelineStatus.valueOf(it) }.getOrNull() }
                dayNumber to Triple(recommendedAction, forecast, status)
            }
        }.orEmpty().toMap()

        if (photos.isNotEmpty()) {
            timelineUploadByDay = photos
        }
        if (visuals.isNotEmpty()) {
            timelineStageVisualByDay = visuals
        }
        if (assessments.isNotEmpty()) {
            timelinePhotoAssessmentByDay = assessments
        }
        if (decisions.isNotEmpty()) {
            timelineActionDecisionByDay = decisions
        }
        if (insights.isNotEmpty()) {
            val suggestedActions = insights
                .mapValuesNotNull { (_, value) -> value.first.takeIf { it.isNotBlank() } }
            val forecasts = insights
                .mapValuesNotNull { (_, value) -> value.second }
            val statuses = insights
                .mapValuesNotNull { (_, value) -> value.third }

            if (suggestedActions.isNotEmpty()) {
                timelineSuggestedActionByDay = suggestedActions
            }
            if (forecasts.isNotEmpty()) {
                timelineRecoveryForecastByDay = forecasts
            }
            if (statuses.isNotEmpty()) {
                timelineDynamicStatusByDay = statuses
            }
        }

        val selectedDayNumber = selectedTimelineDay.dayNumber
        timelineStageVisual = timelineStageVisualByDay[selectedDayNumber]
        timelinePhotoAssessment = timelinePhotoAssessmentByDay[selectedDayNumber]
        refreshSnapshotCurrentDayFromTimeline()
    }

    private fun persistTimelineCacheToLocal() {
        val prioritizedPhotoDays = buildList {
            add(selectedTimelineDay.dayNumber)
            timelinePhotoAssessmentByDay.keys.sortedDescending().forEach { add(it) }
            timelineUploadByDay.keys.sortedDescending().forEach { add(it) }
        }.distinct().take(7)

        val prioritizedVisualDays = buildList {
            lastUpdatedTimelineStageVisualDay?.let { add(it) }
            add(selectedTimelineDay.dayNumber)
            timelineStageVisualByDay.keys.sortedDescending().forEach { add(it) }
        }.distinct().take(14)

        val payload = buildJsonObject {
            put("userId", authenticatedUser?.userId.orEmpty())
            put("timelinePhotoCache", buildJsonArray {
                prioritizedPhotoDays.forEach { dayNumber ->
                    val upload = timelineUploadByDay[dayNumber] ?: return@forEach
                    add(buildJsonObject {
                        put("dayNumber", dayNumber)
                        put("photoBase64", upload.photoBase64)
                        put("photoMimeType", upload.photoMimeType)
                        put("updatedAtEpochMs", upload.updatedAtEpochMs)
                    })
                }
            })
            put("timelineStageVisualCache", buildJsonArray {
                prioritizedVisualDays.forEach { dayNumber ->
                    val visual = timelineStageVisualByDay[dayNumber] ?: return@forEach
                    add(buildJsonObject {
                        put("dayNumber", dayNumber)
                        put("expectedStage", visual.expectedStage)
                        put("cropName", visual.cropName)
                        put("title", visual.title)
                        put("description", visual.description)
                        put("imageDataUrl", visual.imageDataUrl)
                        put("provider", visual.provider)
                    })
                }
            })
            put("timelineAssessmentCache", buildJsonArray {
                timelinePhotoAssessmentByDay.entries
                    .sortedByDescending { it.key }
                    .take(14)
                    .forEach { (dayNumber, assessment) ->
                        add(buildJsonObject {
                            put("dayNumber", dayNumber)
                            put("expectedStage", assessment.expectedStage)
                            put("cropName", assessment.cropName)
                            put("similarityScore", assessment.similarityScore)
                            put("isSimilar", assessment.isSimilar)
                            put("observedStage", assessment.observedStage)
                            put("recommendation", assessment.recommendation)
                            put("rationale", assessment.rationale)
                            put("provider", assessment.provider)
                        })
                    }
            })
            put("timelineActionDecisionCache", buildJsonArray {
                timelineActionDecisionByDay.entries
                    .sortedByDescending { it.key }
                    .take(21)
                    .forEach { (dayNumber, decision) ->
                        add(buildJsonObject {
                            put("dayNumber", dayNumber)
                            put("actionType", decision.actionType.name)
                            put("state", decision.state.name)
                            put("updatedAtEpochMs", decision.updatedAtEpochMs)
                            decision.followUp?.let { followUp ->
                                put("followUp", buildJsonObject {
                                    put("nextBestAction", followUp.nextBestAction)
                                    put("followUpQuestion", followUp.followUpQuestion)
                                    put("confidence", followUp.confidence)
                                    put("riskLevel", followUp.riskLevel)
                                    put("provider", followUp.provider)
                                })
                            }
                        })
                    }
            })
            put("timelineInsightCache", buildJsonArray {
                (timelineSuggestedActionByDay.keys + timelineRecoveryForecastByDay.keys)
                    .sortedDescending()
                    .take(21)
                    .forEach { dayNumber ->
                        val recommendation = timelineSuggestedActionByDay[dayNumber].orEmpty()
                        val forecast = timelineRecoveryForecastByDay[dayNumber]
                        if (recommendation.isBlank() && forecast == null) return@forEach

                        add(buildJsonObject {
                            put("dayNumber", dayNumber)
                            if (recommendation.isNotBlank()) {
                                put("recommendedActionText", recommendation)
                            }
                            timelineDynamicStatusByDay[dayNumber]?.let { put("timelineStatus", it.name) }
                            if (forecast != null) {
                                put("sourceDayNumber", forecast.sourceDayNumber)
                                put("trend", forecast.trend.name)
                                put("etaDaysMin", forecast.etaDaysMin)
                                put("etaDaysMax", forecast.etaDaysMax)
                                put("confidencePercent", forecast.confidencePercent)
                                put("confidenceTier", forecast.confidenceTier.name)
                                put("isUrgent", forecast.isUrgent)
                            }
                        })
                    }
            })
        }

        timelineCacheStore.writeCacheJson(payload.toString())
    }

    private fun persistFarmConfigSilently() {
        val userId = authenticatedUser?.userId ?: return
        scope.launch {
            runCatching {
                farmConfigRepository.upsertFarmConfig(buildFarmConfigDraft(userId))
            }
        }
    }

    private fun currentEpochMs(): Long {
        cacheUpdateSequence += 1L
        return cacheUpdateSequence
    }

    private fun currentIsoDate(): String {
        val nowEpochMillis = currentWallClockEpochMs()
        val offsetMillis = localUtcOffsetMinutes(nowEpochMillis).toLong() * 60_000L
        return epochMillisToIsoDateUtc(nowEpochMillis + offsetMillis)
    }

    private fun currentWallClockEpochMs(): Long = wallClockEpochMillis()

    private fun epochMillisToIsoDateUtc(epochMillis: Long): String {
        val epochDays = epochMillis / 86_400_000L
        var z = epochDays + 719468L
        val era = if (z >= 0L) z / 146097L else (z - 146096L) / 146097L
        val doe = z - era * 146097L
        val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
        var y = yoe + era * 400L
        val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
        val mp = (5L * doy + 2L) / 153L
        val d = doy - (153L * mp + 2L) / 5L + 1L
        val m = mp + if (mp < 10L) 3L else -9L
        if (m <= 2L) y += 1L

        val year = y.toString().padStart(4, '0')
        val month = m.toString().padStart(2, '0')
        val day = d.toString().padStart(2, '0')
        return "$year-$month-$day"
    }

    private fun refreshSnapshotCurrentDayFromTimeline() {
        val derivedDay = timelineUnlockedMaxDayNumber().coerceAtLeast(1)
        if (snapshot.cropSummary.currentDay != derivedDay) {
            snapshot = snapshot.copy(
                cropSummary = snapshot.cropSummary.copy(currentDay = derivedDay)
            )
        }
    }

    private fun mergeUploadsByRecency(
        local: Map<Int, TimelineUploadCache>,
        remote: Map<Int, TimelineUploadCache>,
    ): Map<Int, TimelineUploadCache> {
        if (local.isEmpty()) return remote
        if (remote.isEmpty()) return local
        val merged = remote.toMutableMap()
        local.forEach { (day, localValue) ->
            val remoteValue = merged[day]
            if (remoteValue == null || localValue.updatedAtEpochMs >= remoteValue.updatedAtEpochMs) {
                merged[day] = localValue
            }
        }
        return merged
    }

    private fun mergeActionDecisionsByRecency(
        local: Map<Int, TimelineActionDecision>,
        remote: Map<Int, TimelineActionDecision>,
    ): Map<Int, TimelineActionDecision> {
        if (local.isEmpty()) return remote
        if (remote.isEmpty()) return local
        val merged = remote.toMutableMap()
        local.forEach { (day, localValue) ->
            val remoteValue = merged[day]
            if (remoteValue == null || localValue.updatedAtEpochMs >= remoteValue.updatedAtEpochMs) {
                merged[day] = localValue
            }
        }
        return merged
    }

    private fun buildAiUnavailableFallbackReply(latestMessage: String): String {
        val normalized = latestMessage.lowercase()
        val hint = when {
            "pest" in normalized || "insect" in normalized || "worm" in normalized ->
                "Inspect 10 random plants now, isolate hotspots, and prepare targeted control instead of blanket spraying."
            "water" in normalized || "irrig" in normalized || "dry" in normalized ->
                "Check root-zone moisture first. Water only if topsoil is dry at 5-8 cm depth to avoid overwatering stress."
            "disease" in normalized || "fung" in normalized || "blight" in normalized ->
                "Remove heavily affected leaves, improve airflow, and avoid late-evening overhead watering today."
            else ->
                "Follow the latest timeline recommendation, then upload a new crop photo after 24 hours for re-evaluation."
        }

        return buildString {
            append("I could not reach Gemini right now, so here is safe fallback guidance.\n")
            append("1) ")
            append(hint)
            append("\n2) Document what you observe (leaf color, spots, wilting, soil moisture).")
            append("\n3) Re-open AI Chat shortly, or use Knowledge Base for source-backed references.")
        }
    }

    private inline fun <K, V, R : Any> Map<K, V>.mapValuesNotNull(transform: (Map.Entry<K, V>) -> R?): Map<K, R> {
        val result = LinkedHashMap<K, R>()
        for (entry in entries) {
            val mapped = transform(entry) ?: continue
            result[entry.key] = mapped
        }
        return result
    }

    private fun clearLotRecommendationState() {
        isAnalyzingLots = false
        lotRecommendationBestLotId = null
        lotRecommendationReason = null
        lotRecommendationError = null
        lotRecommendationDataSourceByLotId = emptyMap()
        lotRecommendationSuggestedCropByLotId = emptyMap()
    }

    fun timelineStatusForDay(dayNumber: Int): TimelineStatus? {
        return timelineDynamicStatusByDay[dayNumber]
    }

    fun recommendedActionTextForDay(dayNumber: Int): String {
        val recommendation = timelineSuggestedActionByDay[dayNumber]
            ?: timelinePhotoAssessmentByDay[dayNumber]?.recommendation
            ?: snapshot.cropSummary.latestRecommendation

        if (recommendation.contains("mock", ignoreCase = true) || recommendation.contains("fallback", ignoreCase = true)) {
            return "Run photo comparison to generate the latest AI recommendation."
        }
        return recommendation
    }

    fun defaultActionTypeForDay(dayNumber: Int): ActionType {
        return recommendedActionTypesForDay(dayNumber).first()
    }

    fun recommendedActionTypesForDay(dayNumber: Int): List<ActionType> {
        val recommendation = recommendedActionTextForDay(dayNumber)
        val primary = inferActionTypeFromRecommendation(recommendation)
        val fallbacks = listOf(
            ActionType.ADJUSTED_FERTILIZER,
            ActionType.IMPROVED_DRAINAGE,
            ActionType.WATERED,
            ActionType.PRUNED_AFFECTED_LEAVES,
            ActionType.MONITORED_ONLY,
        )
        return (listOf(primary) + fallbacks).distinct()
    }

    fun latestActionDecisionForDay(dayNumber: Int): TimelineActionDecision? {
        return timelineActionDecisionByDay[dayNumber]
    }

    private fun followUpTargetDayForActionDay(actionDayNumber: Int): Int {
        val maxTimelineDay = snapshot.timeline.maxOfOrNull { it.dayNumber } ?: (actionDayNumber + 1)
        return (actionDayNumber + 1).coerceAtMost(maxTimelineDay)
    }

    fun followUpForTimelineDay(dayNumber: Int): ActionTrackerFollowUp? {
        if (dayNumber <= 1) return null
        return timelineActionDecisionByDay[dayNumber - 1]?.followUp
    }

    fun followUpSourceActionDayForTimelineDay(dayNumber: Int): Int? {
        if (dayNumber <= 1) return null
        return timelineActionDecisionByDay[dayNumber - 1]
            ?.followUp
            ?.let { dayNumber - 1 }
    }

    fun clearTimelineFollowUpForTimelineDay(dayNumber: Int) {
        val sourceDay = followUpSourceActionDayForTimelineDay(dayNumber) ?: return
        clearTimelineActionFollowUp(sourceDay)
    }

    fun latestPendingTimelineFollowUp(): PendingTimelineFollowUp? {
        val latest = timelineActionDecisionByDay.entries
            .mapNotNull { (actionDayNumber, decision) ->
                decision.followUp?.let { followUp ->
                    PendingTimelineFollowUp(
                        actionDayNumber = actionDayNumber,
                        targetDayNumber = followUpTargetDayForActionDay(actionDayNumber),
                        followUp = followUp,
                    )
                }
            }
            .maxByOrNull { it.targetDayNumber }

        return latest
    }

    fun hasAssessmentForDay(dayNumber: Int): Boolean {
        return timelinePhotoAssessmentByDay[dayNumber] != null
    }

    fun timelineUnlockedMaxDayNumber(): Int {
        val minTimelineDay = snapshot.timeline.minOfOrNull { it.dayNumber } ?: 1
        val maxTimelineDay = snapshot.timeline.maxOfOrNull { it.dayNumber } ?: minTimelineDay
        return timelineDateCappedMaxDay(
            minTimelineDay = minTimelineDay,
            maxTimelineDay = maxTimelineDay,
        )
    }

    fun clearTimelineUploadedPhoto(dayNumber: Int) {
        timelineUploadByDay = timelineUploadByDay - dayNumber
        timelinePhotoAssessmentByDay = timelinePhotoAssessmentByDay - dayNumber
        timelinePhotoAssessmentErrorByDay = timelinePhotoAssessmentErrorByDay - dayNumber
        timelineDynamicStatusByDay = timelineDynamicStatusByDay - dayNumber
        timelineSuggestedActionByDay = timelineSuggestedActionByDay - dayNumber
        timelineRecoveryForecastByDay = timelineRecoveryForecastByDay - dayNumber

        if (selectedTimelineDay.dayNumber == dayNumber) {
            timelinePhotoAssessment = null
            timelinePhotoAssessmentError = null
        }

        persistTimelineCacheToCloudIfAuthenticated()
    }

    fun recoveryForecastForDay(dayNumber: Int): TimelineRecoveryForecast? {
        timelineRecoveryForecastByDay[dayNumber]?.let { return it }
        return computeRecoveryForecast(dayNumber)
    }

    private fun computeRecoveryForecast(dayNumber: Int): TimelineRecoveryForecast? {
        val assessedDays = timelinePhotoAssessmentByDay.keys
            .filter { it <= dayNumber }
            .sorted()
        if (assessedDays.isEmpty()) return null

        val windowDays = assessedDays.takeLast(5)
        val sourceDay = windowDays.last()
        val current = timelinePhotoAssessmentByDay[sourceDay] ?: return null
        val previousDay = windowDays.dropLast(1).lastOrNull()
        val previous = previousDay?.let { timelinePhotoAssessmentByDay[it] }
        val delta = if (previous != null) current.similarityScore - previous.similarityScore else 0
        val windowScores = windowDays.mapNotNull { timelinePhotoAssessmentByDay[it]?.similarityScore }

        val trend = when {
            previous == null -> inferInitialRecoveryTrend(current.similarityScore)
            delta >= 6 -> RecoveryTrend.IMPROVING
            delta <= -6 -> RecoveryTrend.WORSENING
            else -> RecoveryTrend.STABLE
        }

        val recentScores = windowScores.takeLast(3)
        val worseningStreak = when {
            recentScores.size < 3 -> false
            else -> recentScores[2] < recentScores[1] && recentScores[1] < recentScores[0]
        }

        val severity = (100 - current.similarityScore).coerceIn(0, 100)
        val baseMin = when {
            severity <= 20 -> 2
            severity <= 35 -> 3
            severity <= 50 -> 5
            else -> 7
        }
        val baseMax = when {
            severity <= 20 -> 4
            severity <= 35 -> 6
            severity <= 50 -> 9
            else -> 14
        }

        val trendMinAdjust = when (trend) {
            RecoveryTrend.IMPROVING -> -1
            RecoveryTrend.WORSENING -> 2
            RecoveryTrend.STABLE -> 0
            RecoveryTrend.UNKNOWN -> 0
        }
        val trendMaxAdjust = when (trend) {
            RecoveryTrend.IMPROVING -> -2
            RecoveryTrend.WORSENING -> 4
            RecoveryTrend.STABLE -> 0
            RecoveryTrend.UNKNOWN -> 1
        }

        val etaMin = (baseMin + trendMinAdjust).coerceAtLeast(1)
        val etaMax = (baseMax + trendMaxAdjust).coerceAtLeast(etaMin)

        val sampleCount = windowScores.size
        var confidence = when {
            sampleCount >= 5 -> 80
            sampleCount >= 3 -> 67
            else -> 52
        }
        if (trend != RecoveryTrend.UNKNOWN) confidence += 6
        if (sourceDay < dayNumber) confidence -= 5
        val volatility = if (windowScores.size >= 2) {
            windowScores.zipWithNext { a, b -> kotlin.math.abs(b - a) }.average()
        } else {
            0.0
        }
        if (volatility > 12.0) confidence -= 8
        if (volatility > 18.0) confidence -= 6
        confidence = confidence.coerceIn(35, 95)

        val confidenceTier = when {
            sampleCount >= 5 && confidence >= 78 -> ForecastConfidenceTier.HIGH
            sampleCount >= 3 -> ForecastConfidenceTier.MEDIUM
            else -> ForecastConfidenceTier.LOW
        }

        val isUrgent = (trend == RecoveryTrend.WORSENING && current.similarityScore < 65) || worseningStreak

        return TimelineRecoveryForecast(
            sourceDayNumber = sourceDay,
            trend = trend,
            etaDaysMin = etaMin,
            etaDaysMax = etaMax,
            confidencePercent = confidence,
            confidenceTier = confidenceTier,
            isUrgent = isUrgent,
        )
    }

    private fun inferInitialRecoveryTrend(similarityScore: Int): RecoveryTrend {
        return when {
            similarityScore >= 75 -> RecoveryTrend.IMPROVING
            similarityScore <= 55 -> RecoveryTrend.WORSENING
            else -> RecoveryTrend.STABLE
        }
    }

    fun openTimelineForDay(dayNumber: Int) {
        val minTimelineDay = snapshot.timeline.minOfOrNull { it.dayNumber } ?: dayNumber
        val unlockedMaxDay = timelineUnlockedMaxDayNumber()
        val targetDay = dayNumber
            .coerceAtLeast(minTimelineDay)
            .coerceAtMost(unlockedMaxDay)

        val nearest = snapshot.timeline
            .filter { it.dayNumber in minTimelineDay..unlockedMaxDay }
            .minByOrNull { kotlin.math.abs(it.dayNumber - targetDay) }
            ?.dayNumber
            ?: snapshot.timeline.minByOrNull { it.dayNumber }?.dayNumber
            ?: targetDay
        selectTimelineDay(nearest)
    }

    private fun deriveTimelineStatus(
        assessment: TimelinePhotoAssessment,
        userMarkedSimilar: Boolean?,
    ): TimelineStatus {
        val score = assessment.similarityScore
        val aiSaysSimilar = assessment.isSimilar
        val userAiMismatch = userMarkedSimilar != null && userMarkedSimilar != aiSaysSimilar

        return when {
            userAiMismatch -> TimelineStatus.WARNING
            score >= 80 && aiSaysSimilar -> TimelineStatus.NORMAL
            score >= 55 -> TimelineStatus.WARNING
            else -> TimelineStatus.ACTION_TAKEN
        }
    }

    private fun inferActionTypeFromRecommendation(recommendation: String): ActionType {
        val text = recommendation.lowercase()
        return when {
            text.contains("drain") || text.contains("waterlog") || text.contains("standing water") -> ActionType.IMPROVED_DRAINAGE
            text.contains("water") || text.contains("irrig") || text.contains("dry") || text.contains("wilt") -> ActionType.WATERED
            text.contains("fertil") || text.contains("nutrient") || text.contains("nitrogen") || text.contains("potassium") || text.contains("phosph") -> ActionType.ADJUSTED_FERTILIZER
            text.contains("pest") || text.contains("fung") || text.contains("disease") || text.contains("spot") || text.contains("rot") || text.contains("insect") -> ActionType.APPLIED_PESTICIDE_FUNGICIDE
            text.contains("prun") || text.contains("remove") || text.contains("leaf") -> ActionType.PRUNED_AFFECTED_LEAVES
            else -> ActionType.MONITORED_ONLY
        }
    }

    private fun weatherLabelForReport(report: FieldInsightReport): String {
        val temp = report.summary.averageTempC
        val rainfall = report.summary.rainfallMm7d
        val condition = when {
            rainfall >= 22.0 -> "Rainy"
            temp >= 32.0 -> "Hot"
            temp <= 23.0 -> "Cool"
            else -> "Clear"
        }
        val roundedTemp = temp.toInt()
        return "$condition $roundedTemp C"
    }

    private fun recommendCropAssignment(
        analyzedLots: List<AnalyzedLot>,
        requestedCrops: List<String>,
    ): CropAssignmentResult {
        val lots = analyzedLots.sortedBy { it.lot.id }
        var bestScore = Double.NEGATIVE_INFINITY
        var bestAssignment: IntArray? = null
        val used = BooleanArray(requestedCrops.size)
        val currentAssignment = IntArray(lots.size) { -1 }

        fun dfs(lotIndex: Int, scoreSoFar: Double) {
            if (lotIndex == lots.size) {
                if (scoreSoFar > bestScore) {
                    bestScore = scoreSoFar
                    bestAssignment = currentAssignment.copyOf()
                }
                return
            }

            for (cropIndex in requestedCrops.indices) {
                if (used[cropIndex]) continue
                used[cropIndex] = true
                currentAssignment[lotIndex] = cropIndex
                val crop = requestedCrops[cropIndex]
                val score = scoreLotForCrop(crop, lots[lotIndex].report!!)
                dfs(lotIndex + 1, scoreSoFar + score)
                used[cropIndex] = false
                currentAssignment[lotIndex] = -1
            }
        }

        dfs(0, 0.0)

        val assignment = bestAssignment ?: IntArray(lots.size) { index -> index.coerceAtMost(requestedCrops.lastIndex) }
        val cropByLotId = lots.mapIndexed { index, analyzedLot ->
            val cropIndex = assignment[index]
            val crop = requestedCrops.getOrElse(cropIndex) { analyzedLot.lot.cropPlan }
            analyzedLot.lot.id to crop
        }.toMap()

        return CropAssignmentResult(
            cropByLotId = cropByLotId,
            totalScore = bestScore,
        )
    }

    private fun inferSoilType(soilMoisture: Double, ndvi: Double, lat: Double, lng: Double): String {
        val moisture = soilMoisture.coerceIn(0.0, 1.0)
        val vegetation = ndvi.coerceIn(0.0, 1.0)

        // Sparse backend outputs should not be forced into a soil class.
        if (moisture <= 0.02 && vegetation <= 0.05) {
            return "Unknown (Need better data)"
        }

        val geoSeed = kotlin.math.abs((lat * 1000 + lng * 1000).toInt()) % 21
        val geoBias = (geoSeed - 10) / 100.0
        val blendedWetness = (moisture * 0.75 + vegetation * 0.25 + geoBias).coerceIn(0.0, 1.0)

        return when {
            blendedWetness < 0.22 -> "Sandy"
            blendedWetness < 0.36 -> "Sandy Loam"
            blendedWetness < 0.50 -> "Loam"
            blendedWetness < 0.64 -> "Silty Loam"
            blendedWetness < 0.78 -> "Clay Loam"
            else -> "Clay"
        }
    }

    private fun inferWaterAvailability(rainfallMm7d: Double, soilMoisture: Double, lat: Double, lng: Double): String {
        val pseudoRandom = kotlin.math.abs((lat * 25000 + lng * 35000).toInt()) % 100
        val adjustedRainfall = rainfallMm7d + pseudoRandom
        
        return when {
            adjustedRainfall >= 65 || soilMoisture >= 0.60 -> "High"
            adjustedRainfall >= 25 || soilMoisture >= 0.38 -> "Medium"
            else -> "Low"
        }
    }

    private fun scoreLotForCrop(crop: String, report: FieldInsightReport): Double {
        val normalized = crop.trim().lowercase()
        val recommendation = report.recommendations.firstOrNull { it.cropName.trim().lowercase() == normalized }

        val suitabilityScore = when (recommendation?.suitability?.trim()?.lowercase()) {
            "high" -> 3.0
            "moderate" -> 2.0
            "low" -> 1.0
            else -> if (recommendation != null) 1.5 else 0.0
        }

        val moistureFit = 1.0 - abs(report.summary.soilMoistureMean - 0.5).coerceIn(0.0, 1.0)
        val ndviBoost = report.summary.ndviMean.coerceIn(0.0, 1.0)
        return suitabilityScore * 2.0 + moistureFit + ndviBoost
    }

    private fun lotReason(crop: String, report: FieldInsightReport): String {
        val normalized = crop.trim().lowercase()
        val recommendation = report.recommendations.firstOrNull { it.cropName.trim().lowercase() == normalized }
        return recommendation?.rationale
            ?: "No direct crop match from Gemini. Used vegetation and moisture indicators from Earth Engine summary."
    }
}

private data class AnalyzedLot(
    val lot: LotSectionDraft,
    val score: Double,
    val reason: String,
    val dataSource: String,
    val report: FieldInsightReport? = null,
)

private data class CropAssignmentResult(
    val cropByLotId: Map<String, String>,
    val totalScore: Double,
)
