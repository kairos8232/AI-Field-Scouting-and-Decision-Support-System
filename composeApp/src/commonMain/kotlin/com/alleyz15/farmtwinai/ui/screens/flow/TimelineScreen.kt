package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.domain.model.TimelineDay
import com.alleyz15.farmtwinai.domain.model.TimelinePhotoAssessment
import com.alleyz15.farmtwinai.domain.model.TimelineStageVisual
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.InfoCard
import com.alleyz15.farmtwinai.ui.components.MetricRow
import com.alleyz15.farmtwinai.ui.components.ImagePickerController
import com.alleyz15.farmtwinai.ui.components.PlatformDataUrlImage
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader
import com.alleyz15.farmtwinai.ui.components.StatusBadge
import com.alleyz15.farmtwinai.ui.components.TimelineChip
import com.alleyz15.farmtwinai.ui.components.rememberImagePickerController
import com.alleyz15.farmtwinai.ui.theme.style

@Composable
fun TimelineScreen(
    days: List<TimelineDay>,
    selectedDay: TimelineDay,
    healthScore: Int,
    stageVisual: TimelineStageVisual?,
    stageVisualError: String?,
    isLoadingStageVisual: Boolean,
    photoAssessment: TimelinePhotoAssessment?,
    photoAssessmentError: String?,
    isAssessingPhoto: Boolean,
    onBack: () -> Unit,
    onSelectDay: (Int) -> Unit,
    onLoadStageVisual: (Int, String) -> Unit,
    onComparePhoto: (Int, String, String, String, Boolean?) -> Unit,
    onOpenChat: () -> Unit,
) {
    var similarityFeedback by remember(selectedDay.dayNumber) { mutableStateOf<Boolean?>(null) }
    var pickedPhotoBase64 by remember(selectedDay.dayNumber) { mutableStateOf<String?>(null) }
    var pickedPhotoMimeType by remember(selectedDay.dayNumber) { mutableStateOf("image/jpeg") }
    var imagePickerMessage by remember(selectedDay.dayNumber) { mutableStateOf<String?>(null) }

    val imagePickerController: ImagePickerController = rememberImagePickerController(
        onImagePicked = { base64, mimeType ->
            pickedPhotoBase64 = base64
            pickedPhotoMimeType = mimeType
            imagePickerMessage = "Photo selected (${mimeType}). Ready for AI compare."
        },
        onError = { message ->
            imagePickerMessage = message
        },
    )

    LaunchedEffect(selectedDay.dayNumber, selectedDay.expectedStage) {
        onLoadStageVisual(selectedDay.dayNumber, selectedDay.expectedStage)
    }

    AppScaffold(title = "Daily Timeline", subtitle = "Planting date to crop cycle", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Expected daily journey",
                // Removed descriptive body to save vertical space
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                days.forEach { day ->
                    TimelineChip(
                        title = "Day ${day.dayNumber}",
                        selected = day.dayNumber == selectedDay.dayNumber,
                        onClick = { onSelectDay(day.dayNumber) },
                    )
                }
            }
            StatusBadge(selectedDay.status.style())
            if (isLoadingStageVisual) {
                CircularProgressIndicator()
            } else {
                stageVisual?.imageDataUrl?.takeIf { it.isNotBlank() }?.let { imageDataUrl ->
                    PlatformDataUrlImage(
                        dataUrl = imageDataUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stageVisual?.title ?: "Generating expected visual...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val desc = stageVisual?.description ?: stageVisualError
                        if (!desc.isNullOrBlank()) {
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            onLoadStageVisual(selectedDay.dayNumber, selectedDay.expectedStage)
                        }
                    ) {
                        Text("Regenerate")
                    }
                }
            }
            SectionHeader(
                title = "Like this AI image?",
                // Removed instruction body text to save space
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = {
                        similarityFeedback = true
                        onOpenChat()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (similarityFeedback == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    ),
                ) {
                    Text("Yes")
                }
                OutlinedButton(
                    onClick = { similarityFeedback = false },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("No")
                }
            }
            if (similarityFeedback != null) {
                Text(
                    text = if (similarityFeedback == true) "✓ Marked as similar." else "✗ Marked as not similar. Upload a photo for AI assessment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (imagePickerMessage != null) {
                Text(
                    text = imagePickerMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = { imagePickerController.launchCamera() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Snap Photo")
                }
                OutlinedButton(
                    onClick = { imagePickerController.launchGallery() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Gallery")
                }
            }
            OutlinedButton(
                onClick = {
                    val payload = pickedPhotoBase64
                    if (payload == null) {
                        imagePickerMessage = "Please snap or upload a plant photo first."
                        return@OutlinedButton
                    }
                    onComparePhoto(
                        selectedDay.dayNumber,
                        selectedDay.expectedStage,
                        payload,
                        pickedPhotoMimeType,
                        similarityFeedback,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Compare with AI")
            }
            
            if (isAssessingPhoto) {
                CircularProgressIndicator()
            }
            if (photoAssessment != null) {
                InfoCard(
                    title = "AI photo comparison",
                    value = "Similarity ${photoAssessment.similarityScore}% (${if (photoAssessment.isSimilar) "Similar" else "Not similar"})",
                    supporting = "Observed stage: ${photoAssessment.observedStage}\nRecommendation: ${photoAssessment.recommendation}",
                )
            } else if (photoAssessmentError != null) {
                InfoCard(
                    title = "AI photo comparison",
                    value = "Comparison unavailable",
                    supporting = photoAssessmentError,
                )
            }
            MetricRow(
                leftTitle = "Health", 
                leftValue = "$healthScore/100",
                rightTitle = "Growth", 
                rightValue = selectedDay.expectedGrowthRange
            )
            InfoCard("Expected stage", selectedDay.expectedStage)
            if (selectedDay.notes.isNotBlank()) {
                InfoCard("Notes", selectedDay.notes)
            }
        }
    }
}
