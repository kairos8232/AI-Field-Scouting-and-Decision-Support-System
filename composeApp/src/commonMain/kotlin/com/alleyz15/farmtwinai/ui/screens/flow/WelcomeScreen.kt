package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.ui.components.OnboardingAdaptiveWidth
import com.alleyz15.farmtwinai.ui.theme.CardDark
import com.alleyz15.farmtwinai.ui.theme.Forest500
import com.alleyz15.farmtwinai.ui.theme.Forest900
import com.alleyz15.farmtwinai.ui.theme.Mint200
import com.alleyz15.farmtwinai.ui.theme.Sand100
import farmtwinai.composeapp.generated.resources.Res
import farmtwinai.composeapp.generated.resources.ic_farm_bg_dark
import farmtwinai.composeapp.generated.resources.ic_farm_bg_light
import org.jetbrains.compose.resources.painterResource
import com.alleyz15.farmtwinai.ui.theme.isAppDarkTheme

@Composable
fun WelcomeScreen(
    onLogin: () -> Unit,
    onSignUp: () -> Unit,
) {
    val darkTheme = isAppDarkTheme()
    val bgResource = if (darkTheme) Res.drawable.ic_farm_bg_dark else Res.drawable.ic_farm_bg_light
    val rootBg = if (darkTheme) CardDark else MaterialTheme.colorScheme.background
    val overlayTop = if (darkTheme) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.12f)
    val overlayMid = if (darkTheme) MaterialTheme.colorScheme.surface.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    val overlayBottom = if (darkTheme) CardDark else MaterialTheme.colorScheme.background
    
    Box(modifier = Modifier.fillMaxSize().background(rootBg)) {
        Image(
            painter = painterResource(bgResource),
            contentDescription = "Farm Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            overlayTop,
                            Color.Transparent,
                            overlayMid,
                            overlayBottom
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        Scaffold(containerColor = Color.Transparent) { padding ->
            OnboardingAdaptiveWidth(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) { maxContentWidth, _ ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    AppBrandBlock()
                    Spacer(modifier = Modifier.height(64.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = maxContentWidth),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Button(
                            onClick = onLogin,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) {
                            Text("Login", style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedButton(
                            onClick = onSignUp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) {
                            Text("Sign Up", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
    }
}

@Composable
private fun AppBrandBlock() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = FarmLogoIcon,
                contentDescription = "FarmTwin logo",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(36.dp),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "FarmTwin",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Your Farm, Reimagined by AI",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
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
