//Bu araç @kraptor tarafından | Cs-Karma için yazılmıştır!
package com.kraptor

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*


open class VAlbrqExtractor : VidSpeedExtractor() {
    override val name = "Albrq"
    override val mainUrl = "https://v.albrq.cc"
    override val requiresReferer = true

}

open class VidSpeed : VidSpeedExtractor() {
    override val name = "VidSpeed"
    override val mainUrl = "https://vidspeeds.com"
    override val requiresReferer = true

}


open class VidSpeedCC : VidSpeedExtractor() {
    override val name = "VidSpeed"
    override val mainUrl = "https://vidspeed.cc"
    override val requiresReferer = true

}



open class VidObaExtractor : VidSpeedExtractor() {
    override val name = "VidOba"
    override val mainUrl = "https://w.vidoba.site"
    override val requiresReferer = true

}

open class VidObaCCExtractor : VidSpeedExtractor() {
    override val name = "VidOba"
    override val mainUrl = "https://vidoba.cc"
    override val requiresReferer = true

}


open class VDeskExtractor : VidSpeedExtractor() {
    override val name = "VDesk"
    override val mainUrl = "https://vdesk.live"
    override val requiresReferer = true

}

open class VidSpeedExtractor : ExtractorApi() {
    override val name = "VidSpeed"
    override val mainUrl = "https://w.vidspeed.store"
    override val requiresReferer = true


    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = referer).document

        val script = doc.selectFirst("script:containsData(eval)")?.data() ?: ""

        val unpack = getAndUnpack(script)

        val regex = Regex(pattern = "file:\"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE))

        val video = regex.find(unpack)?.groupValues[1] ?: ""

        callback.invoke(newExtractorLink(
            this.name,
            this.name,
            video,
            INFER_TYPE,
            {
                this.referer = url
            }
        ))
    }
}