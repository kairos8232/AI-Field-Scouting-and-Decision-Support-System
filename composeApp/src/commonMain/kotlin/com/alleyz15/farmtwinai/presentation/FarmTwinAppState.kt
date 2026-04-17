package com.alleyz15.farmtwinai.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alleyz15.farmtwinai.auth.AuthUser
import com.alleyz15.farmtwinai.data.analysis.FieldInsightsRepository
import com.alleyz15.farmtwinai.data.auth.AuthRepository
import com.alleyz15.farmtwinai.data.farm.FarmConfigDraft
import com.alleyz15.farmtwinai.data.farm.FarmConfigRemote
import com.alleyz15.farmtwinai.data.farm.FarmConfigRepository
import com.alleyz15.farmtwinai.data.farm.TimelinePhotoCacheEntry
import com.alleyz15.farmtwinai.data.farm.TimelineStageVisualCacheEntry
import com.alleyz15.farmtwinai.data.mock.MockFarmTwinRepository
import com.alleyz15.farmtwinai.domain.model.AiChatContext
import com.alleyz15.farmtwinai.domain.model.ActionState
import com.alleyz15.farmtwinai.domain.model.ActionType
import com.alleyz15.farmtwinai.domain.model.AppMode
import com.alleyz15.farmtwinai.domain.model.ChatMessage
import com.alleyz15.farmtwinai.domain.model.FieldInsightReport
import com.alleyz15.farmtwinai.domain.model.FarmPoint
import com.alleyz15.farmtwinai.domain.model.FarmTwinSnapshot
import com.alleyz15.farmtwinai.domain.model.LotSectionDraft
import com.alleyz15.farmtwinai.domain.model.MessageSender
import com.alleyz15.farmtwinai.domain.model.SetupMethod
import com.alleyz15.farmtwinai.domain.model.TimelinePhotoAssessment
import com.alleyz15.farmtwinai.domain.model.TimelineStageVisual
import com.alleyz15.farmtwinai.domain.model.TimelineStatus
import com.alleyz15.farmtwinai.domain.model.ZoneInfo
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

