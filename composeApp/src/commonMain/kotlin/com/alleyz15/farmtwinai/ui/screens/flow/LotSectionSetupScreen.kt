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
import com.alleyz15.farmtwinai.domain.model.LotSectionDraft
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.OptionCard
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun LotSectionSetupScreen(
    boundaryPoints: List<FarmPoint>,
    initialSections: List<LotSectionDraft>,
    onSaveSections: (List<LotSectionDraft>) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val lots = remember(initialSections, boundaryPoints) {
        mutableStateListOf<LotSectionDraft>().apply {
            if (initialSections.isNotEmpty()) addAll(initialSections) else add(
                LotSectionDraft("lot-1", "Lot 1", boundaryPoints, "Tomato", "Loamy", "Medium")
            )
        }
    }

    var selectedLotId by remember { mutableStateOf(lots.firstOrNull()?.id ?: "") }
    var totalFarmAreaHectare by remember { mutableStateOf("2.0") }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var draggingPointIndex by remember { mutableIntStateOf(-1) }
    var warning by remember { mutableStateOf<String?>(null) }

    val selectedIndex = lots.indexOfFirst { it.id == selectedLotId }
    val selectedLot = if (selectedIndex >= 0) lots[selectedIndex] else null

    AppScaffold(title = "Farm Setup", subtitle = "Divide farm into lot sections", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Draw lots inside the farm",
                body = "Use templates for quick equal splits, or draw manually. Lot vertices are constrained to remain inside farm boundary.",
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .onSizeChanged { mapSize = it }
                    .pointerInput(selectedLotId, lots.size, mapSize) {
                        detectTapGestures { tap ->
                            val lotIndex = lots.indexOfFirst { it.id == selectedLotId }
                            if (lotIndex >= 0) {
                                val point = normalizePoint(tap, mapSize)
                                if (isPointInsidePolygon(point, boundaryPoints)) {
                                    lots[lotIndex] = lots[lotIndex].copy(points = lots[lotIndex].points + point)
                                    warning = null
                                } else {
                                    warning = "Point must stay inside farm boundary."
                                }
                            }
                        }
                    }
                    .pointerInput(selectedLotId, lots.size, mapSize) {
                        detectDragGestures(
                            onDragStart = { start ->
                                val lot = lots.firstOrNull { it.id == selectedLotId }
                                draggingPointIndex = if (lot != null) nearestVertexIndex(lot.points, start, mapSize) else -1
                            },
                            onDragEnd = { draggingPointIndex = -1 },
                            onDragCancel = { draggingPointIndex = -1 },
                            onDrag = { change, _ ->
                                val lotIndex = lots.indexOfFirst { it.id == selectedLotId }
                                if (lotIndex >= 0 && draggingPointIndex >= 0) {
                                    val lot = lots[lotIndex]
                                    if (draggingPointIndex < lot.points.size) {
                                        val mutable = lot.points.toMutableList()
                                        val candidate = normalizePoint(change.position, mapSize)
                                        mutable[draggingPointIndex] = keepPointInsideBoundary(candidate, boundaryPoints)
                                        lots[lotIndex] = lot.copy(points = mutable)
                                    }
                                }
                            },
                        )
                    },
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    if (boundaryPoints.isNotEmpty()) {
                        val boundaryPath = Path().apply {
                            val first = denormalizePoint(boundaryPoints.first(), size)
                            moveTo(first.x, first.y)
                            for (i in 1 until boundaryPoints.size) {
                                val point = denormalizePoint(boundaryPoints[i], size)
                                lineTo(point.x, point.y)
                            }
                            close()
                        }
                        drawPath(path = boundaryPath, color = Color(0x22336633))
                        drawPath(path = boundaryPath, color = Color(0xFF2E7D32))
                    }

                    lots.forEachIndexed { idx, lot ->
                        if (lot.points.isNotEmpty()) {
                            val color = lotColor(idx)
                            val path = Path().apply {
                                val first = denormalizePoint(lot.points.first(), size)
                                moveTo(first.x, first.y)
                                for (i in 1 until lot.points.size) {
                                    val p = denormalizePoint(lot.points[i], size)
                                    lineTo(p.x, p.y)
                                }
                                if (lot.points.size > 2) close()
                            }
                            drawPath(path = path, color = color.copy(alpha = 0.25f))
                            drawPath(path = path, color = color)

                            lot.points.forEachIndexed { pointIndex, point ->
                                val marker = denormalizePoint(point, size)
                                drawCircle(
                                    color = if (lot.id == selectedLotId && pointIndex == draggingPointIndex) Color(0xFFD32F2F) else color,
                                    radius = 6f,
                                    center = marker,
                                )
                            }
                        }
                    }
                }
            }

            SectionHeader(
                title = "Quick templates",
                body = "Choose one template to pre-generate lots. You can still drag points afterward.",
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        val generated = buildVerticalTemplate(boundaryPoints, 2)
                        if (generated.isNotEmpty()) {
                            lots.clear()
                            lots.addAll(generated)
                            selectedLotId = generated.first().id
                            warning = null
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("2 Zones") }

                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        val generated = buildGridTemplate(boundaryPoints)
                        if (generated.isNotEmpty()) {
                            lots.clear()
                            lots.addAll(generated)
                            selectedLotId = generated.first().id
                            warning = null
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("4 Zones") }
            }

            OutlinedTextField(
                value = totalFarmAreaHectare,
                onValueChange = { totalFarmAreaHectare = it },
                label = { Text("Total farm area (hectare)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            lots.forEach { lot ->
                val lotArea = lotAreaHectare(lot.points, boundaryPoints, totalFarmAreaHectare.toDoubleOrNull() ?: 0.0)
                OptionCard(
                    title = "${lot.name} - ${formatNumber(lotArea)} ha",
                    description = "Crop: ${lot.cropPlan.ifBlank { "-" }} | Soil: ${lot.soilType.ifBlank { "-" }} | Water: ${lot.waterAvailability.ifBlank { "-" }}",
                    selected = lot.id == selectedLotId,
                    onClick = { selectedLotId = lot.id },
                )
            }

            if (warning != null) {
                Text(text = warning!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            DualActionButtons(
                primaryLabel = "Create Farm Twin",
                onPrimary = {
                    val hasInvalidLot = lots.any { it.points.size < 3 }
                    if (hasInvalidLot) {
                        warning = "Each lot needs at least 3 points."
                    } else {
                        warning = null
                        onSaveSections(lots.toList())
                        onContinue()
                    }
                },
            )
        }
    }
}

private fun normalizePoint(offset: Offset, size: IntSize): FarmPoint {
    if (size.width <= 0 || size.height <= 0) return FarmPoint(0.5f, 0.5f)
    return FarmPoint((offset.x / size.width).coerceIn(0f, 1f), (offset.y / size.height).coerceIn(0f, 1f))
}

private fun denormalizePoint(point: FarmPoint, size: androidx.compose.ui.geometry.Size): Offset {
    return Offset(point.x * size.width, point.y * size.height)
}

private fun nearestVertexIndex(points: List<FarmPoint>, touch: Offset, size: IntSize): Int {
    if (points.isEmpty() || size.width <= 0 || size.height <= 0) return -1
    var nearest = -1
    var minDistance = Float.MAX_VALUE
    points.forEachIndexed { index, point ->
        val dx = point.x * size.width - touch.x
        val dy = point.y * size.height - touch.y
        val dist = dx * dx + dy * dy
        if (dist < minDistance) {
            minDistance = dist
            nearest = index
        }
    }
    return if (minDistance <= 28f * 28f) nearest else -1
}

private fun lotColor(index: Int): Color = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFF4511E),
    Color(0xFF8E24AA), Color(0xFF00897B), Color(0xFF6D4C41),
)[index % 6]

