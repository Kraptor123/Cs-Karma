package com.byayzen

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.*
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import android.util.Base64
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.extractors.FilemoonV2
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


class Ralphy : Voe() { override var mainUrl = "https://ralphysuccessfull.org" }

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
        val response = app.get(url, referer = referer ?: mainUrl).text
        val videoRegex = """sources:\s*\[\s*"([^"]+)"""".toRegex()
        val matchResult = videoRegex.find(response)

        if (matchResult != null) {
            val videoUrl = matchResult.groupValues[1]
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = videoUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.quality = 1080
                    this.referer = mainUrl
                    this.headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:149.0) Gecko/20100101 Firefox/149.0",
                        "Accept" to "video/webm,video/ogg,video/*;q=0.9,application/ogg;q=0.7,audio/*;q=0.6,*/*;q=0.5",
                        "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
                        "Connection" to "keep-alive",
                        "Sec-Fetch-Dest" to "video",
                        "Sec-Fetch-Mode" to "no-cors",
                        "Sec-Fetch-Site" to "same-site"
                    )
                }
            )
        }
    }
}

class UqloadIo : Uqload() {
    override var mainUrl = "https://uqload.io"
}
class Uqloadcx : Uqload() {
    override var mainUrl = "https://uqload.cx"
}

open class DoodStream : ExtractorApi() {
    override var name = "DoodStream"
    override var mainUrl = "https://myvidplay.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = url.replace("doply.net", "myvidplay.com")
        val response = app.get(
            embedUrl,
            referer = mainUrl,
            headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0")
        ).text

        val md5Regex = Regex("/pass_md5/([^/]*)/([^/']*)")
        val md5Match = md5Regex.find(response)
        val md5Path = md5Match?.value.toString()
        val expiry = md5Match?.groupValues?.getOrNull(1) ?: ""
        val token = md5Match?.groupValues?.getOrNull(2) ?: ""
        val md5Url = mainUrl + md5Path

        val md5Response = app.get(
            md5Url,
            referer = embedUrl,
            headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0")
        ).text

        val baseLink = md5Response.trim()
        val directLink = if (token.isNotEmpty() && expiry.isNotEmpty()) {
            "$baseLink?token=$token&expiry=${expiry}000"
        } else {
            baseLink
        }

        callback.invoke(
            newExtractorLink(
                source = this.name, name = this.name, url = directLink, type = INFER_TYPE
            ) {
                this.referer = "https://myvidplay.com"
                this.quality = Qualities.Unknown.value
                this.headers =
                    mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0")
            })
    }
}

class Vide0Net : DoodStream() {
    override var mainUrl = "https://vide0.net"
}

class DoodDoply : DoodStream() {
    override var mainUrl = "https://doply.net"
}

class GhBrisk : StreamWishExtractor() {
    override var mainUrl = "https://ghbrisk.com"
    override var name = "StreamWish"
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
            if (result.contains("var links")) {
                result = result.substringAfter("var links")
            }
            result
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        } ?: return

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

class RyderJet : VidHidePro() { override var mainUrl = "https://ryderjet.com" }
class LuluVdo : VidHidePro() { override var mainUrl = "https://luluvdo.com" }
class VtbeTo : VidHidePro() { override var mainUrl = "https://vtbe.to" }
class SaveFiles : VidHidePro() { override var mainUrl = "https://savefiles.com" }
class DhcPlay : VidHidePro() { override var mainUrl = "https://dhcplay.com" }
class FileLionsLive : VidHidePro() { override var mainUrl = "https://filelions.live" }
class FileLionsOnline : VidHidePro() { override var mainUrl = "https://filelions.online" }
class FileLionsTo : VidHidePro() { override var mainUrl = "https://filelions.to" }
class KinogerBe : VidHidePro() { override val mainUrl = "https://kinoger.be" }
class VidHideHub : VidHidePro() { override val mainUrl = "https://vidhidehub.com" }
class VidHideVip : VidHidePro() { override val mainUrl = "https://vidhidevip.com" }
class VidHidePre : VidHidePro() { override val mainUrl = "https://vidhidepre.com" }
class SmoothPre : VidHidePro() { override var mainUrl = "https://smoothpre.com" }
class DhtPre : VidHidePro() { override var mainUrl = "https://dhtpre.com" }
class PeytonePre : VidHidePro() { override var mainUrl = "https://peytonepre.com" }
class MovearnPre : VidHidePro() { override var mainUrl = "https://movearnpre.com" }
class Dintezuvio : VidHidePro() { override var mainUrl = "https://dintezuvio.com" }

open class Vidzy : ExtractorApi() {
    override val name = "Vidzy"
    override val mainUrl = "https://vidzy.live"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
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

        Log.d("Vidzy", "$script")
        Regex("""src\s*:\s*"([^"]+m3u8[^"]*)"""").findAll(script).forEach { m3u8Match ->
            val m3u8Url = fixUrl(m3u8Match.groupValues[1])
            Log.d("Vidzy", "Extracted M3U8: $m3u8Url")

            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = m3u8Url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.Unknown.value
                    this.headers = mapOf(
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Site" to "cross-site",
                        "Origin" to mainUrl,
                        "User-Agent" to USER_AGENT
                    )
                }
            )
        }
    }

    private fun getEmbedUrl(url: String): String {
        return when {
            url.contains("/d/") -> url.replace("/d/", "/v/")
            url.contains("/download/") -> url.replace("/download/", "/v/")
            url.contains("/file/") -> url.replace("/file/", "/v/")
            url.contains("/embed-") -> url
            else -> url.replace("/f/", "/v/")
        }
    }
}

