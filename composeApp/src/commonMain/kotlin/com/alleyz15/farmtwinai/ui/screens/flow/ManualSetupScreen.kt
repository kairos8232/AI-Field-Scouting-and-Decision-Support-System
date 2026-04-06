package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ManualSetupScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    var farmName by remember { mutableStateOf("Seri Padi Plot A") }
    var cropType by remember { mutableStateOf("Tomato") }
    var plantingDate by remember { mutableStateOf("2026-03-20") }
    var address by remember { mutableStateOf("Pendang, Kedah") }
    var fieldSize by remember { mutableStateOf("1.8 acres") }
    var irrigationSource by remember { mutableStateOf("Drip irrigation") }
    var soilType by remember { mutableStateOf("Loamy") }
    var soilPh by remember { mutableStateOf("6.4") }
    var drainageCondition by remember { mutableStateOf("Mixed drainage") }
    var waterAvailability by remember { mutableStateOf("Medium") }
    var preferredCrop by remember { mutableStateOf("Chili") }
    var latitude by remember { mutableStateOf(6.0607) }
    var longitude by remember { mutableStateOf(100.5071) }
    var mapPin by remember { mutableStateOf(Offset(160f, 90f)) }

    val cropHint = remember(soilType, waterAvailability, preferredCrop) {
        buildCropHint(soilType, waterAvailability, preferredCrop)
    }

    AppScaffold(title = "Manual Setup", subtitle = "Flexible farm entry", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Build the farm profile",
                body = "Pick the farm point on a 2D map, then fill soil and water conditions to get planting guidance.",
            )

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Farm address") },
                placeholder = { Text("Type full address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Button(
                onClick = {
                    val (lat, lng) = geocodeAddressDemo(address)
                    latitude = lat
                    longitude = lng
                    mapPin = latLngToPin(lat, lng)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Use Address to Place Pin")
            }

            Button(
                onClick = {
                    latitude = 3.1390
                    longitude = 101.6869
                    mapPin = latLngToPin(latitude, longitude)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Use Current Location")
            }

            Text(
                text = "Tap the 2D map to move pin",
                style = MaterialTheme.typography.labelLarge,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { tapOffset ->
                            mapPin = tapOffset
                            val (lat, lng) = pinToLatLng(tapOffset)
                            latitude = lat
                            longitude = lng
                        }
                    },
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val stepX = size.width / 6f
                    val stepY = size.height / 6f

                    for (i in 1..5) {
                        drawLine(
                            color = Color(0xFFB5C7BE),
                            start = Offset(stepX * i, 0f),
                            end = Offset(stepX * i, size.height),
                            strokeWidth = 1f,
                        )
                        drawLine(
                            color = Color(0xFFB5C7BE),
                            start = Offset(0f, stepY * i),
                            end = Offset(size.width, stepY * i),
                            strokeWidth = 1f,
                        )
                    }

                    drawCircle(
                        color = Color(0xFFD32F2F),
                        radius = 9f,
                        center = Offset(
                            x = mapPin.x.coerceIn(0f, size.width),
                            y = mapPin.y.coerceIn(0f, size.height),
                        ),
                    )
                }
            }

            Text(
                text = "Pinned location: ${formatCoord(latitude)}, ${formatCoord(longitude)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader(
                title = "Farm details",
                body = "Fill what you know. You can still continue if exact lab values are not available.",
            )

            OutlinedTextField(
                value = farmName,
                onValueChange = { farmName = it },
                label = { Text("Farm name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = cropType,
                onValueChange = { cropType = it },
                label = { Text("Current crop") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = preferredCrop,
                onValueChange = { preferredCrop = it },
                label = { Text("What do you plan to plant?") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = plantingDate,
                onValueChange = { plantingDate = it },
                label = { Text("Planting date") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = fieldSize,
                onValueChange = { fieldSize = it },
                label = { Text("Field size") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = irrigationSource,
                onValueChange = { irrigationSource = it },
                label = { Text("Irrigation source") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = soilType,
                onValueChange = { soilType = it },
                label = { Text("Soil type") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = soilPh,
                onValueChange = { soilPh = it },
                label = { Text("Soil pH") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = drainageCondition,
                onValueChange = { drainageCondition = it },
                label = { Text("Drainage condition") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = waterAvailability,
                onValueChange = { waterAvailability = it },
                label = { Text("Water availability (low / medium / high)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Planting guidance", style = MaterialTheme.typography.titleSmall)
                Text(cropHint, style = MaterialTheme.typography.bodyMedium)
            }

            DualActionButtons(
                primaryLabel = "Create Farm Twin",
                onPrimary = onContinue,
                secondaryLabel = "Use Suggested Demo Values",
                onSecondary = {
                    address = "Alor Setar, Kedah"
                    latitude = 6.1248
                    longitude = 100.3678
                    mapPin = latLngToPin(latitude, longitude)
                    soilType = "Loamy"
                    waterAvailability = "Medium"
                    preferredCrop = "Okra"
                    onContinue()
                },
            )
        }
    }
}

private fun geocodeAddressDemo(address: String): Pair<Double, Double> {
    if (address.isBlank()) return 6.1248 to 100.3678
    val hash = abs(address.lowercase().hashCode())
    val lat = 1.2 + (hash % 5400) / 1000.0
    val lng = 100.0 + (hash % 3900) / 1000.0
    return lat to lng
}

private fun pinToLatLng(pin: Offset): Pair<Double, Double> {
    val width = 320f
    val height = 190f
    val x = pin.x.coerceIn(0f, width)
    val y = pin.y.coerceIn(0f, height)
    val lat = 7.5 - (y / height) * 6.5
    val lng = 99.5 + (x / width) * 6.5
    return lat to lng
}

private fun latLngToPin(lat: Double, lng: Double): Offset {
    val width = 320f
    val height = 190f
    val x = (((lng - 99.5) / 6.5) * width).toFloat().coerceIn(0f, width)
    val y = (((7.5 - lat) / 6.5) * height).toFloat().coerceIn(0f, height)
    return Offset(x, y)
}

private fun buildCropHint(
    soilType: String,
    waterAvailability: String,
    preferredCrop: String,
): String {
    val soil = soilType.lowercase()
    val water = waterAvailability.lowercase()
    val crop = preferredCrop.ifBlank { "vegetables" }

    return when {
        soil.contains("clay") && water.contains("low") ->
            "$crop may struggle in dry clay soil. Consider drought-tolerant crops like okra, sorghum, or cassava and improve mulch cover."

        soil.contains("sandy") && water.contains("high") ->
            "$crop can grow, but nutrients may leach in sandy soil. Add organic matter and split fertilizer application in smaller doses."

        soil.contains("loam") && water.contains("medium") ->
            "Good baseline for $crop. Prioritize balanced NPK, moisture monitoring, and drainage checks after heavy rain."

        water.contains("high") ->
            "High water availability supports leafy greens and paddy-style rotation, but watch root disease risk with poor drainage."

        else ->
            "Start with $crop on a small plot first, then tune irrigation and soil amendments from field observations."
    }
}

private fun formatCoord(value: Double): String {
    val rounded = (value * 100000.0).roundToInt() / 100000.0
    return rounded.toString()
}
