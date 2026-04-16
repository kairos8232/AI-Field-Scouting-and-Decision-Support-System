package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.domain.model.TimelineDay
import com.alleyz15.farmtwinai.domain.model.TimelinePhotoAssessment
import com.alleyz15.farmtwinai.domain.model.TimelineStageVisual
import com.alleyz15.farmtwinai.ui.components.AuroraBackground
import com.alleyz15.farmtwinai.ui.components.ImagePickerController
import com.alleyz15.farmtwinai.ui.components.OnboardingAdaptiveWidth
import com.alleyz15.farmtwinai.ui.components.PlatformDataUrlImage
import com.alleyz15.farmtwinai.ui.components.StatusBadge
import com.alleyz15.farmtwinai.ui.components.rememberImagePickerController
import com.alleyz15.farmtwinai.ui.theme.CardDark
import com.alleyz15.farmtwinai.ui.theme.Mint200
import com.alleyz15.farmtwinai.ui.theme.Sand100
import com.alleyz15.farmtwinai.ui.theme.style

private val ArrowBackIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ArrowBack",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(20f, 11f)
            lineTo(7.83f, 11f)
            lineToRelative(5.59f, -5.59f)
            lineTo(12f, 4f)
            lineToRelative(-8f, 8f)
            lineToRelative(8f, 8f)
            lineToRelative(1.41f, -1.41f)
            lineTo(7.83f, 13f)
            lineTo(20f, 13f)
            close()
        }
    }.build()

@Composable
fun AuroraInfoCard(
    title: String,
    value: String,
    supporting: String? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = Mint200)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = Sand100.copy(alpha = 0.85f))
            }
        }
    }
}

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

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        
        OnboardingAdaptiveWidth { maxContentWidth, _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.08f), CircleShape),
                    ) {
                        Icon(
                            imageVector = ArrowBackIcon,
                            contentDescription = "Back",
                            tint = Sand100,
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Daily Timeline",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Sand100,
                        )
                        Text(
                            text = "Planting date to crop cycle",
                            style = MaterialTheme.typography.bodySmall,
                            color = Sand100.copy(alpha = 0.8f),
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Expected daily journey",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Sand100,
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    days.forEach { day ->
                        val selected = day.dayNumber == selectedDay.dayNumber
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) Mint200 else Color.White.copy(alpha = 0.1f))
                                .clickable { onSelectDay(day.dayNumber) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                "Day ${day.dayNumber}", 
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) Color.Black else Sand100
                            )
                        }
                    }
                }
                StatusBadge(selectedDay.status.style())
                if (isLoadingStageVisual) {
                    CircularProgressIndicator(color = Mint200)
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
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            val desc = stageVisual?.description ?: stageVisualError
                            if (!desc.isNullOrBlank()) {
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Sand100.copy(alpha = 0.8f)
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                onLoadStageVisual(selectedDay.dayNumber, selectedDay.expectedStage)
                            }
                        ) {
                            Text("Regenerate", color = Mint200)
                        }
                    }
                }
                
                Text(
                    text = "Like this AI image?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Sand100,
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
                            containerColor = if (similarityFeedback == true) Mint200 else Color.White.copy(alpha = 0.1f),
                            contentColor = if (similarityFeedback == true) Color.Black else Sand100,
                        ),
                    ) {
                        Text("Yes")
                    }
                    Button(
                        onClick = { similarityFeedback = false },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            contentColor = Sand100,
                        ),
                    ) {
                        Text("No")
                    }
                }
                if (similarityFeedback != null) {
                    Text(
                        text = if (similarityFeedback == true) "✓ Marked as similar." else "✗ Marked as not similar. Upload a photo for AI assessment.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mint200,
                    )
                }
                if (imagePickerMessage != null) {
                    Text(
                        text = imagePickerMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = Mint200,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = { imagePickerController.launchCamera() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint200, contentColor = Color.Black),
                    ) {
                        Text("Snap Photo")
                    }
                    Button(
                        onClick = { imagePickerController.launchGallery() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Sand100),
                    ) {
                        Text("Gallery")
                    }
                }
                Button(
                    onClick = {
                        val payload = pickedPhotoBase64
                        if (payload == null) {
                            imagePickerMessage = "Please snap or upload a plant photo first."
                            return@Button
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Sand100),
                ) {
                    Text("Compare with AI")
                }
                
                if (isAssessingPhoto) {
                    CircularProgressIndicator(color = Mint200)
                }
                
                if (photoAssessment != null) {
                    AuroraInfoCard(
                        title = "AI photo comparison",
                        value = "Similarity ${photoAssessment.similarityScore}% (${if (photoAssessment.isSimilar) "Similar" else "Not similar"})",
                        supporting = "Observed stage: ${photoAssessment.observedStage}\nRecommendation: ${photoAssessment.recommendation}",
                    )
                } else if (photoAssessmentError != null) {
                    AuroraInfoCard(
                        title = "AI photo comparison",
                        value = "Comparison unavailable",
                        supporting = photoAssessmentError,
                    )
                }

                // If real analysis is available, use photoAssessment fields. Otherwise fallback to placeholders.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AuroraInfoCard(
                        title = "Health", 
                        value = if (photoAssessment != null) "${photoAssessment.similarityScore}/100" else "$healthScore/100", 
                        modifier = Modifier.weight(1f)
                    )
                    AuroraInfoCard(
                        title = "Growth", 
                        value = if (photoAssessment != null) "Realtime tracking" else selectedDay.expectedGrowthRange, 
                        modifier = Modifier.weight(1f)
                    )
                }
                
                AuroraInfoCard(
                    title = "Observed Stage", 
                    value = if (photoAssessment != null) photoAssessment.observedStage else selectedDay.expectedStage
                )
                
                val currentNotes = if (photoAssessment != null) {
                    "${photoAssessment.recommendation}\n\n${photoAssessment.rationale}"
                } else {
                    selectedDay.notes
                }
                
                if (currentNotes.isNotBlank()) {
                    AuroraInfoCard("Notes", currentNotes)
                }
            }
        }
    }
}
