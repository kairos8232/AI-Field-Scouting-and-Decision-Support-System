package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.domain.model.AppMode
import com.alleyz15.farmtwinai.ui.components.OnboardingAdaptiveWidth
import com.alleyz15.farmtwinai.ui.components.OnboardingBackground
import com.alleyz15.farmtwinai.ui.theme.CardDark
import com.alleyz15.farmtwinai.ui.theme.Leaf400
import com.alleyz15.farmtwinai.ui.theme.Mint200
import com.alleyz15.farmtwinai.ui.theme.Sand100
import kotlinx.coroutines.delay

@Composable
fun UserSituationScreen(
    onBack: () -> Unit,
    onSituationSelected: (AppMode) -> Unit,
) {
    var showContent by remember { mutableStateOf(false) }
    var pendingSelection by remember { mutableStateOf<AppMode?>(null) }
    var pendingBack by remember { mutableStateOf(false) }
    val selectedMode = pendingSelection
    val headerAlpha by animateFloatAsState(
        targetValue = if (selectedMode == null) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "situationHeaderAlpha",
    )

    LaunchedEffect(Unit) {
        showContent = true
    }

    LaunchedEffect(pendingBack) {
        if (pendingBack) {
            showContent = false
            delay(220)
            onBack()
        }
    }

    LaunchedEffect(pendingSelection) {
        val selection = pendingSelection ?: return@LaunchedEffect
        delay(260)
        showContent = false
        delay(80)
        onSituationSelected(selection)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingBackground(overlayAlpha = 0.50f)

        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(animationSpec = tween(320)) +
                slideInVertically(
                    initialOffsetY = { it / 10 },
                    animationSpec = tween(360),
                ) +
                scaleIn(
                    initialScale = 0.97f,
                    animationSpec = tween(360),
                ),
            exit = fadeOut(animationSpec = tween(220)) +
                slideOutVertically(
                    targetOffsetY = { it / 12 },
                    animationSpec = tween(240),
                ) +
                scaleOut(
                    targetScale = 0.98f,
                    animationSpec = tween(240),
                ),
            modifier = Modifier.fillMaxSize(),
        ) {
            OnboardingAdaptiveWidth { maxContentWidth, isCompact ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = maxContentWidth),
                    ) {
                        IconButton(
                            onClick = { pendingBack = true },
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .alpha(headerAlpha),
                        ) {
                            Icon(
                                imageVector = ArrowBackIcon,
                                contentDescription = "Back",
                                tint = Sand100,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Your Situation",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Sand100,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = maxContentWidth)
                            .alpha(headerAlpha),
                    )
                    Text(
                        text = "Choose how you want to start. You will continue directly into farm setup.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Sand100.copy(alpha = 0.78f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = maxContentWidth)
                            .padding(top = 10.dp)
                            .alpha(headerAlpha),
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (isCompact) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = maxContentWidth),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            SelectableSituationCard(
                                title = "I have not planted yet",
                                subtitle = "Plan your farm before starting",
                                icon = LandPlanIcon,
                                accent = Mint200,
                                mode = AppMode.PLANNING,
                                selectedMode = selectedMode,
                                onClick = {
                                    if (pendingSelection == null) pendingSelection = AppMode.PLANNING
                                },
                            )
                            SelectableSituationCard(
                                title = "I already planted",
                                subtitle = "Set up monitoring for your current field",
                                icon = CropGrowthIcon,
                                accent = Leaf400,
                                mode = AppMode.LIVE_MONITORING,
                                selectedMode = selectedMode,
                                onClick = {
                                    if (pendingSelection == null) pendingSelection = AppMode.LIVE_MONITORING
                                },
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = maxContentWidth)
                                .height(320.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .matchParentSize(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                SelectableSituationCard(
                                    modifier = Modifier.weight(1f),
                                    title = "I have not planted yet",
                                    subtitle = "Plan your farm before starting",
                                    icon = LandPlanIcon,
                                    accent = Mint200,
                                    mode = AppMode.PLANNING,
                                    selectedMode = selectedMode,
                                    onClick = {
                                        if (pendingSelection == null) pendingSelection = AppMode.PLANNING
                                    },
                                )
                                SelectableSituationCard(
                                    modifier = Modifier.weight(1f),
                                    title = "I already planted",
                                    subtitle = "Set up monitoring for your current field",
                                    icon = CropGrowthIcon,
                                    accent = Leaf400,
                                    mode = AppMode.LIVE_MONITORING,
                                    selectedMode = selectedMode,
                                    onClick = {
                                        if (pendingSelection == null) pendingSelection = AppMode.LIVE_MONITORING
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SelectableSituationCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    mode: AppMode,
    selectedMode: AppMode?,
    onClick: () -> Unit,
) {
    val isSelected = selectedMode == mode
    val isOtherSelected = selectedMode != null && !isSelected
    val cardHeight by animateDpAsState(
        targetValue = if (isSelected) 332.dp else 320.dp,
        animationSpec = tween(durationMillis = 240),
        label = "situationCardHeight",
    )
    val offsetY by animateDpAsState(
        targetValue = if (isSelected) (-4).dp else 0.dp,
        animationSpec = tween(durationMillis = 240),
        label = "situationCardOffsetY",
    )
    val alpha by animateFloatAsState(
        targetValue = when {
            isSelected -> 0f
            isOtherSelected -> 0f
            else -> 1f
        },
        animationSpec = tween(durationMillis = if (isSelected) 260 else 180),
        label = "situationCardAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1f,
        animationSpec = tween(durationMillis = 260),
        label = "situationCardScale",
    )

    Card(
        modifier = modifier
            .height(cardHeight)
            .offset(y = offsetY)
            .alpha(alpha)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardDark.copy(alpha = 0.86f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(30.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Sand100,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Sand100.copy(alpha = 0.78f),
                )
            }
        }
    }
}

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

private val LandPlanIcon: ImageVector
    get() = ImageVector.Builder(
        name = "LandPlan",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(4f, 5f)
            lineTo(10f, 3f)
            lineTo(15f, 5f)
            lineTo(20f, 3f)
            verticalLineTo(18f)
            lineTo(14f, 20f)
            lineTo(9f, 18f)
            lineTo(4f, 20f)
            close()
            moveTo(9.5f, 5.4f)
            verticalLineTo(15.7f)
            moveTo(14.5f, 6.1f)
            verticalLineTo(16.8f)
        }
    }.build()

private val CropGrowthIcon: ImageVector
    get() = ImageVector.Builder(
        name = "CropGrowth",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(11.2f, 20f)
            lineTo(11.2f, 13.2f)
            curveTo(8.8f, 13.4f, 6.9f, 15.1f, 5.8f, 17.4f)
            curveTo(5.6f, 14.2f, 7.7f, 11.3f, 11.2f, 10.8f)
            lineTo(11.2f, 4f)
            lineTo(12.8f, 4f)
            lineTo(12.8f, 9.2f)
            curveTo(15.8f, 8.8f, 18.2f, 6.6f, 19.2f, 3.8f)
            curveTo(19.7f, 7.7f, 16.9f, 11.2f, 12.8f, 11.6f)
            lineTo(12.8f, 20f)
            close()
        }
    }.build()