private fun lotAreaHectare(lotPoints: List<FarmPoint>, boundaryPoints: List<FarmPoint>, totalFarmAreaHectare: Double): Double {
    if (lotPoints.size < 3 || boundaryPoints.size < 3 || totalFarmAreaHectare <= 0.0) return 0.0
    val boundaryArea = polygonArea(boundaryPoints)
    val lotArea = polygonArea(lotPoints)
    if (boundaryArea <= 0.0) return 0.0
    return (lotArea / boundaryArea) * totalFarmAreaHectare
}

private fun polygonArea(points: List<FarmPoint>): Double {
    if (points.size < 3) return 0.0
    var area = 0.0
    for (i in points.indices) {
        val next = (i + 1) % points.size
        area += points[i].x.toDouble() * points[next].y.toDouble()
        area -= points[next].x.toDouble() * points[i].y.toDouble()
    }
    return abs(area) / 2.0
}

private fun formatNumber(value: Double): String {
    val rounded = (value * 100.0).roundToInt() / 100.0
    return rounded.toString()
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

private fun polygonCentroid(points: List<FarmPoint>): FarmPoint {
    if (points.isEmpty()) return FarmPoint(0.5f, 0.5f)
    val x = points.sumOf { it.x.toDouble() } / points.size
    val y = points.sumOf { it.y.toDouble() } / points.size
    return FarmPoint(x.toFloat(), y.toFloat())
}

private fun buildVerticalTemplate(boundaryPoints: List<FarmPoint>, count: Int): List<LotSectionDraft> {
    if (boundaryPoints.size < 3 || count < 1) return emptyList()
    val box = boundaryBounds(boundaryPoints)
    return (0 until count).map { i ->
        val x0 = box.minX + (box.maxX - box.minX) * (i.toFloat() / count)
        val x1 = box.minX + (box.maxX - box.minX) * ((i + 1).toFloat() / count)
        val points = listOf(FarmPoint(x0, box.minY), FarmPoint(x1, box.minY), FarmPoint(x1, box.maxY), FarmPoint(x0, box.maxY))
            .map { keepPointInsideBoundary(it, boundaryPoints) }
        LotSectionDraft("lot-${i + 1}", "Lot ${i + 1}", points, "", "", "")
    }
}

private fun buildGridTemplate(boundaryPoints: List<FarmPoint>): List<LotSectionDraft> {
    if (boundaryPoints.size < 3) return emptyList()
    val box = boundaryBounds(boundaryPoints)
    val xMid = (box.minX + box.maxX) / 2f
    val yMid = (box.minY + box.maxY) / 2f
    val raw = listOf(
        listOf(FarmPoint(box.minX, box.minY), FarmPoint(xMid, box.minY), FarmPoint(xMid, yMid), FarmPoint(box.minX, yMid)),
        listOf(FarmPoint(xMid, box.minY), FarmPoint(box.maxX, box.minY), FarmPoint(box.maxX, yMid), FarmPoint(xMid, yMid)),
        listOf(FarmPoint(box.minX, yMid), FarmPoint(xMid, yMid), FarmPoint(xMid, box.maxY), FarmPoint(box.minX, box.maxY)),
        listOf(FarmPoint(xMid, yMid), FarmPoint(box.maxX, yMid), FarmPoint(box.maxX, box.maxY), FarmPoint(xMid, box.maxY)),
    )
    return raw.mapIndexed { index, polygon ->
        LotSectionDraft("lot-${index + 1}", "Lot ${index + 1}", polygon.map { keepPointInsideBoundary(it, boundaryPoints) }, "", "", "")
    }
}

private data class BoundaryBounds(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float)

private fun boundaryBounds(points: List<FarmPoint>): BoundaryBounds {
    var minX = 1f
    var maxX = 0f
    var minY = 1f
    var maxY = 0f
    points.forEach {
        minX = min(minX, it.x)
        maxX = max(maxX, it.x)
        minY = min(minY, it.y)
        maxY = max(maxY, it.y)
    }
    return BoundaryBounds(minX, maxX, minY, maxY)
}
