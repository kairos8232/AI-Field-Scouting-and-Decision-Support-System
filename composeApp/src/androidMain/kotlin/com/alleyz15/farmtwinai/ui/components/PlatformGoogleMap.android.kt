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
import android.view.MotionEvent
import android.view.ViewGroup
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
private var sharedMapWebView: WebView? = null
private var sharedMapApiKey: String? = null

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
    val webView = remember(context, apiKey, mapHtml) {
      val existing = sharedMapWebView
      if (existing != null && sharedMapApiKey == apiKey) {
        existing
      } else {
        WebView(context).apply {
          settings.javaScriptEnabled = true
          settings.domStorageEnabled = true
          settings.setGeolocationEnabled(true)
          settings.useWideViewPort = true
          settings.loadWithOverviewMode = true
          webViewClient = object : WebViewClient() {

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

            override fun onReceivedHttpError(
              view: WebView?,
              request: WebResourceRequest?,
              errorResponse: android.webkit.WebResourceResponse?,
            ) {
              super.onReceivedHttpError(view, request, errorResponse)
              if (request?.url?.toString() == "https://maps.googleapis.com/favicon.ico") return
              Log.w(
                MAP_WEBVIEW_TAG,
                "WebView HTTP error: status=${errorResponse?.statusCode} reason=${errorResponse?.reasonPhrase} url=${request?.url}",
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
              val level = consoleMessage?.messageLevel()
              if (level == ConsoleMessage.MessageLevel.ERROR || level == ConsoleMessage.MessageLevel.WARNING) {
                Log.w(
                  MAP_WEBVIEW_TAG,
                  "JS ${consoleMessage.messageLevel()}: ${consoleMessage.message()} @ ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}",
                )
              }
              return super.onConsoleMessage(consoleMessage)
            }
          }
          loadDataWithBaseURL("https://maps.googleapis.com/", mapHtml, "text/html", "utf-8", null)
        }.also {
          sharedMapWebView = it
          sharedMapApiKey = apiKey
        }
      }
    }

    DisposableEffect(webView) {
      onDispose {
        (webView.parent as? ViewGroup)?.removeView(webView)
      }
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

    LaunchedEffect(Unit) {
      webView.postDelayed({
        webView.evaluateJavascript("window.refreshMapSize && window.refreshMapSize();", null)
      }, 250)
      webView.postDelayed({
        webView.evaluateJavascript("window.refreshMapSize && window.refreshMapSize();", null)
      }, 1000)
    }

    AndroidView(
        modifier = modifier,
      factory = {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView
      },
        update = {
        it.isEnabled = true
        it.isClickable = true
        it.isLongClickable = true
        it.isFocusable = true
        it.isFocusableInTouchMode = true
        if (allowMapInteraction) {
          it.setOnTouchListener(null)
        } else {
          it.setOnTouchListener { _, event ->
            when (event.actionMasked) {
              MotionEvent.ACTION_DOWN,
              MotionEvent.ACTION_MOVE,
              MotionEvent.ACTION_UP,
              MotionEvent.ACTION_POINTER_DOWN,
              MotionEvent.ACTION_POINTER_UP,
              MotionEvent.ACTION_CANCEL -> true
              else -> false
            }
          }
        }
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
        html, body {
          width: 100%;
          height: 100%;
          margin: 0;
          padding: 0;
          background: #d7e3d7;
          overflow: hidden;
        }
        #map {
          position: absolute;
          left: 0;
          top: 0;
          right: 0;
          bottom: 0;
          width: 100%;
          height: 100%;
          min-height: 100%;
          background: #d7e3d7;
        }
      </style>
      <script>
        var map = null;
        var geocoder = null;
        var mapReady = false;
        var pendingQuery = null;

        window.gm_authFailure = function() {
          try { console.warn('Google Maps auth failure (API key restriction/billing).'); } catch (e) {}
        };

        window.onerror = function(message, source, line, col) {
          try { console.warn('Map script error: ' + message + ' @ ' + line + ':' + col); } catch (e) {}
        };

        function saveMapView() {
          if (!map) return;
          var c = map.getCenter();
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
              var encoded = encodeURIComponent(query);
              fetch('https://nominatim.openstreetmap.org/search?format=json&limit=1&q=' + encoded)
                .then(function(r) { return r.json(); })
                .then(function(items) {
                  if (items && items.length > 0) {
                    var lat = parseFloat(items[0].lat);
                    var lng = parseFloat(items[0].lon);
                    map.setCenter({ lat: lat, lng: lng });
                    map.setZoom(16);
                    saveMapView();
                  }
                })
                .catch(function() {});
            }
          });
        }

        function refreshMapSize() {
          if (!map || !(window.google && window.google.maps)) return;
          var center = map.getCenter();
          google.maps.event.trigger(map, 'resize');
          if (center) {
            map.setCenter(center);
          }
        }

        function ensureMapContainerSize() {
          try {
            var htmlEl = document.documentElement;
            var bodyEl = document.body;
            var mapEl = document.getElementById('map');
            if (!htmlEl || !bodyEl || !mapEl) return;

            var viewportHeight = window.innerHeight || htmlEl.clientHeight || bodyEl.clientHeight || 0;
            if (viewportHeight <= 0) viewportHeight = 320;

            htmlEl.style.height = viewportHeight + 'px';
            bodyEl.style.height = viewportHeight + 'px';
            mapEl.style.height = viewportHeight + 'px';
            mapEl.style.width = '100%';
          } catch (e) {}
        }

        function init() {
          try {
            ensureMapContainerSize();
            if (!(window.google && window.google.maps)) {
              return;
            }

            geocoder = new google.maps.Geocoder();
            var mapOptions = {
              center: { lat: 6.1184, lng: 100.3685 },
              zoom: 14,
              mapTypeControl: false,
              streetViewControl: false,
              fullscreenControl: false
            };

            if (google.maps.RenderingType && google.maps.RenderingType.RASTER) {
              mapOptions.renderingType = google.maps.RenderingType.RASTER;
            }

            map = new google.maps.Map(document.getElementById('map'), mapOptions);

            map.addListener('idle', function() {
              saveMapView();
            });

            map.addListener('tilesloaded', function() {
              // no-op; tiles are visible
            });

            mapReady = true;
            window.__farmMap = map;
            restoreMapView();
            if (pendingQuery) {
              geocodeAddress(pendingQuery);
            }
            setTimeout(ensureMapContainerSize, 50);
            setTimeout(ensureMapContainerSize, 250);
            setTimeout(ensureMapContainerSize, 1000);
            setTimeout(refreshMapSize, 100);
            setTimeout(refreshMapSize, 400);
            setTimeout(refreshMapSize, 1200);
          } catch (e) {
            try { console.warn('Map init failed: ' + (e && e.message ? e.message : e)); } catch (ignore) {}
          }
        }

        window.initFarmMap = init;
        window.refreshMapSize = refreshMapSize;

        window.updateLocation = function(query) {
          pendingQuery = query;
          if (mapReady) {
            geocodeAddress(pendingQuery);
          }
        };

        window.useCurrentLocation = function() {
          if (!map) return;
          if (!navigator.geolocation) return;
          navigator.geolocation.getCurrentPosition(function(position) {
            var loc = { lat: position.coords.latitude, lng: position.coords.longitude };
            map.setCenter(loc);
            map.setZoom(17);
            saveMapView();
          }, function(error) {});
        };

        window.onload = function() {
          ensureMapContainerSize();
          setTimeout(function() {
            init();
          }, 0);
        };

        window.addEventListener('resize', function() {
          ensureMapContainerSize();
          refreshMapSize();
        });

        var __farmMapSizingChecks = 0;
        var __farmMapSizingTimer = setInterval(function() {
          ensureMapContainerSize();
          if (mapReady) {
            refreshMapSize();
          }
          __farmMapSizingChecks++;
          if (__farmMapSizingChecks >= 12) {
            clearInterval(__farmMapSizingTimer);
          }
        }, 500);
      </script>
      <script async defer src="https://maps.googleapis.com/maps/api/js?key=$apiKey&loading=async&callback=initFarmMap"></script>
    </head>
    <body>
      <div id="map"></div>
    </body>
    </html>
    """.trimIndent()
}
