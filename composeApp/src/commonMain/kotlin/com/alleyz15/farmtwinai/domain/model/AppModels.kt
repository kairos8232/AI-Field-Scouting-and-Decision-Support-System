package com.alleyz15.farmtwinai.domain.model

enum class AppMode {
    PLANNING,
    LIVE_MONITORING,
    DEMO,
}

enum class SetupMethod {
    DOCUMENT,
    MANUAL,
    QUICK_ESTIMATE,
}

enum class HealthStatus {
    HEALTHY,
    MONITOR,
    ISSUE,
    WATER_ISSUE,
    UPDATED,
}

enum class SuitabilityLevel {
    HIGH,
    MODERATE,
    LOW,
}

enum class TimelineStatus {
    NORMAL,
    WARNING,
    ACTION_TAKEN,
    UPDATED,
}

enum class ActionState {
    DONE,
    NOT_YET,
    SKIP,
}

enum class ActionType {
    WATERED,
    IMPROVED_DRAINAGE,
    ADJUSTED_FERTILIZER,
    APPLIED_PESTICIDE_FUNGICIDE,
    PRUNED_AFFECTED_LEAVES,
    MONITORED_ONLY,
    REPLANTED,
}

enum class RecoveryTrend {
    IMPROVING,
    STABLE,
    WORSENING,
    UNKNOWN,
}

data class TimelineRecoveryForecast(
    val sourceDayNumber: Int,
    val trend: RecoveryTrend,
    val etaDaysMin: Int,
    val etaDaysMax: Int,
    val confidencePercent: Int,
    val confidenceTier: ForecastConfidenceTier,
    val isUrgent: Boolean,
)

enum class ForecastConfidenceTier {
    LOW,
    MEDIUM,
    HIGH,
}

data class UserProfile(
    val name: String,
    val region: String,
    val experienceLabel: String,
)

data class FarmProfile(
    val farmName: String,
    val cropName: String,
    val plantingDate: String,
    val location: String,
    val fieldSize: String,
    val irrigationSource: String,
    val sunlightCondition: String,
    val drainageCondition: String,
    val soilPh: String,
    val mode: AppMode,
)

data class CropSummary(
    val currentDay: Int,
    val expectedGrowthRange: String,
    val currentFarmHealthScore: Int,
    val urgentZones: Int,
    val latestRecommendation: String,
    val expectedStage: String,
)

data class ZoneInfo(
    val id: String,
    val zoneName: String,
    val cropName: String,
    val status: HealthStatus,
    val suitability: SuitabilityLevel,
    val expectedGrowthRange: String,
    val actualConditionSummary: String,
    val issueLevel: String,
    val suggestedAction: String,
)

data class TimelineDay(
    val dayNumber: Int,
    val status: TimelineStatus,
    val expectedGrowthRange: String,
    val expectedStage: String,
    val notes: String,
)

data class IssueLog(
    val id: String,
    val title: String,
    val dayLabel: String,
    val status: HealthStatus,
    val summary: String,
)

data class ChatMessage(
    val id: String,
    val sender: MessageSender,
    val content: String,
    val timestamp: String,
)

data class AiChatContext(
    val farmName: String? = null,
    val cropName: String? = null,
    val mode: String? = null,
    val latestRecommendation: String? = null,
)

data class AiChatReply(
    val reply: String,
    val provider: String,
)

enum class MessageSender {
    USER,
    ASSISTANT,
}

data class ActionRecord(
    val id: String,
    val actionType: ActionType,
    val state: ActionState,
    val dayLabel: String,
    val resultSummary: String,
)

data class DocumentExtractionSummary(
    val title: String,
    val bullets: List<String>,
)

data class FarmTwinSnapshot(
    val user: UserProfile,
    val farm: FarmProfile,
    val cropSummary: CropSummary,
    val zones: List<ZoneInfo>,
    val timeline: List<TimelineDay>,
    val issueLogs: List<IssueLog>,
    val chatMessages: List<ChatMessage>,
    val actionRecords: List<ActionRecord>,
    val documentSummary: DocumentExtractionSummary,
)

data class FarmPoint(
    val x: Float,
    val y: Float,
)

data class LotSectionDraft(
    val id: String,
    val name: String,
    val points: List<FarmPoint>,
    val cropPlan: String,
    val soilType: String,
    val waterAvailability: String,
    val cropSummary: CropSummary? = null,
    val plantingDate: String? = null,
)
