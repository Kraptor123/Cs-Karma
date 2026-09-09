package com.byayzen

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors

class JPFilms : MainAPI() {
    override var mainUrl        = "https://kodasusaka.com"
    override var name           = "JPFilms"
    override val hasMainPage    = true
    override var lang           = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val tag = "cskarma_${name}"

    override val mainPage = mainPageOf(
        "${mainUrl}/movies" to "Movies - Latest Update",
        "${mainUrl}/movies?sort=created" to "Movies - Recently Added",
        "${mainUrl}/movies?sort=views" to "Movies - Most Viewed",
        "${mainUrl}/movies?sort=tmdb" to "Movies - TMDB Rating",
        "${mainUrl}/tv-series" to "TV Series - Latest Update",
        "${mainUrl}/tv-series?sort=created" to "TV Series - Recently Added",
        "${mainUrl}/tv-series?sort=views" to "TV Series - Most Viewed",
        "${mainUrl}/tv-series?sort=tmdb" to "TV Series - TMDB Rating"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) {
            request.data
        } else if (request.data.contains("?")) {
            "${request.data}&page=$page"
        } else {
            "${request.data}?page=$page"
        }

        Log.d(tag, "getMainPage: ${request.name} page=$page")
        val document   = app.get(url).document
        val isTvSeries = request.data.contains("/tv-series")
        val type       = if (isTvSeries) TvType.TvSeries else TvType.Movie

        val home = document.select("article.group").mapNotNull { it.toSearchResult(type) }

        return newHomePageResponse(request.name, home, hasNext = home.isNotEmpty())
    }

    private fun Element.toSearchResult(type: TvType): SearchResponse? {
        val title  = this.selectFirst("h3")?.text() ?: return null
        val link   = this.selectFirst("a")
        val href   = fixUrlNull(link?.attr("href")?.ifEmpty { return null }) ?: return null
        val poster = fixUrlNull(this.selectFirst("img")?.attr("src")?.ifEmpty { null })

        return if (type == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, href, type) { this.posterUrl = poster }
        } else {
            newMovieSearchResponse(title, href, type) { this.posterUrl = poster }
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page <= 1) "$mainUrl/search?q=$query" else "$mainUrl/search?q=$query&page=$page"

        Log.d(tag, "search: $query page=$page")
        val document      = app.get(url).document
        val searchResults = document.select("article.group").mapNotNull { it.toSearchResult(TvType.Movie) }

        return newSearchResponseList(searchResults, searchResults.isNotEmpty())
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        Log.d(tag, "load: $url")
        val document = app.get(url).document
        val title     = document.selectFirst("h1.cls-info-title")?.text()?.trim() ?: return null
        val poster    = fixUrlNull(document.selectFirst(".cls-poster-lg img")?.attr("src")?.ifEmpty { null })
        val plot      = document.selectFirst(".cls-prose")?.text()?.trim()

        val year     = Regex("""(\d+)""").find(document.select("a.cls-badge[href*='/year/']").text())?.groupValues?.get(1)?.toIntOrNull()
        val duration = Regex("""(\d+)""").find(document.select(".cls-badges span.cls-badge").text())?.groupValues?.get(1)?.toIntOrNull()
        val rating   = Regex("""(\d+(?:\.\d+)?)""").find(document.select(".cls-ratings .cls-rating").text())?.groupValues?.get(1)?.toDoubleOrNull()
        val score    = rating?.let { Score.from(it, 10) }

        val tags   = document.select("div.cls-info-people:matches(Genre|Country) a").map { it.text() }
        val actors = document.select("div.cls-info-people:contains(Cast:) a").map { Actor(it.text()) }

        val recommendations = document.select("section:has(h2:contains(Recommended)) article.group").mapNotNull { it.toSearchResult(TvType.Movie) }

        val isTvSeries = document.select("nav.cls-crumb a[href*='/tv-series']").isNotEmpty()
        val watchPath  = document.selectFirst("a.cls-btn-watch")?.attr("href")?.ifEmpty { null } ?: return null
        val watchUrl   = fixUrl(watchPath)

        if (isTvSeries) {
            val epBadge       = document.selectFirst(".cls-badge.cls-badge-ep")?.text() ?: ""
            val totalEpisodes = Regex("""(\d+)""").find(epBadge)?.groupValues?.get(1)?.toIntOrNull() ?: 1

            val episodes = (1..totalEpisodes).map { epNum ->
                val epUrl = if (epNum == 1) watchUrl else "$watchUrl?ep=$epNum"
                newEpisode(epUrl) {
                    this.name    = "Episode $epNum"
                    this.episode = epNum
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl       = poster
                this.plot            = plot
                this.year            = year
                this.tags            = tags
                this.score           = score
                this.duration        = duration
                this.recommendations = recommendations
                addActors(actors)
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, watchUrl) {
            this.posterUrl       = poster
            this.plot            = plot
            this.year            = year
            this.tags            = tags
            this.score           = score
            this.duration        = duration
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d(tag, "loadLinks: $data")
        val epParam = Regex("""ep=(\d+)""").find(data)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val html    = app.get(data).text

        val nextData = Regex("""self\.__next_f\.push\(\[1,"(.*)"]\)""").findAll(html)
            .map { it.groupValues[1] }
            .joinToString("")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")

        val scriptData = if (nextData.contains(""""sources_url":""")) nextData else html
        val pattern     = Regex("""\{"id":\d+,"name":"[^"]*","slug":"[^"]*","lang":"[^"]*","season":\d+,"number":$epParam,"sources_url":"([^"]+)"""")
        val match       = pattern.find(scriptData)

        val sourcesUrl = if (match != null) {
            match.groupValues[1].replace("\\/", "/")
        } else {
            Regex(""""sources_url":"([^"]+)"""").find(scriptData)?.groupValues?.get(1)?.replace("\\/", "/") ?: return false
        }

        val apiResponse = app.get(
            sourcesUrl,
            headers = mapOf("Referer" to data, "Origin" to mainUrl)
        ).text

        val rawFile   = Regex(""""file"\s*:\s*"([^"]+)"""").find(apiResponse)?.groupValues?.get(1) ?: return false
        val baseFile  = rawFile.replace("\\/", "/").removeSuffix("/")
        val streamUrl = "$baseFile/index.json"

        callback(
            newExtractorLink(
                source = name,
                name   = name,
                url    = streamUrl,
                type  = ExtractorLinkType.M3U8
            ) {
                this.headers = mutableMapOf("Referer" to "$mainUrl/", "Origin" to mainUrl)
            }
        )

        return true
    }
}