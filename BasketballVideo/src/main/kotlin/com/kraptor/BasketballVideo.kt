// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.

package com.kraptor

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class BasketballVideo : MainAPI() {
    override var mainUrl              = "https://basketball-video.com"
    override var name                 = "BasketballVideo"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Live)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "${mainUrl}/"                                                     to "Ana Sayfa",
        "${mainUrl}/2025-26"                                                         to "2025-26 NBA Season",
        "${mainUrl}/2024-25"                                                         to "2024-25 NBA Season",
        "${mainUrl}/2023-24"                                                         to "2023-24 NBA Season",
        "${mainUrl}/2022-23"                                                         to "2022-23 NBA Season",
        "${mainUrl}/videos/nba_full_game_replays/nba_2021_22_full_game_replays/48"   to "2021-22 NBA Season",
        "${mainUrl}/videos/nba_full_games/nba_regular_season_2020_21/5"              to "2020-21 NBA Season",
        "${mainUrl}/videos/nba_full_game_replays/nba_playoffs_2020/47"               to "2020 NBA Playoffs",
        "${mainUrl}/videos/nba_full_games/nba_playoffs_2019/7"                       to "NBA Playoffs 2019",
        "${mainUrl}/videos/nba_full_games/nba_playoffs_2018/8"                       to "NBA Playoffs 2018",
        "${mainUrl}/videos/nba_full_games/1-2-3-4-251-999/10"                        to "NBA Archive Full Game",
        "${mainUrl}/videos/nba_news_tv_show/nba_tv_show/46"                          to "NBA TV Shows",
        "${mainUrl}/wnba-video"                                                      to "WNBA",
        "${mainUrl}/ncaa_video"                                                      to "College Basketball",
        "${mainUrl}/videos/nba_full_games/nba_all_star_full_game/9"                  to "NBA All Star Games",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = if (page == 1){
            app.get("${request.data}").document
        } else {
            app.get("${request.data}?page$page").document
        }
        val home     = document.select("div[id^=entryID]").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(HomePageList(request.name, home, isHorizontalImages = true))
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("h3 a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val document = if (page == 1) {
            app.get("${mainUrl}/search/?q=${query}", referer ="${mainUrl}/").document
        } else {
            app.get("${mainUrl}/search/?q=${query}&t=0&p=$page;md=", referer ="${mainUrl}/").document
        }

        val aramaCevap = document.select("table").mapNotNull { it.toSearchResult() }

        return newSearchResponseList(aramaCevap, hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("div.eTitle a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title           = document.selectFirst("h1.h_title")?.text()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("div.full_img img")?.attr("src"))
        val description     = document.selectFirst("p strong span[style=font-size:16px;]")?.text()?.trim()
        val year            = document.selectFirst("p strong span[style=font-size:16px;]")?.text()?.substringAfter(", ")?.substringBefore(" ")?.trim()?.toIntOrNull()
        val tags            = document.select("span.ed-value a").map { it.text() }
        val recommendations = document.select("td.infTd").mapNotNull { it.toRecommendationResult() }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.plot            = description
            this.year            = year
            this.tags            = tags
            this.recommendations = recommendations
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title     = this.selectFirst("h5")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("kraptor_$name", "data = ${data}")
        val document = app.get(data).document

        val iframe = document.selectFirst("div.video-responsive iframe")?.attr("src") ?: ""

        Log.d("kraptor_$name", "iframe = ${iframe}")

        val bosMu = document.selectFirst("td a[onclick]")?.attr("onclick")?.substringAfter("('")?.substringBefore("'") ?: ""


       if (iframe.isNotEmpty()) {
           loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
       } else if (bosMu.isNotEmpty()) {
           document.select("td a[onclick]").map { onclick ->
             val on = onclick.attr("onclick").substringAfter("('").substringBefore("'")
               loadExtractor(on, "${mainUrl}/", subtitleCallback, callback)
           }
       }
       else {
           document.select("strong a.su-button").map { link ->
               val gitLink = fixUrlNull(link.attr("href")).toString()

               Log.d("kraptor_$name", "gitLink = ${gitLink}")

               if (gitLink.contains("gamesontvtoday.com")){
                   Log.d("kraptor_$name", "gitLink = ${gitLink}")

                   val siteGit = app.get(gitLink).document

                   val iframe = fixUrlNull(siteGit.selectFirst("iframe")?.attr("src")).toString()
                   Log.d("kraptor_$name", "iframe = ${iframe}")
                   loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
               } else {
                   loadExtractor(gitLink, "${mainUrl}/", subtitleCallback, callback)
               }
       }



       }

        return true
    }
}