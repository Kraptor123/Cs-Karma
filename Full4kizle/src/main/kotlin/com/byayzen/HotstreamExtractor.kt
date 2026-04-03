package com.byayzen

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import android.util.Base64
import com.fasterxml.jackson.annotation.JsonProperty
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class HotstreamExtractor : ExtractorApi() {
    override val name = "HotStream"
    override val mainUrl = "https://hotstream.club"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:148.0) Gecko/20100101 Firefox/148.0",
            "Referer" to (referer ?: "https://dizifilm.org/"),
            "Sec-Fetch-Dest" to "iframe",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "cross-site"
        )).text

        val match = Regex("""bePlayer\s*\(\s*'([^']+)'\s*,\s*'([^']+)'""").find(response) ?: return

        try {
            val password = match.groupValues[1]
            val encryptedJsonRaw = match.groupValues[2].replace("\\/", "/")

            val jsData = AppUtils.parseJson<JsData>(encryptedJsonRaw)
            val decryptedJson = decryptAes(jsData, password)
            val videoLocation = AppUtils.parseJson<DecryptedLocation>(decryptedJson).videoLocation

            callback(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = videoLocation,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:148.0) Gecko/20100101 Firefox/148.0",
                        "Accept" to "*/*",
                        "Referer" to url,
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Site" to "same-origin"
                    )
                    this.quality = Qualities.P1080.value
                }
            )
        } catch (e: Exception) {}
    }

    private fun decryptAes(data: JsData, password: String): String {
        val salt = hexToBytes(data.s)
        val ciphertext = Base64.decode(data.ct, Base64.DEFAULT)
        val keyIv = opensslKdf(password, salt)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyIv.copyOfRange(0, 32), "AES"),
            IvParameterSpec(keyIv.copyOfRange(32, 48))
        )
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun opensslKdf(password: String, salt: ByteArray): ByteArray {
        val md5 = MessageDigest.getInstance("MD5")
        val passBytes = password.toByteArray(Charsets.UTF_8)
        var keyIv = byteArrayOf()
        var prev = byteArrayOf()
        while (keyIv.size < 48) {
            prev = md5.digest(prev + passBytes + salt)
            keyIv += prev
        }
        return keyIv
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    data class JsData(
        @JsonProperty("ct") val ct: String,
        @JsonProperty("iv") val iv: String,
        @JsonProperty("s") val s: String
    )

    data class DecryptedLocation(
        @JsonProperty("video_location") val videoLocation: String
    )
}