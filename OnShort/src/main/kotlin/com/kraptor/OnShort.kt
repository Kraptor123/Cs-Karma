// ! This Extension Made By @kraptor for GizliKeyif

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
    override val supportedTypes       = setOf(TvType.TvSeries)
    override val vpnStatus            = VPNStatus.MightBeNeeded


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
        val document = app.get("${request.data}").document
        val home     = document.select("article.series-card").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(list = HomePageList(request.name, home, true))
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("h3")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
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

        return newMovieSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    
    override suspend fun load(url: String): LoadResponse? {
        Log.d(name, "Load aşaması: $url")
        val document = app.get(url).document

        val title           = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description     = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        val tags            = document.select("div.tag-cloud span").map { it.text().trim() }
        val recommendations = document.select("article.series-card").mapNotNull { it.toMainPageResult() }

        val totalEpisodes = document.select("button.episode-button")
            .mapNotNull { it.attr("data-episode").toIntOrNull() }
            .maxOrNull() ?: 0

        val episodes = (1..totalEpisodes).map { epNum ->
            newEpisode("$url||$epNum") {
                this.name    = "Episode $epNum"
                this.episode = epNum
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl       = poster
            this.plot            = description
            this.tags            = tags
            this.recommendations = recommendations
        }
    }

    data class OnShortEpisodeResponse(
        @JsonProperty("ok") val ok: Boolean?,
        @JsonProperty("url") val url: String?,
        @JsonProperty("quality") val quality: String?,
        @JsonProperty("candidates") val candidates: List<OnShortCandidate>?,
        @JsonProperty("subtitles") val subtitles: List<OnShortSubtitle>?,
        @JsonProperty("message") val message: String?
    )

    data class OnShortCandidate(
        @JsonProperty("url") val url: String?,
        @JsonProperty("quality") val quality: String?,
        @JsonProperty("kind") val kind: String?
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

        val shell = app.get(pageUrl).document.selectFirst("#onshort-player") ?: return false

        val postId   = shell.attr("data-post").takeIf { it.isNotBlank() } ?: return false
        val ticket   = shell.attr("data-player-ticket")
        val endpoint = shell.attr("data-player-endpoint")
            .takeIf { it.isNotBlank() } ?: "${mainUrl}/wp-json/onshort-player/v1/episode"

        val response = app.get(
            "$endpoint?post=$postId&episode=$episodeNo&_t=${System.currentTimeMillis()}",
            referer = pageUrl,
            headers = mapOf(
                "X-ONShort-Player" to "1",
                "X-ONShort-Ticket" to ticket,
                "Cache-Control" to "no-cache, no-store",
                "Pragma" to "no-cache"
            )
        ).parsedSafe<OnShortEpisodeResponse>()

        if (response?.ok != true) {
            Log.d(name, "loadLinks failed for $pageUrl ep $episodeNo: ${response?.message}")
            return false
        }

        val candidates = response.candidates?.filter { !it.url.isNullOrBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: response.url?.let { listOf(OnShortCandidate(it, response.quality, "hls")) }
            ?: emptyList()

        candidates.forEach { candidate ->
            val videoUrl = candidate.url ?: return@forEach
            val linkType = if (candidate.kind == "hls" || videoUrl.contains(".m3u8")) {
                ExtractorLinkType.M3U8
            } else {
                ExtractorLinkType.VIDEO
            }

            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name   = this.name,
                    url    = videoUrl,
                    type   = linkType,
                    initializer = {
                        this.referer = "${mainUrl}/"
                        this.quality = candidate.quality?.toIntOrNull() ?: getQualityFromName(candidate.quality ?: "")
                    }
                )
            )
        }

        response.subtitles?.forEach { sub ->
            val subUrl = sub.url ?: return@forEach
            subtitleCallback.invoke(newSubtitleFile(sub.lang ?: sub.label ?: "und", subUrl))
        }

        return candidates.isNotEmpty()
    }
}