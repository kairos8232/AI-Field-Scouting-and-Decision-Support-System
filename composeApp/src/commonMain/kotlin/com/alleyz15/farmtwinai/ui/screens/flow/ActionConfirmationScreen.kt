package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.alleyz15.farmtwinai.domain.model.ActionState
import com.alleyz15.farmtwinai.domain.model.ActionType
import com.alleyz15.farmtwinai.domain.model.RecoveryTrend
import com.alleyz15.farmtwinai.domain.model.TimelineRecoveryForecast
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.OptionCard
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader

@Composable
fun ActionConfirmationScreen(
    dayNumber: Int,
    latestAction: String,
    primaryRecommendedAction: ActionType,
    alternativeActions: List<ActionType>,
    recoveryForecast: TimelineRecoveryForecast?,
    onBack: () -> Unit,
    onSubmit: (ActionType, ActionState) -> Unit,
) {
    var selectedAction by remember(dayNumber, primaryRecommendedAction) { mutableStateOf(primaryRecommendedAction) }
    var selectedState by remember(dayNumber) { mutableStateOf(ActionState.DONE) }
    var pesticideWarningAccepted by remember(dayNumber, selectedAction) {
        mutableStateOf(selectedAction != ActionType.APPLIED_PESTICIDE_FUNGICIDE)
    }

    val actionChoices = remember(primaryRecommendedAction, alternativeActions) {
        (listOf(primaryRecommendedAction) + alternativeActions).distinct()
    }
    val requiresPesticideWarning = selectedAction == ActionType.APPLIED_PESTICIDE_FUNGICIDE
    val canSubmit = !requiresPesticideWarning || pesticideWarningAccepted

    AppScaffold(title = "Action Confirmation", subtitle = "Record what happened next", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Recommended action",
                body = latestAction,
            )

            if (recoveryForecast != null) {
                val trendLabel = when (recoveryForecast.trend) {
                    RecoveryTrend.IMPROVING -> "Improving"
                    RecoveryTrend.STABLE -> "Stable"
                    RecoveryTrend.WORSENING -> "Worsening"
                    RecoveryTrend.UNKNOWN -> "Unknown"
                }
                SectionHeader(
                    title = "Recovery forecast",
                    body = "Day $dayNumber - Trend: $trendLabel - ETA ${recoveryForecast.etaDaysMin}-${recoveryForecast.etaDaysMax} days - Confidence ${recoveryForecast.confidencePercent}%",
                )
                if (recoveryForecast.isUrgent) {
                    SectionHeader(
                        title = "Urgent notice",
                        body = "Condition is worsening. Apply action today and upload another photo tomorrow.",
                    )
                }
            }

            SectionHeader(title = "Action type")
            actionChoices.forEach { actionType ->
                OptionCard(
                    title = actionType.label(),
                    description = if (actionType == primaryRecommendedAction) {
                        "AI primary recommendation for this day."
                    } else {
                        actionType.supportingDescription()
                    },
                    selected = actionType == selectedAction,
                    onClick = {
                        selectedAction = actionType
                        if (actionType != ActionType.APPLIED_PESTICIDE_FUNGICIDE) {
                            pesticideWarningAccepted = true
                        }
                    },
                )
            }

            if (requiresPesticideWarning) {
                SectionHeader(
                    title = "Safety warning",
                    body = "Use approved pesticide/fungicide only, follow dosage label, wear PPE, and avoid spraying during strong wind or rain.",
                )
                DualActionButtons(
                    primaryLabel = "I understand and will apply safely",
                    onPrimary = { pesticideWarningAccepted = true },
                    secondaryLabel = null,
                    onSecondary = null,
                    primaryEnabled = !pesticideWarningAccepted,
                )
            }

            SectionHeader(title = "Current status")
            ActionState.entries.forEach { state ->
                OptionCard(
                    title = state.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                    description = when (state) {
                        ActionState.DONE -> "Action is completed today."
                        ActionState.NOT_YET -> "Action is planned but not done yet."
                        ActionState.SKIP -> "Action is intentionally skipped."
                    },
                    selected = state == selectedState,
                    onClick = { selectedState = state },
                )
            }

            DualActionButtons(
                primaryLabel = "Save action update",
                onPrimary = { onSubmit(selectedAction, selectedState) },
                secondaryLabel = "Back to Timeline",
                onSecondary = onBack,
                primaryEnabled = canSubmit,
            )
        }
    }
}

private fun ActionType.label(): String {
    return when (this) {
        ActionType.WATERED -> "Adjust watering"
        ActionType.IMPROVED_DRAINAGE -> "Improve drainage"
        ActionType.ADJUSTED_FERTILIZER -> "Apply fertilizer"
        ActionType.APPLIED_PESTICIDE_FUNGICIDE -> "Apply pesticide/fungicide"
        ActionType.PRUNED_AFFECTED_LEAVES -> "Prune affected leaves"
        ActionType.MONITORED_ONLY -> "Monitor only"
        ActionType.REPLANTED -> "Replanted"
    }
}

private fun ActionType.supportingDescription(): String {
    return when (this) {
        ActionType.WATERED -> "Useful when leaves curl, droop, or soil is too dry."
        ActionType.IMPROVED_DRAINAGE -> "Useful when roots may be stressed by standing water."
        ActionType.ADJUSTED_FERTILIZER -> "Useful when growth is weak or leaf color suggests nutrient gap."
        ActionType.APPLIED_PESTICIDE_FUNGICIDE -> "Use only when symptoms indicate pest or fungal pressure."
        ActionType.PRUNED_AFFECTED_LEAVES -> "Remove visibly damaged leaves to reduce spread stress."
        ActionType.MONITORED_ONLY -> "No intervention now; continue daily photo tracking."
        ActionType.REPLANTED -> "Use when plant survival chance is very low."
    }
}
