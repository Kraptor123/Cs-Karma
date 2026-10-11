// ! Bu araç @kerimmkirac tarafından | @CS-Karma için yazılmıştır!

package com.kraptor

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AnimeAVHLS : ExtractorApi() {
    override var name            = "AnimeAVHLS"
    override var mainUrl         = "https://player.zilla-networks.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val targetUrl = url.replace("/play/", "/m3u8/")
        val customHeaders = mapOf("sec-fetch-site" to "same-origin")

        val response = app.get(
            targetUrl,
            headers = customHeaders
        ).text

        val realM3u8 = Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']""").find(response)?.groupValues?.get(1)
           // ?: Regex("""src:\s*["']([^"']+\.m3u8[^"']*)["']""").find(response)?.groupValues?.get(1)
           // ?: Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""").find(response)?.groupValues?.get(1)

        val finalUrl = realM3u8 ?: targetUrl

        callback.invoke(
            newExtractorLink(
                source  = name,
                name    = name,
                url     = finalUrl,
                type    = ExtractorLinkType.M3U8,
            ) {
                this.quality = Qualities.Unknown.value
                this.headers = customHeaders
            }
        )
    }
}

open class Mp4Upload : ExtractorApi() {
    override var name            = "Mp4Upload"
    override var mainUrl         = "https://www.mp4upload.com"
    override val requiresReferer = true

    private val srcRegex         = Regex("""player\.src\("(.*?)"""")
    private val srcRegex2        = Regex("""player\.src\([\w\W]*src: "(.*?)"""")
    private val idMatch          = Regex("""mp4upload\.com/(embed-|)([A-Za-z0-9]*)""")

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val id      = idMatch.find(url)?.groupValues?.get(2)
        val realUrl = if (id != null) "$mainUrl/embed-$id.html" else url

        Log.d("Mp4Upload", "URL: $realUrl")

        val response     = app.get(realUrl)
        val unpackedText = getAndUnpack(response.text)
        val quality      = unpackedText.lowercase().substringAfter(" height=").substringBefore(" ").toIntOrNull() ?: Qualities.Unknown.value

        val videoUrl     = srcRegex.find(unpackedText)?.groupValues?.get(1)
            ?: srcRegex2.find(unpackedText)?.groupValues?.get(1)

        if (videoUrl.isNullOrBlank()) {
            Log.d("Mp4Upload", "Video bulunamadı")
            return null
        }

        Log.d("Mp4Upload", "Video bağlantısı: $videoUrl | Kalite: $quality")

        return listOf(
            newExtractorLink(
                source = name,
                name   = this.name,
                url    = videoUrl,
            ) {
                this.referer = url
                this.quality = quality
            }
        )
    }
}

class AnimeavUPNS : ExtractorApi() {
    override val name = "AnimeAVUpns"
    override val mainUrl = "https://animeav1.uns.bio"
    override val requiresReferer = true

    private val TAG = "DoramasExtractor"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers =
            mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0")
        val hash = url.substringAfterLast("#").substringAfter("/")
        val baseurl = getBaseUrl(url)

        val encoded = app.get("$baseurl/api/v1/video?id=$hash", headers = headers).text.trim()

        val key = "kiemtienmua911ca"
        val ivList = listOf("1234567890oiuytr", "0123456789abcdef")

        val decryptedText = ivList.firstNotNullOfOrNull { iv ->
            try {
                AesHelper.decryptAES(encoded, key, iv)
            } catch (e: Exception) {
                null
            }
        } ?: throw Exception("Failed to decrypt with all IVs")

        val m3u8 = Regex("\"source\":\"(.*?)\"").find(decryptedText)
            ?.groupValues?.get(1)
            ?.replace("\\/", "/") ?: ""

        callback.invoke(
                    newExtractorLink(
                        this.name,
                        this.name,
                        m3u8,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = url
                        this.quality = quality
                    }
                )
            }

    private fun getBaseUrl(url: String): String {
        return try {
            URI(url).let { "${it.scheme}://${it.host}" }
        } catch (e: Exception) {
            mainUrl
        }
    }
}

object AesHelper {
    private const val TRANSFORMATION = "AES/CBC/PKCS5PADDING"

    fun decryptAES(inputHex: String, key: String, iv: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(iv.toByteArray(Charsets.UTF_8))

        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        val decryptedBytes = cipher.doFinal(inputHex.hexToByteArray())
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Hex string must have an even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
