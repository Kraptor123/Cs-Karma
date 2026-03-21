package com.byayzen

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.JsUnpacker

open class FilemoonV2 : ExtractorApi() {
    override var name = "Filemoon"
    override var mainUrl = "https://filemoon.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val defaultHeaders = mapOf(
            "Referer" to url,
            "Sec-Fetch-Dest" to "iframe",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "cross-site",
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:137.0) Gecko/20100101 Firefox/137.0"
        )

        val initialResponse = app.get(url, defaultHeaders)
        val iframeSrcUrl = initialResponse.document.selectFirst("iframe")?.attr("src")

        if (iframeSrcUrl.isNullOrEmpty()) {
            val fallbackScriptData = initialResponse.document
                .selectFirst("script:containsData(function(p,a,c,k,e,d))")
                ?.data().orEmpty()
            val unpackedScript = JsUnpacker(fallbackScriptData).unpack()

            val videoUrl = unpackedScript?.let {
                Regex("""sources:\[\{file:"(.*?)"""").find(it)?.groupValues?.get(1)
            }

            if (!videoUrl.isNullOrEmpty()) {
                M3u8Helper.generateM3u8(
                    name,
                    videoUrl,
                    mainUrl,
                    headers = defaultHeaders
                ).forEach(callback)
            }
            return
        }

        val iframeHeaders = defaultHeaders + ("Accept-Language" to "en-US,en;q=0.5")
        val iframeResponse = app.get(iframeSrcUrl, headers = iframeHeaders)

        val iframeScriptData = iframeResponse.document
            .selectFirst("script:containsData(function(p,a,c,k,e,d))")
            ?.data().orEmpty()

        val unpackedScript = JsUnpacker(iframeScriptData).unpack()

        val videoUrl = unpackedScript?.let {
            Regex("""sources:\[\{file:"(.*?)"""").find(it)?.groupValues?.get(1)
        }

        if (!videoUrl.isNullOrEmpty()) {
            M3u8Helper.generateM3u8(
                name,
                videoUrl,
                mainUrl,
                headers = defaultHeaders
            ).forEach(callback)
        } else {
            val resolver = WebViewResolver(
                interceptUrl = Regex("""(m3u8|master\.txt)"""),
                additionalUrls = listOf(Regex("""(m3u8|master\.txt)""")),
                useOkhttp = false,
                timeout = 15_000L
            )

            val interceptedUrl = app.get(
                iframeSrcUrl,
                referer = referer,
                interceptor = resolver
            ).url

            if (interceptedUrl.isNotEmpty()) {
                M3u8Helper.generateM3u8(
                    name,
                    interceptedUrl,
                    mainUrl,
                    headers = defaultHeaders
                ).forEach(callback)
            }
        }
    }
}

class FileMoon2 : FilemoonV2() {
    override var mainUrl = "https://filemoon.to"
}
class FileMoonIn : FilemoonV2() {
    override var mainUrl = "https://filemoon.in"
}
class FileMoonSx : FilemoonV2() {
    override var mainUrl = "https://filemoon.sx"
}
class Bysedikamoum : FilemoonV2() {
    override var mainUrl = "https://bysedikamoum.com"
}
class F75 : FilemoonV2() {
    override var mainUrl = "https://f75s.com"
}

