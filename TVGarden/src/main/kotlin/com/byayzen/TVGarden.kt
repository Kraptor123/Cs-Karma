// ! Bu araç @ByAyzen tarafından | @cs-kraptor için yazılmıştır.

package com.byayzen

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class TVGarden : MainAPI() {
    override var mainUrl = "https://famelack.com"
    override var name = "FamelackTV"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    private val apiBaseUrl = "https://raw.githubusercontent.com/famelack/famelack-channels/refs/heads/main/channels/raw"
    private val defaultPoster = "https://famelack.com/apple-touch-icon.png"

    private val countries = listOf(
        "tr", "us", "uk", "de", "fr", "es", "it", "nl", "ru", "jp",
        "kr", "cn", "in", "br", "mx", "ar", "ca", "au", "sa", "ae",
        "az", "ch", "at", "be", "se", "no", "dk", "fi", "pt", "gr",
        "id", "pl", "ie", "ua"
    )

    private val countryFlags = mapOf(
        "tr" to "https://flagcdn.com/w320/tr.png",
        "us" to "https://flagcdn.com/w320/us.png",
        "uk" to "https://flagcdn.com/w320/gb.png",
        "de" to "https://flagcdn.com/w320/de.png",
        "fr" to "https://flagcdn.com/w320/fr.png",
        "es" to "https://flagcdn.com/w320/es.png",
        "it" to "https://flagcdn.com/w320/it.png",
        "nl" to "https://flagcdn.com/w320/nl.png",
        "ru" to "https://flagcdn.com/w320/ru.png",
        "jp" to "https://flagcdn.com/w320/jp.png",
        "kr" to "https://flagcdn.com/w320/kr.png",
        "cn" to "https://flagcdn.com/w320/cn.png",
        "in" to "https://flagcdn.com/w320/in.png",
        "br" to "https://flagcdn.com/w320/br.png",
        "mx" to "https://flagcdn.com/w320/mx.png",
        "ar" to "https://flagcdn.com/w320/ar.png",
        "ca" to "https://flagcdn.com/w320/ca.png",
        "au" to "https://flagcdn.com/w320/au.png",
        "sa" to "https://flagcdn.com/w320/sa.png",
        "ae" to "https://flagcdn.com/w320/ae.png",
        "az" to "https://flagcdn.com/w320/az.png",
        "ch" to "https://flagcdn.com/w320/ch.png",
        "at" to "https://flagcdn.com/w320/at.png",
        "be" to "https://flagcdn.com/w320/be.png",
        "se" to "https://flagcdn.com/w320/se.png",
        "no" to "https://flagcdn.com/w320/no.png",
        "dk" to "https://flagcdn.com/w320/dk.png",
        "fi" to "https://flagcdn.com/w320/fi.png",
        "pt" to "https://flagcdn.com/w320/pt.png",
        "gr" to "https://flagcdn.com/w320/gr.png",
        "id" to "https://flagcdn.com/w320/id.png",
        "pl" to "https://flagcdn.com/w320/pl.png",
        "ie" to "https://flagcdn.com/w320/ie.png",
        "ua" to "https://flagcdn.com/w320/ua.png"
    )

    private val countryNames = mapOf(
        "tr" to "🇹🇷 Turkey",
        "us" to "🇺🇸 USA",
        "uk" to "🇬🇧 United Kingdom",
        "de" to "🇩🇪 Germany",
        "fr" to "🇫🇷 France",
        "es" to "🇪🇸 Spain",
        "it" to "🇮🇹 Italy",
        "nl" to "🇳🇱 Netherlands",
        "ru" to "🇷🇺 Russia",
        "jp" to "🇯🇵 Japan",
        "kr" to "🇰🇷 South Korea",
        "cn" to "🇨🇳 China",
        "in" to "🇮🇳 India",
        "br" to "🇧🇷 Brazil",
        "mx" to "🇲🇽 Mexico",
        "ar" to "🇦🇷 Argentina",
        "ca" to "🇨🇦 Canada",
        "au" to "🇦🇺 Australia",
        "sa" to "🇸🇦 Saudi Arabia",
        "ae" to "🇦🇪 UAE",
        "az" to "🇦🇿 Azerbaijan",
        "ch" to "🇨🇭 Switzerland",
        "at" to "🇦🇹 Austria",
        "be" to "🇧🇪 Belgium",
        "se" to "🇸🇪 Sweden",
        "no" to "🇳🇴 Norway",
        "dk" to "🇩🇰 Denmark",
        "fi" to "🇫🇮 Finland",
        "pt" to "🇵🇹 Portugal",
        "gr" to "🇬🇷 Greece",
        "id" to "🇮🇩 Indonesia",
        "pl" to "🇵🇱 Poland",
        "ie" to "🇮🇪 Ireland",
        "ua" to "🇺🇦 Ukraine"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homePageLists = countries.mapNotNull { countryCode ->
            try {
                val channels = getChannelsForCountry(countryCode)
                if (channels.isNotEmpty()) {
                    HomePageList(countryNames[countryCode] ?: countryCode.uppercase(), channels, true)
                } else null
            } catch (e: Exception) { null }
        }
        return newHomePageResponse(homePageLists)
    }

    override suspend fun search(query: String): List<SearchResponse> = coroutineScope {
        countries.map { countryCode ->
            async {
                try {
                    val response = app.get("$apiBaseUrl/countries/$countryCode.json").text
                    val channelsArray = JSONArray(response)
                    val results = mutableListOf<SearchResponse>()

                    for (i in 0 until channelsArray.length()) {
                        val channel = channelsArray.getJSONObject(i)
                        val name = channel.optString("name", "")

                        if (name.lowercase().contains(query.lowercase())) {
                            Log.d("TVGarden", "Arama eslesmesi: $name ($countryCode)")

                            val iptvUrls = channel.optJSONArray("iptv_urls")
                            val youtubeUrls = channel.optJSONArray("youtube_urls")

                            val streamUrl = when {
                                iptvUrls != null && iptvUrls.length() > 0 && iptvUrls.getString(0).isNotBlank() -> iptvUrls.getString(0)
                                youtubeUrls != null && youtubeUrls.length() > 0 && youtubeUrls.getString(0).isNotBlank() -> youtubeUrls.getString(0)
                                else -> null
                            }

                            if (streamUrl != null) {
                                val logo = channel.optString("logo", "")
                                val posterUrl = if (logo.isNotBlank()) logo else (countryFlags[countryCode] ?: defaultPoster)

                                results.add(newMovieSearchResponse(name, streamUrl, TvType.Live) {
                                    this.posterUrl = posterUrl
                                })
                            }
                        }
                    }
                    results
                } catch (e: Exception) {
                    Log.d("TVGarden", "Arama hatasi ($countryCode): ${e.message}")
                    emptyList<SearchResponse>()
                }
            }
        }.awaitAll().flatten()
    }

    private suspend fun getChannelsForCountry(countryCode: String): List<SearchResponse> {
        return try {
            val channelsArray = JSONArray(app.get("$apiBaseUrl/countries/$countryCode.json").text)
            (0 until channelsArray.length()).mapNotNull {
                channelsArray.getJSONObject(it).toSearchResponse(null, countryCode)
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun JSONObject.toSearchResponse(searchQuery: String? = null, countryCode: String? = null): SearchResponse? {
        return try {
            val name = optString("name", "")
            if (name.isEmpty() || (searchQuery != null && !name.lowercase().contains(searchQuery))) return null

            val iptvUrls = optJSONArray("iptv_urls")
            val youtubeUrls = optJSONArray("youtube_urls")

            val streamUrl = when {
                iptvUrls != null && iptvUrls.length() > 0 && iptvUrls.getString(0).isNotBlank() -> iptvUrls.getString(0)
                youtubeUrls != null && youtubeUrls.length() > 0 && youtubeUrls.getString(0).isNotBlank() -> youtubeUrls.getString(0)
                else -> return null
            }

            val posterUrl = countryFlags[countryCode] ?: defaultPoster

            newMovieSearchResponse(name, streamUrl, TvType.Live) {
                this.posterUrl = posterUrl
            }
        } catch (e: Exception) { null }
    }

    private fun extractYouTubeId(url: String): String? {
        return when {
            url.contains("/embed/") -> url.substringAfter("embed/").substringBefore("?")
            url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
            url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
            else -> null
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val channelInfo = countries.firstNotNullOfOrNull { countryCode ->
            try {
                val channelsArray = JSONArray(app.get("$apiBaseUrl/countries/$countryCode.json").text)
                (0 until channelsArray.length()).map { channelsArray.getJSONObject(it) }
                    .find { channel ->
                        val iptvUrls = channel.optJSONArray("iptv_urls")
                        val youtubeUrls = channel.optJSONArray("youtube_urls")
                        val hasIptv = (0 until (iptvUrls?.length() ?: 0)).any { iptvUrls?.getString(it) == url }
                        val hasYoutube = (0 until (youtubeUrls?.length() ?: 0)).any { youtubeUrls?.getString(it) == url }
                        hasIptv || hasYoutube
                    }?.let { Triple(it, (0 until channelsArray.length()).map { channelsArray.getJSONObject(it) }, countryCode) }
            } catch (e: Exception) { null }
        }

        val channelName = channelInfo?.first?.optString("name") ?: "Live TV"
        val allChannels = channelInfo?.second ?: emptyList()
        val countryCode = channelInfo?.third ?: ""

        val recommendations = if (channelInfo != null) {
            val keyword = channelName.split(" ").firstOrNull { it.length > 1 } ?: channelName
            allChannels.filter {
                val otherName = it.optString("name", "")
                otherName.contains(keyword, ignoreCase = true) && otherName != channelName
            }.take(10).mapNotNull { it.toSearchResponse(null, countryCode) }
        } else emptyList()

        return newMovieLoadResponse(channelName, url, TvType.Live, url) {
            this.posterUrl = countryFlags[countryCode] ?: defaultPoster
            this.plot = "🔴 Live"
            this.recommendations = recommendations
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
                        this.headers = mapOf("User-Agent" to "Mozilla/5.0")
                    }
                )
            }
            true
        } catch (e: Exception) {
            Log.e("TVGarden", "Error: ${e.message}")
            false
        }
    }
}

fun extractYouTubeId(url: String): String {
    return when {
        url.contains("oembed") && url.contains("url=") -> {
            val encodedUrl = url.substringAfter("url=").substringBefore("&")
            val decodedUrl = URLDecoder.decode(encodedUrl, "UTF-8")
            extractYouTubeId(decodedUrl)
        }

        url.contains("attribution_link") && url.contains("u=") -> {
            val encodedUrl = url.substringAfter("u=").substringBefore("&")
            val decodedUrl = URLDecoder.decode(encodedUrl, "UTF-8")
            extractYouTubeId(decodedUrl)
        }

        url.contains("watch?v=") -> url.substringAfter("watch?v=").substringBefore("&")
            .substringBefore("#")

        url.contains("&v=") -> url.substringAfter("&v=").substringBefore("&")
            .substringBefore("#")

        url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
            .substringBefore("#").substringBefore("&")

        url.contains("/embed/") -> url.substringAfter("/embed/").substringBefore("?")
            .substringBefore("#")

        url.contains("/v/") -> url.substringAfter("/v/").substringBefore("?")
            .substringBefore("#")

        url.contains("/e/") -> url.substringAfter("/e/").substringBefore("?")
            .substringBefore("#")

        url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?")
            .substringBefore("#")

        url.contains("/live/") -> url.substringAfter("/live/").substringBefore("?")
            .substringBefore("#")

        url.contains("/watch/") -> url.substringAfter("/watch/").substringBefore("?")
            .substringBefore("#")

        url.contains("watch%3Fv%3D") -> url.substringAfter("watch%3Fv%3D")
            .substringBefore("%26").substringBefore("#")

        url.contains("v%3D") -> url.substringAfter("v%3D").substringBefore("%26")
            .substringBefore("#")

        else -> error("No Id Found")
    }
}