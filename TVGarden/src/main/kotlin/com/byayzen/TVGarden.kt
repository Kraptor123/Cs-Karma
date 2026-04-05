// ! Bu araç @ByAyzen tarafından | @CS-Karma için yazılmıştır.

package com.byayzen

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.byayzen.TVGardenesyalari.API_BASE_URL
import com.byayzen.TVGardenesyalari.countries
import com.byayzen.TVGardenesyalari.getFlagUrl
import com.byayzen.TVGardenesyalari.extractYouTubeId
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class TVGarden : MainAPI() {
    override var mainUrl = "https://famelack.com"
    override var name = "FamelackTV"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    private val defaultPoster = "$mainUrl/apple-touch-icon.png"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homePageLists = countries.mapNotNull { countryCode ->
            try {
                val channels = getChannelsForCountry(countryCode)
                if (channels.isNotEmpty()) {
                    HomePageList(countryCode.uppercase(), channels, true)
                } else null
            } catch (e: Exception) { null }
        }
        return newHomePageResponse(homePageLists)
    }

    override suspend fun search(query: String): List<SearchResponse> = coroutineScope {
        countries.map { countryCode ->
            async {
                try {
                    val response = app.get("$API_BASE_URL/$countryCode.json").text
                    val channelsArray = JSONArray(response)
                    val results = mutableListOf<SearchResponse>()

                    for (i in 0 until channelsArray.length()) {
                        val channel = channelsArray.getJSONObject(i)
                        val name = channel.optString("name", "")

                        if (name.lowercase().contains(query.lowercase())) {
                            val streamUrls = channel.optJSONArray("stream_urls")
                            val youtubeUrls = channel.optJSONArray("youtube_urls")

                            val streamUrl = when {
                                (streamUrls?.length() ?: 0) > 0 -> streamUrls?.getString(0)
                                (youtubeUrls?.length() ?: 0) > 0 -> youtubeUrls?.getString(0)
                                else -> null
                            }

                            if (streamUrl != null) {
                                results.add(newMovieSearchResponse(name, streamUrl, TvType.Live) {
                                    this.posterUrl = getFlagUrl(countryCode)
                                })
                            }
                        }
                    }
                    results
                } catch (e: Exception) {
                    emptyList<SearchResponse>()
                }
            }
        }.awaitAll().flatten()
    }

    private suspend fun getChannelsForCountry(countryCode: String): List<SearchResponse> {
        return try {
            val response = app.get("$API_BASE_URL/$countryCode.json").text
            val channelsArray = JSONArray(response)
            val list = mutableListOf<SearchResponse>()
            for (i in 0 until channelsArray.length()) {
                val channel = channelsArray.getJSONObject(i)
                val name = channel.optString("name", "")
                val streamUrls = channel.optJSONArray("stream_urls")
                val youtubeUrls = channel.optJSONArray("youtube_urls")

                val streamUrl = when {
                    (streamUrls?.length() ?: 0) > 0 -> streamUrls?.getString(0)
                    (youtubeUrls?.length() ?: 0) > 0 -> youtubeUrls?.getString(0)
                    else -> null
                }

                if (streamUrl != null) {
                    list.add(newMovieSearchResponse(name, streamUrl, TvType.Live) {
                        this.posterUrl = getFlagUrl(countryCode)
                    })
                }
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun load(url: String): LoadResponse {
        var channelName = "Live TV"
        var poster = defaultPoster

        countries.forEach { countryCode ->
            try {
                val response = app.get("$API_BASE_URL/$countryCode.json").text
                val channelsArray = JSONArray(response)
                for (i in 0 until channelsArray.length()) {
                    val channel = channelsArray.getJSONObject(i)
                    val streamUrls = channel.optJSONArray("stream_urls")
                    val youtubeUrls = channel.optJSONArray("youtube_urls")

                    val matchFound = (0 until (streamUrls?.length() ?: 0)).any { streamUrls?.getString(it) == url } ||
                            (0 until (youtubeUrls?.length() ?: 0)).any { youtubeUrls?.getString(it) == url }

                    if (matchFound) {
                        channelName = channel.optString("name", "Live TV")
                        poster = getFlagUrl(countryCode)
                        break
                    }
                }
            } catch (e: Exception) { }
        }

        return newMovieLoadResponse(channelName, url, TvType.Live, url) {
            this.plot = "Live Stream"
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            if (data.contains("youtube") || data.contains("youtu.be")) {
                val youtubeId = extractYouTubeId(data)
                loadExtractor("https://www.youtube.com/watch?v=$youtubeId", subtitleCallback, callback)
            } else {
                callback.invoke(
                    newExtractorLink(this.name, this.name, data, type = ExtractorLinkType.M3U8) {
                        this.headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36",
                            "Referer" to "$mainUrl/"
                        )
                    }
                )
            }
            true
        } catch (e: Exception) {
            Log.d("TVGarden", "Link yukleme hatasi: ${e.message}")
            false
        }
    }
}