class FarmTwinAppState(
    repository: MockFarmTwinRepository,
    private val fieldInsightsRepository: FieldInsightsRepository,
    private val authRepository: AuthRepository,
    private val farmConfigRepository: FarmConfigRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var authenticatedUser by mutableStateOf<AuthUser?>(null)
        private set

    var fieldInsightHistory by mutableStateOf<List<com.alleyz15.farmtwinai.domain.model.FieldInsightHistoryRecord>?>(null)
        private set
        
    fun loadFieldInsightHistory() {
        scope.launch {
            try {
                fieldInsightHistory = fieldInsightsRepository.getHistory()
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

    var selectedTimelineDay by mutableStateOf(snapshot.timeline.last())
        private set

    var farmSetupAddress by mutableStateOf("")
        private set

    var farmSetupFarmName by mutableStateOf("")
        private set

    var farmSetupMapQuery by mutableStateOf("")
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

    var farmConfigSyncError by mutableStateOf<String?>(null)
        private set

    var isFetchingEnvData by mutableStateOf(false)
        private set

    var lotReports by mutableStateOf<Map<String, FieldInsightReport>>(emptyMap())
        private set

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
        private set

    var aiConversationMessages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set

    var isSendingAiConversationMessage by mutableStateOf(false)
        private set

    var aiConversationError by mutableStateOf<String?>(null)
        private set

    var aiConversationProvider by mutableStateOf<String?>(null)
        private set

    var themePreference by mutableStateOf(ThemePreference.SYSTEM)
        private set

    private var aiConversationMessageIndex = 0

    val isAuthenticated: Boolean
        get() = authenticatedUser != null

    fun authenticateUser(user: AuthUser) {
        authenticatedUser = user
    }

    fun authenticateAndHydrate(
        user: AuthUser,
        onReady: (hasSavedFarmConfig: Boolean) -> Unit,
    ) {
        authenticatedUser = user
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

            isFarmConfigSyncing = false
            onReady(remote != null)
        }
    }

    fun signOut() {
        authenticatedUser = null
        isFarmConfigSyncing = false
        farmConfigSyncError = null
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

    fun setMode(mode: AppMode) {
        selectedMode = mode
    }

    fun setThemePreference(preference: ThemePreference) {
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

        if (dayNumber in timelineLoadingStageVisualDays) return
        timelineLoadingStageVisualDays = timelineLoadingStageVisualDays + dayNumber
        isLoadingTimelineStageVisual = selectedTimelineDay.dayNumber in timelineLoadingStageVisualDays
        timelineStageVisualError = null

        scope.launch {
            runCatching {
                fieldInsightsRepository.generateTimelineStageVisual(
                    dayNumber = dayNumber,
                    expectedStage = expectedStage,
                    cropName = snapshot.farm.cropName,
                )
            }.onSuccess { visual ->
                    timelineStageVisualByDay = timelineStageVisualByDay + (dayNumber to visual)
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
                )
            }.onSuccess { assessment ->
                    timelinePhotoAssessmentByDay = timelinePhotoAssessmentByDay + (dayNumber to assessment)
                    timelinePhotoAssessmentErrorByDay = timelinePhotoAssessmentErrorByDay - dayNumber
                    timelineDynamicStatusByDay = timelineDynamicStatusByDay + (
                        dayNumber to deriveTimelineStatus(assessment, userMarkedSimilar)
                    )
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
                aiConversationError = error.message ?: "Unable to get response from Gemini right now."
            }

            isSendingAiConversationMessage = false
        }
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
        farmBoundaryPoints = defaultBoundary
        farmSetupFarmName = ""
        farmSetupAddress = ""
        farmSetupMapQuery = farmSetupAddress
        farmSetupSearchTrigger = 0
        farmSetupUseCurrentLocationTrigger = 0
        isFarmMapFrozen = false
        lotTotalAreaInput = snapshot.farm.fieldSize
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
                    val earthSource = if (report.summary.notes.contains("mock", ignoreCase = true)) "Earth: mock" else "Earth: live"
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
                            val earthSource = if (report.summary.notes.contains("mock", ignoreCase = true)) "Earth: mock" else "Earth: live"
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
        val mockDay = if (selectedMode == AppMode.PLANNING) {
            0
        } else if (lot.plantingDate?.isNotBlank() == true) {
            exactDays.coerceAtLeast(0)
        } else {
            val dayOffset = (seed % 20) - 5
            (base.currentDay + dayOffset).coerceAtLeast(1)
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
            ActionState.DONE -> "Mock result: timeline flagged for follow-up simulation."
            ActionState.NOT_YET -> "Mock result: reminder state kept pending."
            ActionState.SKIP -> "Mock result: no re-simulation requested."
        }
        snapshot = snapshot.copy(
            actionRecords = listOf(
                com.alleyz15.farmtwinai.domain.model.ActionRecord(
                    id = "action-${snapshot.actionRecords.size + 1}",
                    actionType = actionType,
                    state = actionState,
                    dayLabel = "Day ${snapshot.cropSummary.currentDay}",
                    resultSummary = summary,
                )
            ) + snapshot.actionRecords
        )
    }

    private fun calculateMockDaysPassed(plantingDate: String?): Int {
        if (plantingDate.isNullOrBlank()) return 0
        val parts = plantingDate.split("-")
        if (parts.size != 3) return 0
        val year = parts[0].toIntOrNull() ?: return 0
        val month = parts[1].toIntOrNull() ?: return 0
        val day = parts[2].toIntOrNull() ?: return 0
        
        val currentYear = 2026
        val currentMonth = 4
        val currentDay = 14
        
        val daysInMonths = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var plantedDayOfYear = day
        for (i in 1 until month.coerceIn(1, 12)) plantedDayOfYear += daysInMonths[i]
        
        var todayDayOfYear = currentDay
        for (i in 1 until currentMonth) todayDayOfYear += daysInMonths[i]
        
        val yearDiff = currentYear - year
        return (yearDiff * 365) + todayDayOfYear - plantedDayOfYear
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

    private fun buildFarmConfigDraft(userId: String): FarmConfigDraft {
        val photoCache = timelineUploadByDay.map { (dayNumber, upload) ->
            TimelinePhotoCacheEntry(
                dayNumber = dayNumber,
                photoBase64 = upload.photoBase64,
                photoMimeType = upload.photoMimeType,
                updatedAtEpochMs = upload.updatedAtEpochMs,
            )
        }

        val stageCache = timelineStageVisualByDay.map { (dayNumber, visual) ->
            TimelineStageVisualCacheEntry(
                dayNumber = dayNumber,
                title = visual.title,
                description = visual.description,
                imageDataUrl = visual.imageDataUrl,
                provider = visual.provider,
                updatedAtEpochMs = currentEpochMs(),
            )
        }

        return FarmConfigDraft(
            userId = userId,
            farmName = farmSetupFarmName.trim(),
            address = farmSetupAddress.trim(),
            mapQuery = farmSetupMapQuery.trim(),
            totalAreaInput = lotTotalAreaInput.trim(),
            mode = selectedMode,
            boundaryPoints = farmBoundaryPoints,
            lots = lotSections,
            timelinePhotoCache = photoCache,
            timelineStageVisualCache = stageCache,
        )
    }

    private fun applyRemoteFarmConfig(remote: FarmConfigRemote) {
        if (remote.farmName.isNotBlank()) {
            farmSetupFarmName = remote.farmName
        }

        if (remote.address.isNotBlank()) {
            farmSetupAddress = remote.address
        }

        if (remote.mapQuery.isNotBlank()) {
            farmSetupMapQuery = remote.mapQuery
        }

        if (remote.totalAreaInput.isNotBlank()) {
            lotTotalAreaInput = remote.totalAreaInput
        }

        selectedMode = remote.mode

        if (remote.boundaryPoints.size >= 3) {
            farmBoundaryPoints = remote.boundaryPoints
        }

        if (remote.lots.isNotEmpty()) {
            lotSections = remote.lots
        }

        val primaryCrop = remote.lots.firstOrNull()?.cropPlan?.trim().orEmpty()
        snapshot = snapshot.copy(
            farm = snapshot.farm.copy(
                farmName = remote.farmName.ifBlank { snapshot.farm.farmName },
                cropName = if (primaryCrop.isNotBlank()) primaryCrop else snapshot.farm.cropName,
                location = remote.address.ifBlank { snapshot.farm.location },
                fieldSize = remote.totalAreaInput.ifBlank { snapshot.farm.fieldSize },
                mode = remote.mode,
            ),
        )

        if (remote.timelinePhotoCache.isNotEmpty()) {
            timelineUploadByDay = remote.timelinePhotoCache.associate { entry ->
                entry.dayNumber to TimelineUploadCache(
                    photoBase64 = entry.photoBase64,
                    photoMimeType = entry.photoMimeType,
                    updatedAtEpochMs = entry.updatedAtEpochMs,
                )
            }
        }

        if (remote.timelineStageVisualCache.isNotEmpty()) {
            timelineStageVisualByDay = remote.timelineStageVisualCache.associate { entry ->
                entry.dayNumber to TimelineStageVisual(
                    dayNumber = entry.dayNumber,
                    expectedStage = snapshot.timeline.firstOrNull { it.dayNumber == entry.dayNumber }?.expectedStage.orEmpty(),
                    cropName = snapshot.farm.cropName,
                    title = entry.title,
                    description = entry.description,
                    imageDataUrl = entry.imageDataUrl,
                    prompt = "",
                    provider = entry.provider.ifBlank { "cached" },
                )
            }
        }

        val selectedDayNumber = selectedTimelineDay.dayNumber
        timelineStageVisual = timelineStageVisualByDay[selectedDayNumber]
        timelinePhotoAssessment = timelinePhotoAssessmentByDay[selectedDayNumber]
        timelineStageVisualError = timelineStageVisualErrorByDay[selectedDayNumber]
        timelinePhotoAssessmentError = timelinePhotoAssessmentErrorByDay[selectedDayNumber]
    }

    private fun persistTimelineCacheToCloudIfAuthenticated() {
        val userId = authenticatedUser?.userId ?: return
        if (farmSetupFarmName.trim().isBlank() || lotSections.isEmpty()) return

        scope.launch {
            runCatching {
                farmConfigRepository.upsertFarmConfig(buildFarmConfigDraft(userId))
            }
        }
    }

    private fun currentEpochMs(): Long = 0L

    private fun clearLotRecommendationState() {
        isAnalyzingLots = false
        lotRecommendationBestLotId = null
        lotRecommendationReason = null
        lotRecommendationError = null
        lotRecommendationDataSourceByLotId = emptyMap()
        lotRecommendationSuggestedCropByLotId = emptyMap()
    }

    fun timelineStatusForDay(dayNumber: Int): TimelineStatus {
        return timelineDynamicStatusByDay[dayNumber]
            ?: snapshot.timeline.firstOrNull { it.dayNumber == dayNumber }?.status
            ?: TimelineStatus.NORMAL
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
