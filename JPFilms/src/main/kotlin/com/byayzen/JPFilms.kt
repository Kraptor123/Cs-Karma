// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.network.WebViewResolver
import org.jsoup.Jsoup

class JPFilms : MainAPI() {
    override var mainUrl = "https://jp-films.com"
    override var name = "JPFilms"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "${mainUrl}/filter-movies/sort/formality/status/country/release/20/" to "Action",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/2659/" to "Action & Adventure",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/295/" to "Adventure",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/3015/" to "Animation",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/56/" to "Comedy",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/4/" to "Crime",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/1055/" to "Documentary",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/5/" to "Drama",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/920/" to "Family",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/894/" to "Fantasy",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/619/" to "History",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/258/" to "Horror",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/71/" to "Jidaigeki",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/1785/" to "Music",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/11/" to "Mystery",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/2464/" to "Pinku",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/281/" to "Romance",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/318/" to "Science Fiction",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/119/" to "Thriller",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/1657/" to "TV Movie",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/249/" to "War",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/1729/" to "Western",
        "${mainUrl}/filter-movies/sort/formality/status/country/release/32/" to "Yakuza"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) {
            request.data
        } else {
            "${request.data.removeSuffix("/")}/page/$page"
        }

        val document = app.get(url).document
        val home = document.select("article.thumb.grid-item").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page == 1) {
            "$mainUrl/search/$query"
        } else {
            "$mainUrl/search/$query/page/$page"
        }

        val document = app.get(url).document
        val searchResults = document.select("article.thumb.grid-item").mapNotNull {
            it.toSearchResult()
        }

        val hasNext = document.selectFirst("ul.pagination li.active + li, a.next") != null

        return newSearchResponseList(searchResults, hasNext = hasNext)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a.halim-thumb")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(
            this.selectFirst("figure img")?.attr("data-src")
                ?: this.selectFirst("figure img")?.attr("src")
        )

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val rawTitle = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null
        val title = rawTitle.replace(Regex("\\s*\\(\\d{4}\\)$"), "")
        val poster = fixUrlNull(document.selectFirst(".movie-thumb")?.attr("src"))
        var description = document.select("article.item-content").text().trim()

        val year = document.selectFirst(".released a[href*='release']")?.text()?.trim()?.toIntOrNull()
        val tags = document.select(".category a[href*='genres']").map { it.text() }

        val ratingtxt = document.selectFirst(".halim_imdbrating .score")?.text()?.trim()?.toDoubleOrNull()
        val score = ratingtxt?.times(2.0)

        val durationtxt = document.select(".released").text()
        val duration = Regex("""(\d+)\s*min""").find(durationtxt)?.groupValues?.get(1)?.toIntOrNull()

        val actors = document.select(".directors a").map { Actor(it.text(), "Director") } +
                document.select(".actors a").map { Actor(it.text()) }

        val episodes = mutableListOf<Episode>()
        val freeornot = document.select(".nav-tabs li a").find { it.text().contains("Free", true) }

        if (freeornot != null) {
            val targetId = freeornot.attr("href")
            val episodeElements = document.select("$targetId .halim-list-eps li")

            episodeElements.forEach { element ->
                val linkElement = element.selectFirst("a")
                val spanElement = element.selectFirst("span")

                val href = fixUrlNull(linkElement?.attr("href")) ?: fixUrlNull(spanElement?.attr("data-href"))
                val name = spanElement?.text()?.trim() ?: linkElement?.text()?.trim() ?: "Episode"

                if (href != null) {
                    val episodeNum = Regex("""\d+""").find(name)?.value?.toIntOrNull()
                    episodes.add(
                        newEpisode(href) {
                            this.name = name
                            this.episode = episodeNum
                        }
                    )
                }
            }
        } else {
            description = "This content is only for paid users!"
        }



        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            this.score = Score.from10(score)
            this.duration = duration
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val freeornot = document.select(".nav-tabs li a").find { it.text().contains("Free", true) }
        if (freeornot == null) return false

        val currentSlugMatch = Regex("""(ep-\d+)""").find(data)
        val currentSlug = currentSlugMatch?.value
        val targetId = freeornot.attr("href")

        document.select("$targetId .halim-list-eps .halim-btn").forEach { element ->
            val slug = element.attr("data-episode-slug")
            val serverId = element.attr("data-server")
            val postId = element.attr("data-post-id")

            if (currentSlug == null || slug == currentSlug || slug.contains("movie", true)) {
                val ajaxUrl = "$mainUrl/wp-content/themes/halimmovies/player.php?episode_slug=$slug&server_id=$serverId&post_id=$postId"

                try {
                    val responseText = app.get(
                        ajaxUrl,
                        headers = mapOf("x-requested-with" to "XMLHttpRequest")
                    ).text

                    val json = AppUtils.parseJson<JpResponse>(responseText)
                    val sourceHtml = json.data?.sources ?: return@forEach
                    val m3u8Url = Jsoup.parse(sourceHtml).selectFirst("source")?.attr("src")

                    if (m3u8Url != null) {
                        callback.invoke(
                            newExtractorLink(
                                name = "JPFilms",
                                source = "JPFilms",
                                url = m3u8Url,
                                type = INFER_TYPE
                            ) {
                                this.headers = mapOf(
                                    "Referer" to "$mainUrl/",
                                    "Origin" to mainUrl
                                )
                            }
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return true
    }


    data class JpSourceData(
        @JsonProperty("status") val status: Boolean? = null,
        @JsonProperty("sources") val sources: String? = null
    )

    data class JpResponse(
        @JsonProperty("data") val data: JpSourceData? = null
    )
    }