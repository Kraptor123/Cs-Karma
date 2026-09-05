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

class OkRuExtractor : ExtractorApi() {
    override val name            = "OkRU"
    override val mainUrl         = "https://ok.ru"
    override val requiresReferer = false

    private suspend fun invokeLink(
        quality: String,
        videoUrl: String,
        type: ExtractorLinkType,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl =
            videoUrl.replace("&amp;", "&").replace("\\u0026", "&").replace("\\/", "/").trim()

        val (qualityValue, qualityLabel) = getQualityData(quality)
        Log.d(name, "$quality -> $cleanUrl")

        callback(
            newExtractorLink(
                name, "$name | $qualityLabel", cleanUrl, type
            ) {
                headers = mutableMapOf(
                    "User-Agent" to userAgent,
                    "Referer" to "$mainUrl/",
                    "Origin" to mainUrl,
                    "Accept" to "*/*",
                    "Accept-Encoding" to "identity"
                )
            })
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d(name, url)

        val id =
            Regex("""(?:videoembed|video)/(\d+)""").find(url.trim())?.groupValues?.get(1) ?: return
        val embedUrl = "$mainUrl/videoembed/$id"

        val response = app.get(
            embedUrl,
            headers = mapOf(
                "User-Agent" to userAgent,
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
        )

        val html = response.text
        val dataOptions =
            response.document.selectFirst("[data-module=OKVideo]")?.attr("data-options")
                ?.ifEmpty { null }
                ?: Regex("""data-options="([^"]+)"""").find(html)?.groupValues?.get(1)
                    ?.replace("&quot;", "\"")
                ?: return

        val originalUrl =
            Regex(""""originalUrl":"([^"]+)"""").find(dataOptions)?.groupValues?.get(1)
                ?.replace("\\/", "/")
        if (originalUrl != null && (originalUrl.contains("youtube.com") || originalUrl.contains("youtu.be"))) {
            loadExtractor(originalUrl, subtitleCallback, callback)
            return
        }

        val ytContentId =
            Regex(""""(?:provider":"USER_YOUTUBE"[^}]*"contentId"|"contentId":"([^"]+)"[^}]*"provider":"USER_YOUTUBE"):"([^"]+)"""")
                .find(dataOptions)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }

        if (!ytContentId.isNullOrEmpty()) {
            loadExtractor(
                "https://www.youtube.com/watch?v=$ytContentId",
                subtitleCallback,
                callback
            )
            return
        }

        val hlsManifestUrl =
            Regex(""""hlsManifestUrl":"([^"]+)"""").find(dataOptions)?.groupValues?.get(1)
        if (hlsManifestUrl != null) {
            invokeLink("HLS", hlsManifestUrl, ExtractorLinkType.M3U8, callback)
        }

        val videosJsonRegex = Regex("""\{"name":"([^"]+)","url":"([^"]+)"""")
        val fallbackMatches = videosJsonRegex.findAll(dataOptions).toList()
        val sortedFallbacks =
            fallbackMatches.sortedByDescending { getQualityData(it.groupValues[1]).first }

        sortedFallbacks.forEach { match ->
            val quality = match.groupValues[1]
            val videoUrl = match.groupValues[2]

            if (!videoUrl.contains("youtube.com") && !videoUrl.contains("youtu.be")) {
                invokeLink(quality, videoUrl, ExtractorLinkType.VIDEO, callback)
            }
        }
    }
}


private val userAgent        = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0"

private val qualityMap       = mapOf(
    "full"   to (Qualities.P1080 to "1080P"),
    "hd"     to (Qualities.P720  to "720P"),
    "sd"     to (Qualities.P480  to "480P"),
    "low"    to (Qualities.P360  to "360P"),
    "lowest" to (Qualities.P240  to "240P"),
    "mobile" to (Qualities.P144  to "144P")
)

private fun getQualityData(quality: String): Pair<Int, String> {
    val match = qualityMap[quality.lowercase()]
    return if (match != null) {
        match.first.value to match.second
    } else {
        Qualities.Unknown.value to quality.uppercase()
    }
}