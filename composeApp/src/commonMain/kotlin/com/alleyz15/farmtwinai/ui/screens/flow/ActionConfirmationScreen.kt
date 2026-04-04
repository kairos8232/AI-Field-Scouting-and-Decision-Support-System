package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.alleyz15.farmtwinai.domain.model.ActionState
import com.alleyz15.farmtwinai.domain.model.ActionType
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.OptionCard
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader

@Composable
fun ActionConfirmationScreen(
    latestAction: String,
    onBack: () -> Unit,
    onSubmit: (ActionType, ActionState) -> Unit,
) {
    var selectedAction by remember { mutableStateOf(ActionType.IMPROVED_DRAINAGE) }
    var selectedState by remember { mutableStateOf(ActionState.DONE) }

    AppScaffold(title = "Action Confirmation", subtitle = "Record what happened next", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Recommended action",
                body = latestAction,
            )
            SectionHeader(title = "Action type")
            ActionType.entries.forEach { actionType ->
                OptionCard(
                    title = actionType.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                    description = "Mock action record for Phase 1.",
                    selected = actionType == selectedAction,
                    onClick = { selectedAction = actionType },
                )
            }
            SectionHeader(title = "Current status")
            ActionState.entries.forEach { state ->
                OptionCard(
                    title = state.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                    description = "This will later change simulation and timeline state.",
                    selected = state == selectedState,
                    onClick = { selectedState = state },
                )
            }
            DualActionButtons(
                primaryLabel = "Save Mock Result",
                onPrimary = { onSubmit(selectedAction, selectedState) },
                secondaryLabel = "Back to Chat",
                onSecondary = onBack,
            )
        }
    }
}
