package com.byayzen

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.util.concurrent.atomic.AtomicInteger

class Yablom : MainAPI() {
    override var mainUrl              = "https://yablom.com"
    override var name                 = "Yablom"
    override val hasMainPage          = true
    override var lang                 = "fr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    override var sequentialMainPage   = true
    override val getMainPageTimeoutMs = 300L

    companion object {
        private val requestCounter = AtomicInteger(0)
        private const val AUTH_COOKIE = "g=true"
        private const val TAG = "YablomLog"
    }

    override val mainPage = mainPageOf(
        "/euvcw7/c/yablom/29/0" to "À l'affiche",
        "/euvcw7/c/yablom/2/0"  to "Animation",
        "/euvcw7/c/yablom/26/0" to "Documentaire",
        "/euvcw7/c/yablom/3/0"  to "Spectacle"
    )

    private val defaultHeaders = mapOf(
        "Cookie" to AUTH_COOKIE,
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:151.0) Gecko/20100101 Firefox/151.0",
        "Accept" to "*/*"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val catId = request.data.split("/").filter { it.isNotEmpty() }.getOrNull(3) ?: ""
        val limit = 20
        val offset = (page - 1) * limit
        val url = "$mainUrl/euvcw7/api_category.php?catid=$catId&offset=$offset&limit=$limit&folder=euvcw7&pr=yablom"

        val res = app.get(url, headers = defaultHeaders, referer = "$mainUrl${request.data}")
        val response = res.text
        val (results, hasMore) = parseJsonData(response)

        return newHomePageResponse(request.name, results, hasMore)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val limit = 20
        val offset = (page - 1) * limit
        val url = "$mainUrl/euvcw7/api_search.php?searchword=$query&offset=$offset&limit=$limit&folder=euvcw7&pr=yablom"

        val res = app.get(
            url = url,
            headers = defaultHeaders + ("X-Requested-With" to "XMLHttpRequest"),
            referer = "$mainUrl/euvcw7/home/yablom"
        )

        val response = res.text
        val (results, hasMore) = parseJsonData(response)

        return newSearchResponseList(results, hasMore)
    }

    private fun parseJsonData(jsonString: String): Pair<List<SearchResponse>, Boolean> {
        return try {
            val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val data = mapper.readValue<FilmResponse>(jsonString)

            val results = data.films?.map { film ->
                newMovieSearchResponse(film.title ?: "No Title", fixUrl(film.link ?: ""), TvType.Movie) {
                    this.posterUrl = film.poster
                }
            } ?: emptyList()

            Pair(results, data.hasMore ?: false)
        } catch (e: Exception) {
            Pair(emptyList(), false)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val currentCount = requestCounter.incrementAndGet()

        if (currentCount <= 2) {
            Log.d(TAG, "LOAD ENGELLENDİ: Preload koruması aktif. İstek No: #$currentCount | URL: $url")
            return null
        }

        val res = app.get(url, headers = defaultHeaders)
        val document = res.document

        val yiltitle = document.selectFirst("div.film-detail-title")?.text()?.trim() ?: return null

        val year = Regex("""\((\d{4})\)""").find(yiltitle)?.groupValues?.get(1)?.toIntOrNull()
        val title = yiltitle.replace(Regex("""\(\d{4}\)"""), "").trim()

        val poster = fixUrlNull(document.selectFirst("div.film-detail-header img")?.attr("src"))
        val plot = document.selectFirst("#film-synopsis-text")?.text()?.trim()
        val tags = document.select("a.film-detail-cat").map { it.text() }

        val recommendations = document.select("a.showcase-card").mapNotNull {
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
        val title = this.selectFirst(".film-card-title")?.text()?.trim() ?: return null
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
        val res = app.get(data, headers = defaultHeaders)
        val document = res.document
        val iframe = document.selectFirst("div.film-player iframe")?.attr("src")

        if (iframe != null) {
            val fixedIframe = fixUrl(iframe)
            loadExtractor(fixedIframe, "$mainUrl/", subtitleCallback, callback)
        }

        return true
    }
}

data class FilmItem(
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("poster") val poster: String? = null,
    @param:JsonProperty("link") val link: String? = null
)

data class FilmResponse(
    @param:JsonProperty("films") val films: List<FilmItem>? = null,
    @param:JsonProperty("hasMore") val hasMore: Boolean? = null
)