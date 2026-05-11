package com.sanibonani.save.ui.components.maps

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.sanibonani.save.domain.model.Group
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class LeafletGroupMarker(
	val id: String,
	val name: String,
	val subtitle: String,
	val lat: Double,
	val lon: Double
)

private class LeafletBridge(
	private val onMarkerClickCallback: (String) -> Unit
) {
	private val mainHandler = Handler(Looper.getMainLooper())

	@JavascriptInterface
	fun onMarkerClick(groupId: String) {
		mainHandler.post { onMarkerClickCallback(groupId) }
	}
}

/**
 * Leaflet map inside a WebView, used for the Discover Groups map view.
 *
 * Markers are fed via a JS function (`window.setGroups([...])`) and marker taps
 * call back into Android via a JS bridge (`Android.onMarkerClick(groupId)`).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LeafletGroupsMap(
	groups: List<Group>,
	onMarker: (String) -> Unit,
	modifier: Modifier = Modifier
) {
	val context = LocalContext.current
	val tag = remember { "LeafletGroupsMap" }
	val onMarkerState by rememberUpdatedState(onMarker)

	val markersJson = remember(groups) {
		val markers = groups.mapNotNull { g ->
			val id = g.id
			val lat = g.latitude
			val lon = g.longitude

			if (id.isNullOrBlank() || lat == null || lon == null) return@mapNotNull null

			LeafletGroupMarker(
				id = id,
				name = g.name,
				subtitle = "${g.type.displayName} • ${g.currentMembers} members",
				lat = lat,
				lon = lon
			)
		}

		// markersJson is valid JS (an array), so we can pass it directly into window.setGroups(...)
		Json.encodeToString(markers)
	}

	var pageReady by remember { mutableStateOf(false) }

	val webView = remember {
		WebView(context).apply {
			settings.javaScriptEnabled = true
			settings.domStorageEnabled = true
			settings.cacheMode = WebSettings.LOAD_DEFAULT
			// The map HTML is loaded from file:///android_asset. Without universal access,
			// some devices block the page from fetching https resources (Leaflet CDN + tiles).
			// This is the most common reason the map appears blank.
			settings.allowFileAccessFromFileURLs = true
			settings.allowUniversalAccessFromFileURLs = true
			settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

			// Basic hardening (still allow assets + required network requests)
			settings.allowFileAccess = true
			settings.allowContentAccess = false

			webChromeClient = object : WebChromeClient() {
				override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
					if (consoleMessage != null) {
						Log.d(
							tag,
							"JS: ${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
						)
					}
					return super.onConsoleMessage(consoleMessage)
				}
			}
			webViewClient = object : WebViewClient() {
				override fun onPageFinished(view: WebView?, url: String?) {
					super.onPageFinished(view, url)
					pageReady = true
				}

				override fun onReceivedError(
					view: WebView?,
					errorCode: Int,
					description: String?,
					failingUrl: String?
				) {
					super.onReceivedError(view, errorCode, description, failingUrl)
					Log.w(tag, "WebView error: code=$errorCode url=$failingUrl desc=$description")
				}
			}

			addJavascriptInterface(LeafletBridge { id -> onMarkerState(id) }, "Android")
			loadUrl("file:///android_asset/leaflet/groups_map.html")
		}
	}

	DisposableEffect(Unit) {
		onDispose {
			runCatching {
				webView.stopLoading()
				webView.loadUrl("about:blank")
				webView.clearHistory()
				webView.removeAllViews()
				webView.destroy()
			}
		}
	}

	LaunchedEffect(pageReady, markersJson) {
		if (!pageReady) return@LaunchedEffect

		var attempt = 0
		val maxAttempts = 10

		fun pushMarkers() {
			if (attempt >= maxAttempts) return

			// Guard against race conditions where onPageFinished fires before Leaflet finishes loading.
			val js = """
				(function(){
					try {
						if (window.__leafletReady && typeof window.setGroups === 'function') {
							window.setGroups($markersJson);
							return true;
						}
					} catch (e) {}
					return false;
				})()
			""".trimIndent()

			webView.evaluateJavascript(js) { result ->
				// WebView returns "true" / "false" (or null on older devices)
				val ok = result?.contains("true", ignoreCase = true) == true
				if (!ok && attempt < maxAttempts) {
					attempt++
					// Use postDelayed on background-ish timing, but limit attempt count
					Handler(Looper.getMainLooper()).postDelayed({ pushMarkers() }, 200)
				}
			}
		}

		pushMarkers()
	}

	AndroidView(
		modifier = modifier,
		factory = { webView },
		update = { /* updates are driven by LaunchedEffect */ }
	)
}

