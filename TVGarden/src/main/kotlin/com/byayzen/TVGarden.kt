// ! Bu araç @ByAyzen tarafından | @cs-kraptor için yazılmıştır.

package com.byayzen

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

class TVGarden : MainAPI() {
    override var mainUrl = "https://famelack.com"
    override var name = "TVGarden"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    private val apiBaseUrl = "https://raw.githubusercontent.com/famelack/famelack-channels/refs/heads/main/channels/raw"
    private val defaultPoster = "https://famelack.com/apple-touch-icon.png"

    private val countries = listOf("tr", "us", "uk", "de", "fr", "es", "it", "nl", "ru", "jp", "kr", "cn", "in", "br", "mx", "ar", "ca", "au", "sa")

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
        "sa" to "https://flagcdn.com/w320/sa.png"
    )

    private val countryNames = mapOf(
        "tr" to "🇹🇷 Türkiye", "us" to "🇺🇸 ABD", "uk" to "🇬🇧 İngiltere",
        "de" to "🇩🇪 Almanya", "fr" to "🇫🇷 Fransa", "es" to "🇪🇸 İspanya",
        "it" to "🇮🇹 İtalya", "nl" to "🇳🇱 Hollanda", "ru" to "🇷🇺 Rusya",
        "jp" to "🇯🇵 Japonya", "kr" to "🇰🇷 Güney Kore", "cn" to "🇨🇳 Çin",
        "in" to "🇮🇳 Hindistan", "br" to "🇧🇷 Brezilya", "mx" to "🇲🇽 Meksika",
        "ar" to "🇦🇷 Arjantin", "ca" to "🇨🇦 Kanada", "au" to "🇦🇺 Avustralya",
        "sa" to "🇸🇦 Suudi Arabistan"
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

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val channelsArray = JSONArray(app.get("$apiBaseUrl/all-channels.json").text)
            (0 until channelsArray.length()).mapNotNull {
                val channel = channelsArray.getJSONObject(it)
                val name = channel.optString("name", "")

                if (name.isEmpty() || !name.lowercase().contains(query.lowercase())) return@mapNotNull null

                val countryCode = channel.optString("country", "")
                Log.d("TVGarden", "Search: $name - Country: $countryCode")

                val iptvUrls = channel.optJSONArray("iptv_urls")
                val youtubeUrls = channel.optJSONArray("youtube_urls")

                val streamUrl = when {
                    iptvUrls != null && iptvUrls.length() > 0 && iptvUrls.getString(0).isNotBlank() -> iptvUrls.getString(0)
                    youtubeUrls != null && youtubeUrls.length() > 0 && youtubeUrls.getString(0).isNotBlank() -> youtubeUrls.getString(0)
                    else -> return@mapNotNull null
                }

                val posterUrl = countryFlags[countryCode] ?: defaultPoster
                Log.d("TVGarden", "Poster URL: $posterUrl")

                newMovieSearchResponse(name, streamUrl, TvType.Live) {
                    this.posterUrl = posterUrl
                }
            }
        } catch (e: Exception) {
            Log.e("TVGarden", "Search error: ${e.message}")
            emptyList()
        }
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
            this.plot = "🔴 Canlı Yayın"
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