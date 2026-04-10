package com.alleyz15.farmtwinai.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.GeolocationPermissions
import android.webkit.ConsoleMessage
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import android.view.View
import com.alleyz15.farmtwinai.BuildConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

private const val MAP_WEBVIEW_TAG = "FarmMapWebView"

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformGoogleMap(
    modifier: Modifier,
    locationQuery: String,
    searchTrigger: Int,
    allowMapInteraction: Boolean,
    useCurrentLocationTrigger: Int,
) {
    val context = LocalContext.current
    val apiKey = remember(context) {
    val fromBuildConfig = BuildConfig.GOOGLE_MAPS_API_KEY.trim()
    if (fromBuildConfig.isNotBlank()) {
        Log.i(MAP_WEBVIEW_TAG, "Using BuildConfig key (length=${fromBuildConfig.length})")
      fromBuildConfig
    } else {
      val appInfo = context.packageManager.getApplicationInfo(
        context.packageName,
        android.content.pm.PackageManager.GET_META_DATA,
      )
        val fromManifest = appInfo.metaData?.getString("com.google.android.geo.API_KEY").orEmpty().trim()
        Log.i(MAP_WEBVIEW_TAG, "Using manifest key (length=${fromManifest.length})")
        fromManifest
    }
    }

    val mapHtml = remember(apiKey) { buildGoogleMapHtml(apiKey) }
    val webView = remember(context) {
      WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.setGeolocationEnabled(true)
        webViewClient = object : WebViewClient() {
          override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            view?.evaluateJavascript(
              """
              (function() {
                var hasGoogle = !!window.google;
                var hasMaps = !!(window.google && window.google.maps);
                var statusEl = document.getElementById('status');
                return JSON.stringify({
                  readyState: document.readyState,
                  hasGoogle: hasGoogle,
                  hasMaps: hasMaps,
                  statusText: statusEl ? statusEl.textContent : "",
                  statusVisible: statusEl ? statusEl.style.display : ""
                });
              })();
              """.trimIndent(),
            ) { result ->
              Log.i(MAP_WEBVIEW_TAG, "WebView health: $result")
            }
          }

          override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?,
          ) {
            super.onReceivedError(view, request, error)
            Log.w(
              MAP_WEBVIEW_TAG,
              "WebView load error: ${error?.description} (code=${error?.errorCode}) url=${request?.url}",
            )
          }
        }
        webChromeClient = object : WebChromeClient() {
          override fun onGeolocationPermissionsShowPrompt(
            origin: String?,
            callback: GeolocationPermissions.Callback?,
          ) {
            callback?.invoke(origin, true, false)
          }

          override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            Log.i(
              MAP_WEBVIEW_TAG,
              "JS ${consoleMessage?.messageLevel()}: ${consoleMessage?.message()} @ ${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}",
            )
            return super.onConsoleMessage(consoleMessage)
          }
        }
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
      }
    }

    DisposableEffect(webView) {
      onDispose {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
      }
    }

    LaunchedEffect(mapHtml) {
      webView.loadDataWithBaseURL("https://maps.googleapis.com/", mapHtml, "text/html", "utf-8", null)
    }

    LaunchedEffect(searchTrigger) {
      if (searchTrigger > 0 && locationQuery.isNotBlank()) {
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
      factory = {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView
      },
        update = {
            it.isClickable = allowMapInteraction
            it.isLongClickable = allowMapInteraction
            it.isFocusable = allowMapInteraction
            it.isFocusableInTouchMode = allowMapInteraction
            it.isEnabled = allowMapInteraction
        },
    )
}

