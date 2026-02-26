package com.kraptor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

open class EmbedStreams(context: Context) : ExtractorApi() {
    override val name = "EmbedStreams"
    override val mainUrl = "https://embedsporty.top"
    override val requiresReferer = true
    private var appContext = context

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        coroutineScope {
            try {
                val videoUrl = getVideoUrlWithWebView(appContext, url)

                if (videoUrl != null) {
                    processVideoUrl(videoUrl, callback)
                }
            } catch (e: Exception) {
            }
        }
    }

    private suspend fun getVideoUrlWithWebView(context: Context, url: String): String? {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<String?> { cont ->
                val captured = AtomicBoolean(false)
                var webView: WebView? = null

                try {
                    webView = WebView(context.applicationContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.mediaPlaybackRequiresUserGesture = false

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    view?.evaluateJavascript("""
                                        (function() {
                                            try {
                                                var playButton = document.querySelector('.jw-icon-display');
                                                if (playButton) { playButton.click(); return 'Play clicked'; }
                                                if (typeof jwplayer !== 'undefined') { jwplayer().play(); return 'JW API Play'; }
                                                return 'Wait...';
                                            } catch(e) { return 'Error: ' + e.message; }
                                        })();
                                    """.trimIndent()) { }
                                }, 2500)
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): android.webkit.WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: return null

                                if (reqUrl.contains(".m3u8") && !captured.get()) {
                                    if (captured.compareAndSet(false, true)) {
                                        cont.resume(reqUrl)
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            webView?.stopLoading()
                                            webView?.destroy()
                                        }, 200)
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                    }

                    webView.loadUrl(url)

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (captured.compareAndSet(false, true)) {
                            cont.resume(null)
                            webView?.destroy()
                        }
                    }, 15000)

                } catch (e: Exception) {
                    if (captured.compareAndSet(false, true)) {
                        cont.resume(null)
                        webView?.destroy()
                    }
                }

                cont.invokeOnCancellation {
                    if (captured.compareAndSet(false, true)) {
                        Handler(Looper.getMainLooper()).post { webView?.destroy() }
                    }
                }
            }
        }
    }

    private suspend fun processVideoUrl(videoUrl: String, callback: (ExtractorLink) -> Unit) {
        val kaynakAdi = when {
            videoUrl.contains("alpha") -> "Alpha-720p 30fps"
            videoUrl.contains("bravo") -> "Bravo-Yüksek Fps"
            videoUrl.contains("charlie") -> "Charlie-Değişken"
            videoUrl.contains("delta") -> "Delta-Yedek"
            videoUrl.contains("golf") -> "Golf-Yüksek Kalite"
            videoUrl.contains("hotel") -> "Hotel-Ultra Kalite"
            videoUrl.contains("echo") -> "Echo-İyi Kalite"
            videoUrl.contains("admin") || videoUrl.contains("rtmp") -> "Admin-Hızlı"
            videoUrl.contains("modifiles") -> "Premium-Source"
            else -> "Streamed-Hızlı"
        }

        callback.invoke(
            newExtractorLink(
                source = kaynakAdi,
                name = kaynakAdi,
                url = videoUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.quality = Qualities.Unknown.value
                this.referer = "https://embedsporty.top/"
                this.headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.7103.48 Safari/537.36",
                    "Origin" to "https://embedsporty.top",
                    "Accept" to "*/*"
                )
            }
        )
    }
}