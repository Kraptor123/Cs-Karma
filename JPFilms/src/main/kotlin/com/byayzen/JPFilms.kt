// ! Bu araç @ByAyzen tarafından | @CS-Karma için yazılmıştır.

package com.byayzen

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import org.json.JSONArray
import org.jsoup.Jsoup

class JPFilms : MainAPI() {
    override var mainUrl = "https://jp-films.com"
    override var name = "JPFilms"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "${mainUrl}/movies?sortby=newest" to "Movies - Newest",
        "${mainUrl}/movies?sortby=latest-update" to "Movies - Latest Update",
        "${mainUrl}/movies?sortby=mostview" to "Movies - Most View",
        "${mainUrl}/tv-series?sortby=newest" to "TV Series - Newest",
        "${mainUrl}/tv-series?sortby=latest-update" to "TV Series - Latest Update",
        "${mainUrl}/tv-series?sortby=mostview" to "TV Series - Most View"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) {
            request.data
        } else {
            val base = request.data.split("?").first().removeSuffix("/")
            val query = request.data.split("?").getOrNull(1)
            if (query != null) "$base/page/$page/?$query" else "$base/page/$page/"
        }
        val document = app.get(url).document
        val home = document.select("article.thumb.grid-item").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(request.name, home, hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst(".entry-title")?.text() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val poster = this.selectFirst("img")?.let { img ->
            val url = img.attr("data-src").ifEmpty { img.attr("src") }
            fixUrlNull(url)
        }

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster?.toString()
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page <= 1) "$mainUrl/search/$query" else "$mainUrl/search/$query/page/$page"
        val document = app.get(url).document
        val searchresults = document.select("article.thumb.grid-item").mapNotNull {
            it.toSearchResult()
        }

        val hasnext = document.select("ul.page-numbers li a").any {
            it.text().contains((page + 1).toString()) || it.hasClass("next")
        }

        return newSearchResponseList(searchresults, hasnext)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url)
        val document = response.document

        val title = document.selectFirst("h1.entry-title")?.text()?.replace(Regex("\\s*\\(\\d{4}\\)$"), "")?.trim() ?: return null

        val posterraw = document.selectFirst("img.movie-thumb")?.let { it.attr("data-src").ifEmpty { it.attr("src") } }
            ?: document.selectFirst(".movie-poster img")?.let { it.attr("data-src").ifEmpty { it.attr("src") } }
        val poster = fixUrlNull(posterraw)

        val country = document.select("p.actors:contains(Country:) a").map { it.text() }
        val tags = document.select(".category a").map { it.text() } + country

        val actors = document.select(".directors a").map { Actor(it.text(), "Director") } +
                document.select(".actors a").filter { it.parent()?.text()?.contains("Country:") == false }.map { Actor(it.text()) }

        val ratingtxt = document.selectFirst(".imdb-icon")?.attr("data-rating")?.toDoubleOrNull()
            ?: document.selectFirst(".halim_imdbrating .score")?.text()?.toDoubleOrNull()?.times(2.0)

        val duration = Regex("""(\d+)\s*min""").find(document.select("p.released").text())?.groupValues?.get(1)?.toIntOrNull()
        val year = document.selectFirst("p.released a[href*='release']")?.text()?.toIntOrNull()

        val allepisodes = mutableListOf<Episode>()

        val scriptdata = document.select("script").map { it.data() }.find { it.contains("var jsonEpisodes") }
        if (scriptdata != null) {
            val jsonstr = scriptdata.substringAfter("var jsonEpisodes = ").substringBefore(";</script>").trim().removeSuffix(";")
            try {
                val outerarray = JSONArray(jsonstr)
                for (i in 0 until outerarray.length()) {
                    val innerarray = outerarray.getJSONArray(i)
                    for (j in 0 until innerarray.length()) {
                        val epobj = innerarray.getJSONObject(j)
                        val sid = epobj.optInt("serverId")
                        if (sid == 2) {
                            val epurl = epobj.getString("postUrl").replace("\\/", "/")
                            val epname = epobj.optString("episodeName")
                            allepisodes.add(newEpisode(epurl) {
                                this.name = epname
                                this.episode = Regex("""\d+""").find(epname)?.value?.toIntOrNull()
                            })
                        }
                    }
                }
            } catch (e: Exception) { }
        }

        val recommendations = document.select(".related-film article.thumb").mapNotNull {
            it.toSearchResult()
        }

        val plot = document.select("article.item-content p").text().trim()

        return if (allepisodes.size <= 1) {
            newMovieLoadResponse(title, url, TvType.Movie, allepisodes.firstOrNull()?.data ?: url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
                this.score = ratingtxt?.let { Score.from10(it) }
                this.duration = duration
                this.recommendations = recommendations
                addActors(actors)
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.Movie, allepisodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
                this.score = ratingtxt?.let { Score.from10(it) }
                this.duration = duration
                this.recommendations = recommendations
                addActors(actors)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val scripts = document.select("script").map { it.data() }

        val scriptdata = scripts.find { it.contains("var jsonEpisodes") } ?: return false
        val jsonstr = scriptdata.substringAfter("var jsonEpisodes = ").substringBefore(";</script>").trim()
        val currentslug = data.split("/").lastOrNull()?.replace(".html", "") ?: ""

        val matches = Regex("""\{"postId":(\d+),"postUrl":"(.*?)","serverId":(\d+),.*?"episodeSlug":"(.*?)","episodeName":"(.*?)".*?\}""").findAll(jsonstr)

        matches.forEach { match ->
            val postid = match.groupValues[1]
            val posturl = match.groupValues[2].replace("\\/", "/")
            val serverid = match.groupValues[3]
            val slug = match.groupValues[4]

            if (data == posturl || slug == currentslug || currentslug.contains(slug)) {
                val ajaxurl = "$mainUrl/wp-content/themes/halimmovies/player.php?episode_slug=$slug&server_id=$serverid&post_id=$postid"

                try {
                    val response = app.get(
                        ajaxurl,
                        headers = mapOf(
                            "x-requested-with" to "XMLHttpRequest",
                            "referer" to data,
                            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                        )
                    ).text

                    val m3u8url = Regex("""<source[^>]+src=["']([^"']+)["']""").find(response)?.groupValues?.get(1)
                        ?: Jsoup.parse(response).selectFirst("source")?.attr("src")

                    if (m3u8url != null) {
                        callback.invoke(
                            newExtractorLink(
                                name = "JPFilms",
                                source = "JPFilms",
                                url = m3u8url,
                                type = INFER_TYPE
                            ) {
                                this.headers = mapOf(
                                    "Referer" to "$mainUrl/",
                                    "Origin" to mainUrl
                                )
                            }
                        )
                    }
                } catch (e: Exception) { }
            }
        }
        return true
    }
}