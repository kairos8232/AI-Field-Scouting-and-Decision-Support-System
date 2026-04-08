package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.ui.components.OnboardingAdaptiveWidth
import com.alleyz15.farmtwinai.ui.components.OnboardingBackground
import com.alleyz15.farmtwinai.ui.theme.Forest500
import com.alleyz15.farmtwinai.ui.theme.Forest900
import com.alleyz15.farmtwinai.ui.theme.Mint200
import com.alleyz15.farmtwinai.ui.theme.Sand100

@Composable
fun WelcomeScreen(
    onLogin: () -> Unit,
    onSignUp: () -> Unit,
) {
    Scaffold(containerColor = Color.Black) { padding ->
        OnboardingAdaptiveWidth(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { maxContentWidth, _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                OnboardingBackground(overlayAlpha = 0.46f)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(64.dp))
                    AppBrandBlock()
                    Spacer(modifier = Modifier.weight(1f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = maxContentWidth),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = onLogin,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                        ) {
                            Text("Login")
                        }
                        OutlinedButton(
                            onClick = onSignUp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                        ) {
                            Text("Sign Up")
                        }
                    }
                    Spacer(modifier = Modifier.height(96.dp))
                }
            }
        }
    }
}

@Composable
private fun AppBrandBlock() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = FarmLogoIcon,
                contentDescription = "FarmTwin logo",
                tint = Sand100,
                modifier = Modifier.size(42.dp),
            )
        }

        Text(
            text = "FarmTwin",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = Sand100,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Your Farm, Reimagined by AI",
            style = MaterialTheme.typography.bodyLarge,
            color = Sand100.copy(alpha = 0.92f),
            textAlign = TextAlign.Center,
        )
    }
}

private val FarmLogoIcon: ImageVector
    get() = ImageVector.Builder(
        name = "FarmLogo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Forest500)) {
            moveTo(12f, 3f)
            curveTo(8.7f, 3f, 6f, 5.7f, 6f, 9f)
            curveTo(6f, 12.8f, 8.9f, 16.2f, 12f, 20f)
            curveTo(15.1f, 16.2f, 18f, 12.8f, 18f, 9f)
            curveTo(18f, 5.7f, 15.3f, 3f, 12f, 3f)
            close()
        }
        path(fill = SolidColor(Forest900)) {
            moveTo(12f, 6f)
            curveTo(10.2f, 6f, 8.8f, 7.6f, 8.8f, 9.5f)
            curveTo(8.8f, 11.7f, 10.5f, 13.9f, 12f, 15.9f)
            curveTo(13.5f, 13.9f, 15.2f, 11.7f, 15.2f, 9.5f)
            curveTo(15.2f, 7.6f, 13.8f, 6f, 12f, 6f)
            close()
        }
        path(fill = SolidColor(Mint200)) {
            moveTo(11.3f, 8.1f)
            curveTo(9.8f, 8.5f, 9.1f, 9.8f, 9.1f, 11.2f)
            curveTo(10.6f, 11f, 11.6f, 10.4f, 12.2f, 9.1f)
            curveTo(12.7f, 8.1f, 13.6f, 7.4f, 14.8f, 7f)
            curveTo(14.3f, 8.6f, 13.2f, 9.7f, 11.6f, 10.2f)
            curveTo(12.7f, 10.7f, 13.8f, 11.6f, 14.4f, 13.1f)
            curveTo(12.8f, 12.6f, 11.7f, 11.7f, 11.1f, 10.2f)
            curveTo(10.6f, 9.2f, 10f, 8.5f, 8.9f, 8.2f)
            curveTo(9.7f, 7.8f, 10.4f, 7.8f, 11.3f, 8.1f)
            close()
        }
    }.build()
