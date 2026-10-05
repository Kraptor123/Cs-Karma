// ! This Extension Made By @kraptor for csKarma

package com.kraptor

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class OnShort : MainAPI() {
    override var mainUrl              = "https://onshort.net"
    override var name                 = "OnShort"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "${mainUrl}/platform/shortmax/" to "ShortMax",
        "${mainUrl}/platform/dramawave/" to "DramaWave",
        "${mainUrl}/platform/netshort/" to "NetShort",
//        "${mainUrl}/platform/reelshort/" to "ReelShort",
//        "${mainUrl}/platform/dramabox/" to "dramabox",
//        "${mainUrl}/platform/shortswave/" to "ShortsWave",
//        "${mainUrl}/platform/moborels/" to "moborels",
//        "${mainUrl}/platform/freereels/" to "FreeReels",
//        "${mainUrl}/platform/stardusttv/" to "StardustTV",
//        "${mainUrl}/platform/flextv/" to "flextv",
//        "${mainUrl}/platform/idrama/" to "idrama",
//        "${mainUrl}/platform/goodshort/" to "GoodShort",
//        "${mainUrl}/platform/storyreel/" to "StoryReel",
//        "${mainUrl}/platform/dramabite/" to "DramaBite",
//        "${mainUrl}/platform/vibeshort-goodbos/" to "VibeShort",
//        "${mainUrl}/platform/microdrama/" to "MicroDrama",
//        "${mainUrl}/platform/vibeshort_goodbos/" to "vibeshort_goodbos",
//        "${mainUrl}/platform/dotdrama/" to "dotdrama",
//        "${mainUrl}/platform/dotdrama_goodbos/" to "dotdrama_goodbos",

    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = if (page==1){
            app.get("${request.data}").document
        } else {
            app.get("${request.data}page/$page/").document
        }
        val home     = document.select("article.series-card").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(list = HomePageList(request.name, home, false))
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("h3")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.AsianDrama) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val response = app.get(
            "${mainUrl}/wp-json/onshort-theme/v1/search?q=$query&limit=48&lang=en",
            referer = "${mainUrl}/"
        ).parsedSafe<OnShortSearchResponse>()

        val searchAnswer = response?.results?.mapNotNull { it.toSearchResult() } ?: emptyList()

        return newSearchResponseList(searchAnswer, hasNext = false)
    }

    data class OnShortSearchResponse(
        @JsonProperty("results") val results: List<OnShortSearchItem>?,
        @JsonProperty("count") val count: Int?
    )

    data class OnShortSearchItem(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("base_title") val baseTitle: String?,
        @JsonProperty("url") val url: String?,
        @JsonProperty("cover") val cover: String?,
        @JsonProperty("total") val total: Int?,
        @JsonProperty("lang") val lang: String?,
        @JsonProperty("platform") val platform: OnShortSearchPlatform?
    )

    data class OnShortSearchPlatform(
        @JsonProperty("slug") val slug: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("logo") val logo: String?,
        @JsonProperty("archive") val archive: String?
    )

    private fun OnShortSearchItem.toSearchResult(): SearchResponse? {
        val title     = this.title ?: return null
        val href      = fixUrlNull(this.url) ?: return null
        val posterUrl = fixUrlNull(this.cover)

        return newMovieSearchResponse(title, href, TvType.AsianDrama) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)


    override suspend fun load(url: String): LoadResponse? {
        Log.d(name, "Load aşaması: $url")
        val document = app.get(url).document

        val title           = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description     = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        val tags            = document.select("div.tag-cloud span").map { it.text().trim() }
        val recommendations = document.select("article.series-card").mapNotNull { it.toMainPageResult() }.distinctBy { it.name }

        val totalEpisodes = document.select("button.episode-button")
            .mapNotNull { it.attr("data-episode").toIntOrNull() }
            .maxOrNull() ?: 0

        val episodes = (1..totalEpisodes).map { epNum ->
            newEpisode("$url||$epNum") {
                this.name    = "Episode $epNum"
                this.episode = epNum
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
            this.posterUrl       = poster
            this.plot            = description
            this.tags            = tags
            this.recommendations = recommendations
        }
    }

    data class OnShortEpisodeResponse(
        @JsonProperty("url") val url: String?,
        @JsonProperty("quality") val quality: String?,
        @JsonProperty("sources") val sources: Map<String, String>?,
        @JsonProperty("subtitles") val subtitles: List<OnShortSubtitle>?
    )

    data class OnShortSubtitle(
        @JsonProperty("url") val url: String?,
        @JsonProperty("lang") val lang: String?,
        @JsonProperty("label") val label: String?
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val pageUrl   = data.substringBeforeLast("||")
        val episodeNo = data.substringAfterLast("||").toIntOrNull() ?: return false

        val document = app.get(pageUrl).document
        val shell    = document.selectFirst("#onshort-player")
        val postId   = shell?.attr("data-post")?.ifEmpty { null }
            ?: shell?.attr("data-series")?.ifEmpty { null }
            ?: document.selectFirst("input[name=post_id]")?.attr("value")

        val platform = shell?.attr("data-platform")?.ifEmpty { null }
            ?: shell?.attr("data-provider")?.ifEmpty { null }
            ?: "shortmax"

        val requestUrl = "${mainUrl}/wp-json/onshort-$platform/v1/series/$postId/episode/$episodeNo?_t=${System.currentTimeMillis()}"

        val response = app.get(
            requestUrl,
            referer = pageUrl,
            headers = mapOf("Cache-Control" to "no-cache", "Pragma" to "no-cache")
        ).parsedSafe<OnShortEpisodeResponse>() ?: return false

        var loaded = false
        val playHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:157.0) Gecko/20100101 Firefox/157.0",
            "Referer"    to "https://akamai-static.shorttv.live/"
        )

        response.sources?.forEach { (qual, streamUrl) ->
            if (streamUrl.isNotBlank()) {
                callback.invoke(
                    newExtractorLink(name, name, streamUrl, ExtractorLinkType.M3U8) {
                        this.referer = "https://akamai-static.shorttv.live/"
                        this.headers = playHeaders
                        this.quality = qual.toIntOrNull() ?: getQualityFromName(qual)
                    }
                )
                loaded = true
            }
        }

        if (!loaded && !response.url.isNullOrBlank()) {
            callback.invoke(
                newExtractorLink(name, name, response.url, ExtractorLinkType.M3U8) {
                    this.referer = "https://akamai-static.shorttv.live/"
                    this.headers = playHeaders
                    this.quality = response.quality?.toIntOrNull() ?: getQualityFromName(response.quality ?: "")
                }
            )
            loaded = true
        }

        response.subtitles?.forEach { sub ->
            sub.url?.let { subtitleCallback.invoke(newSubtitleFile(sub.lang ?: sub.label ?: "und", it)) }
        }

        return loaded
    }
}