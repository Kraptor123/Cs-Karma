package com.byayzen

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class PixeldrainExt : ExtractorApi() {
    override var name = "Pixeldrain"
    override var mainUrl = "https://pixeldrain.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.trim().substringAfterLast("/").trim()
        val videoUrl = "https://pixeldrain.com/api/file/$id?download"

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = videoUrl.trim(),
                type = ExtractorLinkType.VIDEO
            )
        )
    }
}