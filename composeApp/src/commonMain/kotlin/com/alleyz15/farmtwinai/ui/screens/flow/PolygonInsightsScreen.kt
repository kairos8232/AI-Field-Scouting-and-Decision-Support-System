package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.domain.model.FieldInsightReport
import com.alleyz15.farmtwinai.domain.model.FarmPoint
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.InfoCard
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

@Composable
fun PolygonInsightsScreen(
    boundaryPoints: List<FarmPoint>,
    report: FieldInsightReport?,
    isSubmitting: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSubmitPolygon: () -> Unit,
    onContinue: () -> Unit,
) {
    AppScaffold(
        title = "Polygon Analysis",
        subtitle = "Earth Engine and crop recommendations",
        onBack = onBack,
    ) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Step 4: Submit polygon",
                body = "Submit your boundary polygon. Backend can query Earth Engine by polygon and centroid, then return soil/environment summary and recommended crops.",
            )

            InfoCard(
                title = "Boundary geometry",
                value = "${boundaryPoints.size} vertices",
                supporting = "Approx area ratio: ${formatDecimal(polygonArea(boundaryPoints), 3)}",
            )

            if (isSubmitting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Submitting polygon and requesting insights...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (report != null) {
                SectionHeader(title = "Step 6: Soil & environment summary")
                InfoCard(
                    title = "Earth Engine summary",
                    value = "NDVI ${formatDecimal(report.summary.ndviMean, 2)} | Soil moisture ${formatDecimal(report.summary.soilMoistureMean, 2)}",
                    supporting = "Rain (7d): ${formatDecimal(report.summary.rainfallMm7d, 1)} mm\nTemp: ${formatDecimal(report.summary.averageTempC, 1)} C\nCentroid: ${formatDecimal(report.summary.centroidLat, 5)}, ${formatDecimal(report.summary.centroidLng, 5)}\n${report.summary.notes}",
                )

                SectionHeader(title = "Step 7: Gemini crop recommendations")
                report.recommendations.forEach { recommendation ->
                    InfoCard(
                        title = recommendation.cropName,
                        value = "Suitability: ${recommendation.suitability}",
                        supporting = recommendation.rationale,
                    )
                }
            }

            DualActionButtons(
                primaryLabel = if (report == null) "Submit Polygon" else "Continue: Divide Into Lots",
                onPrimary = if (report == null) onSubmitPolygon else onContinue,
                secondaryLabel = if (report == null) "Skip For Now" else "Re-run Analysis",
                onSecondary = if (report == null) onContinue else onSubmitPolygon,
            )
        }
    }
}

private fun polygonArea(points: List<FarmPoint>): Double {
    if (points.size < 3) return 0.0
    var sum = 0.0
    for (i in points.indices) {
        val p1 = points[i]
        val p2 = points[(i + 1) % points.size]
        sum += (p1.x * p2.y - p2.x * p1.y)
    }
    return abs(sum) / 2.0
}

private fun formatDecimal(value: Double, decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val rounded = round(value * factor) / factor
    return rounded.toString()
}
