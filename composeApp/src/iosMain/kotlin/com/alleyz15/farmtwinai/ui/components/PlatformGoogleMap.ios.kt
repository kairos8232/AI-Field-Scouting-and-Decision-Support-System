package com.alleyz15.farmtwinai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.CoreGraphics.CGRectMake
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformGoogleMap(
    modifier: Modifier,
    locationQuery: String,
  allowMapInteraction: Boolean,
  useCurrentLocationTrigger: Int,
) {
    val apiKey = remember {
        NSBundle.mainBundle.objectForInfoDictionaryKey("GMSApiKey")?.toString().orEmpty()
    }

    val html = remember(apiKey) { buildGoogleMapHtml(apiKey) }
    val webView = remember {
      WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = WKWebViewConfiguration()).apply {
            loadHTMLString(html, baseURL = null)
        }
    }

    LaunchedEffect(locationQuery) {
        if (locationQuery.isNotBlank()) {
            val quoted = jsQuoted(locationQuery)
          webView.evaluateJavaScript("window.updateLocation($quoted);", completionHandler = null)
        }
    }

    LaunchedEffect(useCurrentLocationTrigger) {
      if (useCurrentLocationTrigger > 0) {
        webView.evaluateJavaScript("window.useCurrentLocation();", completionHandler = null)
      }
    }

    UIKitView(
        modifier = modifier,
        factory = { webView },
      update = { view ->
        view.userInteractionEnabled = allowMapInteraction
      },
    )
}

private fun jsQuoted(value: String): String {
    val escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
    return "\"$escaped\""
}

private fun buildGoogleMapHtml(apiKey: String): String =
    """
    <!DOCTYPE html>
    <html>
    <head>
      <meta name="viewport" content="initial-scale=1.0, width=device-width" />
      <style>
        html, body, #map { height: 100%; margin: 0; padding: 0; background: #d7e3d7; }
      </style>
      <script src="https://maps.googleapis.com/maps/api/js?key=$apiKey"></script>
      <script>
        let map;
        let geocoder;
        let mapReady = false;
        let pendingQuery = null;

        function geocodeAddress(query) {
          if (!query || !geocoder || !map) return;
          geocoder.geocode({ address: query }, function(results, status) {
            if (status === 'OK' && results && results.length > 0) {
              map.setCenter(results[0].geometry.location);
              map.setZoom(16);
            }
          });
        }

        function init() {
          geocoder = new google.maps.Geocoder();
          map = new google.maps.Map(document.getElementById('map'), {
            center: { lat: 6.1184, lng: 100.3685 },
            zoom: 14,
            mapTypeControl: false,
            streetViewControl: false,
            fullscreenControl: false
          });
          mapReady = true;
          if (pendingQuery) {
            geocodeAddress(pendingQuery);
          }
        }

        window.updateLocation = function(query) {
          pendingQuery = query;
          if (mapReady) {
            geocodeAddress(pendingQuery);
          }
        }

        window.useCurrentLocation = function() {
          if (!map || !navigator.geolocation) return;
          navigator.geolocation.getCurrentPosition(function(position) {
            const loc = { lat: position.coords.latitude, lng: position.coords.longitude };
            map.setCenter(loc);
            map.setZoom(17);
          });
        }

        window.onload = init;
      </script>
    </head>
    <body>
      <div id="map"></div>
    </body>
    </html>
    """.trimIndent()
