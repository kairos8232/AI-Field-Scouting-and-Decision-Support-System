package com.alleyz15.farmtwinai.data.mock

import com.alleyz15.farmtwinai.domain.model.ActionRecord
import com.alleyz15.farmtwinai.domain.model.ActionState
import com.alleyz15.farmtwinai.domain.model.ActionType
import com.alleyz15.farmtwinai.domain.model.AppMode
import com.alleyz15.farmtwinai.domain.model.ChatMessage
import com.alleyz15.farmtwinai.domain.model.CropSummary
import com.alleyz15.farmtwinai.domain.model.DocumentExtractionSummary
import com.alleyz15.farmtwinai.domain.model.FarmProfile
import com.alleyz15.farmtwinai.domain.model.FarmTwinSnapshot
import com.alleyz15.farmtwinai.domain.model.HealthStatus
import com.alleyz15.farmtwinai.domain.model.IssueLog
import com.alleyz15.farmtwinai.domain.model.MessageSender
import com.alleyz15.farmtwinai.domain.model.SuitabilityLevel
import com.alleyz15.farmtwinai.domain.model.TimelineDay
import com.alleyz15.farmtwinai.domain.model.TimelineStatus
import com.alleyz15.farmtwinai.domain.model.UserProfile
import com.alleyz15.farmtwinai.domain.model.ZoneInfo

class MockFarmTwinRepository {
    fun loadSnapshot(): FarmTwinSnapshot {
        return FarmTwinSnapshot(
            user = UserProfile(
                name = "Farm Owner",
                region = "",
                experienceLabel = "",
            ),
            farm = FarmProfile(
                farmName = "My Farm",
                cropName = "",
                plantingDate = "",
                location = "",
                fieldSize = "",
                irrigationSource = "Drip irrigation + rainwater tank",
                sunlightCondition = "Full sun, slight west-side shade",
                drainageCondition = "Mixed, lower corner retains water",
                soilPh = "6.4",
                mode = AppMode.LIVE_MONITORING,
            ),
            cropSummary = CropSummary(
                currentDay = 15,
                expectedGrowthRange = "55% - 70%",
                currentFarmHealthScore = 78,
                urgentZones = 1,
                latestRecommendation = "Inspect drainage in Zone 2 and monitor for 48 hours.",
                expectedStage = "Vegetative expansion",
            ),
            zones = listOf(
                ZoneInfo(
                    id = "zone-1",
                    zoneName = "Zone 1",
                    cropName = "Tomato",
                    status = HealthStatus.HEALTHY,
                    suitability = SuitabilityLevel.HIGH,
                    expectedGrowthRange = "60% - 68%",
                    actualConditionSummary = "Even leaf spread and stable moisture.",
                    issueLevel = "Low",
                    suggestedAction = "Maintain current watering schedule.",
                ),
                ZoneInfo(
                    id = "zone-2",
                    zoneName = "Zone 2",
                    cropName = "Tomato",
                    status = HealthStatus.WATER_ISSUE,
                    suitability = SuitabilityLevel.MODERATE,
                    expectedGrowthRange = "55% - 70%",
                    actualConditionSummary = "Growth lag and damp soil patches after rain.",
                    issueLevel = "Medium",
                    suggestedAction = "Inspect drainage and monitor for 48 hours.",
                ),
                ZoneInfo(
                    id = "zone-3",
                    zoneName = "Zone 3",
                    cropName = "Tomato",
                    status = HealthStatus.MONITOR,
                    suitability = SuitabilityLevel.MODERATE,
                    expectedGrowthRange = "57% - 66%",
                    actualConditionSummary = "Slightly smaller canopy than expected.",
                    issueLevel = "Watch",
                    suggestedAction = "Monitor nutrient response after next feed.",
                ),
                ZoneInfo(
                    id = "zone-4",
                    zoneName = "Zone 4",
                    cropName = "Tomato",
                    status = HealthStatus.UPDATED,
                    suitability = SuitabilityLevel.HIGH,
                    expectedGrowthRange = "58% - 67%",
                    actualConditionSummary = "Recovered after last adjustment.",
                    issueLevel = "Resolved",
                    suggestedAction = "Continue updated simulation baseline.",
                ),
            ),
            timeline = buildTimeline(),
            issueLogs = emptyList(),
            chatMessages = emptyList(),
            actionRecords = emptyList(),
            documentSummary = DocumentExtractionSummary(
                title = "No document uploaded yet",
                bullets = emptyList(),
            ),
        )
    }

    private fun buildTimeline(): List<TimelineDay> {
        return listOf(
            TimelineDay(1, TimelineStatus.NORMAL, "5% - 10%", "Planting", "Seeds planted and initial watering completed."),
            TimelineDay(2, TimelineStatus.NORMAL, "8% - 12%", "Establishment", "Moisture stable across most zones."),
            TimelineDay(3, TimelineStatus.NORMAL, "10% - 15%", "Establishment", "Expected early root settling."),
            TimelineDay(4, TimelineStatus.NORMAL, "12% - 18%", "Early sprout", "No deviation recorded."),
            TimelineDay(5, TimelineStatus.NORMAL, "15% - 22%", "Early sprout", "Canopy emergence begins."),
            TimelineDay(6, TimelineStatus.NORMAL, "18% - 25%", "Seedling", "Healthy root-zone moisture."),
            TimelineDay(7, TimelineStatus.NORMAL, "22% - 28%", "Seedling", "Simulation and field trend aligned."),
            TimelineDay(8, TimelineStatus.NORMAL, "25% - 32%", "Seedling", "Leaf color expected to deepen."),
            TimelineDay(9, TimelineStatus.NORMAL, "28% - 36%", "Seedling", "Normal daily development."),
            TimelineDay(10, TimelineStatus.WARNING, "32% - 42%", "Vegetative start", "Zone 3 appears slightly behind expected spread."),
            TimelineDay(11, TimelineStatus.WARNING, "38% - 48%", "Vegetative start", "Rainfall increased water retention in lower zones."),
            TimelineDay(12, TimelineStatus.ACTION_TAKEN, "43% - 54%", "Vegetative expansion", "Monitoring action recorded for slower canopy growth."),
            TimelineDay(13, TimelineStatus.NORMAL, "47% - 58%", "Vegetative expansion", "Other zones remain within target."),
            TimelineDay(14, TimelineStatus.WARNING, "50% - 63%", "Vegetative expansion", "Zone 2 moisture remains above model expectation."),
            TimelineDay(15, TimelineStatus.UPDATED, "55% - 70%", "Vegetative expansion", "Simulation flagged for possible update after drainage fix."),
        )
    }
}
