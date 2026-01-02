package com.byayzen

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class DhcPlayExtractor : ExtractorApi() {
    override val name = "DhcPlay"
    override val mainUrl = "https://dhcplay.com"

    override val requiresReferer = true


    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val link = url.replace("dhcplay","uasopt")

        val document = app.get(link, headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.5",
            "Sec-GPC" to "1",
            "Connection" to "keep-alive",
            "Referer" to "${mainUrl}/",
            "Upgrade-Insecure-Requests" to "1",
            "Sec-Fetch-Dest" to "iframe",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "cross-site",
            "Priority" to "u=4",
            "Pragma" to "no-cache",
            "Cache-Control" to "no-cache",
            "TE" to "trailers"
        )).document

        val script = document.selectFirst("script:containsData(eval)")?.data().toString()

        val unpack = getAndUnpack(script)

        val regex = Regex(pattern = "\"hls[0-9]+\":\"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE))

        val hls = regex.findAll(unpack)

        hls.forEach { m3u8 ->
            callback.invoke(newExtractorLink(
                this.name,
                this.name,
                m3u8.groupValues[1],
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = link
            })
        }
    }
}