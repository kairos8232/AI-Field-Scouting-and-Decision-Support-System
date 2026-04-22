package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.domain.model.FarmPoint
import com.alleyz15.farmtwinai.ui.components.AuroraBackground
import com.alleyz15.farmtwinai.ui.theme.isAppDarkTheme

import com.alleyz15.farmtwinai.ui.components.OnboardingAdaptiveWidth
import com.alleyz15.farmtwinai.ui.components.PlatformGoogleMap
import com.alleyz15.farmtwinai.ui.theme.Leaf400
import com.alleyz15.farmtwinai.ui.theme.Mint200
import com.alleyz15.farmtwinai.ui.theme.Sand100
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

@Composable
fun LotSectionSetupScreen(
    farmName: String,
    locationQuery: String,
    searchTrigger: Int,
    useCurrentLocationTrigger: Int,
    boundaryPoints: List<FarmPoint>,
    totalAreaHa: String,
    lots: List<List<FarmPoint>>,
    lotCropTypes: Map<Int, String>,
    lotPlantingDates: Map<Int, String>,
    selectedMode: com.alleyz15.farmtwinai.domain.model.AppMode,
    onFarmNameChange: (String) -> Unit,
    onTotalAreaChange: (String) -> Unit,
    onLotsChange: (List<List<FarmPoint>>) -> Unit,
    onLotCropTypeChange: (Int, String) -> Unit,
    onLotPlantingDateChange: (Int, String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    var selectedLotIndex by remember { mutableIntStateOf(if (lots.isNotEmpty()) 0 else -1) }
    var draggingVertexIndex by remember { mutableIntStateOf(-1) }
    var templateMode by remember { mutableStateOf("Vertical") }
    var templateCountText by remember { mutableStateOf("2") }
    var lotWarningMessage by remember { mutableStateOf<String?>(null) }
    var formValidationMessage by remember { mutableStateOf<String?>(null) }

    fun applyTemplate(mode: String, rawCountText: String) {
        val requested = rawCountText.toIntOrNull() ?: 2
        val validated = when (mode) {
            "Grid" -> {
                val atLeastTwo = requested.coerceAtLeast(2)
                if (atLeastTwo % 2 == 0) atLeastTwo else atLeastTwo + 1
            }
            else -> requested.coerceAtLeast(1)
        }
        val normalized = validated.toString()
        if (templateCountText != normalized) {
            templateCountText = normalized
        }
        val generated = generateMockLots(boundaryPoints, mode, validated)
        onLotsChange(generated)
        selectedLotIndex = if (generated.isNotEmpty()) 0 else -1
        lotWarningMessage = null
    }

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
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    ) {
                        Icon(BackIconDraw2, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "Farm Setup",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Step 3 of 3 - Subdivide farm",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedLabelColor = Mint200,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.74f),
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.42f),
                    cursorColor = Leaf400,
                    focusedBorderColor = Leaf400,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.32f),
                )

                OutlinedTextField(
                    value = farmName,
                    onValueChange = {
                        onFarmNameChange(it)
                        formValidationMessage = null
                    },
                    label = { Text("Farm Name") },
                    placeholder = { Text("e.g. Seri Padi Plot A") },
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    singleLine = true,
                    colors = fieldColors,
                )

                if (formValidationMessage != null && farmName.trim().isEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Farm Name is required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE8998E),
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Map & Controls Row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = maxContentWidth)
                        .height(260.dp)
                        .background(if (isAppDarkTheme()) Color.Black.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (boundaryPoints.isEmpty()) {
                        Text("No boundary defined", color = MaterialTheme.colorScheme.onBackground)
                    } else {
                        PlatformGoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            locationQuery = locationQuery,
                            searchTrigger = searchTrigger,
                            allowMapInteraction = false,
                            useCurrentLocationTrigger = useCurrentLocationTrigger,
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(lots, selectedLotIndex) {
                                    detectTapGestures { tap ->
                                        val point = keepPointInsideBoundary(toFarmPointDraw(tap, size), boundaryPoints)

                                        if (selectedLotIndex in lots.indices) {
                                            val updatedLots = lots.toMutableList()
                                            val current = updatedLots[selectedLotIndex].toMutableList()
                                            if (current.size >= 3) {
                                                // If current lot is already a closed shape, start a new lot on next tap.
                                                updatedLots.add(listOf(point))
                                                selectedLotIndex = updatedLots.lastIndex
                                                lotWarningMessage = null
                                            } else {
                                                val candidate = current.toMutableList().apply { add(point) }
                                                val hasOverlap = candidate.size >= 3 &&
                                                    isLotOverlapping(selectedLotIndex, candidate, updatedLots)
                                                if (hasOverlap) {
                                                    lotWarningMessage = "Lots cannot overlap. Move points or start another area."
                                                    return@detectTapGestures
                                                }
                                                updatedLots[selectedLotIndex] = candidate
                                                lotWarningMessage = null
                                            }
                                            onLotsChange(updatedLots)
                                        } else {
                                            onLotsChange(listOf(listOf(point)))
                                            selectedLotIndex = 0
                                            lotWarningMessage = null
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
                                                val candidate = currentLotPoints.toMutableList().apply {
                                                    this[draggingVertexIndex] = FarmPoint(nx, ny)
                                                }
                                                val hasOverlap = candidate.size >= 3 &&
                                                    isLotOverlapping(selectedLotIndex, candidate, currentLots)
                                                if (!hasOverlap) {
                                                    currentLots[selectedLotIndex] = candidate
                                                    onLotsChange(currentLots)
                                                    lotWarningMessage = null
                                                } else {
                                                    lotWarningMessage = "Lots cannot overlap."
                                                }
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
                            drawPath(boundaryPath, color = Color.White.copy(alpha = 0.8f), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))

                            lots.forEachIndexed { index, lotPoints ->
                                if (lotPoints.isNotEmpty()) {
                                    val isSelected = index == selectedLotIndex

                                    if (lotPoints.size >= 2) {
                                        val lotPath = Path().apply {
                                            val first = toOffsetDraw(lotPoints.first(), size)
                                            moveTo(first.x, first.y)
                                            for (i in 1 until lotPoints.size) {
                                                val p = toOffsetDraw(lotPoints[i], size)
                                                lineTo(p.x, p.y)
                                            }
                                            if (lotPoints.size >= 3) {
                                                close()
                                            }
                                        }

                                        if (lotPoints.size >= 3) {
                                            drawPath(lotPath, color = if (isSelected) Mint200.copy(alpha = 0.42f) else Color(0xFF74D8D0).copy(alpha = 0.24f))
                                        }

                                        drawPath(
                                            lotPath,
                                            color = if (isSelected) Color.White else Color(0xFFE0FFFB).copy(alpha = 0.9f),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(if (isSelected) 4f else 2.5f)
                                        )
                                    }

                                    lotPoints.forEach { pt ->
                                        val c = toOffsetDraw(pt, size)
                                        val isHot = isSelected
                                        drawCircle(Color.Black.copy(alpha = if (isHot) 0.35f else 0.22f), radius = if (isHot) 11.5f else 9.5f, center = c)
                                        drawCircle(if (isHot) Color(0xFFF4F1E8) else Color(0xFFDDF8F0), radius = if (isHot) 8.5f else 7f, center = c)
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
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
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
                                        lotWarningMessage = null
                                    }
                                }
                            }
                        ) {
                            Text("Undo", color = MaterialTheme.colorScheme.onBackground)
                        }

                        TextButton(
                            onClick = {
                                val parsed = templateCountText.toIntOrNull() ?: lots.size.coerceAtLeast(1)
                                val target = (parsed.coerceAtLeast(lots.size) + 1).toString()
                                applyTemplate(templateMode, target)
                            }
                        ) {
                            Text("New Lot", color = MaterialTheme.colorScheme.onBackground)
                        }

                        TextButton(
                            onClick = {
                                if (selectedLotIndex in lots.indices) {
                                    val updatedLots = lots.toMutableList()
                                    updatedLots.removeAt(selectedLotIndex)
                                    onLotsChange(updatedLots)
                                    selectedLotIndex = updatedLots.lastIndex.coerceAtLeast(-1)
                                    lotWarningMessage = null
                                } else {
                                    onLotsChange(emptyList())
                                    selectedLotIndex = -1
                                    lotWarningMessage = null
                                }
                            }
                        ) {
                            Text("Clear Lot", color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }

                if (lotWarningMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = lotWarningMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE8998E),
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Templates: mode + count
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf("Vertical", "Horizontal", "Grid").forEach { mode ->
                        val selected = templateMode == mode
                        Button(
                            onClick = {
                                templateMode = mode
                                applyTemplate(mode, templateCountText)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) Mint200.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                contentColor = if (selected) Mint200 else MaterialTheme.colorScheme.onBackground,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.height(36.dp).weight(1f),
                        ) {
                            Text(
                                text = mode,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }

                    OutlinedTextField(
                        value = templateCountText,
                        onValueChange = { value ->
                            val filtered = value.filter { it.isDigit() }.take(2)
                            templateCountText = filtered
                            if (filtered.isNotEmpty()) {
                                applyTemplate(templateMode, filtered)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.width(80.dp).height(56.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedBorderColor = Leaf400,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.32f),
                            focusedLabelColor = Mint200,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            cursorColor = Leaf400,
                        ),
                    )
                }

                if (templateMode == "Grid") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Grid count accepts even numbers only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    )
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
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Selected Lot Area",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
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
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
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
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val cropVal = lotCropTypes[selectedLotIndex] ?: ""
                    OutlinedTextField(
                        value = cropVal,
                        onValueChange = {
                            onLotCropTypeChange(selectedLotIndex, it)
                            formValidationMessage = null
                        },
                        label = { Text("Crop Type") },
                        placeholder = { Text("e.g. Tomato, Corn") },
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                        singleLine = true,
                        colors = fieldColors
                    )

                    if (formValidationMessage != null && cropVal.trim().isEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Crop Type is required for this lot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE8998E),
                            modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                        )
                    }

                    if (selectedMode == com.alleyz15.farmtwinai.domain.model.AppMode.LIVE_MONITORING) {
                        Spacer(modifier = Modifier.height(16.dp))
                        val dateVal = lotPlantingDates[selectedLotIndex] ?: ""
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth)
                        ) {
                            OutlinedTextField(
                                value = dateVal,
                                onValueChange = {
                                    onLotPlantingDateChange(selectedLotIndex, it)
                                },
                                label = { Text("Planting Date") },
                                placeholder = { Text("YYYY-MM-DD") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = fieldColors
                            )
                            IconButton(
                                onClick = {
                                    val daysAgo = (10..30).random()
                                    val mockDate = "2026-03-${(31 - daysAgo).coerceIn(1, 31).toString().padStart(2, '0')}"
                                    onLotPlantingDateChange(selectedLotIndex, mockDate)
                                },
                                modifier = Modifier.padding(top = 8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Mint200.copy(alpha=0.1f))
                            ) {
                                Text("✨")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Soil/Water data will be auto-filled by AI in next step.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
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
                                    contentColor = if (isSel) Mint200 else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
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
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (formValidationMessage != null) {
                    Text(
                        text = formValidationMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE8998E),
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        val missingFarmName = farmName.trim().isEmpty()
                        val lotIndicesMissingCrop = lots.indices.filter { idx ->
                            lotCropTypes[idx].orEmpty().trim().isEmpty()
                        }

                        formValidationMessage = when {
                            missingFarmName -> "Farm Name is required."
                            lots.isEmpty() -> "Please create at least one lot before continuing."
                            lotIndicesMissingCrop.isNotEmpty() -> {
                                val labels = lotIndicesMissingCrop.joinToString(", ") { "Lot ${it + 1}" }
                                "Crop Type is required for: $labels"
                            }
                            else -> null
                        }

                        if (formValidationMessage == null) {
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

private fun generateMockLots(boundary: List<FarmPoint>, mode: String, count: Int): List<List<FarmPoint>> {
    if (boundary.size < 3) return emptyList()
    var minX = 1f; var minY = 1f; var maxX = 0f; var maxY = 0f
    for (p in boundary) {
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y
        if (p.y > maxY) maxY = p.y
    }
    val safeCount = count.coerceAtLeast(1)
    val rawLots = when (mode) {
        "Vertical" -> {
            (0 until safeCount).map { index ->
                val startT = index.toFloat() / safeCount.toFloat()
                val endT = (index + 1).toFloat() / safeCount.toFloat()
                val x1 = minX + (maxX - minX) * startT
                val x2 = minX + (maxX - minX) * endT
                listOf(FarmPoint(x1, minY), FarmPoint(x2, minY), FarmPoint(x2, maxY), FarmPoint(x1, maxY))
            }
        }
        "Horizontal" -> {
            (0 until safeCount).map { index ->
                val startT = index.toFloat() / safeCount.toFloat()
                val endT = (index + 1).toFloat() / safeCount.toFloat()
                val y1 = minY + (maxY - minY) * startT
                val y2 = minY + (maxY - minY) * endT
                listOf(FarmPoint(minX, y1), FarmPoint(maxX, y1), FarmPoint(maxX, y2), FarmPoint(minX, y2))
            }
        }
        "Grid" -> {
            val evenCount = if (safeCount % 2 == 0) safeCount else safeCount + 1
            val cols = (evenCount / 2).coerceAtLeast(1)
            val rows = 2
            val result = mutableListOf<List<FarmPoint>>()
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val x1 = minX + (maxX - minX) * (col.toFloat() / cols.toFloat())
                    val x2 = minX + (maxX - minX) * ((col + 1).toFloat() / cols.toFloat())
                    val y1 = minY + (maxY - minY) * (row.toFloat() / rows.toFloat())
                    val y2 = minY + (maxY - minY) * ((row + 1).toFloat() / rows.toFloat())
                    result += listOf(
                        FarmPoint(x1, y1),
                        FarmPoint(x2, y1),
                        FarmPoint(x2, y2),
                        FarmPoint(x1, y2),
                    )
                }
            }
            result
        }
        else -> listOf(boundary)
    }

    return rawLots.mapNotNull { lot ->
        val xMin = lot.minOf { it.x }
        val xMax = lot.maxOf { it.x }
        val yMin = lot.minOf { it.y }
        val yMax = lot.maxOf { it.y }
        val clipped = clipPolygonAgainstRectangle(boundary, xMin, yMin, xMax, yMax)
        if (clipped.size >= 3) clipped else null
    }
}

private fun clipPolygonAgainstRectangle(subject: List<FarmPoint>, xMin: Float, yMin: Float, xMax: Float, yMax: Float): List<FarmPoint> {
    var clipped = subject
    clipped = clipEdge(clipped, xMin, true) { it.x >= xMin }
    clipped = clipEdge(clipped, xMax, true) { it.x <= xMax }
    clipped = clipEdge(clipped, yMin, false) { it.y >= yMin }
    clipped = clipEdge(clipped, yMax, false) { it.y <= yMax }
    return clipped
}

private fun clipEdge(subject: List<FarmPoint>, linePos: Float, isVertical: Boolean, isInside: (FarmPoint) -> Boolean): List<FarmPoint> {
    if (subject.isEmpty()) return emptyList()
    val out = mutableListOf<FarmPoint>()
    var s = subject.last()
    for (e in subject) {
        if (isInside(e)) {
            if (!isInside(s)) out.add(computeIntersection(s, e, linePos, isVertical))
            out.add(e)
        } else if (isInside(s)) {
            out.add(computeIntersection(s, e, linePos, isVertical))
        }
        s = e
    }
    return out
}

private fun computeIntersection(p1: FarmPoint, p2: FarmPoint, linePos: Float, isVertical: Boolean): FarmPoint {
    return if (isVertical) {
        val t = if (p2.x == p1.x) 0f else (linePos - p1.x) / (p2.x - p1.x)
        FarmPoint(linePos, p1.y + t * (p2.y - p1.y))
    } else {
        val t = if (p2.y == p1.y) 0f else (linePos - p1.y) / (p2.y - p1.y)
        FarmPoint(p1.x + t * (p2.x - p1.x), linePos)
    }
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

private fun isLotOverlapping(
    editingIndex: Int,
    candidate: List<FarmPoint>,
    lots: List<List<FarmPoint>>,
): Boolean {
    if (candidate.size < 3) return false

    return lots.anyIndexed { index, other ->
        if (index == editingIndex) return@anyIndexed false
        if (other.size < 3) return@anyIndexed false
        polygonsOverlap(candidate, other)
    }
}

private inline fun <T> List<T>.anyIndexed(predicate: (index: Int, value: T) -> Boolean): Boolean {
    for (index in indices) {
        if (predicate(index, this[index])) return true
    }
    return false
}

private fun polygonsOverlap(a: List<FarmPoint>, b: List<FarmPoint>): Boolean {
    for (i in a.indices) {
        val a1 = a[i]
        val a2 = a[(i + 1) % a.size]
        for (j in b.indices) {
            val b1 = b[j]
            val b2 = b[(j + 1) % b.size]
            if (segmentsIntersect(a1, a2, b1, b2)) return true
        }
    }

    // One polygon fully inside the other without edge intersection.
    if (isPointInsidePolygon(a.first(), b)) return true
    if (isPointInsidePolygon(b.first(), a)) return true
    return false
}

private fun segmentsIntersect(p1: FarmPoint, p2: FarmPoint, q1: FarmPoint, q2: FarmPoint): Boolean {
    val d1 = direction(q1, q2, p1)
    val d2 = direction(q1, q2, p2)
    val d3 = direction(p1, p2, q1)
    val d4 = direction(p1, p2, q2)

    if (((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) && ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))) {
        return true
    }
    if (d1 == 0f && onSegment(q1, q2, p1)) return true
    if (d2 == 0f && onSegment(q1, q2, p2)) return true
    if (d3 == 0f && onSegment(p1, p2, q1)) return true
    if (d4 == 0f && onSegment(p1, p2, q2)) return true
    return false
}

private fun direction(a: FarmPoint, b: FarmPoint, c: FarmPoint): Float {
    return (c.x - a.x) * (b.y - a.y) - (c.y - a.y) * (b.x - a.x)
}

private fun onSegment(a: FarmPoint, b: FarmPoint, p: FarmPoint): Boolean {
    val minX = minOf(a.x, b.x)
    val maxX = maxOf(a.x, b.x)
    val minY = minOf(a.y, b.y)
    val maxY = maxOf(a.y, b.y)
    return p.x in minX..maxX && p.y in minY..maxY
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