open class VeevToExtractor : ExtractorApi() {

    override val name = "VeevTo"
    override val mainUrl = "https://veev.to"
    override val requiresReferer = false

    private fun jsInt(x: String): Int = x.toIntOrNull() ?: 0

    private fun veevDecode(encoded: String): String {
        return try {
            val result = mutableListOf<String>()
            val lut = mutableMapOf<Int, String>()
            var n = 256
            var c = encoded[0].toString()
            result.add(c)

            for (char in encoded.substring(1)) {
                val code = char.code
                val nc = if (code < 256) char.toString() else lut[code] ?: (c + c[0])
                result.add(nc)
                lut[n] = c + nc[0]
                n++
                c = nc
            }
            result.joinToString("")
        } catch (e: Exception) {
            encoded
        }
    }

    private fun buildArray(encoded: String): List<List<Int>> {
        val d = mutableListOf<List<Int>>()
        val chars = encoded.toCharArray().toMutableList()
        if (chars.isEmpty()) return d

        var count = jsInt(chars.removeAt(0).toString())
        while (count > 0) {
            val currentArray = mutableListOf<Int>()
            for (i in 0 until count) {
                if (chars.isEmpty()) break
                currentArray.add(0, jsInt(chars.removeAt(0).toString()))
            }
            d.add(currentArray)
            if (chars.isEmpty()) break
            count = jsInt(chars.removeAt(0).toString())
        }
        return d
    }

    private fun hexToString(hex: String): String {
        val cleanHex = hex.trim()
        val paddedHex = if (cleanHex.length % 2 != 0) "0$cleanHex" else cleanHex
        val bytes = paddedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun decodeUrl(encoded: String, tArray: List<Int>): String {
        var ds = encoded
        for (t in tArray) {
            if (t == 1) ds = ds.reversed()
            ds = hexToString(ds).replace("dXRmOA==", "")
        }
        return ds
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val initialResponse = app.get(url, referer = "$mainUrl/")
            val pageHtml = initialResponse.text
            val mediaId = initialResponse.url.split("/").lastOrNull { it.isNotEmpty() } ?: url.split("/").lastOrNull { it.isNotEmpty() } ?: ""

            val regex = Regex("""[\.\s'](?:fc|_vvto\[[^\]]*)(?:['\]]*)?\s*[:=]\s*['"]([^'"]+)""")
            val encodedStrings = regex.findAll(pageHtml).map { it.groupValues[1] }.toList()

            var ch: String? = null
            for (f in encodedStrings.reversed()) {
                val decoded = veevDecode(f)
                if (decoded != f) {
                    ch = decoded
                    break
                }
            }
            if (ch == null) return

            val tArrays = buildArray(ch)
            if (tArrays.isEmpty()) return

            val apiUrl = "$mainUrl/dl?op=player_api&cmd=gi&file_code=${URLEncoder.encode(mediaId, "UTF-8")}&ch=${URLEncoder.encode(ch, "UTF-8")}&ie=1"
            val jsonResponse = JSONObject(app.get(apiUrl, referer = url).text)

            if (jsonResponse.optString("status") != "success") return
            val fileObj = jsonResponse.optJSONObject("file") ?: return
            if (fileObj.optString("file_status") != "OK") return

            val dvArray = fileObj.optJSONArray("dv") ?: return
            val tArray = tArrays[0]

            for (i in 0 until dvArray.length()) {
                val source = dvArray.getJSONObject(i)
                val encodedUrl = source.optString("s")
                if (encodedUrl.isNotEmpty()) {
                    val finalUrl = decodeUrl(veevDecode(encodedUrl), tArray)
                    if (finalUrl.startsWith("http")) {
                        Log.d(name, finalUrl)
                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = name,
                                url = finalUrl,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                this.referer = "$mainUrl/"
                            }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(name, "${e.message}")
        }
    }
}


class GoodStream : ExtractorApi() {
    override var name = "GoodStream"
    override var mainUrl = "https://goodstream.one"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val code = url.substringAfterLast("/")
        val post = "$mainUrl/dl"
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:150.0) Gecko/20100101 Firefox/150.0"
        val lang = "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7"
        val res = app.post(
            post,
            data = mapOf(
                "op" to "embed",
                "file_code" to code,
                "auto" to "1",
                "referer" to ""
            ),
            headers = mapOf(
                "Referer" to url,
                "User-Agent" to ua,
                "Accept-Language" to lang
            )
        )

        val src = Regex("""file\s*:\s*"(.*?)"""").find(res.text)?.groupValues?.get(1) ?: return
        callback(
            newExtractorLink(
                source = name,
                name = name,
                url = src,
                type = ExtractorLinkType.M3U8
            ) {
                this.headers = mapOf(
                    "Referer" to "$mainUrl/",
                    "Origin" to mainUrl,
                    "User-Agent" to ua,
                    "Accept-Language" to lang,
                )
            }
        )
    }
}



class Coflix : VidStack() { override var mainUrl = "https://coflix.upn.one" }

class Embedseek : VidStack() { override var mainUrl = "https://movix1.embedseek.com" }

class Lukefirst : FilemoonV2() { override var mainUrl = "https://lukefirst.lol" }

class Bysebuho : FilemoonV2() { override var mainUrl = "https://bysebuho.com" }

