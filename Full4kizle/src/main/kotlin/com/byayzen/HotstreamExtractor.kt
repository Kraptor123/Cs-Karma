package com.kraptor

import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.*
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

open class HotStreamExtractor : ExtractorApi() {
    override val name = "HotStream"
    override val mainUrl = "https://hotstream.club"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("kraptor_HotStream","url = $url")
        val document = app.get(url).document

        val script   = document.selectFirst("script:containsData(bePlayer)")?.data().toString()

        val bRegex = Regex(pattern = " bePlayer\\('([^']*)'", options = setOf(RegexOption.IGNORE_CASE))

        val bKey   = bRegex.find(script)?.groupValues[1].toString()

        Log.d("kraptor_HotStream","bKey = $bKey")

        val jsonAl = script.substringAfter(", '").substringBeforeLast("');")

        Log.d("kraptor_HotStream","jsonAl = $jsonAl")

        val jsoncu = mapper.readValue<Sifre>(jsonAl)

        Log.d("kraptor_HotStream","ct = ${jsoncu.ct} iv = ${jsoncu.iv} s = ${jsoncu.s}")


        val decrypted = decryptAES(jsoncu.ct, bKey, jsoncu.iv, jsoncu.s)
        Log.d("kraptor_HotStream","decrypted = $decrypted")


        val videoData = mapper.readValue<VideoData>(decrypted)

        Log.d("kraptor_HotStream","video = ${videoData.video_location}")

        callback.invoke(newExtractorLink(
            this.name,
            this.name,
            videoData.video_location,
            type = ExtractorLinkType.M3U8,
            {
                this.referer = url
            }
        ))

    }
}

private fun decryptAES(ct: String, password: String, ivHex: String, saltHex: String): String {
    val salt = hexToBytes(saltHex)
    val passwordBytes = password.toByteArray(Charsets.UTF_8)
    val md5_1 = MessageDigest.getInstance("MD5")
    md5_1.update(passwordBytes)
    md5_1.update(salt)
    val d1 = md5_1.digest()
    val md5_2 = MessageDigest.getInstance("MD5")
    md5_2.update(d1)
    md5_2.update(passwordBytes)
    md5_2.update(salt)
    val d2 = md5_2.digest()
    val key = d1 + d2
    val iv = hexToBytes(ivHex)
    val ciphertext = base64DecodeArray(ct)
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val keySpec = SecretKeySpec(key, "AES")
    val ivSpec = IvParameterSpec(iv)
    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

    val decrypted = cipher.doFinal(ciphertext)
    return String(decrypted, Charsets.UTF_8)
}

private fun hexToBytes(hex: String): ByteArray {
    val len = hex.length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        i += 2
    }
    return data
}

data class Sifre(
    val ct: String,
    val iv: String,
    val s: String,
)

data class VideoData(
    val video_location: String,
)