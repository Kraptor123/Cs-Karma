package com.kraptor

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.newExtractorLink

open class PlayCdnExtractor : ExtractorApi() {
    override val name    = "PlayCDN"
    override val mainUrl = "https://playcdn.de"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id        = url.substringAfterLast("/").substringBefore("?")
        val pageUrl   = "$mainUrl/$id"
        val refUrl    = referer ?: "https://videonode.de/"

        app.get(pageUrl, referer = refUrl)

        val verifyUrl = "$mainUrl/verify/$id"
        val response  = app.get(verifyUrl, referer = pageUrl).parsedSafe<VerifyResponse>()
        val m3u8Url   = response?.fileUrl ?: return

        val links = M3u8Helper.generateM3u8(
            name,
            m3u8Url,
            referer = "$mainUrl/"
        )

        if (links.isEmpty()) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name   = name,
                    url    = m3u8Url,
                    type   = ExtractorLinkType.M3U8,
                    initializer = {
                        this.referer = "$mainUrl/"
                    }
                )
            )
        } else {
            links.forEach(callback)
        }
    }

    private data class VerifyResponse(
        @JsonProperty("status") val status: String?   = null,
        @JsonProperty("title") val title: String?     = null,
        @JsonProperty("poster") val poster: String?   = null,
        @JsonProperty("fileUrl") val fileUrl: String? = null
    )
}
