package com.alleyz15.farmtwinai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSData
import platform.Foundation.base64EncodedStringWithOptions
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerEditedImage
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

private val SOURCE_TYPE_PHOTO_LIBRARY = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
private val SOURCE_TYPE_CAMERA = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera

@Composable
actual fun rememberImagePickerController(
    onImagePicked: (base64: String, mimeType: String) -> Unit,
    onError: (String) -> Unit,
): ImagePickerController {
    val latestOnImagePicked by rememberUpdatedState(onImagePicked)
    val latestOnError by rememberUpdatedState(onError)

    val delegate = remember {
        IOSImagePickerDelegate(
            onImagePicked = { base64, mime -> latestOnImagePicked(base64, mime) },
            onError = { message -> latestOnError(message) },
        )
    }

    return remember {
        ImagePickerController(
            launchCamera = {
                presentPicker(
                    sourceType = SOURCE_TYPE_CAMERA,
                    delegate = delegate,
                    onError = latestOnError,
                )
            },
            launchGallery = {
                presentPicker(
                    sourceType = SOURCE_TYPE_PHOTO_LIBRARY,
                    delegate = delegate,
                    onError = latestOnError,
                )
            },
        )
    }
}

private fun presentPicker(
    sourceType: UIImagePickerControllerSourceType,
    delegate: IOSImagePickerDelegate,
    onError: (String) -> Unit,
) {
    if (!UIImagePickerController.isSourceTypeAvailable(sourceType)) {
        onError("Selected image source is not available on this device.")
        return
    }

    val picker = UIImagePickerController()
    picker.sourceType = sourceType
    picker.delegate = delegate
    picker.allowsEditing = false

    val presenter = topViewController()
    if (presenter == null) {
        onError("Unable to open image picker.")
        return
    }

    presenter.presentViewController(picker, animated = true, completion = null)
}

private fun topViewController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) {
        top = top.presentedViewController
    }
    return top
}

private class IOSImagePickerDelegate(
    private val onImagePicked: (base64: String, mimeType: String) -> Unit,
    private val onError: (String) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = (didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage)
            ?: (didFinishPickingMediaWithInfo[UIImagePickerControllerEditedImage] as? UIImage)

        if (image == null) {
            onError("No image returned from picker.")
            picker.dismissViewControllerAnimated(true, completion = null)
            return
        }

        val data: NSData? = UIImageJPEGRepresentation(image, 0.9)
        if (data == null) {
            onError("Unable to encode selected image.")
            picker.dismissViewControllerAnimated(true, completion = null)
            return
        }

        val base64 = data.base64EncodedStringWithOptions(0u)
        onImagePicked(base64, "image/jpeg")
        picker.dismissViewControllerAnimated(true, completion = null)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}
