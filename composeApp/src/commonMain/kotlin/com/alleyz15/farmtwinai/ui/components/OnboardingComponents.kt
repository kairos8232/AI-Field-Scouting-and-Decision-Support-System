package com.alleyz15.farmtwinai.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.ui.theme.isAppDarkTheme
import com.alleyz15.farmtwinai.ui.theme.Leaf400
import com.alleyz15.farmtwinai.ui.theme.Mint200
import com.alleyz15.farmtwinai.ui.theme.WaterBlue
import kotlinx.coroutines.delay

@Composable
fun AuroraBackground() {
    val darkTheme = isAppDarkTheme()
    val baseColor = if (darkTheme) Color(0xFF0C1911) else Color(0xFFF3F8F3)
    val topGlowAlpha = if (darkTheme) 0.15f else 0.28f
    val bottomGlowAlpha = if (darkTheme) 0.12f else 0.22f

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(baseColor)
    ) {
        val width = size.width
        val height = size.height

        // Top right mint glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Mint200.copy(alpha = topGlowAlpha), Color.Transparent),
                center = Offset(width * 1.1f, height * -0.1f),
                radius = width * 0.9f
            ),
            center = Offset(width * 1.1f, height * -0.1f),
            radius = width * 0.9f
        )

        // Bottom left leaf glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Leaf400.copy(alpha = bottomGlowAlpha), Color.Transparent),
                center = Offset(width * -0.2f, height * 1.1f),
                radius = width * 1.0f
            ),
            center = Offset(width * -0.2f, height * 1.1f),
            radius = width * 1.0f
        )
    }
}

@Composable
fun OnboardingBackground(
    modifier: Modifier = Modifier,
    overlayAlpha: Float = 0.48f,
) {
    val backgroundThemes = remember {
        listOf(
            listOf(Color(0xFF567D46), Color(0xFF163B2A), Color(0xFF0B1F17)),
            listOf(Color(0xFFB88A54), Color(0xFF3D5C3E), Color(0xFF13251C)),
            listOf(Color(0xFF40798C), Color(0xFF29524A), Color(0xFF12211D)),
            listOf(Color(0xFF6C8F5A), Color(0xFF2E5A35), Color(0xFF0F2017)),
            listOf(Color(0xFF8E6A4E), Color(0xFF345A45), Color(0xFF101E17)),
        )
    }
    var currentBackground by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            currentBackground = (currentBackground + 1) % backgroundThemes.size
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Crossfade(
            targetState = currentBackground,
            animationSpec = tween(durationMillis = 900),
        ) { index ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(colors = backgroundThemes[index])),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 88.dp, start = 24.dp)
                        .size(220.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Mint200.copy(alpha = 0.24f), Color.Transparent),
                            )
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                        .size(260.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(WaterBlue.copy(alpha = 0.18f), Color.Transparent),
                            )
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, bottom = 92.dp)
                        .size(240.dp)
                        .clip(RoundedCornerShape(42.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Leaf400.copy(alpha = 0.16f), Color.Transparent),
                            )
                        ),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = overlayAlpha)),
        )
    }
}

@Composable
fun OnboardingAdaptiveWidth(
    modifier: Modifier = Modifier,
    phoneMaxWidth: Dp = 420.dp,
    tabletMaxWidth: Dp = 560.dp,
    desktopMaxWidth: Dp = 680.dp,
    content: @Composable (maxContentWidth: Dp, isCompact: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompact = maxWidth < 700.dp
        val targetWidth = when {
            maxWidth >= 1200.dp -> desktopMaxWidth
            maxWidth >= 700.dp -> tabletMaxWidth
            else -> phoneMaxWidth
        }.coerceAtMost(maxWidth)

        content(targetWidth, isCompact)
    }
}
