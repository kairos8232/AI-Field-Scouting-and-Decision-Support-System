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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.alleyz15.farmtwinai.ui.theme.isAppDarkTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.domain.model.FieldInsightHistoryCategory
import com.alleyz15.farmtwinai.domain.model.FieldInsightHistoryRecord
import com.alleyz15.farmtwinai.ui.components.AuroraBackground
import com.alleyz15.farmtwinai.ui.components.OnboardingAdaptiveWidth
import com.alleyz15.farmtwinai.ui.theme.Mint200

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

private enum class HistoryCategory(val label: String) {
    ALL("All"),
    SCANS("Scans"),
    ACTION_LOGS("Action Logs"),
    KB_SEARCHES("KB Searches"),
    TIMELINE_COMPARISONS("Timeline Comparisons"),
    CONVERSATIONS("Conversations"),
}

@Composable
fun HistoryScreen(
    historyRecords: List<FieldInsightHistoryRecord>?,
    onLoadHistory: () -> Unit,
    onContinueChat: (String) -> Unit,
    onBack: () -> Unit,
) {
    val darkTheme = isAppDarkTheme()
    val historyCardAlpha = if (darkTheme) 0.4f else 0.9f
    var selectedCategory by remember { mutableStateOf(HistoryCategory.ALL) }

    LaunchedEffect(Unit) {
        onLoadHistory()
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
                            text = "History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Your cloud-sync history",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                val records = historyRecords.orEmpty()
                val scanRecords = records.filter { it.category == FieldInsightHistoryCategory.SCAN }
                val actionRecords = records.filter { it.category == FieldInsightHistoryCategory.ACTION_LOG }
                val kbRecords = records.filter { it.category == FieldInsightHistoryCategory.KB_SEARCH }
                val comparisonRecords = records.filter { it.category == FieldInsightHistoryCategory.TIMELINE_COMPARISON }
                val conversationRecords = records.filter {
                    it.category == FieldInsightHistoryCategory.CONVERSATION || it.hasConversation || it.chatMessagesCount > 0
                }
                val visibleRecords = when (selectedCategory) {
                    HistoryCategory.ALL -> records
                    HistoryCategory.SCANS -> scanRecords
                    HistoryCategory.ACTION_LOGS -> actionRecords
                    HistoryCategory.KB_SEARCHES -> kbRecords
                    HistoryCategory.TIMELINE_COMPARISONS -> comparisonRecords
                    HistoryCategory.CONVERSATIONS -> conversationRecords
                }

                Text(
                    text = "History categories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HistoryCategory.entries.forEach { category ->
                        val count = when (category) {
                            HistoryCategory.ALL -> records.size
                            HistoryCategory.SCANS -> scanRecords.size
                            HistoryCategory.ACTION_LOGS -> actionRecords.size
                            HistoryCategory.KB_SEARCHES -> kbRecords.size
                            HistoryCategory.TIMELINE_COMPARISONS -> comparisonRecords.size
                            HistoryCategory.CONVERSATIONS -> conversationRecords.size
                        }
                        val selected = category == selectedCategory
                        val bg = if (selected) Mint200 else MaterialTheme.colorScheme.surface.copy(alpha = historyCardAlpha)
                        val fg = if (selected) Color.Black else MaterialTheme.colorScheme.onBackground

                        Box(
                            modifier = Modifier
                                .background(bg, RoundedCornerShape(999.dp))
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${category.label} ($count)",
                                style = MaterialTheme.typography.labelLarge,
                                color = fg,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }

                Text(
                    text = when (selectedCategory) {
                        HistoryCategory.ALL -> "All history records"
                        HistoryCategory.SCANS -> "Past scanned insights"
                        HistoryCategory.ACTION_LOGS -> "Farmer action logs"
                        HistoryCategory.KB_SEARCHES -> "Knowledge base query history"
                        HistoryCategory.TIMELINE_COMPARISONS -> "Expected vs actual timeline comparisons"
                        HistoryCategory.CONVERSATIONS -> "Entries with saved AI conversations"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                
                if (historyRecords == null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Mint200)
                    }
                } else if (visibleRecords.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = historyCardAlpha)),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("No records in this category", style = MaterialTheme.typography.labelLarge, color = Mint200)
                            Text(
                                when (selectedCategory) {
                                    HistoryCategory.ALL, HistoryCategory.SCANS -> "Scan some field insights to populate your history."
                                    HistoryCategory.ACTION_LOGS -> "Save action updates from the action plan to build action logs."
                                    HistoryCategory.KB_SEARCHES -> "Run searches in Knowledge Base to populate this category."
                                    HistoryCategory.TIMELINE_COMPARISONS -> "Upload and compare timeline photos to populate this category."
                                    HistoryCategory.CONVERSATIONS -> "Open a history item with chat and continue the conversation to build this category."
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                } else {
                    visibleRecords.forEach { record ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = historyCardAlpha)),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = record.dateString,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (darkTheme) Mint200 else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = record.title.ifBlank {
                                        when (record.category) {
                                            FieldInsightHistoryCategory.SCAN -> "Recommendation: ${record.recommendedCrops}"
                                            FieldInsightHistoryCategory.ACTION_LOG -> "Action: ${record.recommendedCrops}"
                                            FieldInsightHistoryCategory.KB_SEARCH -> "KB query"
                                            FieldInsightHistoryCategory.TIMELINE_COMPARISON -> "Timeline comparison"
                                            FieldInsightHistoryCategory.CONVERSATION -> "AI consultation"
                                            FieldInsightHistoryCategory.UNKNOWN -> "History record"
                                        }
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                if (record.recommendedCrops.isNotBlank()) {
                                    Text(
                                        text = record.recommendedCrops,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Mint200,
                                    )
                                }
                                Text(
                                    text = record.summaryNotes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                if (record.hasConversation) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${record.chatMessagesCount} messages",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Mint200
                                        )
                                        TextButton(onClick = { onContinueChat("Reviewing conversation for Log ${record.id.take(6)}...") }) {
                                            Text("Continue Chat", color = Mint200, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
