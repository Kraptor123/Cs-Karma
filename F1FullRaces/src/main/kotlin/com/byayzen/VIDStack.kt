package com.byayzen

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

open class VIDStack : ExtractorApi() {
    override var name = "VidStack"
    override var mainUrl = "https://f1seekplayer.com"
    override var requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val baseurl = getBaseUrl(url)
        val id = extractId(url) ?: return
        val videoApiUrl = "$baseurl/api/v1/video?id=$id&w=1920&h=1080&r="

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0",
            "Referer" to "$baseurl/",
            "Accept" to "*/*"
        )

        try {
            val response = app.get(videoApiUrl, headers = headers)
            val encoded = response.text.trim().replace("\n", "").replace("\r", "")
            if (encoded.length < 32 || !encoded.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return
            val decryptedtext = AesHelper.decryptaescbc(encoded)
            if (decryptedtext.isEmpty()) return
            val json = JSONObject(decryptedtext)
            val sourceurl = json.optString("source", "").replace("\\/", "/").replace("https://", "http://")
            if (sourceurl.isEmpty()) return

            val link = newExtractorLink(
                source = this.name,
                name = this.name,
                url = sourceurl,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "$baseurl/"
                this.quality = Qualities.P1080.value
            }
            callback.invoke(link)
        } catch (e: Exception) {
            Log.e("VidstackLog", "Hata: ${e.message}")
        }
    }

    private fun extractId(url: String): String? {
        return try {
            val uri = URI(url)
            val fragment = uri.fragment
            if (!fragment.isNullOrBlank()) return fragment.split("&").firstOrNull()?.takeIf { it.length > 1 }
            val query = uri.query
            if (!query.isNullOrBlank()) {
                val params = query.split("&").associate {
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else it to ""
                }
                params["id"]?.takeIf { it.isNotEmpty() }
            } else {
                uri.path.split("/").lastOrNull()?.takeIf { it.length > 1 }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getBaseUrl(url: String): String {
        return try {
            val uri = URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (e: Exception) {
            mainUrl
        }
    }
}

object AesHelper {
    private const val cbctransformation = "AES/CBC/PKCS5Padding"
    private const val key = "kiemtienmua911ca"
    private val iv = "1234567890oiuytr".toByteArray(Charsets.UTF_8)

    fun decryptaescbc(inputhex: String): String {
        val cipher = Cipher.getInstance(cbctransformation)
        val secretkey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        val ivspec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, secretkey, ivspec)
        val decryptedbytes = cipher.doFinal(inputhex.hextobytearray())
        return String(decryptedbytes, Charsets.UTF_8)
    }

    private fun String.hextobytearray(): ByteArray {
        val clean = this.replace(Regex("[^0-9a-fA-F]"), "")
        val len = clean.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
        }
        return data
    }
}