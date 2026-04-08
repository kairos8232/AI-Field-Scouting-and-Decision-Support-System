package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.domain.model.FarmPoint
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader
import kotlin.math.abs

@Composable
fun FarmMapSetupScreen(
    boundaryPoints: List<FarmPoint>,
    onBoundaryChanged: (List<FarmPoint>) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val points = remember(boundaryPoints) { mutableStateListOf<FarmPoint>().apply { addAll(boundaryPoints) } }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedVertex by remember { mutableIntStateOf(-1) }
    var address by remember { mutableStateOf("Pendang, Kedah") }
    var warningMessage by remember { mutableStateOf<String?>(null) }

    AppScaffold(title = "Farm Setup", subtitle = "Draw farm boundary on map", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Farm map (2D boundary editor)",
                body = "Google Maps SDK layer can be attached next. For now, define your exact farm shape by adding and dragging boundary vertices.",
            )

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Search location...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        val seed = abs(address.lowercase().hashCode())
                        val shiftX = ((seed % 100) - 50) / 1000f
                        val shiftY = (((seed / 3) % 100) - 50) / 1000f
                        for (index in points.indices) {
                            val p = points[index]
                            points[index] = p.copy(
                                x = (p.x + shiftX).coerceIn(0.05f, 0.95f),
                                y = (p.y + shiftY).coerceIn(0.05f, 0.95f),
                            )
                        }
                        onBoundaryChanged(points.toList())
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Search")
                }

                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        points.clear()
                        points.addAll(
                            listOf(
                                FarmPoint(0.20f, 0.25f),
                                FarmPoint(0.82f, 0.22f),
                                FarmPoint(0.88f, 0.70f),
                                FarmPoint(0.26f, 0.78f),
                            )
                        )
                        onBoundaryChanged(points.toList())
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("My Location")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .onSizeChanged { mapSize = it }
                    .pointerInput(points.size, mapSize) {
                        detectTapGestures { tap ->
                            val normalized = toFarmPoint(tap, mapSize)
                            points.add(normalized)
                            onBoundaryChanged(points.toList())
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
                                }
                            },
                        )
                    },
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val gridStepX = size.width / 8f
                    val gridStepY = size.height / 8f

                    for (i in 1..7) {
                        drawLine(Color(0xFFBAC8C2), Offset(gridStepX * i, 0f), Offset(gridStepX * i, size.height), 1f)
                        drawLine(Color(0xFFBAC8C2), Offset(0f, gridStepY * i), Offset(size.width, gridStepY * i), 1f)
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

                        drawPath(path = path, color = Color(0x33558B2F))
                        drawPath(path = path, color = Color(0xFF2E7D32))
                    }

                    points.forEachIndexed { index, point ->
                        val marker = toOffset(point, size)
                        drawCircle(
                            color = if (index == selectedVertex) Color(0xFFD32F2F) else Color(0xFF1B5E20),
                            radius = 7f,
                            center = marker,
                        )
                    }
                }
            }

            Text(
                text = "Tap map to add edge points. Drag any point to reshape.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text = "Edges: ${points.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            if (points.isNotEmpty()) {
                                points.removeLast()
                                onBoundaryChanged(points.toList())
                            }
                        }
                    ) { Text("Undo") }
                    androidx.compose.material3.TextButton(
                        onClick = {
                            points.clear()
                            onBoundaryChanged(emptyList())
                        }
                    ) { Text("Clear All") }
                }
            }

            if (warningMessage != null) {
                Text(
                    text = warningMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            DualActionButtons(
                primaryLabel = "Next: Divide Into Lots",
                onPrimary = {
                    if (points.size < 3) {
                        warningMessage = "Add at least 3 boundary points first."
                    } else {
                        warningMessage = null
                        onBoundaryChanged(points.toList())
                        onContinue()
                    }
                },
            )
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
    return Offset(x = point.x * size.width, y = point.y * size.height)
}

private fun nearestVertexIndex(points: List<FarmPoint>, touch: Offset, size: IntSize): Int {
    if (points.isEmpty() || size.width <= 0 || size.height <= 0) return -1
    var nearest = -1
    var best = Float.MAX_VALUE
    points.forEachIndexed { index, point ->
        val dx = point.x * size.width - touch.x
        val dy = point.y * size.height - touch.y
        val dist = dx * dx + dy * dy
        if (dist < best) {
            best = dist
            nearest = index
        }
    }
    return if (best <= 28f * 28f) nearest else -1
}
