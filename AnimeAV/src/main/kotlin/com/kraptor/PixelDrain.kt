package com.kraptor

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class PixelDrain : ExtractorApi() {
    override val name            = "PixelDrain"
    override val mainUrl         = "https://pixeldrain.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
       val split = url.split("|")
        val mId = Regex("/u/(.*)").find(split[0])?.groupValues?.get(1)
        if (mId.isNullOrEmpty())
        {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    split[0]
                ) {
                    this.referer = split[0]
                }
            )
        }
        else {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name + " ${split[1]}",
                    "$mainUrl/api/file/${mId}?download",
                ) {
                    this.referer = split[0]
                }
            )
        }
    }
}