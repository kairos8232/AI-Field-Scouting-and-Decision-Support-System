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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
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
    var mapSize by remember { mutableStateOf(IntSize.Zero) }

    val cropHint = remember(soilType, waterAvailability, preferredCrop) {
        buildCropHint(soilType, waterAvailability, preferredCrop)
    }

    AppScaffold(title = "Manual Setup", subtitle = "Flexible farm entry", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Build the farm profile",
                body = "These fields are placeholders for Phase 1. Keep wording flexible so users can continue even if they do not know every value.",
            )
            LabeledInput("Farm name", initialValue = "Seri Padi Plot A")
            LabeledInput("Crop type", initialValue = "Tomato")
            LabeledInput("Planting date", initialValue = "2026-03-20")
            LabeledInput("Location", initialValue = "")
            LabeledInput("Field size", initialValue = "1.8 acres")
            LabeledInput("Irrigation source", initialValue = "Drip irrigation / I don't know")
            LabeledInput("Sunlight condition", initialValue = "Full sun / partly shaded")
            LabeledInput("Drainage condition", initialValue = "Mixed drainage / I don't know")
            LabeledInput("Soil pH if known", initialValue = "6.4 / I don't know")
            DualActionButtons(
                primaryLabel = "Create Farm Twin",
                onPrimary = onContinue,
                secondaryLabel = "Use Suggested Demo Values",
                onSecondary = {
                    address = "Alor Setar, Kedah"
                    latitude = 6.1248
                    longitude = 100.3678
                    mapPin = latLngToPin(latitude, longitude, mapSize)
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

private fun pinToLatLng(pin: Offset, mapSize: IntSize): Pair<Double, Double> {
    val width = if (mapSize.width > 0) mapSize.width.toFloat() else 320f
    val height = if (mapSize.height > 0) mapSize.height.toFloat() else 190f
    val x = pin.x.coerceIn(0f, width)
    val y = pin.y.coerceIn(0f, height)
    val lat = 7.5 - (y / height) * 6.5
    val lng = 99.5 + (x / width) * 6.5
    return lat to lng
}

private fun latLngToPin(lat: Double, lng: Double, mapSize: IntSize): Offset {
    val width = if (mapSize.width > 0) mapSize.width.toFloat() else 320f
    val height = if (mapSize.height > 0) mapSize.height.toFloat() else 190f
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
