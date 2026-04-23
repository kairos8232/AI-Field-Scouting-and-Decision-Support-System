package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.auth.AuthUser
import com.alleyz15.farmtwinai.domain.model.ChatMessage
import com.alleyz15.farmtwinai.domain.model.MessageSender
import com.alleyz15.farmtwinai.ui.components.AuroraBackground
import com.alleyz15.farmtwinai.ui.components.OnboardingAdaptiveWidth
import com.alleyz15.farmtwinai.ui.theme.Leaf400
import com.alleyz15.farmtwinai.ui.theme.isAppDarkTheme

@Composable
fun AiChatScreen(
    messages: List<ChatMessage>,
    isSending: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenKnowledgeBase: () -> Unit,
    authenticatedUser: AuthUser?,
) {
    val darkTheme = isAppDarkTheme()
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

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
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), CircleShape),
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
                            text = "AI Consultation",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = if (authenticatedUser != null) {
                                "Signed in as ${authenticatedUser.email}"
                            } else {
                                "Tap Account to sign in for personalized advice."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.88f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onOpenKnowledgeBase,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (darkTheme) 0.3f else 0.8f), CircleShape),
                        ) {
                            Icon(
                                imageVector = KnowledgeIcon,
                                contentDescription = "Knowledge Base",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        IconButton(
                            onClick = onOpenHistory,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (darkTheme) 0.3f else 0.8f), CircleShape),
                        ) {
                            Icon(
                                imageVector = HistoryIcon,
                                contentDescription = "History",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (messages.isEmpty() && !isSending) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Start a conversation to get insights.",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(messages) { message ->
                                ChatMessageRow(message = message)
                            }
                            if (isSending) {
                                item {
                                    TypingIndicatorRow()
                                }
                            }
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = Color.White,
                        tonalElevation = 0.dp,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.35.dp, Leaf400.copy(alpha = 0.9f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (draft.isBlank()) {
                                    Text(
                                        text = "Ask follow-up question...",
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                BasicTextField(
                                    value = draft,
                                    onValueChange = { draft = it },
                                    enabled = !isSending,
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onBackground
                                    ),
                                    cursorBrush = SolidColor(Leaf400),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = Leaf400,
                                shadowElevation = 3.dp,
                            ) {
                                IconButton(
                                    onClick = {
                                        if (isSending) return@IconButton
                                        val prompt = draft.trim()
                                        if (prompt.isNotEmpty()) {
                                            onSend(prompt)
                                            draft = ""
                                        }
                                    },
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        imageVector = SendUpIcon,
                                        contentDescription = "Send",
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageRow(message: ChatMessage) {
    val darkTheme = isAppDarkTheme()
    val isUser = message.sender == MessageSender.USER
    // Increased contrast: opaque Leaf400 for user, surfaceVariant for AI
    val containerColor = if (isUser) Leaf400 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (darkTheme) 0.45f else 0.85f)
    val labelColor = if (isUser) Color.White.copy(alpha = 0.95f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
    val contentColor = if (isUser) Color.White else MaterialTheme.colorScheme.onBackground

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 8.dp,
                bottomEnd = if (isUser) 8.dp else 20.dp
            ),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = if (isUser) null else BorderStroke(1.dp, if (darkTheme) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 4.dp else 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (isUser) "You" else "FarmTwin AI",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = labelColor,
                )
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                )
                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.72f),
                    modifier = Modifier.align(Alignment.End)
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

private val HistoryIcon: ImageVector
    get() = ImageVector.Builder(
        name = "History",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(13f, 3f)
            curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
            lineTo(1f, 12f)
            lineToRelative(3.89f, 3.89f)
            lineToRelative(0.07f, 0.14f)
            lineTo(9f, 12f)
            lineTo(6f, 12f)
            curveToRelative(0f, -3.87f, 3.13f, -7f, 7f, -7f)
            curveToRelative(3.87f, 0f, 7f, 3.13f, 7f, 7f)
            curveToRelative(0f, 3.87f, -3.13f, 7f, -7f, 7f)
            curveToRelative(-1.93f, 0f, -3.68f, -0.79f, -4.94f, -2.06f)
            lineToRelative(-1.42f, 1.42f)
            curveTo(8.27f, 19.99f, 10.51f, 21f, 13f, 21f)
            curveToRelative(4.97f, 0f, 9f, -4.03f, 9f, -9f)
            curveToRelative(0f, -4.97f, -4.03f, -9f, -9f, -9f)
            close()
            moveTo(12f, 8f)
            verticalLineToRelative(5f)
            lineToRelative(4.28f, 2.54f)
            lineToRelative(0.72f, -1.21f)
            lineToRelative(-3.5f, -2.08f)
            lineTo(13.5f, 8f)
            lineTo(12f, 8f)
            close()
        }
    }.build()

private val KnowledgeIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Knowledge",
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

private val SendUpIcon: ImageVector
    get() = ImageVector.Builder(
        name = "SendUp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 4f)
            lineToRelative(-6f, 6f)
            lineToRelative(1.41f, 1.41f)
            lineTo(11f, 7.83f)
            lineTo(11f, 20f)
            lineToRelative(2f, 0f)
            lineTo(13f, 7.83f)
            lineToRelative(3.59f, 3.58f)
            lineTo(18f, 10f)
            close()
        }
    }.build()


@Composable
private fun TypingIndicatorRow() {
    val darkTheme = isAppDarkTheme()
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (darkTheme) 0.45f else 0.85f)
    
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .align(Alignment.CenterStart),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 8.dp,
                bottomEnd = 20.dp
            ),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(1.dp, if (darkTheme) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "FarmTwin AI",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Gemini is thinking...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}
