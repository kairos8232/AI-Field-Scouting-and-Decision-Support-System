package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.alleyz15.farmtwinai.domain.model.LotSectionDraft
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.OptionCard
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader

@Composable
fun LotRecommendationScreen(
    lots: List<LotSectionDraft>,
    isAnalyzing: Boolean,
    bestLotId: String?,
    recommendationReason: String?,
    errorMessage: String?,
    dataSourceByLotId: Map<String, String>,
    onBack: () -> Unit,
    onAnalyze: () -> Unit,
    onFollowAndContinue: () -> Unit,
    onSkipAndContinue: () -> Unit,
) {
    AppScaffold(
        title = "Farm Setup",
        subtitle = "AI lot recommendation",
        onBack = onBack,
    ) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Earth Engine + Gemini analysis",
                body = "This step fills soil/water for each lot and recommends the best lot for the entered crop plans.",
            )

            androidx.compose.material3.OutlinedButton(
                onClick = onAnalyze,
                enabled = !isAnalyzing,
            ) {
                Text(if (isAnalyzing) "Analyzing lots..." else "Run analysis")
            }

            if (recommendationReason != null) {
                Text(
                    text = recommendationReason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            lots.forEach { lot ->
                val selected = lot.id == bestLotId
                OptionCard(
                    title = if (selected) "${lot.name} - Recommended" else lot.name,
                    description = "Crop: ${lot.cropPlan.ifBlank { "-" }} | Soil: ${lot.soilType.ifBlank { "-" }} | Water: ${lot.waterAvailability.ifBlank { "-" }}",
                    selected = selected,
                    onClick = {},
                )

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = "Data source: ${dataSourceByLotId[lot.id] ?: "Not analyzed yet"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            DualActionButtons(
                primaryLabel = "Follow AI Advice & Create Farm Twin",
                onPrimary = onFollowAndContinue,
                secondaryLabel = "Skip Advice & Create Farm Twin",
                onSecondary = onSkipAndContinue,
            )
        }
    }
}
