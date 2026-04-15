package com.alleyz15.farmtwinai.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun PlatformDataUrlImage(
    dataUrl: String,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val webView = remember(context) {
        @SuppressLint("SetJavaScriptEnabled")
        WebView(context).apply {
            settings.javaScriptEnabled = false
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            webViewClient = WebViewClient()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { webView },
        update = {
            it.loadDataWithBaseURL(
                null,
                htmlForDataUrl(dataUrl),
                "text/html",
                "utf-8",
                null,
            )
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
