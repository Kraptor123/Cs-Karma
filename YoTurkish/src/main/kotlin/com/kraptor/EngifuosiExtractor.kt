//Bu araç @kraptor tarafından | Cs-Karma için yazılmıştır!

package com.kraptor

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class EngifuosiExtractor : ExtractorApi() {
    override val name = "Engifuosi"
    override val mainUrl = "https://engifuosi.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val text = app.get(url, referer = referer).text

        val evalCikar = getAndUnpack(text)

        val video = Regex(pattern = "file:\"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE)).find(evalCikar)?.groupValues[1] ?: ""

        callback.invoke(
            newExtractorLink(
            name = this.name,
            source = this.name,
            url = video,
            type = ExtractorLinkType.M3U8, /* ExtractorLinkType.M3U8, ExtractorLinkType.VIDEO*/
            initializer = {
                this.referer = "${mainUrl}/"
            }
        ))
    }
}