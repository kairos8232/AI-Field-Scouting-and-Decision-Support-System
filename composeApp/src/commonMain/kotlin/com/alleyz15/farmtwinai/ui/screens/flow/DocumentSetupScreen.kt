package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.domain.model.DocumentExtractionSummary
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader

@Composable
fun DocumentSetupScreen(
    summary: DocumentExtractionSummary,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    AppScaffold(title = "Document Setup", subtitle = "Mock upload and extraction", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Upload farm-related documents",
                body = "Phase 1 does not process files yet, but the UI communicates how document-assisted onboarding will work later.",
            )
            listOf(
                "Land title / lot info",
                "Soil report",
                "Crop suitability report",
                "PDF or image of farm sketch",
            ).forEach {
                OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Text("Attach $it")
                }
            }
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(summary.title, style = MaterialTheme.typography.titleMedium)
                    summary.bullets.forEach { bullet ->
                        Text("• $bullet", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            DualActionButtons(
                primaryLabel = "Use Extracted Summary",
                onPrimary = onContinue,
                secondaryLabel = "Continue With Mock Data",
                onSecondary = onContinue,
            )
        }
    }
}
