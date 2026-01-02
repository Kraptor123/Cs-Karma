// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.

package com.kraptor

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class NbaReplay : MainAPI() {
    override var mainUrl              = "https://www.nba-replay.org"
    override var name                 = "NbaReplay"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Live)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "${mainUrl}/"                      to "Ana Sayfa",
        "${mainUrl}/category/atlanta-hawks"            to "Atlanta Hawks",
        "${mainUrl}/category/boston-celtics"           to "Boston Celtics",
        "${mainUrl}/category/brooklyn-nets"            to "Brooklyn Nets",
        "${mainUrl}/category/charlotte-hornets"        to "Charlotte Hornets",
        "${mainUrl}/category/chicago-bulls"            to "Chicago Bulls",
        "${mainUrl}/category/cleveland-cavaliers"      to "Cleveland Cavaliers",
        "${mainUrl}/category/dallas-mavericks"         to "Dallas Mavericks",
        "${mainUrl}/category/denver-nuggets"           to "Denver Nuggets",
        "${mainUrl}/category/detroit-pistons"          to "Detroit Pistons",
        "${mainUrl}/category/golden-state-warriors"    to "Golden State Warriors",
        "${mainUrl}/category/guangzhou-loong-lions"    to "Guangzhou Loong-Lions",
        "${mainUrl}/category/houston-rockets"          to "Houston Rockets",
        "${mainUrl}/category/indiana-pacers"           to "Indiana Pacers",
        "${mainUrl}/category/la-clippers"              to "LA Clippers",
        "${mainUrl}/category/los-angeles-clippers"     to "Los Angeles Clippers",
        "${mainUrl}/category/los-angeles-lakers"       to "Los Angeles Lakers",
        "${mainUrl}/category/melbourne-united"         to "Melbourne United",
        "${mainUrl}/category/memphis-grizzlies"        to "Memphis Grizzlies",
        "${mainUrl}/category/miami-heat"               to "Miami Heat",
        "${mainUrl}/category/milwaukee-bucks"          to "Milwaukee Bucks",
        "${mainUrl}/category/minnesota-timberwolves"   to "Minnesota Timberwolves",
        "${mainUrl}/category/nba-summer-league"        to "NBA Summer League",
        "${mainUrl}/category/new-orleans-pelicans"     to "New Orleans Pelicans",
        "${mainUrl}/category/new-york-knicks"          to "New York Knicks",
        "${mainUrl}/category/oklahoma-city-thunder"    to "Oklahoma City Thunder",
        "${mainUrl}/category/orlando-magic"            to "Orlando Magic",
        "${mainUrl}/category/philadelphia-76ers"       to "Philadelphia 76ers",
        "${mainUrl}/category/phoenix-suns"             to "Phoenix Suns",
        "${mainUrl}/category/portland-trail-blazers"   to "Portland Trail Blazers",
        "${mainUrl}/category/sacramento-kings"         to "Sacramento Kings",
        "${mainUrl}/category/san-antonio-spurs"        to "San Antonio Spurs",
        "${mainUrl}/category/shows"                    to "Shows",
        "${mainUrl}/category/toronto-raptors"          to "Toronto Raptors",
        "${mainUrl}/category/utah-jazz"                to "Utah Jazz",
        "${mainUrl}/category/washington-wizards"       to "Washington Wizards"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = if (page == 1){
            app.get("${request.data}/", referer ="${mainUrl}/").document
        } else {
            app.get("${request.data}/page/$page/", referer ="${mainUrl}/").document
        }
        val home     = document.select("div.col-md-6").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(HomePageList(
            request.name, home, isHorizontalImages = true
        ))
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("h4.entry-title a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, "$href|$posterUrl", TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val document = if (page == 1) {
            app.get("${mainUrl}/?s=${query}", referer ="${mainUrl}/").document
        } else {
            app.get("${mainUrl}/page/$page/?s=${query}", referer ="${mainUrl}/").document
        }

        val aramaCevap = document.select("div.col-md-6").mapNotNull { it.toMainPageResult() }

        return newSearchResponseList(aramaCevap, hasNext = true)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val parts = url.split("|")
        val link     = parts[0]
        val poster     = parts[1]
        val document = app.get(link, referer ="${mainUrl}/").document

        val title           = document.selectFirst("h1")?.text()?.trim() ?: return null
        val description     = document.selectFirst("table.responsive-table + h3")?.text()?.trim()
        val year            = document.selectFirst("h3 strong:contains(,)")?.text()?.substringAfter(" ")?.substringBefore(",")?.trim()?.toIntOrNull()
        val tags            = document.select("div.post-category a").map { it.text() }
        val recommendations = document.select("div.col-md-4").mapNotNull { it.toRecommendationResult() }

        return newMovieLoadResponse(title, link, TvType.Movie, link) {
            this.posterUrl       = poster
            this.plot            = description
            this.year            = year
            this.tags            = tags
            this.recommendations = recommendations
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title     = this.selectFirst("img")?.attr("alt") ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, "$href|$posterUrl", TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
//        Log.d("kraptor_$name", "data = ${data}")
        val document = app.get(data, referer ="${mainUrl}/").document

        val links = document.select("td a").map { it.attr("href") }.toMutableList()
        document.selectFirst("iframe")?.attr("src")?.let { links.add(it) }

        links.forEach { url ->
//            Log.d("kraptor_$name", "url = ${url}")
            loadExtractor(url, "${mainUrl}/", subtitleCallback, callback)
        }

        return true
    }
}