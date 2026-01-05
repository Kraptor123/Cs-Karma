package com.byayzen

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

class Tubeless : Voe() {
    override val name = "Tubeless"
    override val mainUrl = "https://tubelessceliolymph.com"
}

class Simpulumlamerop : Voe() {
    override val name = "Simplum"
    override var mainUrl = "https://simpulumlamerop.com"
}

class Urochsunloath : Voe() {
    override val name = "Uroch"
    override var mainUrl = "https://urochsunloath.com"
}

class NathanFromSubject : Voe() {
    override val mainUrl = "https://nathanfromsubject.com"
}

class Yipsu : Voe() {
    override val name = "Yipsu"
    override var mainUrl = "https://yip.su"
}

class MetaGnathTuggers : Voe() {
    override val name = "Metagnath"
    override val mainUrl = "https://metagnathtuggers.com"
}

class Voe1 : Voe() {
    override val mainUrl = "https://donaldlineelse.com"
}

class CrystalTreatmentEast : Voe() {
    override val name = "CrystalTreatment"
    override val mainUrl = "https://crystaltreatmenteast.com"
}

open class Voe : ExtractorApi() {
    override val name = "Voe"
    override val mainUrl = "https://voe.sx"
    override val requiresReferer = true
    private val redirectRegex = Regex("""window\.location\.href\s*=\s*'([^']+)'""")

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        var currentUrl = url
        var res = app.get(currentUrl, referer = referer)
        val redirectMatch = redirectRegex.find(res.document.data())
        if (redirectMatch != null) {
            val redirectUrl = redirectMatch.groupValues[1]
            res = app.get(redirectUrl, referer = referer)
            currentUrl = redirectUrl
        }
        val encodedString = res.document.selectFirst("script[type=application/json]")?.data()?.trim()?.substringAfter("[\"")?.substringBeforeLast("\"]")
        if (encodedString == null) {
            return
        }

        val json = decryptF7(encodedString)
        val m3u8 = json.optString("source")
        val mp4 = json.optString("direct_access_url")

        if (m3u8.isNotEmpty()) {
            M3u8Helper.generateM3u8(
                name,
                m3u8,
                "$mainUrl/",
                headers = mapOf("Origin" to "$mainUrl/")
            ).forEach(callback)
        }
        if (mp4.isNotEmpty()) {
            callback.invoke(
                newExtractorLink(
                    source = "$name MP4",
                    name = "$name MP4",
                    url = mp4,
                    type = INFER_TYPE
                ) {
                    this.referer = currentUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }

    private fun decryptF7(p8: String): JSONObject {
        return try {
            val vF = rot13(p8)
            val vF2 = replacePatterns(vF)
            val vF3 = removeUnderscores(vF2)
            val vF4 = base64Decode(vF3)
            val vF5 = charShift(vF4, 3)
            val vF6 = reverse(vF5)
            val vAtob = base64Decode(vF6)

            JSONObject(vAtob)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun rot13(input: String): String {
        return input.map { c ->
            when (c) {
                in 'A'..'Z' -> ((c - 'A' + 13) % 26 + 'A'.code).toChar()
                in 'a'..'z' -> ((c - 'a' + 13) % 26 + 'a'.code).toChar()
                else -> c
            }
        }.joinToString("")
    }

    private fun replacePatterns(input: String): String {
        val patterns = listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&")
        var result = input
        for (pattern in patterns) {
            result = result.replace(pattern, "_")
        }
        return result
    }

    private fun removeUnderscores(input: String): String = input.replace("_", "")

    private fun charShift(input: String, shift: Int): String {
        return input.map { (it.code - shift).toChar() }.joinToString("")
    }

    private fun reverse(input: String): String = input.reversed()
}