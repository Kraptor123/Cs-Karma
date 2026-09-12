package com.byayzen

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

open class OkRuExtractor : ExtractorApi() {
    override val name            = "OkRU"
    override val mainUrl         = "https://ok.ru"
    override val requiresReferer = false

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0"

    private fun mapQuality(quality: String): Int {
        return when (quality.lowercase()) {
            "full"   -> Qualities.P1080.value
            "hd"     -> Qualities.P720.value
            "sd"     -> Qualities.P480.value
            "low"    -> Qualities.P360.value
            "lowest" -> Qualities.P240.value
            "mobile" -> Qualities.P144.value
            else     -> Qualities.Unknown.value
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
                fixUrl(videoUrl),
                ExtractorLinkType.VIDEO
            ) {
                headers = mutableMapOf(
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

        val id =
            Regex("""(?:videoembed|video)/(\d+)""").find(url.trim())?.groupValues?.get(1)
                ?: return
        val embedUrl = fixUrl("/videoembed/$id")

        val response = app.get(
            embedUrl,
            headers = mapOf(
                "User-Agent" to userAgent,
                "Accept"     to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
        )

        val dataOptions =
            response.document.selectFirst("[data-module=OKVideo]")?.attr("data-options")
                ?.ifEmpty { null }
                ?: Regex("""data-options="([^"]+)"""").find(response.text)?.groupValues?.get(1)
                    ?.replace("&quot;", "\"")
                ?: return

        val originalUrl =
            Regex(""""originalUrl":"([^"]+)"""").find(dataOptions)?.groupValues?.get(1)
                ?.replace("\\/", "/")
        if (originalUrl != null && (originalUrl.contains("youtube.com") || originalUrl.contains("youtu.be"))) {
            Log.d(name, "YouTube redirect: $originalUrl")
            loadExtractor(originalUrl, subtitleCallback, callback)
            return
        }

        val videosJson =
            Regex(""""videos":(\[.*?\])""").find(dataOptions)?.groupValues?.get(1)
                ?: return

        val videoLinks =
            Regex("""\{"name":"([^"]+)","url":"([^"]+)"""").findAll(videosJson)
                .map { it.groupValues[1] to it.groupValues[2] }
                .sortedByDescending { mapQuality(it.first) }
                .toList()

        if (videoLinks.isEmpty()) {
            Log.d(name, "No video links found")
            return
        }

        Log.d(name, "Links found: ${videoLinks.size}")

        videoLinks.forEach { (quality, videoUrl) ->
            val cleanUrl = videoUrl
                .replace("\\u0026", "&")
                .replace("\\/", "/")
            if (!cleanUrl.contains("youtube.com") && !cleanUrl.contains("youtu.be")) {
                invokeLink(quality, cleanUrl, callback)
            }
        }
    }
}

class Odnoklassniki : OkRuExtractor() {
    override val name    = "OkRu"
    override val mainUrl = "https://odnoklassniki.ru"
}