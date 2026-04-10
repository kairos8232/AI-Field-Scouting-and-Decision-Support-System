package com.alleyz15.farmtwinai.ui.screens.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alleyz15.farmtwinai.ui.components.AppScaffold
import com.alleyz15.farmtwinai.ui.components.DualActionButtons
import com.alleyz15.farmtwinai.ui.components.PlatformGoogleMap
import com.alleyz15.farmtwinai.ui.components.ScreenColumn
import com.alleyz15.farmtwinai.ui.components.SectionHeader

@Composable
fun FarmAddressSetupScreen(
    address: String,
    locationQuery: String,
    searchTrigger: Int,
    useCurrentLocationTrigger: Int,
    onAddressChange: (String) -> Unit,
    onSearch: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    AppScaffold(title = "Farm Setup", subtitle = "Step 1 of 3 - Set address", onBack = onBack) { _ ->
        ScreenColumn {
            SectionHeader(
                title = "Search farm area",
                body = "Enter a place name first. This positions the map before you freeze it and draw the farm lot.",
            )

            OutlinedTextField(
                value = address,
                onValueChange = onAddressChange,
                label = { Text("Search location...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onSearch,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Search")
                }

                OutlinedButton(
                    onClick = onUseCurrentLocation,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("My Location")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
            ) {
                PlatformGoogleMap(
                    modifier = Modifier.matchParentSize(),
                    locationQuery = locationQuery,
                    searchTrigger = searchTrigger,
                    allowMapInteraction = true,
                    useCurrentLocationTrigger = useCurrentLocationTrigger,
                )
            }

            Text(
                text = "Pan and zoom to your area. Next will save this map view and continue to boundary drawing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DualActionButtons(
                primaryLabel = "Next: Freeze + Draw Boundary",
                onPrimary = onContinue,
            )
        }
    }
}
