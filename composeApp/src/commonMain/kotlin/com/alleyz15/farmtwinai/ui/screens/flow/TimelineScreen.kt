package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import com.alleyz15.farmtwinai.ui.theme.isAppDarkTheme
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
import com.alleyz15.farmtwinai.domain.model.ActionTrackerFollowUp
import com.alleyz15.farmtwinai.domain.model.TimelineDay
import com.alleyz15.farmtwinai.domain.model.TimelinePhotoAssessment
import com.alleyz15.farmtwinai.domain.model.TimelineRecoveryForecast
import com.alleyz15.farmtwinai.domain.model.TimelineStageVisual
import com.alleyz15.farmtwinai.domain.model.TimelineStatus
import com.alleyz15.farmtwinai.ui.components.AuroraBackground
import com.alleyz15.farmtwinai.ui.components.ImagePickerController
import com.alleyz15.farmtwinai.ui.components.OnboardingAdaptiveWidth
import com.alleyz15.farmtwinai.ui.components.PlatformDataUrlImage
import com.alleyz15.farmtwinai.ui.components.StatusBadge
import com.alleyz15.farmtwinai.ui.components.rememberImagePickerController
import com.alleyz15.farmtwinai.ui.theme.Mint200
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
    val darkTheme = isAppDarkTheme()
    val cardAlpha = if (darkTheme) 0.4f else 0.9f

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = cardAlpha)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = Mint200)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
fun TimelineScreen(
    days: List<TimelineDay>,
    selectedDay: TimelineDay,
    farmStartDate: String?,
    healthScore: Int,
    stageVisual: TimelineStageVisual?,
    stageVisualError: String?,
    isLoadingStageVisual: Boolean,
    photoAssessment: TimelinePhotoAssessment?,
    photoAssessmentError: String?,
    isAssessingPhoto: Boolean,
    resolvedStatus: TimelineStatus?,
    recoveryForecast: TimelineRecoveryForecast?,
    recommendedActionText: String,
    hasAssessmentForSelectedDay: Boolean,
    unlockedMaxDayNumber: Int,
    cachedPhotoBase64: String?,
    cachedPhotoMimeType: String?,
    isFarmConfigCacheReady: Boolean,
    actionBannerMessage: String?,
    persistentFollowUp: ActionTrackerFollowUp?,
    onBack: () -> Unit,
    onSelectDay: (Int) -> Unit,
    onLoadStageVisual: (Int, String) -> Unit,
    onRegenerateStageVisual: (Int, String) -> Unit,
    onCacheUploadedPhoto: (Int, String, String) -> Unit,
    onComparePhoto: (Int, String, String, String, Boolean?) -> Unit,
    onClearUploadedPhoto: (Int) -> Unit,
    onOpenActionPlan: () -> Unit,
    onOpenChat: () -> Unit,
    onConsumeActionBanner: () -> Unit,
    onAcknowledgeFollowUp: (Int) -> Unit,
) {
    var imagePickerMessage by remember(selectedDay.dayNumber) { mutableStateOf<String?>(null) }
    val visibleDays = remember(days, unlockedMaxDayNumber) {
        days.filter { it.dayNumber <= unlockedMaxDayNumber }
    }
    val selectedIndex = visibleDays.indexOfFirst { it.dayNumber == selectedDay.dayNumber }.let { if (it >= 0) it else 0 }
    val resolvedPhotoMimeType = cachedPhotoMimeType?.ifBlank { "image/jpeg" } ?: "image/jpeg"
    val pickedPhotoDataUrl = cachedPhotoBase64?.let { base64 ->
        if (base64.startsWith("data:")) base64 else "data:$resolvedPhotoMimeType;base64,$base64"
    }
    val timelineSubtitle = farmStartDate
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { "Started from $it" }
        ?: "Planting date to crop cycle"
    val expectedCalendarDate = remember(farmStartDate, selectedDay.dayNumber) {
        calculateTimelineDate(farmStartDate, selectedDay.dayNumber)
    }

    val imagePickerController: ImagePickerController = rememberImagePickerController(
        onImagePicked = { base64, mimeType ->
            onCacheUploadedPhoto(selectedDay.dayNumber, base64, mimeType)
            imagePickerMessage = "Photo selected (${mimeType}). Ready for AI compare."
        },
        onError = { message ->
            imagePickerMessage = message
        },
    )

    LaunchedEffect(selectedDay.dayNumber, selectedDay.expectedStage, isFarmConfigCacheReady) {
        if (isFarmConfigCacheReady) {
            onLoadStageVisual(selectedDay.dayNumber, selectedDay.expectedStage)
        }
    }

    LaunchedEffect(actionBannerMessage) {
        if (!actionBannerMessage.isNullOrBlank()) {
            // Auto-dismiss after 5 seconds
            kotlinx.coroutines.delay(5000)
            onConsumeActionBanner()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        
        OnboardingAdaptiveWidth { maxContentWidth, _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 16.dp)
                    .widthIn(max = maxContentWidth),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), CircleShape),
                    ) {
                        Icon(
                            imageVector = ArrowBackIcon,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground,
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
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = timelineSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(visibleDays.size) { index ->
                        val day = visibleDays[index]
                        val isSelected = day.dayNumber == selectedDay.dayNumber
                        val bgColor = if (isSelected) Mint200 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        val textColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onBackground
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgColor)
                                .clickable { onSelectDay(day.dayNumber) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Day ${day.dayNumber}", 
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, 
                                color = textColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!actionBannerMessage.isNullOrBlank() && persistentFollowUp == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.88f)),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            text = actionBannerMessage,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                if (persistentFollowUp != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7).copy(alpha = 0.92f)),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Follow-up for Day ${selectedDay.dayNumber}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E),
                            )
                            if (persistentFollowUp.followUpQuestion.isNotBlank()) {
                                Text(
                                    text = "Q: ${persistentFollowUp.followUpQuestion}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF78350F),
                                )
                            }
                            if (persistentFollowUp.nextBestAction.isNotBlank()) {
                                Text(
                                    text = "Next: ${persistentFollowUp.nextBestAction}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF78350F),
                                )
                            }
                            TextButton(
                                onClick = { onAcknowledgeFollowUp(selectedDay.dayNumber) },
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text("Acknowledge")
                            }
                        }
                    }
                }

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Day ${selectedDay.dayNumber} expected journey",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (resolvedStatus != null) {
                            StatusBadge(resolvedStatus.style())
                        }
                    }

                    expectedCalendarDate?.let { dateText ->
                        Text(
                            text = "Expected date: $dateText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // AI Expected Visual
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    val imageDataUrl = stageVisual?.imageDataUrl
                                    if (!imageDataUrl.isNullOrBlank()) {
                                        PlatformDataUrlImage(dataUrl = imageDataUrl, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
                                    } else {
                                        if (isLoadingStageVisual) {
                                            CircularProgressIndicator(color = Mint200, modifier = Modifier.size(24.dp))
                                        } else {
                                            Text("No AI image", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                            }
                            Text(
                                text = "Stage: ${selectedDay.expectedStage}", 
                                style = MaterialTheme.typography.labelSmall, 
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Regenerate Image", 
                                style = MaterialTheme.typography.labelSmall, 
                                color = Mint200,
                                modifier = Modifier.clickable { onRegenerateStageVisual(selectedDay.dayNumber, selectedDay.expectedStage) }
                            )
                        }

                        // Farmer Photo
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize().clickable { if (pickedPhotoDataUrl == null) imagePickerController.launchCamera() }, 
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (pickedPhotoDataUrl != null) {
                                        PlatformDataUrlImage(dataUrl = pickedPhotoDataUrl, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
                                    } else {
                                        Text("+ Add Photo", style = MaterialTheme.typography.labelSmall, color = Mint200)
                                    }
                                }
                            }
                            Text(
                                text = "Your Farm", 
                                style = MaterialTheme.typography.labelSmall, 
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (pickedPhotoDataUrl == null) {
                                Text(
                                    text = "Gallery Upload", 
                                    style = MaterialTheme.typography.labelSmall, 
                                    color = Mint200,
                                    modifier = Modifier.clickable { imagePickerController.launchGallery() }
                                )
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "Retake Photo", 
                                        style = MaterialTheme.typography.labelSmall, 
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                        modifier = Modifier.clickable { imagePickerController.launchCamera() }
                                    )
                                    Text(
                                        text = "Reupload", 
                                        style = MaterialTheme.typography.labelSmall, 
                                        color = Mint200,
                                        modifier = Modifier.clickable { imagePickerController.launchGallery() }
                                    )
                                }
                            }
                        }
                    }

                    if (!stageVisualError.isNullOrBlank()) {
                        Text(
                            text = stageVisualError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (imagePickerMessage != null && !isAssessingPhoto) {
                        Text(
                            text = imagePickerMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isAppDarkTheme()) Mint200 else MaterialTheme.colorScheme.primary,
                        )
                    }

                    // Main Action Button
                    Button(
                        onClick = {
                            val payload = cachedPhotoBase64
                            if (payload == null) {
                                imagePickerMessage = "Please snap or upload a plant photo first."
                                return@Button
                            }
                            onComparePhoto(selectedDay.dayNumber, selectedDay.expectedStage, payload, resolvedPhotoMimeType, null)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = !isAssessingPhoto && pickedPhotoDataUrl != null && !stageVisual?.imageDataUrl.isNullOrBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Mint200, 
                            contentColor = Color.Black,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isAssessingPhoto) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Assessing...", color = Color.Black)
                        } else {
                            val buttonText = when {
                                stageVisual?.imageDataUrl.isNullOrBlank() -> "Waiting for AI expected visual..."
                                pickedPhotoDataUrl == null -> "Upload photo to compare"
                                photoAssessment != null -> "Regenerate comparison"
                                else -> "Compare with AI"
                            }
                            Text(buttonText, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }

                    if (photoAssessment != null) {
                        val darkTheme = isAppDarkTheme()
                        val cardAlpha = if (darkTheme) 0.4f else 0.9f
                        val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = cardAlpha)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = containerColor),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Similarity", style = MaterialTheme.typography.labelSmall, color = Mint200)
                                        Text("${photoAssessment.similarityScore}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Health", style = MaterialTheme.typography.labelSmall, color = Mint200)
                                        Text("${photoAssessment.similarityScore}/100", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
                                        Text("Growth", style = MaterialTheme.typography.labelSmall, color = Mint200)
                                        Text(selectedDay.expectedGrowthRange, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Observed Stage", style = MaterialTheme.typography.labelSmall, color = Mint200)
                                    Text(photoAssessment.observedStage, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                                }

                                val currentNotes = listOf(photoAssessment.recommendation, photoAssessment.rationale).filter { it.isNotBlank() }.joinToString("\n\n")
                                if (currentNotes.isNotBlank()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("AI Notes", style = MaterialTheme.typography.labelSmall, color = Mint200)
                                        Text(currentNotes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f))
                                    }
                                }
                            }
                        }
                    } else if (photoAssessmentError != null) {
                        AuroraInfoCard("AI comparison error", "Unable to compare", photoAssessmentError)
                    } else {
                        AuroraInfoCard("Comparison pending", "Upload photo and compare", "Unlocks Health, Growth, and Stage data.")
                    }

                    val recovery = recoveryForecast
                    val showForecastOrAction = (recovery != null && (resolvedStatus == TimelineStatus.WARNING || resolvedStatus == TimelineStatus.ACTION_TAKEN || recovery.isUrgent)) || 
                                              (resolvedStatus == TimelineStatus.WARNING || resolvedStatus == TimelineStatus.ACTION_TAKEN || recoveryForecast?.isUrgent == true)
                    
                    if (showForecastOrAction) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isAppDarkTheme()) 0.4f else 0.9f)),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (recovery != null) {
                                    val trendLabel = when (recovery.trend) {
                                        com.alleyz15.farmtwinai.domain.model.RecoveryTrend.IMPROVING -> "Improving"
                                        com.alleyz15.farmtwinai.domain.model.RecoveryTrend.STABLE -> "Stable"
                                        com.alleyz15.farmtwinai.domain.model.RecoveryTrend.WORSENING -> "Worsening"
                                        com.alleyz15.farmtwinai.domain.model.RecoveryTrend.UNKNOWN -> "Unknown"
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Recovery Forecast (Trend: $trendLabel)", style = MaterialTheme.typography.labelSmall, color = Mint200)
                                        Text("ETA ${recovery.etaDaysMin}-${recovery.etaDaysMax} days • Confidence ${recovery.confidencePercent}%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(if (recovery?.isUrgent == true) "Urgent action required" else "Suggested action", style = MaterialTheme.typography.labelSmall, color = if (recovery?.isUrgent == true) MaterialTheme.colorScheme.error else Mint200)
                                    Text(recommendedActionText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                    Text("Confirm what you actually did so AI can track recovery accurately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                                }

                                Button(
                                    onClick = onOpenActionPlan,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Mint200, contentColor = Color.Black),
                                    enabled = hasAssessmentForSelectedDay,
                                ) {
                                    Text("Open action plan", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Text(
                        text = "Daily reminder: upload one photo each day to keep recovery prediction accurate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                    )
                }
            }
        }
    }
}

private fun summarizeRecommendationForFarmer(raw: String): String {
    val text = raw.trim().lowercase()
    if (text.isBlank()) return "Upload another photo tomorrow to keep tracking recovery."

    return when {
        text.contains("drain") || text.contains("waterlog") || text.contains("standing water") ->
            "Inspect drainage and remove standing water today."
        text.contains("water") || text.contains("irrig") || text.contains("dry") || text.contains("wilt") ->
            "Adjust watering to keep moisture steady, not excessive."
        text.contains("fertil") || text.contains("nutrient") || text.contains("nitrogen") || text.contains("potassium") || text.contains("phosph") ->
            "Apply a balanced fertilizer dose and monitor leaf response."
        text.contains("pest") || text.contains("fung") || text.contains("disease") || text.contains("spot") || text.contains("insect") ->
            "Check for pest or fungal signs and follow safe treatment guidance."
        text.contains("prun") || text.contains("remove") || text.contains("leaf") ->
            "Prune visibly affected leaves and monitor regrowth."
        else -> "Follow the recommended action and upload a new photo tomorrow."
    }
}

private fun calculateTimelineDate(startDate: String?, dayNumber: Int): String? {
    val plantedEpoch = startDate
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let(::isoDateToEpochDay)
        ?: return null
    val safeDay = dayNumber.coerceAtLeast(1)
    val targetEpoch = plantedEpoch + (safeDay - 1).toLong()
    return epochDayToIsoDate(targetEpoch)
}

private fun isoDateToEpochDay(value: String): Long? {
    val parts = value.split("-")
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31) return null

    var y = year.toLong()
    val m = month.toLong()
    val d = day.toLong()
    y -= if (m <= 2L) 1L else 0L
    val era = if (y >= 0L) y / 400L else (y - 399L) / 400L
    val yoe = y - era * 400L
    val mp = m + if (m > 2L) -3L else 9L
    val doy = (153L * mp + 2L) / 5L + d - 1L
    val doe = yoe * 365L + yoe / 4L - yoe / 100L + doy
    return era * 146097L + doe - 719468L
}

private fun epochDayToIsoDate(epochDay: Long): String {
    var z = epochDay + 719468L
    val era = if (z >= 0L) z / 146097L else (z - 146096L) / 146097L
    val doe = z - era * 146097L
    val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
    var y = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val d = doy - (153L * mp + 2L) / 5L + 1L
    val m = mp + if (mp < 10L) 3L else -9L
    if (m <= 2L) y += 1L

    return buildString {
        append(y.toString().padStart(4, '0'))
        append('-')
        append(m.toString().padStart(2, '0'))
        append('-')
        append(d.toString().padStart(2, '0'))
    }
}
