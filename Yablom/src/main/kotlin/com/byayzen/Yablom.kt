// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*

data class FilmItem(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("poster") val poster: String? = null,
    @JsonProperty("link") val link: String? = null
)

data class FilmResponse(
    @JsonProperty("films") val films: List<FilmItem>? = null,
    @JsonProperty("hasMore") val hasMore: Boolean? = null
)

class Yablom : MainAPI() {
    override var mainUrl              = "https://yablom.com"
    override var name                 = "Yablom"
    override val hasMainPage          = true
    override var lang                 = "fr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "/euvcw7/c/yablom/29/0" to "À l'affiche",
        "/euvcw7/c/yablom/2/0"  to "Animation",
        "/euvcw7/c/yablom/26/0" to "Documentaire",
        "/euvcw7/c/yablom/3/0"  to "Spectacle"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val catId = request.data.split("/").filter { it.isNotEmpty() }.getOrNull(3) ?: ""
        val limit = 20
        val offset = (page - 1) * limit
        val url = "$mainUrl/euvcw7/api_category.php?catid=$catId&offset=$offset&limit=$limit&folder=euvcw7&pr=yablom"

        val response = app.get(url, referer = "$mainUrl${request.data}").text
        val (results, hasMore) = parseJsonData(response)

        return newHomePageResponse(request.name, results, hasMore)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val limit = 20
        val offset = (page - 1) * limit
        val url = "$mainUrl/euvcw7/api_search.php?searchword=$query&offset=$offset&limit=$limit&folder=euvcw7&pr=yablom"

        val response = app.get(
            url = url,
            referer = "$mainUrl/euvcw7/home/yablom",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).text

        val (results, hasMore) = parseJsonData(response)

        return newSearchResponseList(results, hasMore)
    }

    private fun parseJsonData(jsonString: String): Pair<List<SearchResponse>, Boolean> {
        val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val data = mapper.readValue<FilmResponse>(jsonString)

        val results = data.films?.map { film ->
            newMovieSearchResponse(film.title ?: "", fixUrl(film.link ?: ""), TvType.Movie) {
                this.posterUrl = film.poster
            }
        } ?: emptyList()

        return Pair(results, data.hasMore ?: false)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.film-detail-title")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("a.film-detail-poster img")?.attr("src"))
        val plot = document.selectFirst("div.film-detail-synopsis")?.text()?.trim()
        val year = Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()
        val tags = document.select("a.film-detail-cat").map { it.text() }

        val recommendations = document.selectFirst("div.carousel-wrap")?.select("a.showcase-card")?.mapNotNull {
            it.toRecommendationResult()
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.tags = tags
            this.recommendations = recommendations
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title = this.selectFirst("div.film-card-title")?.text() ?: return null
        val href = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("Ayzen", "data = $data")
        val document = app.get(data).document

        val iframe = document.selectFirst("div.film-player iframe")?.attr("src")
        Log.d("Ayzen", "iframe = $iframe")

        if (iframe != null) {
            loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback)
        }

        return true
    }
}

