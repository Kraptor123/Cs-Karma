package com.byayzen

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class FilmatekExtractor : ExtractorApi() {
    override val name = "Filmatek"
    override val mainUrl = "https://filmatek.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url, referer = referer).text.replace("\\/", "/")
        val link = Regex(""""file"\s*:\s*"([^"]+)"""").find(res)?.groupValues?.get(1) ?: return

        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                link,
                if (link.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }
}