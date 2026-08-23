package com.byayzen

import android.net.Uri
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Collections

object MovixLive {
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:154.0) Gecko/20100101 Firefox/154.0"
    private const val DEFAULT_NORTHLIVE_API_KEY =
        "ff_5ae9661d6220e612a00645cb2889d6da5231504cbb68cc32214030b1a783e8e3"
    private const val PLACEHOLDER_POSTER =
        "https://movix.fun/movix.png"

    private val cachedChannels = Collections.synchronizedList(mutableListOf<SearchResponse>())
    private val channelMap = Collections.synchronizedMap(mutableMapOf<String, MovixLiveMeta>())



    suspend fun MainAPI.getMainPageLive(
        request: MainPageRequest,
        apibase: String,
        apiheaders: Map<String, String>
    ): HomePageResponse {
        val path = request.data
        val catalogUrl = "$apibase/$path"
        Log.d("MovixLive", catalogUrl)

        val res = try {
            val response = app.get(catalogUrl, headers = apiheaders, timeout = 15)
            val jsonText = try { response.body.string() } catch (_: Exception) { response.text }
            tryParseJson<MovixLiveCatalogResponse>(jsonText)
        } catch (e: Exception) {
            Log.d("MovixLive", "${e.message}")
            null
        }

        val items = res?.metas?.mapNotNull { meta ->
            val id = meta.id ?: return@mapNotNull null
            val name = meta.name ?: "Live TV"
            val poster = if (!meta.poster.isNullOrBlank()) meta.poster else PLACEHOLDER_POSTER
            val loadUrl = "live/$id?name=${Uri.encode(name)}"

            channelMap[id] = meta.copy(name = name, poster = poster)

            newLiveSearchResponse(name, loadUrl, TvType.Live) {
                this.posterUrl = poster
            }
        } ?: emptyList()

        if (items.isNotEmpty()) {
            synchronized(cachedChannels) {
                cachedChannels.removeAll { it.url in items.map { i -> i.url } }
                cachedChannels.addAll(items)
            }
        }

        return newHomePageResponse(
            HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = true
            ),
            hasNext = false
        )
    }

    suspend fun MainAPI.loadLive(url: String): LoadResponse {
        val rawId = if (url.contains("live/")) {
            url.substringAfter("live/").substringBefore("?")
        } else {
            url.substringBefore("?")
        }

        val cachedMeta = channelMap[rawId]

        val rawName = if (url.contains("name=")) {
            Uri.decode(url.substringAfter("name=").substringBefore("&"))
        } else {
            cachedMeta?.name ?: rawId.replace("northlive_", "").replace("vavoo_", "").replace("-", " ").replace("_", " ").uppercase()
        }

        val poster = cachedMeta?.poster?.takeIf { it.isNotBlank() } ?: PLACEHOLDER_POSTER
        val recs = synchronized(cachedChannels) {
            if (cachedChannels.isNotEmpty()) {
                cachedChannels.filter { !it.url.contains(rawId) }.shuffled().take(20).map { rec ->
                    newLiveSearchResponse(rec.name, rec.url, TvType.Live) {
                        this.posterUrl = rec.posterUrl?.takeIf { it.isNotBlank() } ?: PLACEHOLDER_POSTER
                    }
                }
            } else {
                channelMap.values.filter { it.id != rawId }.shuffled().take(20).mapNotNull { meta ->
                    val id = meta.id ?: return@mapNotNull null
                    val name = meta.name ?: "Live TV"
                    val posterUrl = meta.poster?.takeIf { it.isNotBlank() } ?: PLACEHOLDER_POSTER
                    val loadUrl = "live/$id?name=${Uri.encode(name)}"
                    newLiveSearchResponse(name, loadUrl, TvType.Live) {
                        this.posterUrl = posterUrl
                    }
                }
            }
        }

        return newLiveStreamLoadResponse(rawName, url, url) {
            this.posterUrl = poster
            this.backgroundPosterUrl = poster
            this.plot = "Chaîne en direct: $rawName"
            this.recommendations = recs
        }
    }

    suspend fun loadLiveLinks(
        data: String,
        apibase: String,
        apiheaders: Map<String, String>,
        mainUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val channelId = if (data.contains("live/")) {
            data.substringAfter("live/").substringBefore("?")
        } else {
            data.substringBefore("?")
        }

        val streamApiUrl = "$apibase/livetv/stream/tv/$channelId"
        Log.d("MovixLive", streamApiUrl)

        val streamRes = try {
            val response = app.get(streamApiUrl, headers = apiheaders, timeout = 15)
            val jsonText = try { response.body.string() } catch (_: Exception) { response.text }
            tryParseJson<MovixLiveStreamResponse>(jsonText)
        } catch (e: Exception) {
            Log.d("MovixLive", "${e.message}")
            null
        }

        val streams = streamRes?.streams
        if (streams.isNullOrEmpty()) {
            Log.d("MovixLive", channelId)
            return false
        }

        streams.forEach { stream ->
            val streamUrl = stream.url ?: stream.originalUrl ?: return@forEach
            Log.d("MovixLive", streamUrl)

            if (streamUrl.contains("northlive.lol")) {
                extractNorthLive(streamUrl, mainUrl, callback)
            } else if (streamUrl.contains(".m3u") || streamUrl.contains(".mp4") || streamUrl.contains("/hls/")) {
                val brand = stream.title?.ifBlank { "Live TV" } ?: "Live TV"
                callback.invoke(
                    newExtractorLink(
                        source = brand,
                        name = "$brand | Live",
                        url = streamUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.Unknown.value
                        this.headers = mapOf(
                            "User-Agent" to USER_AGENT,
                            "Referer" to "$mainUrl/"
                        )
                    }
                )
            } else {
                loadcustomextractor(stream.title ?: "Live TV", streamUrl, mainUrl, subtitleCallback, callback)
            }
        }

        return true
    }

    private suspend fun extractNorthLive(
        playerUrl: String,
        mainUrl: String,
        callback: (ExtractorLink) -> Unit
    ) {
        val apiKey = Regex("""api_key=([^&]+)""").find(playerUrl)?.groupValues?.get(1)
            ?: DEFAULT_NORTHLIVE_API_KEY
        val slugFromUrl = Regex("""/tv/([^/?&]+)""").find(playerUrl)?.groupValues?.get(1) ?: ""

        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer" to "$mainUrl/"
        )

        Log.d("MovixLive", playerUrl)
        val playerHtml = try {
            val resp = app.get(playerUrl, headers = headers, timeout = 10)
            try { resp.body.string() } catch (_: Exception) { resp.text }
        } catch (e: Exception) {
            Log.d("MovixLive", "${e.message}")
            return
        }

        val slug = Regex("""PRIMARY_SLUG\s*=\s*["']([^"']+)["']""").find(playerHtml)?.groupValues?.get(1)
            ?: Regex("""["']?slug["']?\s*[:=]\s*["']([^"']+)["']""").find(playerHtml)?.groupValues?.get(1)
            ?: slugFromUrl
        val playToken = Regex("""PLAY_TOKEN\s*=\s*["']([^"']+)["']""").find(playerHtml)?.groupValues?.get(1)
            ?: Regex("""["']?play_token["']?\s*[:=]\s*["']([^"']+)["']""").find(playerHtml)?.groupValues?.get(1)
            ?: Regex("""["']token["']\s*[:=]\s*["']([^"']+)["']""").find(playerHtml)?.groupValues?.get(1)

        if (playToken.isNullOrBlank()) {
            Log.d("MovixLive", playerUrl)
            return
        }

        val unlockUrl = "https://northlive.lol/api/v1/index.php?route=play_url&api_key=$apiKey"
        val postBody = JSONObject().apply {
            put("slug", slug)
            put("api_key", apiKey)
            put("play_token", playToken)
        }.toString()

        val postHeaders = mapOf(
            "Host" to "northlive.lol",
            "User-Agent" to USER_AGENT,
            "Accept" to "*/*",
            "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
            "Referer" to playerUrl,
            "Content-Type" to "application/json",
            "Authorization" to "Bearer $apiKey",
            "Origin" to "https://northlive.lol"
        )

        val unlockResp = try {
            app.post(
                unlockUrl,
                requestBody = postBody.toRequestBody("application/json".toMediaType()),
                headers = postHeaders,
                timeout = 10
            )
        } catch (e: Exception) {
            Log.d("MovixLive", "${e.message}")
            return
        }

        val unlockText = try { unlockResp.body.string() } catch (_: Exception) { unlockResp.text }
        val unlockJson = JSONObject(unlockText)
        val isSuccess = unlockJson.optBoolean("success", false)
        val finalUrl = unlockJson.optString("url")

        Log.d("MovixLive", "$finalUrl")
        if (isSuccess && finalUrl.isNotBlank()) {
            val signedUrl = if (finalUrl.contains("pt=") || playToken.isBlank()) {
                finalUrl
            } else if (finalUrl.contains("?")) {
                "$finalUrl&pt=$playToken"
            } else {
                "$finalUrl?pt=$playToken"
            }

            callback.invoke(
                newExtractorLink(
                    source = "NorthLive",
                    name = "NorthLive | Live",
                    url = signedUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "https://northlive.lol/"
                    this.quality = Qualities.Unknown.value
                    this.headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "https://northlive.lol/"
                    )
                }
            )
        }
    }
}


data class MovixLiveCatalogResponse(
    val metas: List<MovixLiveMeta>? = null
)

data class MovixLiveMeta(
    val id: String? = null,
    val type: String? = null,
    val name: String? = null,
    val poster: String? = null
)

data class MovixLiveStreamResponse(
    val streams: List<MovixLiveStream>? = null
)

data class MovixLiveStream(
    val title: String? = null,
    val url: String? = null,
    val originalUrl: String? = null,
    val _isEmbed: Boolean? = null
)