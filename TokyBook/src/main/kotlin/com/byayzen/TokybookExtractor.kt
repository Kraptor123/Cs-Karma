package com.byayzen

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

open class TokybookExtractor : ExtractorApi() {
    override val name = "Tokybook"
    override val mainUrl = "https://tokybook.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val parts = url.split("|")
        if (parts.size < 3) return

        val fullIdString = parts[0].substringAfterLast("/")
        val audiobookId = fullIdString.substringBefore("-")
        val streamToken = parts[1]
        val trackPath = parts[2]

        val encodedPath = trackPath.replace(" ", "%20")
        val m3u8Url = "https://tokybook.com/api/v1/public/audio/$encodedPath"
        val m3u8PathHeader = "/api/v1/public/audio/$encodedPath"

        val baseHeaders = mapOf(
            "x-audiobook-id" to audiobookId,
            "x-stream-token" to streamToken,
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0",
            "Accept" to "*/*",
            "Accept-Language" to "tr-TR,tr;q=0.8,en-US;q=0.5,en;q=0.3",
            "Origin" to "https://tokybook.com",
            "Referer" to "https://tokybook.com/",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin"
        )

        val m3u8Content = try {
            app.get(
                m3u8Url,
                headers = baseHeaders + mapOf("x-track-src" to m3u8PathHeader)
            ).text
        } catch (e: Exception) {
            return
        }

        val baseUrl = m3u8Url.substringBeforeLast("/") + "/"
        val basePathHeader = m3u8PathHeader.substringBeforeLast("/") + "/"

        m3u8Content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val tsUrl = "$baseUrl$trimmed"
                val tsHeaderPath = "$basePathHeader$trimmed"

                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = tsUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://tokybook.com/"
                        this.quality = Qualities.P1080.value
                        this.headers = baseHeaders + mapOf("x-track-src" to tsHeaderPath)
                    }
                )
            }
        }
    }
}