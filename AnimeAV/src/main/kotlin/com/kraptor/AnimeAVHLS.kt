package com.kraptor

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class AnimeAVHLS : ExtractorApi() {
    override var name            = "AnimeAVHLS"
    override var mainUrl         = "https://player.zilla-networks.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val targetUrl = url.replace("/play/", "/m3u8/")
        val customHeaders = mapOf("sec-fetch-site" to "same-origin")

        val response = app.get(
            targetUrl,
            headers = customHeaders
        ).text

        val realM3u8 = Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']""").find(response)?.groupValues?.get(1)
           // ?: Regex("""src:\s*["']([^"']+\.m3u8[^"']*)["']""").find(response)?.groupValues?.get(1)
           // ?: Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""").find(response)?.groupValues?.get(1)

        val finalUrl = realM3u8 ?: targetUrl

        callback.invoke(
            newExtractorLink(
                source  = name,
                name    = name,
                url     = finalUrl,
                type    = ExtractorLinkType.M3U8,
            ) {
                this.quality = Qualities.Unknown.value
                this.headers = customHeaders
            }
        )
    }
}