package com.alleyz15.farmtwinai.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alleyz15.farmtwinai.data.mock.MockFarmTwinRepository
import com.alleyz15.farmtwinai.domain.model.ActionState
import com.alleyz15.farmtwinai.domain.model.ActionType
import com.alleyz15.farmtwinai.domain.model.AppMode
import com.alleyz15.farmtwinai.domain.model.FarmTwinSnapshot
import com.alleyz15.farmtwinai.domain.model.SetupMethod
import com.alleyz15.farmtwinai.domain.model.ZoneInfo

class FarmTwinAppState(
    repository: MockFarmTwinRepository,
) {
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

    fun currentZone(): ZoneInfo {
        return snapshot.zones.first { it.id == selectedZoneId }
    }

    fun recordAction(actionType: ActionType, actionState: ActionState) {
        // TODO Phase 2: persist actions and trigger re-simulation logic.
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
}
