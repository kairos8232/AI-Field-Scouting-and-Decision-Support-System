package com.alleyz15.farmtwinai.data.farm

import com.alleyz15.farmtwinai.domain.model.AppMode
import com.alleyz15.farmtwinai.domain.model.ActionState
import com.alleyz15.farmtwinai.domain.model.ActionType
import com.alleyz15.farmtwinai.domain.model.FarmPoint
import com.alleyz15.farmtwinai.domain.model.ForecastConfidenceTier
import com.alleyz15.farmtwinai.domain.model.LotSectionDraft
import com.alleyz15.farmtwinai.domain.model.RecoveryTrend
import com.alleyz15.farmtwinai.domain.model.TimelineStatus

data class TimelinePhotoCacheEntry(
    val dayNumber: Int,
    val photoBase64: String,
    val photoMimeType: String,
    val updatedAtEpochMs: Long,
)

data class TimelineStageVisualCacheEntry(
    val dayNumber: Int,
    val title: String,
    val description: String,
    val imageDataUrl: String,
    val provider: String,
    val updatedAtEpochMs: Long,
)

data class TimelinePhotoAssessmentCacheEntry(
    val dayNumber: Int,
    val expectedStage: String,
    val cropName: String,
    val similarityScore: Int,
    val isSimilar: Boolean,
    val observedStage: String,
    val recommendation: String,
    val rationale: String,
    val provider: String,
    val updatedAtEpochMs: Long,
)

data class TimelineActionDecisionCacheEntry(
    val dayNumber: Int,
    val actionType: ActionType,
    val state: ActionState,
    val updatedAtEpochMs: Long,
    val nextBestAction: String,
    val followUpQuestion: String,
    val confidence: Double,
    val riskLevel: String,
    val provider: String,
)

data class TimelineInsightCacheEntry(
    val dayNumber: Int,
    val recommendedActionText: String,
    val timelineStatus: TimelineStatus?,
    val sourceDayNumber: Int,
    val trend: RecoveryTrend,
    val etaDaysMin: Int,
    val etaDaysMax: Int,
    val confidencePercent: Int,
    val confidenceTier: ForecastConfidenceTier,
    val isUrgent: Boolean,
    val updatedAtEpochMs: Long,
)

data class FarmConfigFarmEntry(
    val id: String,
    val farmName: String,
    val address: String,
    val mapQuery: String,
    val totalAreaInput: String,
    val mode: AppMode,
    val boundaryPoints: List<FarmPoint>,
    val lots: List<LotSectionDraft>,
)

data class FarmConfigDraft(
    val userId: String,
    val activeFarmId: String,
    val farms: List<FarmConfigFarmEntry>,
    val farmName: String,
    val address: String,
    val mapQuery: String,
    val totalAreaInput: String,
    val mode: AppMode,
    val boundaryPoints: List<FarmPoint>,
    val lots: List<LotSectionDraft>,
    val timelinePhotoCache: List<TimelinePhotoCacheEntry> = emptyList(),
    val timelineStageVisualCache: List<TimelineStageVisualCacheEntry> = emptyList(),
    val timelineAssessmentCache: List<TimelinePhotoAssessmentCacheEntry> = emptyList(),
    val timelineActionDecisionCache: List<TimelineActionDecisionCacheEntry> = emptyList(),
    val timelineInsightCache: List<TimelineInsightCacheEntry> = emptyList(),
)

data class FarmConfigRemote(
    val activeFarmId: String,
    val farms: List<FarmConfigFarmEntry>,
    val farmName: String,
    val address: String,
    val mapQuery: String,
    val totalAreaInput: String,
    val mode: AppMode,
    val boundaryPoints: List<FarmPoint>,
    val lots: List<LotSectionDraft>,
    val timelinePhotoCache: List<TimelinePhotoCacheEntry> = emptyList(),
    val timelineStageVisualCache: List<TimelineStageVisualCacheEntry> = emptyList(),
    val timelineAssessmentCache: List<TimelinePhotoAssessmentCacheEntry> = emptyList(),
    val timelineActionDecisionCache: List<TimelineActionDecisionCacheEntry> = emptyList(),
    val timelineInsightCache: List<TimelineInsightCacheEntry> = emptyList(),
)

interface FarmConfigRepository {
    suspend fun upsertFarmConfig(draft: FarmConfigDraft)

    suspend fun fetchLatestFarmConfig(userId: String): FarmConfigRemote?
}
