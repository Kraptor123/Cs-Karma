// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.

package com.kraptor

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class Esheaq : MainAPI() {
    override var mainUrl              = "https://x.esheeg.onl"
    override var name                 = "Esheaq"
    override val hasMainPage          = true
    override var lang                 = "ar"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "${mainUrl}/e3blk2h43q/"      to "جميع المسلسلات",
        "${mainUrl}/category/1-movi-tr/"      to "أفلام",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = if (page == 1){
            app.get("${request.data}").document
        }  else if (request.data.contains("1-movi-tr")){
            app.get("${request.data}?page=$page").document
        } else {
            app.get("${request.data}/page/$page/").document
        }
        val home     = document.select("div.load-post article").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(HomePageList(request.name, home, true))
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("a")?.attr("title") ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = this.selectFirst("img")?.attr("data-image") ?: this.selectFirst("div.imgBg")?.attr("style")?.substringAfter("url(")?.substringBefore(");")

        val poster = fixUrlNull(posterUrl)

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
    }
    override suspend fun search(query: String, page: Int): SearchResponseList {
        val document = if (page == 1){
            app.get("${mainUrl}/?s=${query}").document
        } else {
            app.get("${mainUrl}/page/$page/?s=${query}").document
        }

        val aramaCevap = document.select("div.load-post article").mapNotNull { it.toMainPageResult() }

        return newSearchResponseList(aramaCevap, hasNext = true)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = document.selectFirst("div.story")?.text()?.trim()
        val year = document.selectFirst("div.extra span.C a")?.text()?.trim()?.toIntOrNull()
        val tags = document.select("div.tax span:contains(الانواع) ~ a").map { it.text() }
        val score = document.selectFirst("span.dt_rating_vgs")?.text()?.trim()
        val duration = document.selectFirst("span.runtime")?.text()?.split(" ")?.first()?.trim()?.toIntOrNull()
        val actors = document.select("div.tax span:contains(الممثلين) ~ a").map { Actor(it.text()) }
        val dizi = document.selectFirst("div#epiList article")

        val bolumler = document.select("div#epiList article").map { bolum ->
            val title     = bolum.selectFirst("a")?.attr("title") ?: return null
            val href      = fixUrlNull(bolum.selectFirst("a")?.attr("href")) ?: return null
            val posterUrl = fixUrlNull(bolum.selectFirst("img")?.attr("data-image"))
            val epNum     = bolum.selectFirst("div.episodeNum span + span")?.text()?.toIntOrNull()
            newEpisode("${href}see/", {
                this.name = title
                this.posterUrl = posterUrl
                this.episode   = epNum
            })
        }

        return if (dizi != null) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, bolumler) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = Score.from10(score)
                this.duration = duration
                addActors(actors)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, "${url}see/") {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = Score.from10(score)
                this.duration = duration
                addActors(actors)
            }
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title     = this.selectFirst("a img")?.attr("alt") ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a img")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("kraptor_$name", "data = ${data}")
        val document = app.get(data).document

        val videoServer = document.select("div.secContainer ul.serversList li")

        videoServer.forEach { video ->
            val vServer = video.attr("data-src")

            Log.d("kraptor_$name", "vServer = ${vServer}")

            val serverAl = app.get(vServer, referer = "${mainUrl}/").document

            val iframe = fixUrlNull(serverAl.selectFirst("iframe")?.attr("src")) ?: ""

            Log.d("kraptor_$name", "iframe = ${iframe}")

            loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)

        }

        return true
    }
}