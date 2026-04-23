package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.domain.model.KnowledgeBaseResult
import com.alleyz15.farmtwinai.ui.components.AuroraBackground
import com.alleyz15.farmtwinai.ui.components.OnboardingAdaptiveWidth
import com.alleyz15.farmtwinai.ui.theme.Leaf400
import com.alleyz15.farmtwinai.ui.theme.isAppDarkTheme

private data class KnowledgeQuickChip(
    val label: String,
    val query: String,
)

private val defaultKnowledgeQuickChips = listOf(
    KnowledgeQuickChip("Pest", "pest control best practices"),
    KnowledgeQuickChip("Disease", "crop disease prevention guide"),
    KnowledgeQuickChip("Fertilizer", "fertilizer schedule and nutrient management"),
    KnowledgeQuickChip("Irrigation", "irrigation best practices for small farms"),
    KnowledgeQuickChip("Rice", "rice blast and paddy management"),
    KnowledgeQuickChip("Corn", "corn stem borer integrated pest management"),
    KnowledgeQuickChip("Chili", "chili flowering and fruit set nutrition"),
)

@Composable
fun KnowledgeBaseScreen(
    results: List<KnowledgeBaseResult>,
    isSearching: Boolean,
    errorMessage: String?,
    provider: String?,
    totalResults: Int,
    lastQuery: String,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
) {
    val darkTheme = isAppDarkTheme()
    val uriHandler = LocalUriHandler.current
    var draft by remember(lastQuery) { mutableStateOf(lastQuery) }
    var openUrlError by remember { mutableStateOf<String?>(null) }
    val fieldContainer = if (darkTheme) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()

        OnboardingAdaptiveWidth { maxContentWidth, _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 18.dp)
                    .widthIn(max = maxContentWidth),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Knowledge Base",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Search best practices, pest control, and crop guides.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
                        )
                    }
                }

                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Try: rice blast prevention in humid season") },
                    shape = RoundedCornerShape(16.dp),
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isSearching,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedBorderColor = Leaf400,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (darkTheme) 0.45f else 0.62f),
                        focusedContainerColor = fieldContainer,
                        unfocusedContainerColor = fieldContainer,
                        cursorColor = Leaf400,
                    ),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    defaultKnowledgeQuickChips.forEach { chip ->
                        OutlinedButton(
                            onClick = {
                                draft = chip.query
                                onSearch(chip.query)
                            },
                            enabled = !isSearching,
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (darkTheme) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            ),
                            contentPadding = ButtonDefaults.ContentPadding,
                        ) {
                            Text(
                                text = chip.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val query = draft.trim()
                        if (query.isNotEmpty()) {
                            openUrlError = null
                            onSearch(query)
                        }
                    },
                    enabled = !isSearching,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Leaf400, contentColor = Color.White),
                ) {
                    Text(if (isSearching) "Searching..." else "Search Knowledge Base")
                }

                if (!errorMessage.isNullOrBlank() || !openUrlError.isNullOrBlank()) {
                    Text(
                        text = openUrlError ?: errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (lastQuery.isNotBlank() && errorMessage.isNullOrBlank()) {
                    Text(
                        text = "${results.size} shown • $totalResults total • ${provider ?: "provider unknown"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    )
                }

                if (results.isEmpty() && !isSearching && errorMessage.isNullOrBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (lastQuery.isBlank()) {
                                "Search for practical farming knowledge."
                            } else {
                                "No results found. Try a more specific crop, disease, or location keyword."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(results) { result ->
                            KnowledgeResultCard(
                                result = result,
                                onOpenUrl = { rawUrl ->
                                    val safeUrl = normalizeKnowledgeUrl(rawUrl)
                                    if (safeUrl == null) {
                                        openUrlError = "This result does not contain a valid source URL."
                                    } else {
                                        runCatching {
                                            uriHandler.openUri(safeUrl)
                                        }.onFailure {
                                            openUrlError = "Unable to open source link right now."
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KnowledgeResultCard(
    result: KnowledgeBaseResult,
    onOpenUrl: (String) -> Unit,
) {
    val darkTheme = isAppDarkTheme()
    val clickableUrl = normalizeKnowledgeUrl(result.uri)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (darkTheme) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (darkTheme) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = clickableUrl != null) {
                clickableUrl?.let(onOpenUrl)
            },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            if (result.snippet.isNotBlank()) {
                Text(
                    text = result.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = result.sourceId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                )
                Text(
                    text = "Score ${(result.score * 100).toInt().coerceIn(0, 100)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
            }

            clickableUrl?.let { url ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = url,
                    style = MaterialTheme.typography.labelSmall,
                    color = Leaf400,
                    maxLines = 1,
                    modifier = Modifier.clickable { onOpenUrl(url) },
                )
            }
        }
    }
}

private fun normalizeKnowledgeUrl(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return null
    val lower = value.lowercase()
    return when {
        lower.startsWith("http://") || lower.startsWith("https://") -> value
        lower.startsWith("www.") -> "https://$value"
        lower.startsWith("mailto:") || lower.startsWith("tel:") -> value
        value.contains('.') && !value.contains(' ') -> "https://$value"
        else -> null
    }
}

private val ArrowBackIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ArrowBackKnowledge",
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
