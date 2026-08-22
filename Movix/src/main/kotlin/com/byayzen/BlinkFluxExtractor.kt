package com.byayzen

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class BlinkFluxExtractor : ExtractorApi() {
    override val name            = "BlinkFlux"
    override val mainUrl         = "https://blinkflux.lol"
    override val requiresReferer = true

    private val apiKey       = "ff_5ae9661d6220e612a00645cb2889d6da5231504cbb68cc32214030b1a783e8e3"
    private val userAgent    = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:154.0) Gecko/20100101 Firefox/154.0"

    private val baseHeaders  = mapOf(
        "User-Agent"      to userAgent,
        "Accept"          to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    private val payloadRegex  = Regex("""ENCRYPTED_PAYLOAD\s*=\s*"([^"]+)"""")
    private val ivRegex       = Regex("""ENCRYPTED_IV\s*=\s*"([0-9a-fA-F]+)"""")
    private val subsUrlRegex  = Regex("""SUBS_URL\s*=\s*"([^"]+)"""")

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("BlinkFlux", "$url")
        val fullUrl   = if (url.startsWith("http")) url else fixUrl(url)
        val docHeaders = if (referer.isNullOrEmpty()) baseHeaders else baseHeaders + ("Referer" to referer)

        val playerHtml = try {
            app.get(fullUrl, headers = docHeaders).text
        } catch (e: Exception) {
            Log.d("BlinkFlux", "${e.message}")
            return
        }

        val encryptedPayload = payloadRegex.find(playerHtml)?.groupValues?.get(1)
            ?.replace("\\/", "/")
        val encryptedIv = ivRegex.find(playerHtml)?.groupValues?.get(1)
        val subsRel    = subsUrlRegex.find(playerHtml)?.groupValues?.get(1)?.replace("\\/", "/")

        if (encryptedPayload == null || encryptedIv == null) {
            Log.d("BlinkFlux", "$fullUrl")
            return
        }
        Log.d("BlinkFlux", "$encryptedIv")

        val unlockUrl = "$mainUrl/api/v1/index.php?route=unlock&api_key=$apiKey"
        val jsonBody  = """{"token":"","payload":"$encryptedPayload","iv":"$encryptedIv"}"""
        val postHeaders = mapOf(
            "Content-Type" to "application/json",
            "User-Agent"   to userAgent,
            "Accept"       to "*/*",
            "Origin"       to mainUrl,
            "Referer"      to fullUrl
        )

        Log.d("BlinkFlux", "$unlockUrl")
        val unlockResp = try {
            app.post(
                unlockUrl,
                requestBody = jsonBody.toRequestBody("application/json".toMediaType()),
                headers    = postHeaders
            ).text
        } catch (e: Exception) {
            Log.d("BlinkFlux", "${e.message}")
            return
        }

        val unlockJson = JSONObject(unlockResp)
        val isSuccess = unlockJson.optBoolean("success", false)
        Log.d("BlinkFlux", "$isSuccess")
        if (!isSuccess) return

        val videoUrl = unlockJson.optString("url").ifEmpty {
            Log.d("BlinkFlux", unlockResp)
            return
        }
        val isM3u8 = videoUrl.contains(".m3u8")
        Log.d("BlinkFlux", "$videoUrl")

        callback.invoke(
            newExtractorLink(
                source  = name,
                name    = name,
                url     = videoUrl,
                type    = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.Unknown.value
                this.headers = mapOf(
                    "User-Agent" to userAgent,
                    "Referer"    to "$mainUrl/"
                )
            }
        )

        if (!subsRel.isNullOrEmpty()) {
            val subsUrl  = if (subsRel.startsWith("http")) subsRel else fixUrl(subsRel)
            Log.d("BlinkFlux", "$subsUrl")
            val subsResp = try {
                app.get(subsUrl, headers = baseHeaders).text
            } catch (e: Exception) {
                Log.d("BlinkFlux", "${e.message}")
                return
            }
            val subsJson = JSONObject(subsResp)
            val tracks   = subsJson.optJSONArray("tracks")
            if (tracks != null) {
                Log.d("BlinkFlux", "${tracks.length()}")
                for (i in 0 until tracks.length()) {
                    val t   = tracks.optJSONObject(i) ?: continue
                    val src = t.optString("src").ifEmpty { continue }
                    val lang = t.optString("srclang").ifEmpty { "und" }
                    Log.d("BlinkFlux", "$src")
                    subtitleCallback.invoke(newSubtitleFile(lang, src))
                }
            }
        }
    }
}
