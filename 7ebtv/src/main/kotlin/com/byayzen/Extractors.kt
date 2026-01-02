package com.byayzen

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
class LulusStream : ExtractorApi() {
    override val  name = "LuluStream"
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

//CS'nin extractoründen alındı ve sadece mainUrl luluvid'e dönüştürüldü.


class Uqloadnet : Uqload() {
    override var mainUrl = "https://uqload.net"
}

open class Uqload : ExtractorApi() {
    override val name: String = "Uqload"
    override val mainUrl: String = "https://www.uqload.com"
    private val srcRegex = Regex("""sources:.\[(.*?)\]""")  // would be possible to use the parse and find src attribute
    override val requiresReferer = true


    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(app.get(url)) {  // raised error ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED (3003) is due to the response: "error_nofile"
            srcRegex.find(this.text)?.groupValues?.get(1)?.replace("\"", "")?.let { link ->
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
        }
        return null
    }
}

class VidHidePro1 : VidHidePro() {
    override var mainUrl = "https://filelions.live"
}

class Dhcplay: VidHidePro() {
    override var name = "DHC Play"
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

class VidHidePro5: VidHidePro() {
    override val mainUrl = "https://vidhidevip.com"
}

class VidHidePro6 : VidHidePro() {
    override val mainUrl = "https://vidhidepre.com"
}

class Smoothpre: VidHidePro() {
    override var name = "EarnVids"
    override var mainUrl = "https://smoothpre.com"
}

class Dhtpre: VidHidePro() {
    override var name = "EarnVids"
    override var mainUrl = "https://dhtpre.com"
}

class Peytonepre : VidHidePro() {
    override var name = "EarnVids"
    override var mainUrl = "https://peytonepre.com"
}
class Movearnpre : VidHidePro() {
    override var name = "EarnVids"
    override var mainUrl = "https://movearnpre.com"
}
class Dintezuvio : VidHidePro() {
    override var name = "EarnVids"
    override var mainUrl = "https://dintezuvio.com"
}

open class VidHidePro : ExtractorApi() {
    override val name = "VidHidePro"
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
            if(result.contains("var links")){
                result = result.substringAfter("var links")
            }
            result
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        } ?: return

        // m3u8 urls could be prefixed by 'file:', 'hls2:' or 'hls4:', so we just match ':'
        Regex(":\\s*\"(.*?m3u8.*?)\"").findAll(script).forEach { m3u8Match ->
            generateM3u8(
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