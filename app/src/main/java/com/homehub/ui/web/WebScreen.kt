package com.homehub.ui.web

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.homehub.core.ServiceLocator
import com.homehub.data.settings.ServiceId

/**
 * Hält pro Dienst genau eine WebView-Instanz am Leben, damit beim
 * Tab-Wechsel nichts neu lädt und Logins/Scrollposition erhalten bleiben.
 */
private object WebViewCache {
    private val views = mutableMapOf<ServiceId, WebView>()
    fun get(serviceId: ServiceId): WebView? = views[serviceId]
    fun put(serviceId: ServiceId, view: WebView) { views[serviceId] = view }
}

/**
 * Generischer In-App-Browser für Home Assistant und Open WebUI.
 * - Login bleibt dank Cookie-Persistenz erhalten
 * - Datei-Uploads (z.B. in Open WebUI) funktionieren über den System-Picker
 * - Zurück-Geste navigiert erst in der Web-History, dann zur App zurück
 */
@Composable
fun WebScreen(serviceId: ServiceId) {
    var resolvedUrl by remember(serviceId) { mutableStateOf<String?>(null) }
    var configured by remember(serviceId) { mutableStateOf(true) }

    LaunchedEffect(serviceId) {
        val cfg = ServiceLocator.settings.get(serviceId)
        configured = cfg.isConfigured
        if (configured) resolvedUrl = ServiceLocator.urls.baseUrl(serviceId)
    }

    if (!configured) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "${serviceId.title} ist noch nicht eingerichtet.\nHinterlege die URL in den Einstellungen.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        fileCallback?.onReceiveValue(uris.toTypedArray())
        fileCallback = null
    }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    BackHandler(enabled = canGoBack) { webView?.goBack() }

    resolvedUrl?.let { url ->
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                // Vorhandene Instanz wiederverwenden -> kein Neuladen beim Tab-Wechsel
                val cached = WebViewCache.get(serviceId)
                if (cached != null) {
                    (cached.parent as? android.view.ViewGroup)?.removeView(cached)
                    webView = cached
                    canGoBack = cached.canGoBack()
                    return@AndroidView cached
                }
                WebView(context.applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.allowFileAccess = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?, request: WebResourceRequest?
                        ): Boolean = false // alles in der App halten

                        override fun doUpdateVisitedHistory(view: WebView?, u: String?, isReload: Boolean) {
                            canGoBack = view?.canGoBack() == true
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            view: WebView?,
                            callback: ValueCallback<Array<Uri>>?,
                            params: FileChooserParams?
                        ): Boolean {
                            fileCallback?.onReceiveValue(emptyArray())
                            fileCallback = callback
                            filePicker.launch("*/*")
                            return true
                        }
                    }
                    loadUrl(url)
                    webView = this
                    WebViewCache.put(serviceId, this)
                }
            },
            onRelease = { view ->
                // Nur aus der Hierarchie lösen, NICHT zerstören – Instanz bleibt im Cache
                (view.parent as? android.view.ViewGroup)?.removeView(view)
            },
            update = { /* URL-Wechsel passiert über remember(serviceId) */ }
        )
    } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Verbinde …", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
