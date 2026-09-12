package com.kraptor

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.Request

open class EmbedSporty : EmbedStreams() {
    override val name = "EmbedSporty"
    override val mainUrl = "https://embed.st"
}

open class EmbedStreams : ExtractorApi() {
    override val name = "EmbedStreams"
    override val mainUrl = "https://embedsports.top"
    override val requiresReferer = true

    companion object {
        private const val PLAYER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.7103.48 Safari/537.36"

        private val DROPPED_HEADERS = setOf(
            "host", "connection", "content-length", "accept-encoding", "range"
        )

        private val MANIFEST_REGEX = Regex("""\.m3u8|\.mpd""")

        private val PLAY_SCRIPT = """
            (function() {
                try {
                    if (typeof jwplayer !== 'undefined') {
                        try { jwplayer().play(true); } catch (e) {}
                    }
                    if (window.player && typeof window.player.play === 'function') {
                        try { window.player.play(); } catch (e) {}
                    }
                    var playButton = document.querySelector('.jw-icon-display, .jw-display-icon-container');
                    if (playButton) { playButton.click(); }
                    document.querySelectorAll('video').forEach(function(v) {
                        try { v.muted = true; v.play(); } catch (e) {}
                    });
                    var unmuteBtn = document.querySelector('button.unmute, #UnMutePlayer button, .player-poster, [data-player] button');
                    if (unmuteBtn) { unmuteBtn.click(); }
                } catch (e) {}
            })();
        """.trimIndent()
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val resolver = WebViewResolver(
            interceptUrl = MANIFEST_REGEX,
            additionalUrls = listOf(MANIFEST_REGEX),
            userAgent = PLAYER_USER_AGENT,
            useOkhttp = false,
            script = PLAY_SCRIPT,
            timeout = 20_000L
        )

        val (intercepted, additional) = resolver.resolveUsingWebView(
            url = url,
            referer = referer ?: mainUrl
        )

        val request = intercepted
            ?: additional.firstOrNull { MANIFEST_REGEX.containsMatchIn(it.url.toString()) }
            ?: return

        processVideoUrl(url, request, callback)
    }

    private suspend fun processVideoUrl(
        embedUrl: String,
        request: Request,
        callback: (ExtractorLink) -> Unit
    ) {
        val videoUrl = request.url.toString()
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

        val linkType = if (videoUrl.contains(".mpd", ignoreCase = true)) {
            ExtractorLinkType.DASH
        } else {
            ExtractorLinkType.M3U8
        }

        callback.invoke(newExtractorLink(
            source = kaynakAdı,
            name = kaynakAdı,
            url = videoUrl,
            type = linkType
        ) {
            this.quality = Qualities.Unknown.value
            this.referer = "$mainUrl/"
            this.headers = buildPlaybackHeaders(request)
        })
    }

    private fun buildPlaybackHeaders(request: Request): Map<String, String> {
        val headers = java.util.TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)

        headers["Referer"] = "$mainUrl/"
        headers["Origin"] = mainUrl

        request.headers.forEach { (key, value) ->
            if (key.lowercase() !in DROPPED_HEADERS && value.isNotBlank()) {
                headers[key] = value
            }
        }

        headers["User-Agent"] = PLAYER_USER_AGENT

        return headers
    }
}
