package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.domain.model.ChatMessage
import com.alleyz15.farmtwinai.domain.model.MessageSender
import com.alleyz15.farmtwinai.ui.components.AuroraBackground
import com.alleyz15.farmtwinai.ui.components.OnboardingAdaptiveWidth
import com.alleyz15.farmtwinai.ui.theme.Leaf400
import com.alleyz15.farmtwinai.ui.theme.Mint200
import com.alleyz15.farmtwinai.ui.theme.Sand100

@Composable
fun AiConversationScreen(
    messages: List<ChatMessage>,
    isSending: Boolean,
    errorMessage: String?,
    providerLabel: String?,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
) {
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
                        modifier = Modifier.background(Color.White.copy(alpha = 0.08f), CircleShape),
                    ) {
                        Icon(
                            imageVector = ArrowBackIcon,
                            contentDescription = "Back",
                            tint = Sand100,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Gemini Chat",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Sand100,
                        )
                        Text(
                            text = providerLabel ?: "Connected to backend AI assistant",
                            style = MaterialTheme.typography.bodySmall,
                            color = Sand100.copy(alpha = 0.76f),
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                ) {
                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Start by sending a farming question. Gemini will answer in this conversation.",
                                color = Sand100.copy(alpha = 0.82f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(messages, key = { it.id }) { message ->
                                LiveChatMessageRow(message = message)
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFFB4AB),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    text = "Ask Gemini about crop issues, timeline, or next actions...",
                                    color = Sand100.copy(alpha = 0.58f),
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            minLines = 2,
                            enabled = !isSending,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Sand100,
                                unfocusedTextColor = Sand100,
                                focusedBorderColor = Leaf400,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                focusedContainerColor = Color.White.copy(alpha = 0.03f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                                cursorColor = Leaf400,
                            ),
                        )

                        Button(
                            onClick = {
                                val prompt = draft.trim()
                                if (prompt.isNotEmpty()) {
                                    onSend(prompt)
                                    draft = ""
                                }
                            },
                            enabled = !isSending,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Leaf400, contentColor = Color.White),
                        ) {
                            if (isSending) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = Color.White,
                                        modifier = Modifier.height(18.dp),
                                    )
                                    Text("Gemini is replying...")
                                }
                            } else {
                                Text("Send")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun LiveChatMessageRow(message: ChatMessage) {
    val isUser = message.sender == MessageSender.USER
    val containerColor = if (isUser) Leaf400.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f)
    val labelColor = if (isUser) Mint200 else Sand100

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = if (isUser) {
                BorderStroke(1.dp, Leaf400.copy(alpha = 0.5f))
            } else {
                BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
            },
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (isUser) "You" else "Gemini",
                    style = MaterialTheme.typography.labelLarge,
                    color = labelColor,
                )
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Sand100,
                )
                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = Sand100.copy(alpha = 0.72f),
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
