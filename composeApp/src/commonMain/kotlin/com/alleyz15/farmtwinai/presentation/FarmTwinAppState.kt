package com.alleyz15.farmtwinai.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alleyz15.farmtwinai.auth.AuthUser
import com.alleyz15.farmtwinai.data.mock.MockFarmTwinRepository
import com.alleyz15.farmtwinai.domain.model.ActionState
import com.alleyz15.farmtwinai.domain.model.ActionType
import com.alleyz15.farmtwinai.domain.model.AppMode
import com.alleyz15.farmtwinai.domain.model.FarmPoint
import com.alleyz15.farmtwinai.domain.model.FarmTwinSnapshot
import com.alleyz15.farmtwinai.domain.model.LotSectionDraft
import com.alleyz15.farmtwinai.domain.model.SetupMethod
import com.alleyz15.farmtwinai.domain.model.ZoneInfo

class FarmTwinAppState(
    repository: MockFarmTwinRepository,
) {
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
                cropPlan = "Tomato",
                soilType = "Loamy",
                waterAvailability = "Medium",
            )
        )
    )
        private set

    val isAuthenticated: Boolean
        get() = authenticatedUser != null

    fun authenticateUser(user: AuthUser) {
        authenticatedUser = user
    }

    fun signOut() {
        authenticatedUser = null
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
        if (lotSections.isEmpty()) {
            lotSections = listOf(
                LotSectionDraft(
                    id = "lot-1",
                    name = "Lot 1",
                    points = points,
                    cropPlan = "Tomato",
                    soilType = "Loamy",
                    waterAvailability = "Medium",
                )
            )
        } else {
            lotSections = lotSections.map { lot ->
                val adjustedPoints = lot.points.map { point ->
                    keepPointInsideBoundary(point, points)
                }
                lot.copy(points = if (adjustedPoints.size >= 3) adjustedPoints else points)
            }
        }
    }

    fun updateLotSections(sections: List<LotSectionDraft>) {
        lotSections = sections
    }

    fun prepareNewFarmDraft() {
        val defaultBoundary = defaultFarmBoundary()
        farmBoundaryPoints = defaultBoundary
        lotSections = listOf(
            LotSectionDraft(
                id = "lot-1",
                name = "Lot 1",
                points = defaultBoundary,
                cropPlan = snapshot.farm.cropName,
                soilType = "Loamy",
                waterAvailability = "Medium",
            )
        )
        selectedSetupMethod = SetupMethod.MANUAL
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
}
