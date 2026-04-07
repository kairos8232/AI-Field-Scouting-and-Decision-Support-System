package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.ui.theme.Forest700
import com.alleyz15.farmtwinai.ui.theme.Mint200

@Composable
fun WelcomeScreen(
    onLogin: () -> Unit,
    onSignUp: () -> Unit,
    onTryDemo: () -> Unit,
) {
    androidx.compose.material3.Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
        ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Forest700, RoundedCornerShape(28.dp))
                    .padding(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("FarmTwin AI", style = MaterialTheme.typography.headlineLarge, color = Mint200)
                    Text(
                        "A digital twin command center for smallholder farmers to compare expected crop progress against real field conditions.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.surface,
                    )
                    Text(
                        "Phase 1 demo focuses on setup, dashboard, timeline, map, and AI consultation flow using mock data.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.surface,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Login") }
                OutlinedButton(onClick = onSignUp, modifier = Modifier.fillMaxWidth()) { Text("Sign Up") }
                OutlinedButton(onClick = onTryDemo, modifier = Modifier.fillMaxWidth()) { Text("Try Demo") }
            }
        }
    }
    }
}
