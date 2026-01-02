// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.

package com.kraptor

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class FullMatchShows : MainAPI() {
    override var mainUrl              = "https://fullmatchshows.com"
    override var name                 = "FullMatchShows"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Live)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "${mainUrl}/"                  to "Last Matches",
        "${mainUrl}/leagues/england/"              to "England",
        "${mainUrl}/leagues/premier-league/"       to "Premier League",
        "${mainUrl}/leagues/championship/"         to "ChampionShip",
        "${mainUrl}/leagues/fa-cup/"               to "FA Cup",
        "${mainUrl}/leagues/carabao-cup/"          to "Carabao Cup",
        "${mainUrl}/leagues/spain/"                to "Spain",
        "${mainUrl}/leagues/la-liga/"              to "La liga",
        "${mainUrl}/leagues/copa-del-rey/"         to "Copa Del Rey",
        "${mainUrl}/leagues/italy/"                to "Italy",
        "${mainUrl}/leagues/serie-a/"              to "Serie A",
        "${mainUrl}/leagues/coppa-italia/"         to "Coppa Italia",
        "${mainUrl}/leagues/germany/"              to "Germany",
        "${mainUrl}/leagues/dfb-pokal/"            to "DFB Pokal",
        "${mainUrl}/leagues/bundesliga/"           to "BundesLiga",
        "${mainUrl}/leagues/eredivisie/"           to "Eredivisie",
        "${mainUrl}/leagues/europe/"               to "Europe",
        "${mainUrl}/leagues/champions-league/"     to "Champions League",
        "${mainUrl}/leagues/europa-league/"        to "Europa League",
        "${mainUrl}/leagues/nations-league/"       to "Nations League",
        "${mainUrl}/leagues/super-cup/"            to "Super Cup",
        "${mainUrl}/leagues/international/"        to "International",
        "${mainUrl}/leagues/friendly-match/"       to "Friendly Match",
        "${mainUrl}/leagues/club-friendlies/"      to "Club Friendlies",
        "${mainUrl}/leagues/world-cup-qualifiers/" to "World Cup Qualifiers",
        "${mainUrl}/leagues/liga-portugal/"        to "Liga Portugal"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = if (page == 1) {
            app.get("${request.data}", referer = "${mainUrl}/").document
        } else {
            app.get("${request.data}/page/$page/", referer = "${mainUrl}/").document
        }
        val home     = document.select("li.post-item").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(HomePageList(request.name, home, true))
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("h2")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Live) {
            this.posterUrl = posterUrl
            this.posterHeaders = mapOf("Referer" to "${mainUrl}/")
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val document = if (page == 1){
            app.get("${mainUrl}/?s=${query}").document
        } else {
            app.get("${mainUrl}/page/$page/?s=${query}").document
        }

        val aramaCevap = document.select("li.post-item").mapNotNull { it.toMainPageResult() }

        return newSearchResponseList(aramaCevap, hasNext = true)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, referer = "${mainUrl}/").document

        val title           = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("figure img")?.attr("src"))
        val description     = document.selectFirst("div.stream-item + p")?.text()?.trim()
        val year            = document.selectFirst("div._tableContainer_1rjym_1 td:contains(Date) + td")?.text()?.trim()?.substringAfterLast(" ")?.toIntOrNull()
        val tags            = document.select("div.sgeneros a").map { it.text() }
        val rating          = document.selectFirst("span.dt_rating_vgs")?.text()?.trim()?.toIntOrNull()
        val duration        = document.selectFirst("span.runtime")?.text()?.split(" ")?.first()?.trim()?.toIntOrNull()
        val recommendations = document.select("div.related-posts-list div.related-item").mapNotNull { it.toRecommendationResult() }
        val actors          = document.select("span.valor a").map { Actor(it.text()) }
        val trailer         = Regex("""embed\/(.*)\?rel""").find(document.html())?.groupValues?.get(1)?.let { "https://www.youtube.com/embed/$it" }

        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl       = poster
            this.plot            = description
            this.year            = year
            this.tags            = tags
            this.score           = Score.from10(rating)
            this.duration        = duration
            this.recommendations = recommendations
            addActors(actors)
            addTrailer(trailer)
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title     = this.selectFirst("h3 a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Live) { this.posterUrl = posterUrl }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("kraptor_$name", "data = ${data}")

        val document = app.get(data).document

        val videoUrl        = document.select("a.myButton:contains(Full)")

        videoUrl.forEach { video ->
            val link = video?.attr("href").toString()
            Log.d("kraptor_$name", "link = ${link}")
            loadExtractor(link, "${mainUrl}/", subtitleCallback, callback)
        }

        return true
    }
}