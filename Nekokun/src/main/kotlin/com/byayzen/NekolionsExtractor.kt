package com.byayzen

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*


//vidhide
open class NekolionsExtractor : ExtractorApi() {
    override val name = "Nekolions"
    override val mainUrl = "https://nekolions.my.id"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("Ayzen", url)
        Log.d("Ayzen", referer.toString())

        val embedUrl = getEmbedUrl(url)
        Log.d("Ayzen", embedUrl)

        val headers = mapOf(
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT
        )

        val response = app.get(embedUrl, referer = referer)
        Log.d("Ayzen", response.url)
        Log.d("Ayzen", response.code.toString())

        val packed = getPacked(response.text)
        Log.d("Ayzen", packed.toString())

        val script = if (!packed.isNullOrEmpty()) {
            var result = getAndUnpack(response.text)
            Log.d("Ayzen", result)
            if (result.contains("var links")) {
                result = result.substringAfter("var links")
                Log.d("Ayzen", result)
            }
            result
        } else {
            val scriptData = response.document.selectFirst("script:containsData(sources:)")?.data()
            Log.d("Ayzen", scriptData.toString())
            scriptData
        }

        Log.d("Ayzen", script.toString())

        if (script == null) {
            Log.d("Ayzen", "null")
            return
        }

        val matches = Regex(":\\s*\"(.*?m3u8.*?)\"").findAll(script).toList()
        Log.d("Ayzen", matches.size.toString())

        matches.forEach { m3u8Match ->
            val rawM3u8 = m3u8Match.groupValues[1]
            Log.d("Ayzen", rawM3u8)

            val fixedM3u8 = fixUrl(rawM3u8)
            Log.d("Ayzen", fixedM3u8)

            val generatedLinks = M3u8Helper.generateM3u8(
                name,
                fixedM3u8,
                referer = "$mainUrl/",
                headers = headers
            )
            Log.d("Ayzen", generatedLinks.size.toString())

            generatedLinks.forEach { link ->
                Log.d("Ayzen", link.url)
                callback(link)
            }
        }

        Log.d("Ayzen", "true")
    }

    private fun getEmbedUrl(url: String): String {
        val convertedUrl = when {
            url.contains("/d/") -> url.replace("/d/", "/v/")
            url.contains("/download/") -> url.replace("/download/", "/v/")
            url.contains("/file/") -> url.replace("/file/", "/v/")
            else -> url.replace("/f/", "/v/")
        }
        Log.d("Ayzen", convertedUrl)
        return convertedUrl
    }
}