// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Document

class AnimeWorld : MainAPI() {
    override var mainUrl = "https://animeworld.ac"
    override var name = "AnimeWorld"
    override val hasMainPage = true
    override var lang = "it"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Anime)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "${mainUrl}/updated" to "Nuovi Episodi",
        "${mainUrl}/animes" to "Anime",
        "${mainUrl}/ongoing" to "In Corso",
        "${mainUrl}/movies" to "Film Anime"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}?page=$page"
        val document = app.get(url).document
        val home = document.select("div.film-list div.item").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home, hasNext = true)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val titleElement = this.selectFirst("a.name") ?: return null
        val title = titleElement.text() ?: return null
        val href = fixUrlNull(titleElement.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val isDub = this.selectFirst("div.status div.dub") != null

        return newAnimeSearchResponse(title, href, TvType.AnimeMovie) {
            this.posterUrl = posterUrl
            addDubStatus(isDub)
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page == 1) {
            "${mainUrl}/filter?sort=0&keyword=${query}"
        } else {
            "${mainUrl}/filter?sort=0&keyword=${query}&page=$page"
        }

        val results = app.get(url).document.select("div.film-list div.item")
            .mapNotNull { it.toMainPageResult() }

        return newSearchResponseList(results, hasNext = true)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val realUrl = app.get(url, allowRedirects = false).headers["Location"] ?: return null
        val document = app.get(realUrl).document

        val title = document.selectFirst("h2.title")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.thumb img")?.attr("src"))
        val year = document.select("dl.meta dd")
            .firstOrNull { it.text().contains("20") || it.text().contains("19") }?.text()?.trim()
            ?.takeLast(4)?.toIntOrNull()
        val tags = document.select("dl.meta dd a[href*=/genre/]").map { it.text() }
        val rating = document.selectFirst("span#average-vote")?.text()?.trim()?.toDoubleOrNull()
        val duration =
            document.select("dl.meta dd").firstOrNull { it.text().contains("min/ep") }?.text()
                ?.trim()?.split(" ")?.firstOrNull()?.toIntOrNull()
        val plot = document.selectFirst("div.desc")?.text()?.trim()
        val status = when (document.select("dl.meta dd a[href*=/status/]").text().trim()) {
            "Finito" -> ShowStatus.Completed
            "In corso" -> ShowStatus.Ongoing
            else -> null
        }

        val episodes = document.select("ul.episodes li.episode a").mapNotNull {
            val epId = it.attr("data-id") ?: return@mapNotNull null
            val epNum = it.attr("data-episode-num").toIntOrNull() ?: return@mapNotNull null
            newEpisode(epId) {
                this.episode = epNum
            }
        }

        if (episodes.isEmpty()) return null

        return newTvSeriesLoadResponse(title, realUrl, TvType.Anime, episodes) {
            this.posterUrl = poster
            this.year = year
            this.tags = tags
            this.plot = plot
            this.duration = duration
            this.showStatus = status
            this.recommendations = recommendations(document)
            rating?.let { this.score = Score.from10(it) }
        }
    }

    private fun recommendations(document: Document): List<SearchResponse> {
        return document.select("div.interesting div.item").mapNotNull {
            val onerititle = it.selectFirst("a.name")?.text() ?: return@mapNotNull null
            val onerihref = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val oneriposter = fixUrlNull(it.selectFirst("img")?.attr("src"))
            newAnimeSearchResponse(onerititle, onerihref, TvType.Anime) { this.posterUrl = oneriposter }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("Ayzen_$name", "data = $data")

        val realData =
            app.get(data, allowRedirects = false).headers["Location"]?.substringAfterLast("/")
                ?: return false
        val response =
            app.get("${mainUrl}/api/episode/info?id=$realData&alt=0").parsedSafe<EpisodeInfo>()
                ?: return false

        callback(
            newExtractorLink(
                source = name,
                name = name,
                url = response.grabber,
                type = ExtractorLinkType.VIDEO
            )
        )

        return true
    }

    data class EpisodeInfo(
        @JsonProperty("grabber") val grabber: String
    )
}