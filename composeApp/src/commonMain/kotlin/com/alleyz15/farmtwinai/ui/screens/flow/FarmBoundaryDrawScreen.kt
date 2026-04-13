package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.domain.model.FarmPoint
import com.alleyz15.farmtwinai.ui.components.AuroraBackground
import com.alleyz15.farmtwinai.ui.components.OnboardingAdaptiveWidth
import com.alleyz15.farmtwinai.ui.components.PlatformGoogleMap
import com.alleyz15.farmtwinai.ui.theme.Leaf400
import com.alleyz15.farmtwinai.ui.theme.Mint200
import com.alleyz15.farmtwinai.ui.theme.Sand100
import kotlin.math.pow
import kotlin.math.sqrt

@Composable
fun FarmBoundaryDrawScreen(
    boundaryPoints: List<FarmPoint>,
    locationQuery: String,
    searchTrigger: Int,
    useCurrentLocationTrigger: Int,
    onBoundaryChanged: (List<FarmPoint>) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val points = remember(boundaryPoints) {
        mutableStateListOf<FarmPoint>().apply { addAll(normalizeBoundaryToRectangle(boundaryPoints)) }
    }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedVertex by remember { mutableIntStateOf(-1) }
    var warningMessage by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()

        OnboardingAdaptiveWidth { maxContentWidth, _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 18.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = maxContentWidth),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                    ) {
                        Icon(
                            imageVector = BackIconDraw,
                            contentDescription = "Back",
                            tint = Sand100,
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "Farm Setup",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Sand100,
                        )
                        Text(
                            text = "Step 2 of 3 - Draw boundary",
                            style = MaterialTheme.typography.bodySmall,
                            color = Sand100.copy(alpha = 0.7f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Draw boundary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Sand100,
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                )
                Text(
                    text = "Tap to add points and drag to refine.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Sand100.copy(alpha = 0.78f),
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth).padding(top = 4.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = maxContentWidth)
                        .height(320.dp)
                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .onSizeChanged { mapSize = it },
                ) {
                    PlatformGoogleMap(
                        modifier = Modifier.matchParentSize(),
                        locationQuery = locationQuery,
                        searchTrigger = searchTrigger,
                        allowMapInteraction = false,
                        useCurrentLocationTrigger = useCurrentLocationTrigger,
                    )

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(points.size, mapSize) {
                                detectTapGestures { tap ->
                                    val normalized = toFarmPoint(tap, mapSize)
                                    points.add(normalized)
                                    val rectified = normalizeBoundaryToRectangle(points.toList())
                                    points.clear()
                                    points.addAll(rectified)
                                    onBoundaryChanged(rectified)
                                    warningMessage = null
                                }
                            }
                            .pointerInput(points.size, mapSize) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        selectedVertex = nearestVertexIndex(points, start, mapSize)
                                    },
                                    onDragEnd = {
                                        selectedVertex = -1
                                        onBoundaryChanged(points.toList())
                                    },
                                    onDragCancel = { selectedVertex = -1 },
                                    onDrag = { change, _ ->
                                        val index = selectedVertex
                                        if (index in points.indices) {
                                            points[index] = toFarmPoint(change.position, mapSize)
                                            val rectified = normalizeBoundaryToRectangle(points.toList())
                                            points.clear()
                                            points.addAll(rectified)
                                        }
                                    },
                                )
                            },
                    ) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val gridStepX = size.width / 8f
                            val gridStepY = size.height / 8f

                            for (i in 1..7) {
                                drawLine(Color.White.copy(alpha = 0.1f), Offset(gridStepX * i, 0f), Offset(gridStepX * i, size.height), 1f)
                                drawLine(Color.White.copy(alpha = 0.1f), Offset(0f, gridStepY * i), Offset(size.width, gridStepY * i), 1f)
                            }

                            if (points.isNotEmpty()) {
                                val path = Path().apply {
                                    val first = toOffset(points.first(), size)
                                    moveTo(first.x, first.y)
                                    for (i in 1 until points.size) {
                                        val p = toOffset(points[i], size)
                                        lineTo(p.x, p.y)
                                    }
                                    if (points.size > 2) close()
                                }

                                drawPath(path = path, color = Leaf400.copy(alpha = 0.4f))
                                drawPath(path = path, color = Leaf400, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                            }

                            points.forEachIndexed { index, point ->
                                val marker = toOffset(point, size)
                                drawCircle(
                                    color = if (index == selectedVertex) Color(0xFFE57373) else Sand100,
                                    radius = 12f,
                                    center = marker,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edges: ${points.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Mint200,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                if (points.isNotEmpty()) {
                                    points.removeLast()
                                    onBoundaryChanged(points.toList())
                                }
                            }
                        ) {
                            Text("Undo", color = Sand100)
                        }
                        TextButton(
                            onClick = {
                                points.clear()
                                onBoundaryChanged(emptyList())
                            }
                        ) {
                            Text("Clear All", color = Sand100)
                        }
                    }
                }

                if (warningMessage != null) {
                    Text(
                        text = warningMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE57373),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (points.size < 4) {
                            warningMessage = "Add at least 2 taps to form a rectangular boundary."
                        } else {
                            warningMessage = null
                            onBoundaryChanged(points.toList())
                            onContinue()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Leaf400, contentColor = Color.White),
                ) {
                    Text("Next", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun toFarmPoint(offset: Offset, size: IntSize): FarmPoint {
    if (size.width <= 0 || size.height <= 0) return FarmPoint(0.5f, 0.5f)
    return FarmPoint(
        x = (offset.x / size.width).coerceIn(0f, 1f),
        y = (offset.y / size.height).coerceIn(0f, 1f),
    )
}

private fun toOffset(point: FarmPoint, size: androidx.compose.ui.geometry.Size): Offset {
    return Offset(point.x * size.width, point.y * size.height)
}

private fun nearestVertexIndex(points: List<FarmPoint>, tap: Offset, size: IntSize): Int {
    if (points.isEmpty()) return -1
    var best = -1
    var bestDist = Float.MAX_VALUE
    for (i in points.indices) {
        val p = toOffset(points[i], androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()))
        val dist = sqrt((p.x - tap.x).pow(2) + (p.y - tap.y).pow(2))
        if (dist < bestDist && dist < 80f) {
            bestDist = dist
            best = i
        }
    }
    return best
}

private fun normalizeBoundaryToRectangle(points: List<FarmPoint>): List<FarmPoint> {
    if (points.isEmpty()) return emptyList()
    if (points.size == 1) {
        val p = points.first()
        return listOf(FarmPoint(p.x.coerceIn(0f, 1f), p.y.coerceIn(0f, 1f)))
    }

    val minX = points.minOf { it.x.toDouble() }.toFloat().coerceIn(0f, 1f)
    val maxX = points.maxOf { it.x.toDouble() }.toFloat().coerceIn(0f, 1f)
    val minY = points.minOf { it.y.toDouble() }.toFloat().coerceIn(0f, 1f)
    val maxY = points.maxOf { it.y.toDouble() }.toFloat().coerceIn(0f, 1f)

    return listOf(
        FarmPoint(minX, minY),
        FarmPoint(maxX, minY),
        FarmPoint(maxX, maxY),
        FarmPoint(minX, maxY),
    )
}

private val BackIconDraw: ImageVector
    get() = ImageVector.Builder(
        name = "BackIconDraw",
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
