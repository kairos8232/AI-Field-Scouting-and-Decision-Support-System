package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.auth.AuthUser
import com.alleyz15.farmtwinai.domain.model.ChatMessage
import com.alleyz15.farmtwinai.domain.model.MessageSender
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader

@Composable
fun AiChatScreen(
    messages: List<ChatMessage>,
    onBack: () -> Unit,
    onConfirmAction: () -> Unit,
    authenticatedUser: AuthUser?,
) {
    val draft = remember { mutableStateOf("") }

    AppScaffold(title = "AI Consultation", subtitle = "Mocked advisory chat", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Ask why actual conditions differ",
                body = "This Phase 1 screen demonstrates the consultation pattern. The chat is static for now, but the layout is ready for real model integration later.",
            )
            Text(
                text = if (authenticatedUser != null) {
                    "Signed in as ${authenticatedUser.email}. Ready to send authenticated requests to Gemini backend."
                } else {
                    "Not signed in. Use Account Access screen to enable authenticated Gemini requests."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            messages.forEach { message ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.sender == MessageSender.USER) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            if (message.sender == MessageSender.USER) "Farmer" else "FarmTwin AI",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(message.content, style = MaterialTheme.typography.bodyLarge)
                        Text(message.timestamp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedTextField(
                value = draft.value,
                onValueChange = { draft.value = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Type your question or follow-up action...") },
            )
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Send")
            }
            Button(onClick = onConfirmAction, modifier = Modifier.fillMaxWidth()) {
                Text("Confirm Recommended Action")
            }
        }
    }
}
