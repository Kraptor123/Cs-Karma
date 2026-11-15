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

        val runtimeUrl      = fixUrlNull(document.selectFirst("#onshort-player")?.attr("data-runtime")) ?: return null
        val title           = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description     = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        val tags            = document.select("div.tag-cloud span").map { it.text().trim() }
        val recommendations = document.select("article.series-card").mapNotNull { it.toMainPageResult() }.distinctBy { it.name }

        val totalEpisodes = document.select("button.episode-button")
            .mapNotNull { it.attr("data-episode").toIntOrNull() }
            .maxOrNull() ?: 0

        val episodes = (1..totalEpisodes).map { epNum ->
            newEpisode("$runtimeUrl||$epNum") {
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

    data class OnShortRuntimeResponse(
        @JsonProperty("episodes") val episodes: List<OnShortEpisodeResponse>?,
        @JsonProperty("episode_resolver") val episodeResolver: String?
    )

    data class OnShortEpisodeResponse(
        @JsonProperty("episode") val episode: Int?,
        @JsonProperty("url") val url: String?
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val runtimeUrl = data.substringBeforeLast("||")
        val episodeNo  = data.substringAfterLast("||").toIntOrNull() ?: return false

        val runtime  = app.get(runtimeUrl, referer = "${mainUrl}/").parsedSafe<OnShortRuntimeResponse>() ?: return false
        val episode  = runtime.episodes?.firstOrNull { it.episode == episodeNo }
            ?: runtime.episodeResolver?.let {
                app.get(it.replace("{episode}", "$episodeNo"), referer = "${mainUrl}/").parsedSafe<OnShortEpisodeResponse>()
            }
        val videoUrl = fixUrlNull(episode?.url) ?: return false
        Log.d(name, "Bölüm $episodeNo: $videoUrl")

        callback.invoke(
            newExtractorLink(name, name, videoUrl, if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                this.quality = Qualities.Unknown.value
            }
        )

        return true
    }
}