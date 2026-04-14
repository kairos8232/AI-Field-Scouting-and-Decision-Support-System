package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.domain.model.LotSectionDraft
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.OptionCard
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader

@Composable
fun LotRecommendationScreen(
    lots: List<LotSectionDraft>,
    isFetchingEnvData: Boolean,
    isAnalyzing: Boolean,
    bestLotId: String?,
    recommendationReason: String?,
    errorMessage: String?,
    dataSourceByLotId: Map<String, String>,
    recommendedCropByLotId: Map<String, String>,
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
                body = "This step fills soil/water for each lot and recommends the best crop-to-lot assignment for your selected crops.",
            )

            if (lots.size > 1) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onAnalyze,
                    enabled = !isAnalyzing && !isFetchingEnvData,
                ) {
                    Text(if (isAnalyzing) "Analyzing crop placements..." else "Analyze Crop Placements")
                }
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
                val suggestedCrop = recommendedCropByLotId[lot.id]
                val cropSwapSuggested = suggestedCrop != null && suggestedCrop.trim().lowercase() != lot.cropPlan.trim().lowercase()
                val selected = lot.id == bestLotId || cropSwapSuggested
                
                val soilText = if (isFetchingEnvData) "Fetching..." else lot.soilType.ifBlank { "-" }
                val waterText = if (isFetchingEnvData) "Fetching..." else lot.waterAvailability.ifBlank { "-" }
                
                OptionCard(
                    title = if (cropSwapSuggested) "${lot.name} - Swap Suggested" else lot.name,
                    description = "Crop: ${lot.cropPlan.ifBlank { "-" }} | Soil: $soilText | Water: $waterText",
                    selected = selected,
                    onClick = {},
                )

                if (suggestedCrop != null) {
                    Text(
                        text = if (cropSwapSuggested) {
                            "AI suggested crop: $suggestedCrop"
                        } else {
                            "AI suggested crop: Keep ${lot.cropPlan}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

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

            if (lots.size > 1) {
                DualActionButtons(
                    primaryLabel = "Follow AI Advice & Create Farm Twin",
                    onPrimary = onFollowAndContinue,
                    secondaryLabel = "Skip Advice & Create Farm Twin",
                    onSecondary = onSkipAndContinue,
                    primaryEnabled = !isFetchingEnvData,
                    secondaryEnabled = !isFetchingEnvData
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onSkipAndContinue,
                    enabled = !isFetchingEnvData,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text("Continue & Create Farm Twin")
                }
            }
        }
    }
}
