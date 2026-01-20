// ! Bu araç @Kraptor123 tarafından | @cs-kraptor için yazılmıştır.

package com.kraptor

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors

class Krmzy : MainAPI() {
    override var mainUrl              = "https://krmzy.org"
    override var name                 = "Krmzy"
    override val hasMainPage          = true
    override var lang                 = "ar"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "${mainUrl}/series-list/" to "Series"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = if (page == 1){
            app.get("${request.data}").document
        } else {
            app.get("${request.data}/page/$page/").document
        }
        val home     = document.select("article.postEp div.block-post").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(HomePageList(request.name, home, true))
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("div.title")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = this.selectFirst("div.imgSer, div.imgBg")?.attr("style")?.substringAfter("url(")?.substringBefore(");")

        val poster = fixUrlNull(posterUrl)

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
    }
    override suspend fun search(query: String, page: Int): SearchResponseList {
        val document = app.get("${mainUrl}/search/${query}/").document

        val aramaCevap = document.select("div.block-post").mapNotNull { it.toMainPageResult() }

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
        val dizi = document.selectFirst("article.postEp div.block-post")

        val bolumler = document.select("article.postEp div.block-post").map { bolum ->
            val title     = bolum.selectFirst("a")?.attr("title") ?: return null
            val href      = fixUrlNull(bolum.selectFirst("a")?.attr("href")) ?: return null
            val posterUrl = fixUrlNull(bolum.selectFirst("div.imgSer")?.attr("style")?.substringAfter("url(")?.substringBefore(");"))
            val epNum     = bolum.selectFirst("div.episodeNum span + span")?.text()?.toIntOrNull()
            newEpisode(href, {
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

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("kraptor_$name", "data = ${data}")
        val document = app.get(data).document

        val videoServer = document.selectFirst("a.fullscreen-clickable")?.attr("href") ?: ""

        val videoAl = app.get(videoServer, referer = "$mainUrl/").document

        val videolar = videoAl.select("ul.serversList li")

        videolar.forEach { video ->
            val vServer = video.attr("data-server")
            Log.d("kraptor_$name", "vServer = ${vServer}")
            val vName = video.attr("data-name")
            Log.d("kraptor_$name", "vName = ${vName}")
            if (vServer.contains("http")){
                loadExtractor(vServer, "${mainUrl}/", subtitleCallback, callback)
            } else if (vName.contains("ok", true)){
                val okru = "https://ok.ru/videoembed/$vServer"
                loadExtractor(okru, "${mainUrl}/", subtitleCallback, callback)
            } else if (vName.contains("Arab", true)) {
                val url = "https://v.turkvearab.com/embed-$vServer.html"
                Log.d("kraptor_$name", "arab url = ${url}")
                loadExtractor(url, "${mainUrl}/", subtitleCallback, callback)
            } else if (vName.contains("Red", true)){
                val url =  "https://iplayerhls.com/e/$vServer"
                Log.d("kraptor_$name", "Red url = ${url}")
                loadExtractor(url, "${mainUrl}/", subtitleCallback, callback)
            } else if (vName.contains("Pro", true)) {
                val url = "https://w.larhu.website/hls/$vServer.m3u8"
                Log.d("kraptor_$name", "Pro url = ${url}")
                callback.invoke(newExtractorLink(
                    source = vName,
                    name   = vName,
                    url    = url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "https://qesen.net/"
                })
            } else {
                val url = "https://arabveturk.com/embed-$vServer.html"
                Log.d("kraptor_$name", "turkvearab url = ${url}")
                loadExtractor(url, "${mainUrl}/", subtitleCallback, callback)
            }
        }
        val iframe = fixUrlNull(videoAl.selectFirst("iframe")?.attr("src")) ?: ""
        loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)

        return true
    }
}