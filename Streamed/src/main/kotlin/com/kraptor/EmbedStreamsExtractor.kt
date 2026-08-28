package com.kraptor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
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


open class EmbedSporty(context: Context) : EmbedStreams(context) {
    override val name = "EmbedSporty"
    override val mainUrl = "https://embed.st"
}
open class EmbedStreams(context: Context) : ExtractorApi() {
    override val name = "EmbedStreams"
    override val mainUrl = "https://embedsports.top"
    override val requiresReferer = true
    private var appContext = context

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) = coroutineScope {
        try {
            val videoUrl = withContext(Dispatchers.Main) {
                getVideoUrlWithWebView(appContext, url)
            }
            if (videoUrl != null) {
                processVideoUrl(url, videoUrl, callback)
            }
        } catch (e: Exception) { }
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
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.7103.48 Safari/537.36"

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)

                                val playScript = """
                                    (function() {
                                        try {
                                            // JWPlayer
                                            var playButton = document.querySelector('.jw-icon-display, .jw-display-icon-container');
                                            if (playButton) {
                                                playButton.click();
                                            }
                                            if (typeof jwplayer !== 'undefined') {
                                                try { jwplayer().play(); } catch(e) {}
                                            }

                                            // Clappr / Video tags
                                            if (window.player && typeof window.player.play === 'function') {
                                                try { window.player.play(); } catch(e) {}
                                            }
                                            var vids = document.querySelectorAll('video');
                                            vids.forEach(function(v) {
                                                try { v.muted = true; v.play(); } catch(e) {}
                                            });

                                            // Unmute or play buttons
                                            var unmuteBtn = document.querySelector('button.unmute, #UnMutePlayer button, .player-poster, [data-player] button');
                                            if (unmuteBtn) {
                                                unmuteBtn.click();
                                            }
                                        } catch(e) {}
                                    })();
                                """.trimIndent()

                                val playRunnable = object : Runnable {
                                    var count = 0
                                    override fun run() {
                                        if (!captured.get() && count < 4) {
                                            count++
                                            view?.evaluateJavascript(playScript, null)
                                            Handler(Looper.getMainLooper()).postDelayed(this, 1500)
                                        }
                                    }
                                }
                                Handler(Looper.getMainLooper()).postDelayed(playRunnable, 1000)
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): android.webkit.WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: return null

                                // .m3u8 veya .mpd içeren URL'leri yakala (query parametreleri olabilir)
                                if ((reqUrl.contains(".m3u8", ignoreCase = true) || reqUrl.contains(".mpd", ignoreCase = true)) && !captured.get()) {
                                    if (captured.compareAndSet(false, true)) {
                                        cont.resume(reqUrl)
                                        Handler(Looper.getMainLooper()).postDelayed({ destroy() }, 500)
                                    }
                                }

                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                    }

                    webView.loadUrl(url)

                    // Zaman aşımı ekle
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

    private suspend fun processVideoUrl(
        embedUrl: String,
        videoUrl: String,
        callback: (ExtractorLink) -> Unit
    ) {
        val target = "$embedUrl $videoUrl".lowercase()
        val kaynakAdı = if (target.contains("alpha")) {
            "Alpha-Most Reliable 720p 30fps"
        } else if (target.contains("bravo")) {
            "Bravo-High FPS but Low Bitrate"
        } else if (target.contains("charlie")) {
            "Charlie-May sometimes have poor quality"
        } else if (target.contains("delta")) {
            "Delta-Backup, not bad (may lag/fail to load)"
        } else if (target.contains("echo")) {
            "Echo-Decent quality"
        } else if (target.contains("foxtrot")) {
            "Foxtrot"
        } else if (target.contains("golf")) {
            "Golf-High quality, direct from source"
        } else if (target.contains("intel")) {
            "Intel-Wide event coverage, questionable quality"
        } else if (target.contains("admin") || target.contains("poocloud")) {
            "Admin-Added by admin"
        } else if (target.contains("hotel")) {
            "Hotel-Very high quality"
        } else {
            val sourceFromUrl = if (embedUrl.contains("/embed/")) {
                embedUrl.substringAfter("/embed/").substringBefore("/").trim()
            } else {
                ""
            }
            if (sourceFromUrl.isNotEmpty()) {
                sourceFromUrl.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            } else {
                "Streamed"
            }
        }

        callback.invoke(newExtractorLink(
            source = kaynakAdı,
            name = kaynakAdı,
            url = videoUrl,
            type = ExtractorLinkType.M3U8
        ) {
            this.quality = Qualities.Unknown.value
            this.referer = mainUrl
            this.headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.7103.48 Safari/537.36",
                "Origin" to mainUrl,
                "Connection" to "keep-alive"
            )
        })
    }
}