package com.alleyz15.farmtwinai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

@OptIn(ExperimentalForeignApi::class)
@Suppress("DEPRECATION")
@Composable
actual fun PlatformDataUrlImage(
    dataUrl: String,
    modifier: Modifier,
) {
    val html = remember(dataUrl) { htmlForDataUrl(dataUrl) }
    val webView = remember {
        WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = WKWebViewConfiguration()).apply {
            opaque = false
            backgroundColor = null
        }
    }

    UIKitView(
        modifier = modifier,
        factory = { webView },
        update = { view ->
            view.loadHTMLString(html, baseURL = NSURL(string = "about:blank"))
        },
    )
}

private fun htmlForDataUrl(dataUrl: String): String {
    val safeUrl = dataUrl.replace("'", "%27")
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
          <style>
            html, body {
              margin: 0;
              padding: 0;
              background: transparent;
              overflow: hidden;
            }
            .wrap {
              width: 100%;
              height: 100%;
              display: flex;
              align-items: center;
              justify-content: center;
            }
            img {
              width: 100%;
              height: 100%;
              object-fit: contain;
            }
          </style>
        </head>
        <body>
          <div class=\"wrap\">
            <img src='$safeUrl' alt='AI expected plant image' />
          </div>
        </body>
        </html>
    """.trimIndent()
}
