package com.example.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.model.UserAgentMode
import com.example.model.VirtualInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

data class TapPulseEvent(
    val instanceId: String,
    val xPct: Float,
    val yPct: Float,
    val timestampMs: Long = System.currentTimeMillis()
)

data class ViewportRuntimeInfo(
    val currentUrl: String = "",
    val pageTitle: String = "",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val isSecureHttps: Boolean = true
)

class AetherWebViewManager(
    context: Context,
    private val networkMonitor: NetworkMonitor,
    private val onDownloadRequested: (url: String, fileName: String, mimeType: String) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val webViews = ConcurrentHashMap<String, WebView>()
    private val instanceConfigs = ConcurrentHashMap<String, VirtualInstance>()

    private val _viewportInfo = MutableStateFlow<Map<String, ViewportRuntimeInfo>>(emptyMap())
    val viewportInfo: StateFlow<Map<String, ViewportRuntimeInfo>> = _viewportInfo.asStateFlow()

    private val _tapPulses = MutableSharedFlow<TapPulseEvent>(extraBufferCapacity = 32)
    val tapPulses: SharedFlow<TapPulseEvent> = _tapPulses.asSharedFlow()

    private val blockedHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "facebook.net",
        "connect.facebook.net",
        "ads.yahoo.com",
        "adservice.google.com",
        "scorecardresearch.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "pubmatic.com",
        "rubiconproject.com"
    )

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(instance: VirtualInstance): WebView {
        instanceConfigs[instance.id] = instance
        val existing = webViews[instance.id]
        if (existing != null) {
            applyConfig(existing, instance)
            return existing
        }

        val webView = WebView(appContext).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isFocusable = true
            isFocusableInTouchMode = true

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                allowFileAccess = false
                allowContentAccess = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    val urlStr = uri.toString()
                    val cfg = instanceConfigs[instance.id] ?: instance
                    if (cfg.httpsOnly && urlStr.startsWith("http://", ignoreCase = true)) {
                        val upgraded = "https://" + urlStr.removePrefix("http://")
                        networkMonitor.recordHttpsUpgrade()
                        view?.loadUrl(upgraded)
                        return true
                    }
                    return false
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val host = request?.url?.host?.lowercase() ?: return null
                    val cfg = instanceConfigs[instance.id] ?: instance
                    if (cfg.adShieldEnabled && blockedHosts.any { host == it || host.endsWith(".$it") }) {
                        networkMonitor.recordTrackerBlocked()
                        return WebResourceResponse(
                            "text/plain",
                            "utf-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    val safeUrl = url ?: ""
                    updateViewportInfo(instance.id) {
                        it.copy(
                            currentUrl = safeUrl,
                            isLoading = true,
                            canGoBack = view?.canGoBack() == true,
                            isSecureHttps = safeUrl.startsWith("https://", ignoreCase = true)
                        )
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    val safeUrl = url ?: ""
                    updateViewportInfo(instance.id) {
                        it.copy(
                            currentUrl = safeUrl,
                            pageTitle = view?.title ?: safeUrl,
                            isLoading = false,
                            progress = 100,
                            canGoBack = view?.canGoBack() == true,
                            isSecureHttps = safeUrl.startsWith("https://", ignoreCase = true)
                        )
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    updateViewportInfo(instance.id) {
                        it.copy(
                            progress = newProgress,
                            isLoading = newProgress < 100
                        )
                    }
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) {
                        updateViewportInfo(instance.id) { it.copy(pageTitle = title) }
                    }
                }
            }

            setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                if (!url.isNullOrBlank()) {
                    val guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    onDownloadRequested(url, guessedName, mimeType ?: "application/octet-stream")
                }
            }
        }

        applyConfig(webView, instance)
        val initialUrl = normalizeUrl(instance.targetUrl, instance.httpsOnly)
        webView.loadUrl(initialUrl)
        updateViewportInfo(instance.id) {
            it.copy(
                currentUrl = initialUrl,
                isSecureHttps = initialUrl.startsWith("https://", ignoreCase = true)
            )
        }
        webViews[instance.id] = webView
        return webView
    }

    fun updateInstanceConfig(instance: VirtualInstance) {
        instanceConfigs[instance.id] = instance
        webViews[instance.id]?.let { wv ->
            mainScope.launch {
                applyConfig(wv, instance)
            }
        }
    }

    private fun applyConfig(webView: WebView, instance: VirtualInstance) {
        val desiredUa = UserAgentMode.fromRaw(instance.userAgentMode).userAgentString
        if (webView.settings.userAgentString != desiredUa) {
            webView.settings.userAgentString = desiredUa
        }
    }

    fun navigate(instanceId: String, rawUrl: String) {
        val cfg = instanceConfigs[instanceId]
        val normalized = normalizeUrl(rawUrl, cfg?.httpsOnly ?: true)
        mainScope.launch {
            webViews[instanceId]?.loadUrl(normalized)
            updateViewportInfo(instanceId) {
                it.copy(
                    currentUrl = normalized,
                    isSecureHttps = normalized.startsWith("https://", ignoreCase = true)
                )
            }
        }
    }

    fun reload(instanceId: String) {
        mainScope.launch {
            webViews[instanceId]?.reload()
        }
    }

    fun goBack(instanceId: String) {
        mainScope.launch {
            val wv = webViews[instanceId]
            if (wv != null && wv.canGoBack()) {
                wv.goBack()
            }
        }
    }

    fun dispatchVirtualTap(instanceId: String, xPct: Float, yPct: Float) {
        val clampedX = xPct.coerceIn(0.02f, 0.98f)
        val clampedY = yPct.coerceIn(0.02f, 0.98f)
        _tapPulses.tryEmit(TapPulseEvent(instanceId, clampedX, clampedY))

        mainScope.launch {
            val wv = webViews[instanceId] ?: return@launch
            val w = wv.width.takeIf { it > 0 } ?: 720
            val h = wv.height.takeIf { it > 0 } ?: 1280
            val x = w * clampedX
            val y = h * clampedY
            val downTime = SystemClock.uptimeMillis()
            val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
            wv.dispatchTouchEvent(downEvent)
            downEvent.recycle()
            delay(45L)
            val upTime = SystemClock.uptimeMillis()
            val upEvent = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0)
            wv.dispatchTouchEvent(upEvent)
            upEvent.recycle()
        }
    }

    fun dispatchVirtualScroll(instanceId: String, deltaY: Int) {
        mainScope.launch {
            val wv = webViews[instanceId] ?: return@launch
            wv.evaluateJavascript("window.scrollBy({top: $deltaY, behavior: 'smooth'});", null)
        }
    }

    fun evaluateScript(instanceId: String, script: String) {
        mainScope.launch {
            webViews[instanceId]?.evaluateJavascript(script, null)
        }
    }

    fun clearSandboxStorage(instanceId: String) {
        mainScope.launch {
            webViews[instanceId]?.clearCache(true)
            webViews[instanceId]?.clearHistory()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }
    }

    fun destroyInstanceWebView(instanceId: String) {
        mainScope.launch {
            webViews.remove(instanceId)?.apply {
                stopLoading()
                destroy()
            }
        }
    }

    fun destroyAll() {
        mainScope.launch {
            val keys = webViews.keys.toList()
            keys.forEach { key ->
                webViews.remove(key)?.apply {
                    stopLoading()
                    destroy()
                }
            }
        }
    }

    fun normalizeUrl(raw: String, httpsOnly: Boolean): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "https://example.org"
        return when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> {
                if (httpsOnly) {
                    networkMonitor.recordHttpsUpgrade()
                    "https://" + trimmed.removePrefix("http://")
                } else {
                    trimmed
                }
            }
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://duckduckgo.com/?q=${android.net.Uri.encode(trimmed)}"
        }
    }

    private fun updateViewportInfo(
        instanceId: String,
        transform: (ViewportRuntimeInfo) -> ViewportRuntimeInfo
    ) {
        _viewportInfo.update { current ->
            val prev = current[instanceId] ?: ViewportRuntimeInfo()
            current + (instanceId to transform(prev))
        }
    }
}
