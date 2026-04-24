package com.alleyz15.farmtwinai.domain.model

data class EarthEngineSummary(
    val centroidLat: Double,
    val centroidLng: Double,
    val ndviMean: Double,
    val soilMoistureMean: Double,
    val rainfallMm7d: Double,
    val averageTempC: Double,
    val notes: String,
    val source: String = "unknown",
    val sourceVerified: Boolean = false,
)

data class CropRecommendation(
    val cropName: String,
    val suitability: String,
    val rationale: String,
)

data class FieldInsightReport(
    val summary: EarthEngineSummary,
    val recommendations: List<CropRecommendation>,
    val provider: String,
)

data class TimelineStageVisual(
    val dayNumber: Int,
    val expectedStage: String,
    val cropName: String,
    val farmId: String,
    val title: String,
    val description: String,
    val imageDataUrl: String,
    val imageStoragePath: String = "",
    val prompt: String,
    val provider: String,
)

data class TimelinePhotoAssessment(
    val dayNumber: Int,
    val expectedStage: String,
    val cropName: String,
    val similarityScore: Int,
    val isSimilar: Boolean,
    val observedStage: String,
    val recommendation: String,
    val rationale: String,
    val provider: String,
)

enum class FieldInsightHistoryCategory {
    SCAN,
    ACTION_LOG,
    KB_SEARCH,
    TIMELINE_COMPARISON,
    CONVERSATION,
    UNKNOWN,
}

data class FieldInsightHistoryRecord(
    val id: String,
    val category: FieldInsightHistoryCategory = FieldInsightHistoryCategory.SCAN,
    val title: String = "",
    val summaryNotes: String,
    val recommendedCrops: String,
    val dateString: String,
    val hasConversation: Boolean = false,
    val chatMessagesCount: Int = 0,
)

data class CurrentWeatherNow(
    val location: String,
    val resolvedAddress: String,
    val temperatureC: Double,
    val condition: String,
    val icon: String,
    val provider: String,
)

data class ActionTrackerFollowUp(
    val nextBestAction: String,
    val followUpQuestion: String,
    val confidence: Double,
    val riskLevel: String,
    val provider: String,
)
