package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.alleyz15.farmtwinai.domain.model.ActionState
import com.alleyz15.farmtwinai.domain.model.ActionType
import com.alleyz15.farmtwinai.domain.model.ActionTrackerFollowUp
import com.alleyz15.farmtwinai.domain.model.RecoveryTrend
import com.alleyz15.farmtwinai.domain.model.TimelineRecoveryForecast
import com.alleyz15.farmtwinai.ui.components.AuroraBackground
import com.alleyz15.farmtwinai.ui.components.OnboardingAdaptiveWidth
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.OptionCard
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.ui.theme.Leaf400
import com.alleyz15.farmtwinai.ui.theme.isAppDarkTheme

@Composable
fun ActionConfirmationScreen(
    dayNumber: Int,
    cropName: String,
    latestAction: String,
    primaryRecommendedAction: ActionType,
    alternativeActions: List<ActionType>,
    recoveryForecast: TimelineRecoveryForecast?,
    followUp: ActionTrackerFollowUp?,
    onBack: () -> Unit,
    onOpenAiChat: (String) -> Unit,
    onOpenKnowledgeBase: (String) -> Unit,
    onSubmit: (ActionType, ActionState) -> Unit,
) {
    var selectedAction by remember(dayNumber, primaryRecommendedAction) { mutableStateOf(primaryRecommendedAction) }
    var selectedState by remember(dayNumber) { mutableStateOf(ActionState.DONE) }
    var pesticideWarningAccepted by remember(dayNumber, selectedAction) {
        mutableStateOf(selectedAction != ActionType.APPLIED_PESTICIDE_FUNGICIDE)
    }

    val actionChoices = remember(primaryRecommendedAction, alternativeActions) {
        (listOf(primaryRecommendedAction) + alternativeActions).distinct()
    }
    val requiresPesticideWarning = selectedAction == ActionType.APPLIED_PESTICIDE_FUNGICIDE
    val canSubmit = !requiresPesticideWarning || pesticideWarningAccepted
    val aiStarterPrompt = remember(dayNumber, cropName, latestAction, recoveryForecast, selectedAction, selectedState) {
        buildString {
            append("I'm reviewing Day ")
            append(dayNumber)
            if (cropName.isNotBlank()) {
                append(" for crop ")
                append(cropName)
            }
            append(" action: ")
            append(selectedAction.label())
            append(". Recommendation was: ")
            append(latestAction)
            append(". Current status is ")
            append(selectedState.name.lowercase())
            append(". ")
            if (recoveryForecast != null) {
                append("Forecast trend is ")
                append(recoveryForecast.trend.name.lowercase())
                append(" with ETA ")
                append(recoveryForecast.etaDaysMin)
                append("-")
                append(recoveryForecast.etaDaysMax)
                append(" days.")
            } else {
                append("No forecast yet.")
            }
            append(" Give me exact next steps for the next 48 hours.")
        }
    }
    val kbStarterQuery = remember(selectedAction, latestAction) {
        when (selectedAction) {
            ActionType.WATERED -> "watering schedule and soil moisture checks"
            ActionType.IMPROVED_DRAINAGE -> "field drainage improvement and waterlogging prevention"
            ActionType.ADJUSTED_FERTILIZER -> "fertilizer adjustment for weak growth"
            ActionType.APPLIED_PESTICIDE_FUNGICIDE -> "safe pesticide and fungicide timing with PPE"
            ActionType.PRUNED_AFFECTED_LEAVES -> "pruning diseased leaves and post-pruning care"
            ActionType.MONITORED_ONLY -> "crop monitoring checklist after stress symptoms"
            ActionType.REPLANTED -> "replanting best practices and early establishment"
        }
    }

    val darkTheme = isAppDarkTheme()

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        
        OnboardingAdaptiveWidth { maxContentWidth, _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 18.dp)
                    .widthIn(max = maxContentWidth),
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
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
                            text = "Action Summary",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Log your final crop action",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
            // 1. AI Recommendation Hero Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (darkTheme) 0.4f else 0.9f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF22C55E).copy(alpha = 0.15f), // Soft Green
                                    Color(0xFF3B82F6).copy(alpha = 0.15f)  // Soft Blue (Gemini Vibe)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(AiSparkHubIcon, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
                            Text("AI Solution • Powered by Gemini", style = MaterialTheme.typography.labelLarge, color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
                        }
                        Text(latestAction, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        
                        if (recoveryForecast != null) {
                            val trendLabel = when (recoveryForecast.trend) {
                                RecoveryTrend.IMPROVING -> "Improving"
                                RecoveryTrend.STABLE -> "Stable"
                                RecoveryTrend.WORSENING -> "Worsening"
                                RecoveryTrend.UNKNOWN -> "Unknown"
                            }
                            val eta = "${recoveryForecast.etaDaysMin}-${recoveryForecast.etaDaysMax}D"
                            val confidence = "${recoveryForecast.confidencePercent}%"
                            Text("Forecast: $trendLabel • ETA: $eta • Acc: $confidence", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            
                            if (recoveryForecast.isUrgent) {
                                Text("⚠️ Urgent action required today.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // 2. Assistance Shortcuts
            Text("Need more details?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AssistanceShortcutCard(
                    title = "AI Chat",
                    description = "Ask questions",
                    icon = AiSparkHubIcon,
                    onClick = { onOpenAiChat(aiStarterPrompt) },
                    modifier = Modifier.weight(1f)
                )
                AssistanceShortcutCard(
                    title = "KB Search",
                    description = "Find manuals",
                    icon = KnowledgeHubIcon,
                    onClick = { onOpenKnowledgeBase(kbStarterQuery.ifBlank { latestAction }) },
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Compact Action Selector
            Text("Action Taken", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            var expanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(selectedAction.label(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(if (selectedAction == primaryRecommendedAction) "AI Recommended" else "Alternative Action", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    actionChoices.forEach { actionType ->
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text(actionType.label(), fontWeight = FontWeight.Medium)
                                    Text(actionType.supportingDescription(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = {
                                selectedAction = actionType
                                if (actionType != ActionType.APPLIED_PESTICIDE_FUNGICIDE) {
                                    pesticideWarningAccepted = true
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }

            if (requiresPesticideWarning) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Safety Warning", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Use approved pesticide only, wear PPE, and avoid spraying during strong wind.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        DualActionButtons(
                            primaryLabel = "I agree",
                            onPrimary = { pesticideWarningAccepted = true },
                            secondaryLabel = null,
                            onSecondary = null,
                            primaryEnabled = !pesticideWarningAccepted,
                        )
                    }
                }
            }

            // 4. Status Chips
            Text("Current Status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionState.entries.forEach { state ->
                    val isSelected = selectedState == state
                    val bgColor = if (isSelected) Leaf400 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    val contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(bgColor, RoundedCornerShape(12.dp))
                            .clickable { selectedState = state }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                            color = contentColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            val statusGuide = when (selectedState) {
                ActionState.DONE -> "DONE: Action applied now. Next step: upload the next photo to verify recovery trend update."
                ActionState.NOT_YET -> "NOT YET: Action is pending. Next step: ask AI or KB for exact method, then apply and update status."
                ActionState.SKIP -> "SKIP: Action intentionally skipped. Next step: monitor symptoms closely and upload the next photo."
            }
            Text(
                text = statusGuide,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                modifier = Modifier.padding(bottom = 18.dp),
            )

            // 5. Bottom Save Buttons
            DualActionButtons(
                primaryLabel = "Save Action Update",
                onPrimary = { onSubmit(selectedAction, selectedState) },
                secondaryLabel = "Back to Timeline",
                onSecondary = onBack,
                primaryEnabled = canSubmit,
            )
                }
            }
        }
    }
}

private fun ActionType.label(): String {
    return when (this) {
        ActionType.WATERED -> "Adjust watering"
        ActionType.IMPROVED_DRAINAGE -> "Improve drainage"
        ActionType.ADJUSTED_FERTILIZER -> "Apply fertilizer"
        ActionType.APPLIED_PESTICIDE_FUNGICIDE -> "Apply pesticide/fungicide"
        ActionType.PRUNED_AFFECTED_LEAVES -> "Prune affected leaves"
        ActionType.MONITORED_ONLY -> "Monitor only"
        ActionType.REPLANTED -> "Replanted"
    }
}

private fun ActionType.supportingDescription(): String {
    return when (this) {
        ActionType.WATERED -> "Useful when leaves curl, droop, or soil is too dry."
        ActionType.IMPROVED_DRAINAGE -> "Useful when roots may be stressed by standing water."
        ActionType.ADJUSTED_FERTILIZER -> "Useful when growth is weak or leaf color suggests nutrient gap."
        ActionType.APPLIED_PESTICIDE_FUNGICIDE -> "Use only when symptoms indicate pest or fungal pressure."
        ActionType.PRUNED_AFFECTED_LEAVES -> "Remove visibly damaged leaves to reduce spread stress."
        ActionType.MONITORED_ONLY -> "No intervention now; continue daily photo tracking."
        ActionType.REPLANTED -> "Use when plant survival chance is very low."
    }
}

@Composable
private fun AssistanceShortcutCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isAppDarkTheme()
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (darkTheme) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        ),
        border = BorderStroke(1.dp, if (darkTheme) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Leaf400.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = Leaf400, modifier = Modifier.size(20.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
        }
    }
}

private val AiSparkHubIcon: ImageVector
    get() = ImageVector.Builder(
        name = "AiSparkHub",
        defaultWidth = 20.dp,
        defaultHeight = 20.dp,
        viewportWidth = 20f,
        viewportHeight = 20f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(10f, 2f)
            lineTo(11.5f, 6f)
            lineTo(15.5f, 7.5f)
            lineTo(11.5f, 9f)
            lineTo(10f, 13f)
            lineTo(8.5f, 9f)
            lineTo(4.5f, 7.5f)
            lineTo(8.5f, 6f)
            close()
            moveTo(15.5f, 12.5f)
            lineTo(16.2f, 14.3f)
            lineTo(18f, 15f)
            lineTo(16.2f, 15.7f)
            lineTo(15.5f, 17.5f)
            lineTo(14.8f, 15.7f)
            lineTo(13f, 15f)
            lineTo(14.8f, 14.3f)
            close()
        }
    }.build()

private val KnowledgeHubIcon: ImageVector
    get() = ImageVector.Builder(
        name = "KnowledgeHub",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(4f, 5f)
            curveTo(4f, 3.9f, 4.9f, 3f, 6f, 3f)
            horizontalLineTo(18f)
            curveTo(19.1f, 3f, 20f, 3.9f, 20f, 5f)
            verticalLineTo(19f)
            curveTo(20f, 20.1f, 19.1f, 21f, 18f, 21f)
            horizontalLineTo(6f)
            curveTo(4.9f, 21f, 4f, 20.1f, 4f, 19f)
            close()
            moveTo(7f, 7f)
            horizontalLineTo(17f)
            verticalLineTo(9f)
            horizontalLineTo(7f)
            close()
            moveTo(7f, 11f)
            horizontalLineTo(17f)
            verticalLineTo(13f)
            horizontalLineTo(7f)
            close()
            moveTo(7f, 15f)
            horizontalLineTo(14f)
            verticalLineTo(17f)
            horizontalLineTo(7f)
            close()
        }
    }.build()

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

