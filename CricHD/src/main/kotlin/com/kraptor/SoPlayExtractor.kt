//Bu araç @kraptor tarafından | Cs-Karma için yazılmıştır!

package com.kraptor

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class SoPlayExtractor : ExtractorApi() {
    override val name = "CricHD"
    override val mainUrl = "https://crichd.one"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit
    ) {
        val url = url.replace(mainUrl, "https://soplay.pro").replace("stream", "embed")
        val document = app.get(
            url, referer = referer, allowRedirects = false, headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
                "Referer" to "$mainUrl/",
            )
        ).text

        val vCon = Regex("v_con=[\"'](.*?)[\"']").find(document)?.groupValues?.get(1) ?: ""
        val vDt = Regex("v_dt=[\"'](.*?)[\"']").find(document)?.groupValues?.get(1) ?: ""

        val fId =
            Regex(pattern = "fid=\"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE)).find(document)?.groupValues[1]

        val targetUrl = "https://bhalocast.com/player.php?v=$fId&secure=$vCon&expires=$vDt"

        val html = app.get(targetUrl, headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
            "Referer" to url,
            "Sec-Fetch-Dest" to "iframe",
        )).text

        val videoAyiran =
            html.substringAfter("return ([")
                .substringBefore("}")
                .substringBefore("]")


        val birlestir = videoAyiran.split(",").joinToString("", "").replace("\"","").replace("\\","")

        callback.invoke(
            newExtractorLink(
                name = this.name,
                source = this.name,
                url = birlestir,
                type = ExtractorLinkType.M3U8,
                initializer = {
                    this.referer = "https://bhalocast.com/"
                })
        )
    }
}