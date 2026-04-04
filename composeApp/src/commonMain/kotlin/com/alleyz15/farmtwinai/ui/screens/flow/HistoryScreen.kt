package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.runtime.Composable
import com.alleyz15.farmtwinai.domain.model.FarmTwinSnapshot
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.InfoCard
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader
import com.alleyz15.farmtwinai.ui.components.StatusBadge
import com.alleyz15.farmtwinai.ui.theme.style

@Composable
fun HistoryScreen(
    snapshot: FarmTwinSnapshot,
    onBack: () -> Unit,
) {
    AppScaffold(title = "History", subtitle = "Issues, recommendations, actions", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Past issue logs",
                body = "History combines issue detection, AI recommendations, action confirmations, and timeline updates.",
            )
            snapshot.issueLogs.forEach { issue ->
                StatusBadge(issue.status.style())
                InfoCard(issue.title, issue.summary, issue.dayLabel)
            }
            SectionHeader(title = "Past action confirmations")
            snapshot.actionRecords.forEach { record ->
                InfoCard(
                    title = record.actionType.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                    value = record.resultSummary,
                    supporting = "${record.dayLabel} • ${record.state.name.replace('_', ' ')}",
                )
            }
        }
    }
}
