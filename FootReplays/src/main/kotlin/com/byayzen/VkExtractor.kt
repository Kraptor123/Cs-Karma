package com.byayzen

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.*
import android.util.Log
import com.lagradost.cloudstream3.network.WebViewResolver

open class VkExtractor : ExtractorApi() {
    override val name = "Vk"
    override val mainUrl = "https://vkvideo.ru"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("VkVideo", "Start")
        val commonUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

        val headers = mapOf(
            "User-Agent" to commonUserAgent,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer" to mainUrl,
        )

        var response = app.get(url, headers = headers)

        if (response.text.contains("hash429") || response.text.contains("challenge.html")) {
            Log.d("VkVideo", "")
            response = app.get(url, interceptor = WebViewResolver(Regex(".*video_ext\\.php.*")), headers = headers)
        }

        var foundLinks = linkcikart(response.text, commonUserAgent, callback)
        if (!foundLinks && url.contains("hash=")) {
            Log.d("VkVideo", "")

            val oid = Regex("""oid=([^&]+)""").find(url)?.groupValues?.get(1)
            val id = Regex("""id=([^&]+)""").find(url)?.groupValues?.get(1)
            val hash = Regex("""hash=([^&]+)""").find(url)?.groupValues?.get(1)

            if (oid != null && id != null && hash != null) {
                val tokenRegex = Regex("""anonym\.eyJ[\w\.\-]+""")
                val fallbackTokenRegex = Regex(""""access_token"\s*:\s*"([^"]+)"""")

                val token = tokenRegex.find(response.text)?.value
                    ?: fallbackTokenRegex.find(response.text)?.groupValues?.get(1)

                if (token != null) {
                    Log.d("VkVideo", "")
                    val apiUrl = "https://api.vk.com/method/video.get?v=5.269&client_id=52461373"
                    val postData = mapOf(
                        "owner_id" to "",
                        "videos" to "${oid}_${id}_${hash}",
                        "extended" to "0",
                        "is_embed" to "true",
                        "track_code" to "",
                        "access_token" to token
                    )

                    val apiResponse = app.post(
                        apiUrl,
                        headers = headers,
                        data = postData
                    )
                    linkcikart(apiResponse.text, commonUserAgent, callback)
                } else {
                    Log.d("VkVideo", "")
                }
            }
        }

        Log.d("VkVideo", "")
    }

    private suspend fun linkcikart(
        text: String,
        userAgent: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundAny = false

        val streamRegex = Regex("\"(hls|hls_ondemand|dash|dash_sep|dash_ondemand)\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        streamRegex.findAll(text).forEach { match ->
            val typeRaw = match.groupValues[1].lowercase()
            val videoUrl = match.groupValues[2].replace("\\", "")

            if (videoUrl.isNotBlank()) {
                foundAny = true
                val isDash = typeRaw.contains("dash")
                val typeName = if (isDash) "Dash" else "HLS"
                val linkType = if (isDash) ExtractorLinkType.DASH else ExtractorLinkType.M3U8

                callback.invoke(
                    newExtractorLink(
                        "${this.name} $typeName",
                        "${this.name} $typeName",
                        videoUrl,
                        linkType
                    ) {
                        this.referer = mainUrl
                        this.headers = mapOf(
                            "User-Agent" to userAgent,
                            "Referer" to mainUrl
                        )
                    }
                )
            }
        }

        return foundAny
    }
}

class VkCom : VkExtractor() {
    override var mainUrl = "https://vk.com"
}