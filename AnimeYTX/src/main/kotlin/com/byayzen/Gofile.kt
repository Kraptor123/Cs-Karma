package com.byayzen

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.security.MessageDigest

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