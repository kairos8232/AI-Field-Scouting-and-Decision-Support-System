package com.alleyz15.farmtwinai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun PlatformGoogleMap(
    modifier: Modifier,
    locationQuery: String,
    searchTrigger: Int,
    allowMapInteraction: Boolean,
    useCurrentLocationTrigger: Int,
)
