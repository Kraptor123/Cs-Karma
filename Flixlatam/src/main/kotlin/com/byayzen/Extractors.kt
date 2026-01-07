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
import com.lagradost.cloudstream3.network.WebViewResolver
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

class Uqloadnet : Uqload() {
    override var mainUrl = "https://uqload.net"
}

open class Uqload : ExtractorApi() {
    override val name: String = "Uqload"
    override val mainUrl: String = "https://www.uqload.com"
    private val srcRegex = Regex("""sources:.\[(.*?)\]""")
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url)
        srcRegex.find(response.text)?.groupValues?.get(1)?.replace("\"", "")?.let { link ->
            return listOf(
                newExtractorLink(
                    name,
                    name,
                    link
                ) {
                    this.referer = url
                }
            )
        }
        return null
    }
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
open class StreamWishExtractor : ExtractorApi() {
    override val name = "Streamwish" // İstenen isim
    override val mainUrl = "https://streamwish.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Referer" to "$mainUrl/",
            "Origin" to "$mainUrl/",
            "User-Agent" to USER_AGENT
        )

        val pageResponse = app.get(resolveEmbedUrl(url), referer = referer)

        val playerScriptData = when {
            !getPacked(pageResponse.text).isNullOrEmpty() -> getAndUnpack(pageResponse.text)
            pageResponse.document.select("script").any { it.html().contains("jwplayer(\"vplayer\").setup(") } ->
                pageResponse.document.select("script").firstOrNull {
                    it.html().contains("jwplayer(\"vplayer\").setup(")
                }?.html()
            else -> pageResponse.document.selectFirst("script:containsData(sources:)")?.data()
        }

        val directStreamUrl = playerScriptData?.let {
            Regex("""file:\s*"(.*?m3u8.*?)"""").find(it)?.groupValues?.getOrNull(1)
        }

        if (!directStreamUrl.isNullOrEmpty()) {
            M3u8Helper.generateM3u8(
                name,
                directStreamUrl,
                mainUrl,
                headers = headers
            ).forEach(callback)
        } else {
            val webViewM3u8Resolver = WebViewResolver(
                interceptUrl = Regex("""txt|m3u8"""),
                additionalUrls = listOf(Regex("""txt|m3u8""")),
                useOkhttp = false,
                timeout = 15_000L
            )

            val interceptedStreamUrl = app.get(
                url,
                referer = referer,
                interceptor = webViewM3u8Resolver
            ).url

            if (interceptedStreamUrl.isNotEmpty()) {
                M3u8Helper.generateM3u8(
                    name,
                    interceptedStreamUrl,
                    mainUrl,
                    headers = headers
                ).forEach(callback)
            } else {
                Log.d("StreamwishExtractor", "No m3u8 found in fallback either.")
            }
        }
    }

    private fun resolveEmbedUrl(inputUrl: String): String {
        return if (inputUrl.contains("/f/")) {
            val videoId = inputUrl.substringAfter("/f/")
            "$mainUrl/$videoId"
        } else {
            inputUrl
        }
    }
}
class Mwish : StreamWishExtractor() {
    override val mainUrl = "https://mwish.pro"
}
class HgLink : StreamWishExtractor() {
    override val mainUrl = "https://hglink.to"
}
class Dwish : StreamWishExtractor() {
    override val mainUrl = "https://dwish.pro"
}
class Ewish : StreamWishExtractor() {
    override val mainUrl = "https://embedwish.com"
}
class WishembedPro : StreamWishExtractor() {
    override val mainUrl = "https://wishembed.pro"
}
class Kswplayer : StreamWishExtractor() {
    override val mainUrl = "https://kswplayer.info"
}
class Wishfast : StreamWishExtractor() {
    override val mainUrl = "https://wishfast.top"
}
class SfastwishCom : StreamWishExtractor() {
    override val mainUrl = "https://sfastwish.com"
}
class Strwish : StreamWishExtractor() {
    override val mainUrl = "https://strwish.xyz"
}
class Strwish2 : StreamWishExtractor() {
    override val mainUrl = "https://strwish.com"
}
class FlaswishCom : StreamWishExtractor() {
    override val mainUrl = "https://flaswish.com"
}
class Awish : StreamWishExtractor() {
    override val mainUrl = "https://awish.pro"
}
class Obeywish : StreamWishExtractor() {
    override val mainUrl = "https://obeywish.com"
}
class Jodwish : StreamWishExtractor() {
    override val mainUrl = "https://jodwish.com"
}
class Swhoi : StreamWishExtractor() {
    override val mainUrl = "https://swhoi.com"
}
class Multimovies : StreamWishExtractor() {
    override val mainUrl = "https://multimovies.cloud"
}
class UqloadsXyz : StreamWishExtractor() {
    override val mainUrl = "https://uqloads.xyz"
}
class Doodporn : StreamWishExtractor() {
    override val mainUrl = "https://doodporn.xyz"
}
class CdnwishCom : StreamWishExtractor() {
    override val mainUrl = "https://cdnwish.com"
}
class Asnwish : StreamWishExtractor() {
    override val mainUrl = "https://asnwish.com"
}
class Nekowish : StreamWishExtractor() {
    override val mainUrl = "https://nekowish.my.id"
}
class Nekostream : StreamWishExtractor() {
    override val mainUrl = "https://neko-stream.click"
}
class Swdyu : StreamWishExtractor() {
    override val mainUrl = "https://swdyu.com"
}
class Wishonly : StreamWishExtractor() {
    override val mainUrl = "https://wishonly.site"
}
class Playerwish : StreamWishExtractor() {
    override val mainUrl = "https://playerwish.com"
}


open class FilemoonV2 : ExtractorApi() {
    override var name = "Filemoon" // İstenen isim
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

