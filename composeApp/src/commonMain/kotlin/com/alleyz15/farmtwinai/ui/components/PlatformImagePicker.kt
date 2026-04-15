package com.alleyz15.farmtwinai.ui.components

import androidx.compose.runtime.Composable

class ImagePickerController internal constructor(
    val launchCamera: () -> Unit,
    val launchGallery: () -> Unit,
)

@Composable
expect fun rememberImagePickerController(
    onImagePicked: (base64: String, mimeType: String) -> Unit,
    onError: (String) -> Unit,
): ImagePickerController
