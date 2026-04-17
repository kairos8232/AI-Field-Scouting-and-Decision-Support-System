package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
    cachedPhotoBase64: String?,
    cachedPhotoMimeType: String?,
    onBack: () -> Unit,
    onSelectDay: (Int) -> Unit,
    onLoadStageVisual: (Int, String) -> Unit,
    onCacheUploadedPhoto: (Int, String, String) -> Unit,
    onComparePhoto: (Int, String, String, String, Boolean?) -> Unit,
    onOpenChat: () -> Unit,
) {
    var similarityFeedback by remember(selectedDay.dayNumber) { mutableStateOf<Boolean?>(null) }
    var imagePickerMessage by remember(selectedDay.dayNumber) { mutableStateOf<String?>(null) }
    val selectedIndex = days.indexOfFirst { it.dayNumber == selectedDay.dayNumber }.let { if (it >= 0) it else 0 }
    val resolvedPhotoMimeType = cachedPhotoMimeType?.ifBlank { "image/jpeg" } ?: "image/jpeg"
    val pickedPhotoDataUrl = cachedPhotoBase64?.let { base64 ->
        if (base64.startsWith("data:")) base64 else "data:$resolvedPhotoMimeType;base64,$base64"
    }
    val dayPagerState = rememberPagerState(initialPage = selectedIndex, pageCount = { days.size })

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
        if (dayPagerState.currentPage != selectedIndex && selectedIndex in days.indices) {
            dayPagerState.animateScrollToPage(selectedIndex)
        }
    }

    LaunchedEffect(dayPagerState.currentPage) {
        val day = days.getOrNull(dayPagerState.currentPage) ?: return@LaunchedEffect
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

                    Text(
                        text = "Day ${selectedDay.dayNumber} expected journey",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (resolvedStatus != null) {
                        StatusBadge(resolvedStatus.style())
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isAppDarkTheme()) 0.35f else 0.8f)
                            ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            onLoadStageVisual(selectedDay.dayNumber, selectedDay.expectedStage)
                                            imagePickerMessage = "Regenerating expected image..."
                                        },
                                    )
                            ) {
                                val imageDataUrl = stageVisual?.imageDataUrl
                                if (!imageDataUrl.isNullOrBlank()) {
                                    PlatformDataUrlImage(
                                        dataUrl = imageDataUrl,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp)),
                                    )
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        if (isLoadingStageVisual) {
                                            CircularProgressIndicator(color = Mint200)
                                        } else {
                                            Text(
                                                text = "No AI image yet. Long press to regenerate.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stageVisual?.title ?: "AI expected visual",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "Expected for day ${selectedDay.dayNumber}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                if (pickedPhotoDataUrl == null) {
                                    Text(
                                        text = "Farmer upload",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Button(
                                        onClick = { imagePickerController.launchCamera() },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Mint200, contentColor = Color.Black),
                                    ) {
                                        Text("Snap")
                                    }
                                    OutlinedButton(
                                        onClick = { imagePickerController.launchGallery() },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Gallery")
                                    }
                                } else {
                                    Text(
                                        text = "Your preview (long press to retake)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(96.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = {
                                                    imagePickerController.launchCamera()
                                                    imagePickerMessage = "Long-press detected. Camera opened for retake."
                                                },
                                            )
                                    ) {
                                        PlatformDataUrlImage(
                                            dataUrl = pickedPhotoDataUrl,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        onLoadStageVisual(selectedDay.dayNumber, selectedDay.expectedStage)
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Regenerate", color = Mint200)
                                }
                            }
                        }
                    }

                    Text(
                        text = "Expected output: ${selectedDay.expectedStage}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    )
                
                    Text(
                        text = "Like this AI image?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                    Button(
                        onClick = {
                            similarityFeedback = true
                            imagePickerMessage = "Marked as similar. You can compare your photo now."
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (similarityFeedback == true) Mint200 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            contentColor = if (similarityFeedback == true) Color.Black else MaterialTheme.colorScheme.onBackground,
                        ),
                    ) {
                        Text("Yes")
                    }
                    Button(
                        onClick = {
                            similarityFeedback = false
                            imagePickerMessage = "Marked as not similar. Upload and compare to get AI assessment."
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            contentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                    ) {
                        Text("No")
                    }
                    Button(
                        onClick = {
                            val payload = cachedPhotoBase64
                            if (payload == null) {
                                imagePickerMessage = "Please snap or upload a plant photo first."
                                return@Button
                            }
                            onComparePhoto(
                                selectedDay.dayNumber,
                                selectedDay.expectedStage,
                                payload,
                                resolvedPhotoMimeType,
                                similarityFeedback,
                            )
                        },
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pickedPhotoDataUrl != null) Mint200 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            contentColor = if (pickedPhotoDataUrl != null) Color.Black else MaterialTheme.colorScheme.onBackground,
                        ),
                    ) {
                        Text("Compare")
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
                            color = if (isAppDarkTheme()) Mint200 else MaterialTheme.colorScheme.primary,
                        )
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

                    val currentNotes = "${photoAssessment.recommendation}\n\n${photoAssessment.rationale}"
                    if (currentNotes.isNotBlank()) {
                        AuroraInfoCard("Notes", currentNotes)
                    }
                    } else {
                    AuroraInfoCard(
                        title = "Comparison pending",
                        value = "Upload a farmer photo and tap Compare with AI to unlock Health, Growth, Observed Stage, and Notes.",
                    )
                    }
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
                            val day = days[page]
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
                    }
                }
            }
        }
    }
}