private fun buildGoogleMapHtml(apiKey: String): String {
    if (apiKey.isBlank()) {
        return """
    <!DOCTYPE html>
    <html>
    <head>
      <meta name="viewport" content="initial-scale=1.0, width=device-width" />
      <style>
        html, body {
          height: 100%;
          margin: 0;
          padding: 0;
          background: #f5f3ee;
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
          color: #1f2a21;
        }
        .wrap {
          height: 100%;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 16px;
          box-sizing: border-box;
        }
        .card {
          border: 1px solid #cad6ca;
          border-radius: 12px;
          background: #ffffff;
          padding: 14px;
          max-width: 380px;
          line-height: 1.4;
        }
        .title {
          font-size: 15px;
          font-weight: 700;
          margin-bottom: 8px;
        }
        .code {
          margin-top: 8px;
          padding: 8px;
          border-radius: 8px;
          background: #f3f6f3;
          font-family: ui-monospace, Menlo, Consolas, monospace;
          font-size: 12px;
          word-break: break-all;
        }
      </style>
    </head>
    <body>
      <div class="wrap">
        <div class="card">
          <div class="title">Google Maps API key is missing</div>
          Set one of these values in the project root config:
          <div class="code">local.properties: GOOGLE_MAPS_API_KEY_ANDROID=YOUR_KEY</div>
        </div>
      </div>
    </body>
    </html>
    """.trimIndent()
    }

    return """
    <!DOCTYPE html>
    <html>
    <head>
      <meta name="viewport" content="initial-scale=1.0, width=device-width" />
      <style>
        html, body, #map { height: 100%; margin: 0; padding: 0; background: #d7e3d7; }
        #status {
          position: absolute;
          left: 12px;
          right: 12px;
          top: 12px;
          z-index: 1000;
          display: none;
          padding: 10px;
          border-radius: 8px;
          font: 12px/1.4 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
          color: #3a2e00;
          background: #fff2c2;
          border: 1px solid #e9d98c;
        }
      </style>
      <script>
        let map;
        let geocoder;
        let mapReady = false;
        let pendingQuery = null;

        function showStatus(message) {
          const el = document.getElementById('status');
          if (!el) return;
          el.style.display = 'block';
          el.textContent = message;
        }

        window.onerror = function(message, source, line, col) {
          showStatus('Map script error: ' + message + ' @ ' + line + ':' + col);
        };

        function saveMapView() {
          if (!map) return;
          const c = map.getCenter();
          if (!c) return;
          window.__farmMapView = {
            lat: c.lat(),
            lng: c.lng(),
            zoom: map.getZoom() || 14
          };
        }

        function restoreMapView() {
          if (!map || !window.__farmMapView) return;
          map.setCenter({ lat: window.__farmMapView.lat, lng: window.__farmMapView.lng });
          map.setZoom(window.__farmMapView.zoom || 14);
        }

        function geocodeAddress(query) {
          if (!query || !map) return;
          if (!geocoder) {
            geocoder = new google.maps.Geocoder();
          }
          geocoder.geocode({ address: query }, function(results, status) {
            if (status === 'OK' && results && results.length > 0) {
              map.setCenter(results[0].geometry.location);
              map.setZoom(16);
              saveMapView();
              return;
            }

            if (status === 'ZERO_RESULTS' || status === 'REQUEST_DENIED' || status === 'INVALID_REQUEST') {
              const encoded = encodeURIComponent(query);
              fetch('https://nominatim.openstreetmap.org/search?format=json&limit=1&q=' + encoded)
                .then(r => r.json())
                .then(items => {
                  if (items && items.length > 0) {
                    const lat = parseFloat(items[0].lat);
                    const lng = parseFloat(items[0].lon);
                    map.setCenter({ lat: lat, lng: lng });
                    map.setZoom(16);
                    saveMapView();
                  }
                })
                .catch(() => {});
            }
          });
        }

        function init() {
          try {
            geocoder = new google.maps.Geocoder();
            map = new google.maps.Map(document.getElementById('map'), {
              center: { lat: 6.1184, lng: 100.3685 },
              zoom: 14,
              mapTypeControl: false,
              streetViewControl: false,
              fullscreenControl: false
            });

            map.addListener('idle', function() {
              saveMapView();
            });

            mapReady = true;
            restoreMapView();
            if (pendingQuery) {
              geocodeAddress(pendingQuery);
            }
          } catch (e) {
            showStatus('Map init failed: ' + (e && e.message ? e.message : e));
          }
        }

        window.initFarmMap = init;

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
            saveMapView();
          });
        }

        setTimeout(function() {
          if (!mapReady) {
            showStatus('Map did not initialize. Check FarmMapWebView logs for API restriction errors.');
          }
        }, 6000);
      </script>
      <script async defer src="https://maps.googleapis.com/maps/api/js?key=$apiKey&loading=async&callback=initFarmMap"></script>
    </head>
    <body>
      <div id="status"></div>
      <div id="map"></div>
    </body>
    </html>
    """.trimIndent()
}
