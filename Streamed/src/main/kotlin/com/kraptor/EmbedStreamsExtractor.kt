package com.kraptor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

open class EmbedStreams(context: Context) : ExtractorApi() {
    override val name = "EmbedStreams"
    override val mainUrl = "https://embedstreams.top"
    override val requiresReferer = true

    private var appContext = context

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
//        Log.d("kraptor_${this.name}", "İşleme girdi")

      try {
          val context = appContext
//            Log.d("kraptor_$name", "Video URL'si çıkarılıyor...")

            // WebView ile URL'yi yakala
            val videoUrl = withContext(Dispatchers.Main) {
                getVideoUrlWithWebView(context, url)
            }

            if (videoUrl != null) {
                processVideoUrl(videoUrl, callback)
                return
            }

//            Log.e("kraptor_$name", "Video URL bulunamadı")
        } catch (e: Exception) {
//           Log.e("kraptor_${this.name}", "Genel hata: ${e.message}", e)
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
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
//                                Log.d("kraptor_webview", "Sayfa yükleniyor: $url")
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
//                                Log.d("kraptor_webview", "Sayfa yüklendi: $url")

                                // Play butonuna tıkla
                                Handler(Looper.getMainLooper()).postDelayed({
                                    view?.evaluateJavascript("""
                                        (function() {
                                            try {
                                                // JWPlayer play butonunu bul ve tıkla
                                                var playButton = document.querySelector('.jw-icon-display');
                                                if (playButton) {
                                                    console.log('Play butonuna tıklanıyor...');
                                                    playButton.click();
                                                    return 'Play butonuna tıklandı';
                                                }
                                                
                                                // Alternatif olarak direkt JWPlayer API'sini kullan
                                                if (typeof jwplayer !== 'undefined') {
                                                    jwplayer().play();
                                                    return 'JWPlayer API ile oynatıldı';
                                                }
                                                
                                                return 'Play butonu bulunamadı';
                                            } catch(e) {
                                                return 'Hata: ' + e.message;
                                            }
                                        })();
                                    """.trimIndent()) { result ->
//                                        Log.d("kraptor_webview", "Play butonu sonucu: $result")
                                    }
                                }, 2000) // 2 saniye bekle, sayfa tam yüklensin

//                                Log.d("kraptor_webview", "shouldInterceptRequest bekleniyor...")
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): android.webkit.WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: return null
//                                Log.d("kraptor_webview", "İstek: $reqUrl")

                                // Sadece .m3u8 ile biten URL'leri yakala
                                if (reqUrl.endsWith(".m3u8") && !captured.get()) {
//                                    Log.d("kraptor_webview", "✅ Video URL'si yakalandı: $reqUrl")
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
//                            Log.d("kraptor_webview", "Zaman aşımı: URL bulunamadı")
                            cont.resume(null)
                            webView?.destroy()
                        }
                    }, 15000) // 15 saniye timeout

                } catch (e: Exception) {
//                    Log.e("kraptor_webview", "WebView oluşturma hatası: ${e.message}")
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
//       Log.d("kraptor_$name", "Video URL: $videoUrl")
        val kaynakAdı = if (videoUrl.contains("alpha")) {
            "Alpha-En Güvenilir 720p 30fps"
        } else if (videoUrl.contains("bravo")) {
            "Bravo-Yüksek Fps Düşük Bitrate"
        } else if (videoUrl.contains("charlie")) {
            "Charlie-Bazen kötü kaliteli yayın verebilir"
        } else if (videoUrl.contains("delta")) {
            "Delta-Yedekleme fena değil (gecikme olabilir/yüklenmeyebilir)"
        } else if (videoUrl.contains("echo")) {
            "Echo-İyi sayılabilecek kalite"
        } else if (videoUrl.contains("foxtrot")) {
            "Foxtrot"
        } else if (videoUrl.contains("golf")) {
            "Golf-Yüksek kalite, doğrudan kaynaktan"
        } else if (videoUrl.contains("intel")) {
            "Intel-Geniş etkinlik kapsamı, şüpheli kalite"
        } else if (videoUrl.contains("admin") || videoUrl.contains("poocloud")) {
            "Admin-Admin tarafından eklenen"
        } else if (videoUrl.contains("hotel")) {
            "Hotel-Çok yüksek kalite"
        } else {
            "Streamed"
        }

        callback.invoke(newExtractorLink(
            source = kaynakAdı,
            name = kaynakAdı,
            url = videoUrl,
            type = ExtractorLinkType.M3U8
        ) {
            this.quality = Qualities.Unknown.value
            this.referer = "https://embedsports.top/"
            this.headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.7103.48 Safari/537.36",
                "Origin" to "https://embedsports.top",
                "Connection" to "keep-alive"
            )
        })
    }
}