package com.byayzen

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI

class OkRuExtractor : ExtractorApi() {
    override val name            = "OkRU"
    override val mainUrl         = "https://ok.ru"
    override val requiresReferer = false

    private val userAgent        = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0"

    private fun mapQuality(quality: String): Int {
        return when (quality.lowercase()) {
            "full"    -> Qualities.P1080.value
            "hd"      -> Qualities.P720.value
            "sd"      -> Qualities.P480.value
            "low"     -> Qualities.P360.value
            "lowest"  -> Qualities.P240.value
            "mobile"  -> Qualities.P144.value
            else      -> Qualities.Unknown.value
        }
    }

    private suspend fun invokeLink(
        quality: String,
        videoUrl: String,
        callback: (ExtractorLink) -> Unit
    ) {
        val mappedQuality = mapQuality(quality)
        Log.d(name, "$quality -> $videoUrl")
        callback(
            newExtractorLink(
                name,
                name,
                videoUrl,
                ExtractorLinkType.VIDEO
            ) {
                headers      = mutableMapOf(
                    "Referer"    to "$mainUrl/",
                    "Origin"     to mainUrl,
                    "User-Agent" to userAgent
                )
                this.quality = mappedQuality
            }
        )
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d(name, url)

        val id       = Regex("""(?:videoembed|video)/(\d+)""").find(url.trim())?.groupValues?.get(1) ?: return
        val embedUrl = "$mainUrl/videoembed/$id"

        val response = app.get(
            embedUrl,
            headers = mapOf(
                "User-Agent" to userAgent,
                "Accept"     to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
        )

        val html        = response.text
        val dataOptions = response.document.selectFirst("[data-module=OKVideo]")?.attr("data-options")?.ifEmpty { null }
            ?: Regex("""data-options="([^"]+)"""").find(html)?.groupValues?.get(1)?.replace("&quot;", "\"")
            ?: return

        val originalUrl = Regex(""""originalUrl":"([^"]+)"""").find(dataOptions)?.groupValues?.get(1)?.replace("\\/", "/")
        if (originalUrl != null && (originalUrl.contains("youtube.com") || originalUrl.contains("youtu.be"))) {
            loadExtractor(originalUrl, subtitleCallback, callback)
            return
        }

        val ytContentId = Regex(""""(?:provider":"USER_YOUTUBE"[^}]*"contentId"|"contentId":"([^"]+)"[^}]*"provider":"USER_YOUTUBE"):"([^"]+)"""")
            .find(dataOptions)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }

        if (!ytContentId.isNullOrEmpty()) {
            loadExtractor("https://www.youtube.com/watch?v=$ytContentId", subtitleCallback, callback)
            return
        }

        val metadataUrl = Regex(""""metadataUrl":"([^"]+)"""").find(dataOptions)?.groupValues?.get(1)?.replace("\\u0026", "&")
        if (metadataUrl != null) {
            val metaUri     = URI(metadataUrl)
            val baseUrl     = "${metaUri.scheme}://${metaUri.host}/"
            val mpdResponse = app.get(
                metadataUrl,
                headers = mapOf(
                    "Referer"    to "$mainUrl/",
                    "Origin"     to mainUrl,
                    "User-Agent" to userAgent
                )
            ).text

            val videoRegex = Regex("""<Representation[^>]*quality="([^"]+)"[^>]*>\s*<BaseURL>([^<]+)</BaseURL>""")
            val matches    = videoRegex.findAll(mpdResponse).toList()

            if (matches.isNotEmpty()) {
                val sortedMatches = matches.sortedByDescending { mapQuality(it.groupValues[1]) }
                sortedMatches.forEach { match ->
                    val quality      = match.groupValues[1]
                    val videoPath    = match.groupValues[2].replace("&amp;", "&")
                    val fullVideoUrl = if (videoPath.startsWith("http")) videoPath else baseUrl + videoPath.removePrefix("/")
                    invokeLink(quality, fullVideoUrl, callback)
                }
                return
            }
        }

        val videosJsonRegex = Regex("""\{"name":"([^"]+)","url":"([^"]+)"""")
        val fallbackMatches = videosJsonRegex.findAll(dataOptions).toList()
        val sortedFallbacks = fallbackMatches.sortedByDescending { mapQuality(it.groupValues[1]) }

        sortedFallbacks.forEach { match ->
            val quality  = match.groupValues[1]
            val videoUrl = match.groupValues[2].replace("\\u0026", "&").replace("\\/", "/")

            if (!videoUrl.contains("youtube.com") && !videoUrl.contains("youtu.be")) {
                invokeLink(quality, videoUrl, callback)
            }
        }
    }
}