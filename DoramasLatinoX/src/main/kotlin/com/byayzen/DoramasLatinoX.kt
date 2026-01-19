// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

class DoramasLatinoX : MainAPI() {
    override var mainUrl = "https://doramaslatinox.com"
    override var name = "DoramasLatinoX"
    override val hasMainPage = true
    override var lang = "mx"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.AsianDrama)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "https://doramaslatinox.com/movies/" to "Todas las Películas",
        "https://doramaslatinox.com/estado/completo/" to "Completos",
        "https://doramaslatinox.com/estado/emision/" to "En Emision",
        "https://doramaslatinox.com/audio/latino/" to "Latino",
        "https://doramaslatinox.com/audio/subtitulado" to "Subtitulado",
        "https://doramaslatinox.com/tipo/dorama/" to "Doramas",
        "https://doramaslatinox.com/tipo/serie/" to "Series",
        "https://doramaslatinox.com/pais/corea-del-sur/" to "Corea del Sur",
        "https://doramaslatinox.com/pais/china/" to "China",
        "https://doramaslatinox.com/pais/japon/" to "Japón",
        "https://doramaslatinox.com/pais/tailandia/" to "Tailandia",
        "https://doramaslatinox.com/pais/taiwan/" to "Taiwán",
        "https://doramaslatinox.com/pais/singapur/" to "Singapur",
        "https://doramaslatinox.com/pais/estados-unidos/" to "Estados Unidos",
        "https://doramaslatinox.com/series/" to "Todas las Series"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page <= 1) {
            request.data
        } else {
            "${request.data.removeSuffix("/")}/page/$page/"
        }

        val document = app.get(url).document
        val home = document.select("article.item").mapNotNull {
            it.toMainPageResult()
        }

        return newHomePageResponse(
            listOf(HomePageList(request.name, home)),
            hasNext = home.isNotEmpty()
        )
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("div.data h3 a")?.text()?.trim() ?: return null
        val href = this.selectFirst("div.poster a")?.attr("href") ?: return null
        val posterUrl = fixUrlNull(
            this.selectFirst("div.poster img")?.attr("src")
                ?: this.selectFirst("div.poster img")?.attr("data-src")
        )

        val type = if (this.hasClass("tvshows")) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page == 1) {
            "$mainUrl/?s=$query"
        } else {
            "$mainUrl/page/$page/?s=$query"
        }

        val document = app.get(url).document
        val results = document.select("div.result-item article").mapNotNull {
            val titleElement = it.selectFirst("div.details div.title a") ?: return@mapNotNull null
            val title = titleElement.text()
            val href = titleElement.attr("href")
            val posterUrl = it.selectFirst("div.image div.thumbnail img")?.attr("src")

            newMovieSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }

        return newSearchResponseList(results, hasNext = results.isNotEmpty())
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description =
            document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        val year = document.selectFirst("div.extra span")?.text()?.trim()?.toIntOrNull()

        val tags = document.select("div.sgeneros a").map { it.text() }
        val rating = document.selectFirst("span.dt_rating_vgs")?.text()?.trim()?.toDoubleOrNull()
        val duration =
            document.selectFirst("span.runtime")?.text()?.replace(Regex("[^0-9]"), "")?.trim()
                ?.toIntOrNull()

        val trailer = document.selectFirst("iframe[src*='youtube']")?.attr("src")
            ?: Regex("""embed\/(.*)\?rel""").find(document.html())?.groupValues?.get(1)
                ?.let { "https://www.youtube.com/embed/$it" }
        val actors = document.select("div.person").mapNotNull {
            val name = it.selectFirst("div.name a")?.text() ?: return@mapNotNull null
            val role = it.selectFirst("div.caracter")?.text()
            val image = it.selectFirst("img")?.attr("src")

            val actor = Actor(name, image)
            actor to role
        }

        val recommendations =
            document.select("div.srelacionados article").mapNotNull { it.toRecommendationResult() }

        val isTvSeries = document.selectFirst("ul.episodios") != null

        if (isTvSeries) {
            val episodes = document.select("ul.episodios a").mapNotNull {
                val href = it.attr("href") ?: return@mapNotNull null
                val numText = it.selectFirst("div.numerando")?.text() ?: ""
                val season = numText.split("-").firstOrNull()?.trim()?.toIntOrNull() ?: 1
                val episode = numText.split("-").lastOrNull()?.trim()?.toIntOrNull() ?: 1
                val epTitle = it.selectFirst("div.episodiotitle")?.ownText()?.trim()
                val thumb = it.selectFirst("img")?.attr("src")

                newEpisode(href) {
                    this.name = epTitle
                    this.season = season
                    this.episode = episode
                    this.posterUrl = thumb
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = Score.from10(rating)
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.duration = duration
                this.score = Score.from10(rating)
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title = this.selectFirst("h3 a")?.text()
            ?: this.selectFirst("img")?.attr("alt")
            ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
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
        val document = app.get(data).document
        document.select("li.dooplay_player_option").forEach { option ->
            val post = option.attr("data-post")
            val type = option.attr("data-type")
            val nume = option.attr("data-nume")

            if (post.isNotEmpty()) {
                val apiUrl = "$mainUrl/wp-json/dooplayer/v2/$post/$type/$nume"
                val response = app.get(apiUrl).text
                val embedUrl =
                    AppUtils.parseJson<EmbedResponse>(response).embedUrl?.replace("\\/", "/")

                if (!embedUrl.isNullOrEmpty()) {
                    val iframe = app.get(embedUrl, referer = data).document.selectFirst("iframe")
                        ?.attr("src")
                    loadExtractor(iframe ?: embedUrl, embedUrl, subtitleCallback, callback)
                }
            }
        }
        return true
    }

    data class EmbedResponse(
        @JsonProperty("embed_url") val embedUrl: String? = null
    )
}