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
import com.alleyz15.farmtwinai.data.mock.MockFarmTwinRepository
import com.alleyz15.farmtwinai.domain.model.ActionState
import com.alleyz15.farmtwinai.domain.model.ActionType
import com.alleyz15.farmtwinai.domain.model.AppMode
import com.alleyz15.farmtwinai.domain.model.FieldInsightReport
import com.alleyz15.farmtwinai.domain.model.FarmPoint
import com.alleyz15.farmtwinai.domain.model.FarmTwinSnapshot
import com.alleyz15.farmtwinai.domain.model.LotSectionDraft
import com.alleyz15.farmtwinai.domain.model.SetupMethod
import com.alleyz15.farmtwinai.domain.model.ZoneInfo
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FarmTwinAppState(
    repository: MockFarmTwinRepository,
    private val fieldInsightsRepository: FieldInsightsRepository,
    private val authRepository: AuthRepository,
    private val farmConfigRepository: FarmConfigRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var authenticatedUser by mutableStateOf<AuthUser?>(null)
        private set

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

    var farmSetupAddress by mutableStateOf("Pendang, Kedah")
        private set

    var farmSetupFarmName by mutableStateOf("")
        private set

    var farmSetupMapQuery by mutableStateOf("Pendang, Kedah")
        private set

    var farmSetupSearchTrigger by mutableStateOf(0)
        private set

    var farmSetupUseCurrentLocationTrigger by mutableStateOf(0)
        private set

    var isFarmMapFrozen by mutableStateOf(false)
        private set

    var farmBoundaryPoints by mutableStateOf(
        listOf(
            FarmPoint(0.20f, 0.25f),
            FarmPoint(0.82f, 0.22f),
            FarmPoint(0.88f, 0.70f),
            FarmPoint(0.26f, 0.78f),
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

    fun setSetupMethod(method: SetupMethod) {
        selectedSetupMethod = method
    }

    fun selectZone(zoneId: String) {
        selectedZoneId = zoneId
    }

    fun selectTimelineDay(dayNumber: Int) {
        snapshot.timeline.firstOrNull { it.dayNumber == dayNumber }?.let {
            selectedTimelineDay = it
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
        isFarmMapFrozen = true
    }

    fun updateLotSections(sections: List<LotSectionDraft>) {
        lotSections = sections
        clearLotRecommendationState()
    }

    fun updateLotTotalAreaInput(value: String) {
        lotTotalAreaInput = value
        clearLotRecommendationState()
    }

    fun prepareNewFarmDraft() {
        val defaultBoundary = defaultFarmBoundary()
        farmBoundaryPoints = defaultBoundary
        farmSetupFarmName = ""
        farmSetupAddress = "Pendang, Kedah"
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
        lotRecommendationDataSourceByLotId = emptyMap()
        lotRecommendationSuggestedCropByLotId = emptyMap()

        scope.launch {
            runCatching {
                val totalFarmAreaHa = parseAreaInputToHectares(lotTotalAreaInput)
                val boundaryArea = polygonArea(farmBoundaryPoints)
                val analyzed = lotSections.map { lot ->
                    if (lot.points.size < 3) {
                        AnalyzedLot(
                            lot = lot,
                            score = -1.0,
                            reason = "Lot boundary is incomplete.",
                            dataSource = "Unavailable",
                        )
                    } else {
                        val lotAreaHa = if (totalFarmAreaHa != null && boundaryArea > 0.0f) {
                            val ratio = (polygonArea(lot.points) / boundaryArea).coerceIn(0.0f, 1.0f)
                            totalFarmAreaHa * ratio
                        } else {
                            null
                        }
                        val report = fieldInsightsRepository.analyzePolygon(
                            points = lot.points,
                            targetCrops = requestedCrops,
                            totalFarmAreaHectares = totalFarmAreaHa,
                            lotAreaHectares = lotAreaHa,
                        )
                        val updatedLot = lot.copy(
                            soilType = inferSoilType(report.summary.soilMoistureMean, report.summary.ndviMean),
                            waterAvailability = inferWaterAvailability(report.summary.rainfallMm7d, report.summary.soilMoistureMean),
                        )
                        val score = scoreLotForCrop(updatedLot.cropPlan, report)
                        val reason = lotReason(updatedLot.cropPlan, report)
                        val earthSource = if (report.summary.notes.contains("mock", ignoreCase = true)) "Earth: mock" else "Earth: live"
                        val aiSource = "Gemini: ${report.provider}"
                        AnalyzedLot(
                            lot = updatedLot,
                            score = score,
                            reason = reason,
                            dataSource = "$earthSource | $aiSource",
                            report = report,
                        )
                    }
                }

                lotSections = analyzed.map { it.lot }
                lotRecommendationDataSourceByLotId = analyzed.associate { it.lot.id to it.dataSource }
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

        clearLotRecommendationState()
    }

    fun completeLotRecommendationAndPersist(
        followRecommendation: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        finalizeLotRecommendation(followRecommendation = followRecommendation)

        val userId = authenticatedUser?.userId
        if (userId.isNullOrBlank()) {
            onComplete(true)
            return
        }

        val draft = buildFarmConfigDraft(userId)
        isFarmConfigSyncing = true
        farmConfigSyncError = null
        lotRecommendationError = null

        scope.launch {
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

    private fun defaultFarmBoundary(): List<FarmPoint> {
        return listOf(
            FarmPoint(0.20f, 0.25f),
            FarmPoint(0.82f, 0.22f),
            FarmPoint(0.88f, 0.70f),
            FarmPoint(0.26f, 0.78f),
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
        return FarmConfigDraft(
            userId = userId,
            farmName = farmSetupFarmName.trim(),
            address = farmSetupAddress.trim(),
            mapQuery = farmSetupMapQuery.trim(),
            totalAreaInput = lotTotalAreaInput.trim(),
            mode = selectedMode,
            boundaryPoints = farmBoundaryPoints,
            lots = lotSections,
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
    }

    private fun clearLotRecommendationState() {
        isAnalyzingLots = false
        lotRecommendationBestLotId = null
        lotRecommendationReason = null
        lotRecommendationError = null
        lotRecommendationDataSourceByLotId = emptyMap()
        lotRecommendationSuggestedCropByLotId = emptyMap()
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

    private fun inferSoilType(soilMoisture: Double, ndvi: Double): String {
        return when {
            soilMoisture >= 0.58 && ndvi >= 0.55 -> "Clay Loam"
            soilMoisture in 0.42..0.58 -> "Loamy"
            soilMoisture < 0.30 -> "Sandy Loam"
            else -> "Silty Loam"
        }
    }

    private fun inferWaterAvailability(rainfallMm7d: Double, soilMoisture: Double): String {
        return when {
            rainfallMm7d >= 35 || soilMoisture >= 0.60 -> "High"
            rainfallMm7d >= 18 || soilMoisture >= 0.40 -> "Medium"
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
