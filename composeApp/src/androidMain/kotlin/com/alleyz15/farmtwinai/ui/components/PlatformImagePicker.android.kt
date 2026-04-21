package com.alleyz15.farmtwinai.ui.components

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream

@Composable
actual fun rememberImagePickerController(
    onImagePicked: (base64: String, mimeType: String) -> Unit,
    onError: (String) -> Unit,
): ImagePickerController {
    val context = LocalContext.current

    val pickGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Unable to read selected image")
        }.onSuccess { bytes ->
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            onImagePicked(base64, mimeType)
        }.onFailure {
            onError(it.message ?: "Unable to load selected image.")
        }
    }

    val takePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        if (bitmap == null) {
            onError("No photo was captured.")
            return@rememberLauncherForActivityResult
        }
        runCatching {
            bitmap.toJpegBase64(quality = 90)
        }.onSuccess { base64 ->
            onImagePicked(base64, "image/jpeg")
        }.onFailure {
            onError(it.message ?: "Unable to encode captured photo.")
        }
    }

    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            takePreviewLauncher.launch(null)
        } else {
            onError("Camera permission denied.")
        }
    }

    return remember {
        ImagePickerController(
            launchCamera = {
                val granted = context.checkSelfPermission(Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (granted) {
                    takePreviewLauncher.launch(null)
                } else {
                    requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            launchGallery = {
                pickGalleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
}

private fun Bitmap.toJpegBase64(quality: Int): String {
    val stream = ByteArrayOutputStream()
    val success = compress(Bitmap.CompressFormat.JPEG, quality, stream)
    if (!success) error("Bitmap compression failed")
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}
