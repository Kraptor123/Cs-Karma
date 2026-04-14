package com.byayzen

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

class FireLoad : ExtractorApi() {
    override val name = "FireLoad"
    override val mainUrl = "https://www.fireload.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("FireLoad", url)
        val response = app.get(url, referer = referer)

        Regex("""window\.Fl\s*=\s*(\{.*?\})""").find(response.text)?.groupValues?.get(1)?.let { jsonStr ->
            val dlink = JSONObject(jsonStr).optString("dlink")
            Log.d("FireLoad", dlink)

            if (dlink.isNotBlank()) {
                val locationres = app.get(dlink, referer = url, cookies = response.cookies, allowRedirects = false)
                val videoUrl = locationres.headers["location"] ?: locationres.headers["Location"] ?: locationres.url
                Log.d("FireLoad", videoUrl)

                if (videoUrl.isNotBlank()) {
                    callback(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = videoUrl,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = url
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            }
        }
    }
}

class Coflix : VidStack() { override var mainUrl = "https://coflix.upn.one" }

class Embedseek : VidStack() { override var mainUrl = "https://movix1.embedseek.com" }

class Ytplay : VidStack() { override var mainUrl = "https://ytplay.rpmvid.com" }

open class Mytsumi : ExtractorApi() {
    override val name = "Mytsumi"
    override val mainUrl = "https://mytsumi.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            Log.d("Mytsumi", url)
            val response = app.get(url, referer = referer)
            val document = response.text

            val qualityRegex = Regex("""const\s+qualities\s*=\s*(\{.*?\});""")
            qualityRegex.find(document)?.groupValues?.get(1)?.let { jsonStr ->
                val qualJson = JSONObject(jsonStr)
                qualJson.keys().forEach { label ->
                    val videoUrl = qualJson.getString(label).replace("\\/", "/")
                    Log.d("Mytsumi", videoUrl)

                    callback(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = videoUrl,
                            type = INFER_TYPE
                        ) {
                            this.referer = "https://mytsumi.com/"
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.d("Mytsumi", "${e.message}")
        }
    }
}

open class BurstCloud : ExtractorApi() {
    override val name = "BurstCloud"
    override val mainUrl = "https://www.burstcloud.co"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            Log.d("BurstCloud", url)
            val response = app.get(url)
            val fileId = response.document.selectFirst("div#player")?.attr("data-file-id") ?: return
            Log.d("BurstCloud", fileId)

            val playRequest = app.post(
                "$mainUrl/file/play-request/",
                data = mapOf("fileId" to fileId),
                referer = url,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).text

            val json = JSONObject(playRequest)
            val cdnUrl = json.getJSONObject("purchase").getString("cdnUrl")
            Log.d("BurstCloud", cdnUrl)

            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = cdnUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = "https://www.burstcloud.co/"
                }
            )
        } catch (e: Exception) {
            Log.d("BurstCloud", "${e.message}")
        }
    }
}