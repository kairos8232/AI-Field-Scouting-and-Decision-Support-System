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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.domain.model.FarmPoint
import com.alleyz15.farmtwinai.ui.components.AuroraBackground
import com.alleyz15.farmtwinai.ui.components.OnboardingAdaptiveWidth
import com.alleyz15.farmtwinai.ui.theme.Leaf400
import com.alleyz15.farmtwinai.ui.theme.Mint200
import com.alleyz15.farmtwinai.ui.theme.Sand100
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

@Composable
fun LotSectionSetupScreen(
    boundaryPoints: List<FarmPoint>,
    totalAreaHa: String,
    lots: List<List<FarmPoint>>,
    lotCropTypes: Map<Int, String>,
    onTotalAreaChange: (String) -> Unit,
    onLotsChange: (List<List<FarmPoint>>) -> Unit,
    onLotCropTypeChange: (Int, String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    var selectedLotIndex by remember { mutableIntStateOf(if (lots.isNotEmpty()) 0 else -1) }
    var draggingVertexIndex by remember { mutableIntStateOf(-1) }

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
                        Icon(BackIconDraw2, contentDescription = "Back", tint = Sand100)
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
                            text = "Step 3 of 3 - Subdivide farm",
                            style = MaterialTheme.typography.bodySmall,
                            color = Sand100.copy(alpha = 0.7f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Map & Controls Row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = maxContentWidth)
                        .height(260.dp)
                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (boundaryPoints.isEmpty()) {
                        Text("No boundary defined", color = Sand100)
                    } else {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(lots, selectedLotIndex) {
                                    detectTapGestures { tap ->
                                        val point = keepPointInsideBoundary(toFarmPointDraw(tap, size), boundaryPoints)

                                        if (selectedLotIndex in lots.indices) {
                                            val updatedLots = lots.toMutableList()
                                            val current = updatedLots[selectedLotIndex].toMutableList()
                                            current.add(point)
                                            updatedLots[selectedLotIndex] = current
                                            onLotsChange(updatedLots)
                                        } else {
                                            onLotsChange(listOf(listOf(point)))
                                            selectedLotIndex = 0
                                        }
                                    }
                                }
                                .pointerInput(lots) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            if (selectedLotIndex in lots.indices) {
                                                val lotPoints = lots[selectedLotIndex]
                                                draggingVertexIndex = nearestVertexDraw(lotPoints, start, size)
                                            }
                                        },
                                        onDragEnd = { draggingVertexIndex = -1 },
                                        onDragCancel = { draggingVertexIndex = -1 },
                                        onDrag = { change, dragAmount ->
                                            if (selectedLotIndex in lots.indices && draggingVertexIndex != -1) {
                                                val currentLots = lots.toMutableList()
                                                val currentLotPoints = currentLots[selectedLotIndex].toMutableList()
                                                val p = currentLotPoints[draggingVertexIndex]
                                                val nx = (p.x + dragAmount.x / size.width).coerceIn(0f, 1f)
                                                val ny = (p.y + dragAmount.y / size.height).coerceIn(0f, 1f)
                                                currentLotPoints[draggingVertexIndex] = FarmPoint(nx, ny)
                                                currentLots[selectedLotIndex] = currentLotPoints
                                                onLotsChange(currentLots)
                                            }
                                        }
                                    )
                                }
                        ) {
                            val boundaryPath = Path().apply {
                                val first = toOffsetDraw(boundaryPoints.first(), size)
                                moveTo(first.x, first.y)
                                for (i in 1 until boundaryPoints.size) {
                                    val p = toOffsetDraw(boundaryPoints[i], size)
                                    lineTo(p.x, p.y)
                                }
                                close()
                            }
                            drawPath(boundaryPath, color = Leaf400.copy(alpha = 0.2f))
                            drawPath(boundaryPath, color = Leaf400, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))

                            lots.forEachIndexed { index, lotPoints ->
                                if (lotPoints.size >= 3) {
                                    val lotPath = Path().apply {
                                        val first = toOffsetDraw(lotPoints.first(), size)
                                        moveTo(first.x, first.y)
                                        for (i in 1 until lotPoints.size) {
                                            val p = toOffsetDraw(lotPoints[i], size)
                                            lineTo(p.x, p.y)
                                        }
                                        close()
                                    }
                                    val isSelected = index == selectedLotIndex
                                    drawPath(lotPath, color = if (isSelected) Mint200.copy(alpha = 0.5f) else Mint200.copy(alpha = 0.2f))
                                    drawPath(lotPath, color = if (isSelected) Mint200 else Mint200.copy(alpha = 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))

                                    if (isSelected) {
                                        lotPoints.forEach { pt ->
                                            drawCircle(Sand100, radius = 10f, center = toOffsetDraw(pt, size))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${lots.size} lot(s)",
                        style = MaterialTheme.typography.labelLarge,
                        color = Sand100.copy(alpha = 0.7f),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                if (selectedLotIndex in lots.indices) {
                                    val updatedLots = lots.toMutableList()
                                    val current = updatedLots[selectedLotIndex].toMutableList()
                                    if (current.isNotEmpty()) {
                                        current.removeLast()
                                        if (current.isEmpty()) {
                                            updatedLots.removeAt(selectedLotIndex)
                                            selectedLotIndex = if (updatedLots.isNotEmpty()) 0 else -1
                                        } else {
                                            updatedLots[selectedLotIndex] = current
                                        }
                                        onLotsChange(updatedLots)
                                    }
                                }
                            }
                        ) {
                            Text("Undo", color = Sand100)
                        }

                        TextButton(
                            onClick = {
                                if (lots.isEmpty()) {
                                    onLotsChange(listOf(boundaryPoints))
                                    selectedLotIndex = 0
                                } else {
                                    onLotsChange(emptyList())
                                    selectedLotIndex = -1
                                }
                            }
                        ) {
                            Text(if (lots.isEmpty()) "Full Lot" else "Clear Lot", color = Sand100)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Templates List
                val templates = listOf("Vertical 2", "Horizontal 2", "Vertical 3", "Grid 4")
                LazyRow(
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(templates) { template ->
                        Button(
                            onClick = {
                                val generated = generateMockLots(boundaryPoints, template)
                                onLotsChange(generated)
                                selectedLotIndex = 0
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Sand100),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(template, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                val totalFarmAreaHaValue = parseAreaInputToHectaresDraw(totalAreaHa)
                val boundaryArea = polygonAreaDraw(boundaryPoints)
                val selectedLotArea = if (selectedLotIndex in lots.indices) {
                    polygonAreaDraw(lots[selectedLotIndex])
                } else {
                    null
                }
                val selectedLotAreaHa = if (
                    totalFarmAreaHaValue != null &&
                    boundaryArea > 0.0 &&
                    selectedLotArea != null
                ) {
                    totalFarmAreaHaValue * (selectedLotArea / boundaryArea)
                } else {
                    null
                }
                val selectedLotPercentage = if (boundaryArea > 0.0 && selectedLotArea != null) {
                    (selectedLotArea / boundaryArea) * 100.0
                } else {
                    null
                }
                
                // Selected lot area display card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = maxContentWidth),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.08f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Selected Lot Area",
                            style = MaterialTheme.typography.labelSmall,
                            color = Sand100.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                selectedLotAreaHa != null -> "${formatFixedDraw(selectedLotAreaHa, 3)} ha"
                                totalFarmAreaHaValue == null -> "Set total farm area in previous step"
                                else -> "Draw/select a lot"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Mint200
                        )
                        if (selectedLotPercentage != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${formatFixedDraw(selectedLotPercentage, 1)}% of farm",
                                style = MaterialTheme.typography.bodySmall,
                                color = Sand100.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                if (lots.isNotEmpty() && selectedLotIndex in lots.indices) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Lot ${selectedLotIndex + 1} details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Sand100,
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Sand100,
                        unfocusedTextColor = Sand100,
                        focusedLabelColor = Mint200,
                        unfocusedLabelColor = Sand100.copy(alpha = 0.74f),
                        focusedPlaceholderColor = Sand100.copy(alpha = 0.52f),
                        unfocusedPlaceholderColor = Sand100.copy(alpha = 0.42f),
                        cursorColor = Leaf400,
                        focusedBorderColor = Leaf400,
                        unfocusedBorderColor = Sand100.copy(alpha = 0.32f),
                    )

                    val cropVal = lotCropTypes[selectedLotIndex] ?: ""
                    OutlinedTextField(
                        value = cropVal,
                        onValueChange = { onLotCropTypeChange(selectedLotIndex, it) },
                        label = { Text("Crop Type") },
                        placeholder = { Text("e.g. Tomato, Corn") },
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                        singleLine = true,
                        colors = fieldColors
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Soil/Water data will be auto-filled by AI in next step.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Sand100.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth).padding(start = 4.dp),
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(lots.indices.toList()) { idx ->
                            val isSel = idx == selectedLotIndex
                            Button(
                                onClick = { selectedLotIndex = idx },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSel) Mint200.copy(alpha = 0.15f) else Color.Transparent,
                                    contentColor = if (isSel) Mint200 else Sand100.copy(alpha = 0.7f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Lot ${idx + 1}")
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Select or draw a lot to configure crop details.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sand100.copy(alpha = 0.6f),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onContinue,
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

private fun toOffsetDraw(point: FarmPoint, size: androidx.compose.ui.geometry.Size): Offset {
    return Offset(point.x * size.width, point.y * size.height)
}

private fun toFarmPointDraw(offset: Offset, size: IntSize): FarmPoint {
    if (size.width <= 0 || size.height <= 0) return FarmPoint(0.5f, 0.5f)
    return FarmPoint(
        x = (offset.x / size.width).coerceIn(0f, 1f),
        y = (offset.y / size.height).coerceIn(0f, 1f),
    )
}

private fun nearestVertexDraw(points: List<FarmPoint>, tap: Offset, size: IntSize): Int {
    if (points.isEmpty()) return -1
    var best = -1
    var bestDist = Float.MAX_VALUE
    val drawSize = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())
    for (i in points.indices) {
        val p = toOffsetDraw(points[i], drawSize)
        val dist = sqrt((p.x - tap.x).pow(2) + (p.y - tap.y).pow(2))
        if (dist < bestDist && dist < 60f) {
            bestDist = dist
            best = i
        }
    }
    return best
}

private fun generateMockLots(boundary: List<FarmPoint>, template: String): List<List<FarmPoint>> {
    if (boundary.size < 3) return emptyList()
    var minX = 1f; var minY = 1f; var maxX = 0f; var maxY = 0f
    for (p in boundary) {
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y
        if (p.y > maxY) maxY = p.y
    }
    val cx = (minX + maxX) / 2f
    val cy = (minY + maxY) / 2f

    val rawLots = when (template) {
        "Vertical 2" -> listOf(
            listOf(FarmPoint(minX, minY), FarmPoint(cx, minY), FarmPoint(cx, maxY), FarmPoint(minX, maxY)),
            listOf(FarmPoint(cx, minY), FarmPoint(maxX, minY), FarmPoint(maxX, maxY), FarmPoint(cx, maxY))
        )
        "Horizontal 2" -> listOf(
            listOf(FarmPoint(minX, minY), FarmPoint(maxX, minY), FarmPoint(maxX, cy), FarmPoint(minX, cy)),
            listOf(FarmPoint(minX, cy), FarmPoint(maxX, cy), FarmPoint(maxX, maxY), FarmPoint(minX, maxY))
        )
        "Vertical 3" -> {
            val s1 = minX + (maxX - minX) * 0.33f
            val s2 = minX + (maxX - minX) * 0.66f
            listOf(
                listOf(FarmPoint(minX, minY), FarmPoint(s1, minY), FarmPoint(s1, maxY), FarmPoint(minX, maxY)),
                listOf(FarmPoint(s1, minY), FarmPoint(s2, minY), FarmPoint(s2, maxY), FarmPoint(s1, maxY)),
                listOf(FarmPoint(s2, minY), FarmPoint(maxX, minY), FarmPoint(maxX, maxY), FarmPoint(s2, maxY))
            )
        }
        "Grid 4" -> listOf(
            listOf(FarmPoint(minX, minY), FarmPoint(cx, minY), FarmPoint(cx, cy), FarmPoint(minX, cy)),
            listOf(FarmPoint(cx, minY), FarmPoint(maxX, minY), FarmPoint(maxX, cy), FarmPoint(cx, cy)),
            listOf(FarmPoint(minX, cy), FarmPoint(cx, cy), FarmPoint(cx, maxY), FarmPoint(minX, maxY)),
            listOf(FarmPoint(cx, cy), FarmPoint(maxX, cy), FarmPoint(maxX, maxY), FarmPoint(cx, maxY))
        )
        else -> listOf(boundary)
    }

    return rawLots.map { lot ->
        val constrained = lot.map { point -> keepPointInsideBoundary(point, boundary) }
        if (constrained.distinctBy { "${it.x}-${it.y}" }.size >= 3) constrained else boundary
    }
}

private fun keepPointInsideBoundary(candidate: FarmPoint, boundaryPoints: List<FarmPoint>): FarmPoint {
    if (boundaryPoints.size < 3 || isPointInsidePolygon(candidate, boundaryPoints)) return candidate

    val center = polygonCentroid(boundaryPoints)
    var t = 1.0f
    while (t > 0.0f) {
        val trial = FarmPoint(
            x = center.x + (candidate.x - center.x) * t,
            y = center.y + (candidate.y - center.y) * t,
        )
        if (isPointInsidePolygon(trial, boundaryPoints)) return trial
        t -= 0.05f
    }

    return center
}

private fun isPointInsidePolygon(point: FarmPoint, polygon: List<FarmPoint>): Boolean {
    if (polygon.size < 3) return true

    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        val intersects = ((pi.y > point.y) != (pj.y > point.y)) &&
            (point.x < (pj.x - pi.x) * (point.y - pi.y) / ((pj.y - pi.y).takeIf { it != 0f } ?: 0.000001f) + pi.x)
        if (intersects) inside = !inside
        j = i
    }
    return inside
}

private fun polygonCentroid(points: List<FarmPoint>): FarmPoint {
    if (points.isEmpty()) return FarmPoint(0.5f, 0.5f)
    val x = points.sumOf { it.x.toDouble() } / points.size
    val y = points.sumOf { it.y.toDouble() } / points.size
    return FarmPoint(x.toFloat(), y.toFloat())
}

private fun polygonAreaDraw(points: List<FarmPoint>): Double {
    if (points.size < 3) return 0.0
    var area = 0.0
    for (i in points.indices) {
        val j = (i + 1) % points.size
        area += points[i].x.toDouble() * points[j].y.toDouble()
        area -= points[j].x.toDouble() * points[i].y.toDouble()
    }
    return abs(area) / 2.0
}

private fun parseAreaInputToHectaresDraw(raw: String): Double? {
    val text = raw.trim()
    if (text.isEmpty()) return null

    val normalized = text.replace(',', '.')
    val value = Regex("""[0-9]+(?:\.[0-9]+)?""").find(normalized)?.value?.toDoubleOrNull() ?: return null
    val lower = normalized.lowercase()

    return when {
        lower.contains("acre") || lower.contains("ac") -> value * 0.40468564224
        else -> value
    }
}

private fun formatFixedDraw(value: Double, decimals: Int): String {
    val scale = pow10Draw(decimals)
    val absolute = abs(value)
    val roundedScaled = round(absolute * scale).toLong()
    val integerPart = roundedScaled / scale.toLong()
    val fractionalPart = roundedScaled % scale.toLong()

    val number = if (decimals > 0) {
        val fraction = fractionalPart.toString().padStart(decimals, '0')
        "$integerPart.$fraction"
    } else {
        integerPart.toString()
    }

    return if (value < 0) "-$number" else number
}

private fun pow10Draw(decimals: Int): Double {
    var result = 1.0
    repeat(decimals.coerceAtLeast(0)) { result *= 10.0 }
    return result
}

private val BackIconDraw2: ImageVector
    get() = ImageVector.Builder(
        name = "BackIconDraw2",
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
