// ! Bu araç @Kraptor123 tarafından | @CS-Karma için yazılmıştır.

package com.kraptor

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.network.WebViewResolver

class YoTurkish : MainAPI() {
    override var mainUrl              = "https://yoturkish.to"
    override var name                 = "YoTurkish"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "${mainUrl}/series/"      to "Series",
        "${mainUrl}/genre/adventure/"     to "Adventure",
        "${mainUrl}/genre/action/"        to "Action",
        "${mainUrl}/genre/romance/"       to "Romance",
        "${mainUrl}/genre/drama/"         to "Drama",
        "${mainUrl}/genre/comedy/"        to "Comedy",
        "${mainUrl}/genre/crime/"         to "Crime",
        "${mainUrl}/genre/family/"        to "Family",
        "${mainUrl}/genre/history/"       to "History",
        "${mainUrl}/genre/mystery/"       to "Mystery",
        "${mainUrl}/genre/thriller/"      to "Thriller",
        "${mainUrl}/genre/war/"           to "War",
        "${mainUrl}/genre/horror/"        to "Horror",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = if (page==1){
            app.get("${request.data}").document
        } else {
            app.get("${request.data}page/$page/").document
        }
        val home     = document.select("div.item.tooltipstered").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("a")?.attr("title") ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val score     = this.selectFirst("span.imdb")?.text()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.score     = Score.from10(score)
        }
    }
    override suspend fun search(query: String, page: Int): SearchResponseList {
        val document = if (page == 1){
            app.get("${mainUrl}/?s=${query}").document
        } else {
            app.get("${mainUrl}/page/$page/?s=${query}").document
        }

        val aramaCevap = document.select("div.item.tooltipstered").mapNotNull { it.toMainPageResult() }

        return newSearchResponseList(aramaCevap, hasNext = true)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title           = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description     = document.selectFirst("div.desc.shorting p")?.text()?.trim()
        val year            = document.selectFirst("span a[href*=year/]")?.text()?.trim()?.toIntOrNull()
        val tags            = document.select("span a[href*=genre/]").map { it.text() }
        val rating          = document.selectFirst("span.imdb")?.text()?.trim()?.toIntOrNull()
        val duration        = document.selectFirst("span.imdb + span")?.text()?.split(" ")?.first()?.trim()?.toIntOrNull()
        val recommendations = document.select("div.item.tooltipstered").mapNotNull { it.toMainPageResult() }
        val actors          = document.select("span.shorting a").map { Actor(it.text()) }
        val trailer         = Regex("""embed\/(.*)\?rel""").find(document.html())?.groupValues?.get(1)?.let { "https://www.youtube.com/embed/$it" }

        val episodes = document.select("div#episodes a.episod")
            .reversed()
            .map { bolum ->
                val fullText = bolum.text()
                val epMatch = Regex("Episode\\s*(\\d+)").find(fullText)
                val epNum = epMatch?.groupValues?.get(1)?.toIntOrNull()
                val bHref = bolum.attr("href")

                newEpisode(bHref) {
                    this.name = title
                    this.episode = epNum
                }
            }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
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

    override suspend fun loadLinks(
        data: String,
        iscasting: Boolean,
        subtitlecallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("Yoturkish", data)

        try {
            val resolver = WebViewResolver(
                interceptUrl = Regex("""yoturkish\.to/all_done"""),
                additionalUrls = listOf(
                    Regex("""engifuosi\.\w+"""),
                    Regex("""rufiiguta\.\w+"""),
                    Regex("""kitraskimisi\.\w+"""),
                    Regex("""tukipasti\.\w+"""),
                    Regex("""sssrr\.org/sora""")
                ),
                useOkhttp = false,
                timeout = 30000,
                script = """
            (function() {
                for (var i = 1; i <= 4; i++) {
                    (function(idx) {
                        setTimeout(function() {
                            var btn = document.querySelector('.player_nav > ul > li:nth-child(' + idx + ') > div > a');
                            if (btn) { btn.click(); }
                        }, idx * 1500);
                    })(i);
                }

                setTimeout(function() {
                    var xhr = new XMLHttpRequest();
                    xhr.open('GET', 'https://yoturkish.to/all_done', true);
                    xhr.send();
                }, 12000);
            })();
        """.trimIndent()
            )

            val result = resolver.resolveUsingWebView(url = data)
            val foundurls = mutableSetOf<String>()

            result?.second?.forEach { req ->
                val url = req.url.toString()

                if (url.contains("/sora/") || url.contains(".m3u8")) {
                    foundurls.add(url)
                }
                else if (url.contains("rufiiguta.com/?v=") || url.contains("engifuosi.com/f/")) {
                    if (!url.contains(".js") && !url.contains(".css")) {
                        foundurls.add(url)
                    }
                }
            }

            Log.d("Yoturkish", "${foundurls.size}")

            foundurls.forEach { url ->
                Log.d("Yoturkish", url)
                try {
                    if (url.contains("/sora/") || url.contains(".m3u8")) {
                        callback.invoke(
                            newExtractorLink(
                                name = if(url.contains("sssrr")) "Rufiiguta (Sora)" else "Yoturkish Stream",
                                source = "YoTurkish",
                                url = url,
                                type = ExtractorLinkType.M3U8,
                                initializer = {
                                    this.referer = "https://rufiiguta.com/"
                                    this.headers = mapOf("Origin" to "https://rufiiguta.com")
                                }
                            )
                        )
                    } else {
                        loadExtractor(url, data, subtitlecallback, callback)
                    }
                } catch (e: Exception) {
                    Log.d("Yoturkish", "${e.message}")
                }
            }

            return foundurls.isNotEmpty()

        } catch (e: Exception) {
            Log.d("Yoturkish", "${e.message}")
        }
        return false
    }
}