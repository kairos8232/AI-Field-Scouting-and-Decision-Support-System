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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
    onBack: () -> Unit,
    onSelectDay: (Int) -> Unit,
    onLoadStageVisual: (Int, String) -> Unit,
    onRegenerateStageVisual: (Int, String) -> Unit,
    onCacheUploadedPhoto: (Int, String, String) -> Unit,
    onComparePhoto: (Int, String, String, String, Boolean?) -> Unit,
    onClearUploadedPhoto: (Int) -> Unit,
    onOpenActionPlan: () -> Unit,
    onOpenChat: () -> Unit,
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
    val dayPagerState = rememberPagerState(initialPage = selectedIndex, pageCount = { visibleDays.size })

    val imagePickerController: ImagePickerController = rememberImagePickerController(
        onImagePicked = { base64, mimeType ->
            onCacheUploadedPhoto(selectedDay.dayNumber, base64, mimeType)
            imagePickerMessage = "Photo selected (${mimeType}). Ready for AI compare."
        },
        onError = { message ->
            imagePickerMessage = message
        },
    )

    LaunchedEffect(selectedDay.dayNumber, selectedDay.expectedStage) {
        onLoadStageVisual(selectedDay.dayNumber, selectedDay.expectedStage)
    }

    LaunchedEffect(selectedIndex) {
        if (dayPagerState.currentPage != selectedIndex && selectedIndex in visibleDays.indices) {
            dayPagerState.animateScrollToPage(selectedIndex)
        }
    }

    LaunchedEffect(dayPagerState.currentPage) {
        val day = visibleDays.getOrNull(dayPagerState.currentPage) ?: return@LaunchedEffect
        if (day.dayNumber != selectedDay.dayNumber) {
            onSelectDay(day.dayNumber)
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
                            text = "Planting date to crop cycle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                        )
                    }
                }
                
                    Spacer(modifier = Modifier.height(8.dp))

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
                                text = "Expected: ${selectedDay.expectedStage}", 
                                style = MaterialTheme.typography.labelSmall, 
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Regenerate Image", 
                                style = MaterialTheme.typography.labelSmall, 
                                color = Mint200,
                                modifier = Modifier.clickable { onLoadStageVisual(selectedDay.dayNumber, selectedDay.expectedStage) }
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
                                Text(
                                    text = "Retake Photo", 
                                    style = MaterialTheme.typography.labelSmall, 
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                    modifier = Modifier.clickable { imagePickerController.launchCamera() }
                                )
                            }
                        }
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
                    AuroraInfoCard(
                        title = "AI photo comparison",
                        value = "Similarity ${photoAssessment.similarityScore}% (${if (photoAssessment.isSimilar) "Similar" else "Not similar"})",
                    )
                    } else if (photoAssessmentError != null) {
                    AuroraInfoCard(
                        title = "AI photo comparison",
                        value = "Comparison unavailable",
                        supporting = photoAssessmentError,
                    )
                }

                    if (photoAssessment != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AuroraInfoCard(
                            title = "Health",
                            value = "${photoAssessment.similarityScore}/100",
                            modifier = Modifier.weight(1f)
                        )
                        AuroraInfoCard(
                            title = "Growth",
                            value = selectedDay.expectedGrowthRange,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    AuroraInfoCard(
                        title = "Observed Stage",
                        value = photoAssessment.observedStage
                    )

                    val currentNotes = listOf(photoAssessment.recommendation, photoAssessment.rationale).filter { it.isNotBlank() }.joinToString("\n\n")
                    if (currentNotes.isNotBlank()) {
                        AuroraInfoCard("Notes", currentNotes)
                    }
                    } else {
                    AuroraInfoCard(
                        title = "Comparison pending",
                        value = "Upload a farmer photo and tap Compare with AI to unlock Health, Growth, and Observed Stage.",
                    )
                    }

                    val recovery = recoveryForecast
                    val showRecoveryForecast = recovery != null && (
                        resolvedStatus == TimelineStatus.WARNING ||
                            resolvedStatus == TimelineStatus.ACTION_TAKEN ||
                            recovery.isUrgent
                        )

                    if (showRecoveryForecast) {
                        val trendLabel = when (recovery.trend) {
                            com.alleyz15.farmtwinai.domain.model.RecoveryTrend.IMPROVING -> "Improving"
                            com.alleyz15.farmtwinai.domain.model.RecoveryTrend.STABLE -> "Stable"
                            com.alleyz15.farmtwinai.domain.model.RecoveryTrend.WORSENING -> "Worsening"
                            com.alleyz15.farmtwinai.domain.model.RecoveryTrend.UNKNOWN -> "Unknown"
                        }
                        val confidenceTierLabel = when (recovery.confidenceTier) {
                            com.alleyz15.farmtwinai.domain.model.ForecastConfidenceTier.LOW -> "low"
                            com.alleyz15.farmtwinai.domain.model.ForecastConfidenceTier.MEDIUM -> "medium"
                            com.alleyz15.farmtwinai.domain.model.ForecastConfidenceTier.HIGH -> "high"
                        }
                        val sourceLabel = if (recovery.sourceDayNumber == selectedDay.dayNumber) {
                            "Based on Day ${recovery.sourceDayNumber}"
                        } else {
                            "No upload for this day. Keeping previous trend from Day ${recovery.sourceDayNumber}."
                        }

                        AuroraInfoCard(
                            title = "Recovery forecast",
                            value = "ETA ${recovery.etaDaysMin}-${recovery.etaDaysMax} days • Confidence ${recovery.confidencePercent}% ($confidenceTierLabel)",
                            supporting = "Trend: $trendLabel. $sourceLabel",
                        )
                    }

                    if (resolvedStatus == TimelineStatus.WARNING || resolvedStatus == TimelineStatus.ACTION_TAKEN || recoveryForecast?.isUrgent == true) {
                        AuroraInfoCard(
                            title = if (recoveryForecast?.isUrgent == true) "Urgent treatment needed" else "Suggested action",
                            value = recommendedActionText,
                            supporting = "Confirm what you actually did so AI can track recovery accurately.",
                        )
                        Button(
                            onClick = onOpenActionPlan,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Mint200, contentColor = Color.Black),
                            enabled = hasAssessmentForSelectedDay,
                        ) {
                            Text("Open action plan")
                        }
                    }

                    Text(
                        text = "Daily reminder: upload one photo each day to keep recovery prediction accurate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isAppDarkTheme()) 0.45f else 0.88f)
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Expected daily journey",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        HorizontalPager(
                            state = dayPagerState,
                            pageSpacing = 12.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) { page ->
                            val day = visibleDays[page]
                            val selected = day.dayNumber == selectedDay.dayNumber
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectDay(day.dayNumber) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) Mint200 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "Day ${day.dayNumber}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.Black else MaterialTheme.colorScheme.onBackground,
                                    )
                                    Text(
                                        text = day.expectedStage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (selected) Color.Black.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "Growth ${day.expectedGrowthRange}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) Color.Black.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Slide cards left/right to switch days",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        )
                        Text(
                            text = "Next day unlocks after check-in.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        )
                    }
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
