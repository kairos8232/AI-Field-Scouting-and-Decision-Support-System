package com.alleyz15.farmtwinai.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformGoogleMap(
    modifier: Modifier,
    locationQuery: String,
  allowMapInteraction: Boolean,
  useCurrentLocationTrigger: Int,
) {
    val context = LocalContext.current
    val apiKey = remember(context) {
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, android.content.pm.PackageManager.GET_META_DATA)
        appInfo.metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
    }

    val mapHtml = remember(apiKey) { buildGoogleMapHtml(apiKey) }
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            loadDataWithBaseURL("https://maps.googleapis.com/", mapHtml, "text/html", "utf-8", null)
        }
    }

    LaunchedEffect(locationQuery) {
        if (locationQuery.isNotBlank()) {
            val quoted = JSONObject.quote(locationQuery)
            webView.evaluateJavascript("window.updateLocation($quoted);", null)
        }
    }

    LaunchedEffect(useCurrentLocationTrigger) {
      if (useCurrentLocationTrigger > 0) {
        webView.evaluateJavascript("window.useCurrentLocation();", null)
      }
    }

    AndroidView(
        modifier = modifier,
        factory = { webView },
      update = {
        it.isClickable = allowMapInteraction
        it.isLongClickable = allowMapInteraction
        it.isFocusable = allowMapInteraction
        it.isFocusableInTouchMode = allowMapInteraction
        it.isEnabled = allowMapInteraction
      },
    )
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
