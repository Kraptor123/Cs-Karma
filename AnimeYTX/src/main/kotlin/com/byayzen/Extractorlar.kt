package com.byayzen

import com.cskarma.ortak.extractor.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import java.security.MessageDigest

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

class Coflix : VidStackExt() { override var mainUrl = "https://coflix.upn.one" }

class Embedseek : VidStackExt() { override var mainUrl = "https://movix1.embedseek.com" }

class Ytplay : VidStackExt() { override var mainUrl = "https://ytplay.rpmvid.com" }

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

class Gofile : ExtractorApi() {
    override val name = "Gofile"
    override val mainUrl = "https://gofile.io"
    override val requiresReferer = false
    private val mainApi = "https://api.gofile.io"
    private val browserLanguage = "en-US"
    private val secret = "9844d94d963d30"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = Regex("/(?:\\?c=|d/)([\\da-zA-Z-]+)").find(url)?.groupValues?.get(1) ?: return

        val websiteTokenForAccountCreation = generateWebsiteToken(USER_AGENT, "")

        val token = app.post(
            "$mainApi/accounts",
            headers = mapOf(
                "X-Website-Token" to websiteTokenForAccountCreation,
                "X-BL" to browserLanguage
            )
        ).parsedSafe<AccountResponse>()?.data?.token ?: return

        val hashedToken = generateWebsiteToken(USER_AGENT, token)

        val headers = mapOf(
            "Referer" to "$mainUrl/",
            "User-Agent" to USER_AGENT,
            "Authorization" to "Bearer $token",
            "X-BL" to browserLanguage,
            "X-Website-Token" to hashedToken
        )

        val parsedResponse = app.get(
            "$mainApi/contents/$id?cache=true&sortField=createTime&sortDirection=1",
            headers = headers
        ).parsedSafe<GofileResponse>()

        Log.d(name, "parsedResponse: $parsedResponse")

        val childrenMap = parsedResponse?.data?.children ?: return
        for ((_, file) in childrenMap) {
            if (file.link.isNullOrEmpty() || file.type != "file") continue
            val fileName = file.name ?: ""
            val size = file.size ?: 0L
            val formattedSize = formatBytes(size)
            callback.invoke(
                newExtractorLink(
                    "Gofile",
                    "[Gofile] $fileName [$formattedSize]",
                    file.link,
                    ExtractorLinkType.VIDEO
                ) {
                    this.quality = getQuality(fileName)
                    this.headers = mapOf("Cookie" to "accountToken=$token")
                }
            )
        }
    }

    private fun generateWebsiteToken(userAgent: String, accountToken: String): String {
        val timeSlot = System.currentTimeMillis() / 1000 / 14400
        val raw = "$userAgent::$browserLanguage::$accountToken::$timeSlot::$secret"
        return sha256(raw)
    }

    private fun getQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024L * 1024 * 1024 -> "%.2f MB".format(bytes.toDouble() / (1024 * 1024))
            else -> "%.2f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
        }
    }

    data class AccountResponse(
        @param:JsonProperty("data") val data: AccountData? = null
    )

    data class AccountData(
        @param:JsonProperty("token") val token: String? = null
    )

    data class GofileResponse(
        @param:JsonProperty("data") val data: GofileData? = null
    )

    data class GofileData(
        @param:JsonProperty("children") val children: Map<String, GofileFile>? = null
    )

    data class GofileFile(
        @param:JsonProperty("type") val type: String? = null,
        @param:JsonProperty("name") val name: String? = null,
        @param:JsonProperty("link") val link: String? = null,
        @param:JsonProperty("size") val size: Long? = 0L
    )
}

