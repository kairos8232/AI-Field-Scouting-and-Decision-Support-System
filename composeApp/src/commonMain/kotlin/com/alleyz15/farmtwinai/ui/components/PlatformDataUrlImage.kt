package com.alleyz15.farmtwinai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun PlatformDataUrlImage(
    dataUrl: String,
    modifier: Modifier = Modifier,
)
