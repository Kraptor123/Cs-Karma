package com.byayzen

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.api.Log
import com.lagradost.cloudstream3.extractors.Streamwish2
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.JsUnpacker

class LulusStream : ExtractorApi() {
    override val name = "LuluStream"
    override val mainUrl = "https://luluvid.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val filecode = url.substringAfterLast("/")
        val postUrl = "$mainUrl/dl"
        val post = app.post(
            postUrl,
            data = mapOf(
                "op" to "embed",
                "file_code" to filecode,
                "auto" to "1",
                "referer" to (referer ?: "")
            )
        ).document

        post.selectFirst("script:containsData(vplayer)")?.data()
            ?.let { script ->
                Regex("file:\"(.*)\"").find(script)?.groupValues?.get(1)?.let { link ->
                    callback(
                        newExtractorLink(
                            name,
                            name,
                            link,
                        ) {
                            this.referer = mainUrl
                            this.quality = Qualities.P1080.value
                        }
                    )
                }
            }
    }
}

open class Uqload : ExtractorApi() {
    override var name = "Uqload"
    override var mainUrl = "https://uqload.is"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val embedResponse = app.get(
                url,
                referer = referer ?: mainUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:151.0) Gecko/20100101 Firefox/151.0",
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
                    "Upgrade-Insecure-Requests" to "1"
                )
            )

            val responseText = embedResponse.text
            val cookies = embedResponse.cookies
            val cookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            val unpacked = getAndUnpack(responseText)
            val m3u8Urls = Regex("""https?://[^\s"'<>\\]+\.m3u8[^\s"'<>\\]*""")
                .findAll(unpacked).map { it.value }.toList()
            val mp4Urls = Regex("""https?://[^\s"'<>\\]+\.mp4[^\s"'<>\\]*""")
                .findAll(unpacked).map { it.value }.toList()

            val videoUrl = m3u8Urls.firstOrNull() ?: mp4Urls.firstOrNull() ?: return

            val streamHeaders = buildMap {
                put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:151.0) Gecko/20100101 Firefox/151.0")
                put("Accept", "*/*")
                put("Origin", mainUrl)
                put("Referer", "$mainUrl/")
                if (cookieHeader.isNotEmpty()) put("Cookie", cookieHeader)
            }

            val isM3u8 = videoUrl.contains(".m3u8")
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = videoUrl,
                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = if (isM3u8) Qualities.Unknown.value else 1080
                    this.headers = streamHeaders
                }
            )
        } catch (e: Exception) {
        }
    }
}

class UqloadIo : Uqload() {
    override var mainUrl = "https://uqload.io"
}

class Uqloadcx : Uqload() {
    override var mainUrl = "https://uqload.cx"
}

class Uqloadto : Uqload() {
    override var mainUrl = "https://uqload.to"
}

open class VidHidePro : ExtractorApi() {
    override val name = "Vidhide"
    override val mainUrl = "https://vidhidepro.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT
        )

        val response = app.get(getEmbedUrl(url), referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            var result = getAndUnpack(response.text)
            if (result.contains("var links")) {
                result = result.substringAfter("var links")
            }
            result
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        } ?: return

        Regex(":\\s*\"(.*?m3u8.*?)\"").findAll(script).forEach { m3u8Match ->
            M3u8Helper.generateM3u8(
                name,
                fixUrl(m3u8Match.groupValues[1]),
                referer = "$mainUrl/",
                headers = headers
            ).forEach(callback)
        }
    }

    private fun getEmbedUrl(url: String): String {
        return when {
            url.contains("/d/") -> url.replace("/d/", "/v/")
            url.contains("/download/") -> url.replace("/download/", "/v/")
            url.contains("/file/") -> url.replace("/file/", "/v/")
            else -> url.replace("/f/", "/v/")
        }
    }
}

class VidHidePro1 : VidHidePro() {
    override var mainUrl = "https://filelions.live"
}
class Dhcplay : VidHidePro() {
    override var mainUrl = "https://dhcplay.com"
}
class VidHidePro2 : VidHidePro() {
    override var mainUrl = "https://filelions.online"
}
class VidHidePro3 : VidHidePro() {
    override var mainUrl = "https://filelions.to"
}
class VidHidePro4 : VidHidePro() {
    override val mainUrl = "https://kinoger.be"
}
class VidHidePro7 : VidHidePro() {
    override val mainUrl = "https://vidhidehub.com"
}
class VidHidePro5 : VidHidePro() {
    override val mainUrl = "https://vidhidevip.com"
}
class VidHidePro6 : VidHidePro() {
    override val mainUrl = "https://vidhidepre.com"
}
class Smoothpre : VidHidePro() {
    override var mainUrl = "https://smoothpre.com"
}
class Dhtpre : VidHidePro() {
    override var mainUrl = "https://dhtpre.com"
}
class Peytonepre : VidHidePro() {
    override var mainUrl = "https://peytonepre.com"
}
class Movearnpre : VidHidePro() {
    override var mainUrl = "https://movearnpre.com"
}
class Dintezuvio : VidHidePro() {
    override var mainUrl = "https://dintezuvio.com"
}
class Moorearn : VidHidePro() {
    override var mainUrl = "https://moorearn.com"
}
class Travid : VidHidePro() {
    override var mainUrl = "https://travid.pro"
}
class Vidspeeder : VidHidePro() {
    override var mainUrl = "https://vidspeeder.com"
}

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

