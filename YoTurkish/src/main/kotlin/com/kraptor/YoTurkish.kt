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
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("Yoturkish", data)

        try {
            val resolver = WebViewResolver(
                interceptUrl = Regex("${mainUrl.replace("https://", "").replace("/", "")}/all_done"),
                additionalUrls = listOf(
                    Regex("""srv\.tokvoy\.com/.*\.m3u8"""),
                    Regex("""engifuosi\.\w+/(f|d)/.*"""),
                    Regex("""rufiiguta\.\w+/\?v=.*"""),
                    Regex("""tukipasti\.\w+/t/.*"""),
                    Regex("""kitraskimisi\.\w+/e/.*"""),
                    Regex("""sssrr\.org/sora.*"""),
                    Regex("${mainUrl.replace("https://", "").replace("/", "")}/yoturkish_dl.*")
                ),
                useOkhttp = false,
                timeout = 20000,
                script = """
                (function() {
                    if (window !== window.top) return;
                    if (window.hasRunAlready) return;
                    window.hasRunAlready = true;

                    var sentLinks = new Set();
                    
                    function sendFoundLink(type, link) {
                        if(!link || !link.includes('http')) return;
                        if(link.includes('sharethis') || link.includes('pubadx') || link.includes('yandex') || link.includes('a-ads')) return;
                        
                        if (!sentLinks.has(link)) {
                             sentLinks.add(link);
                             var xhr = new XMLHttpRequest();
                             xhr.open('GET', '${mainUrl.removeSuffix("/")}/yoturkish_dl?url=' + encodeURIComponent(link), true);
                             xhr.send();
                        }
                    }

                    function triggerTabsAndScrape() {
                        var dl = document.querySelector('.dl-contenti a');
                        if (dl && dl.href) sendFoundLink('download', dl.href);

                        var tabs = document.querySelectorAll('.optitabs a[href^="#tab"]');
                        if(tabs.length === 0) {
                            var xhr = new XMLHttpRequest();
                            xhr.open('GET', '${mainUrl.removeSuffix("/")}/all_done', true);
                            xhr.send();
                            return;
                        }

                        tabs.forEach(function(btn, idx) {
                            setTimeout(function() {
                                btn.click();
                                setTimeout(function() {
                                     var iframes = document.querySelectorAll('#player iframe, .play iframe');
                                     iframes.forEach(function(ifr) {
                                         sendFoundLink('iframe', ifr.src);
                                     });
                                }, 1000);
                            }, idx * 1500); 
                        });
                        
                        var totalTime = (tabs.length * 1500) + 1500;
                        setTimeout(function() {
                            var xhr = new XMLHttpRequest();
                            xhr.open('GET', '${mainUrl.removeSuffix("/")}/all_done', true);
                            xhr.send();
                        }, totalTime);
                    }

                    var attempts = 0;
                    var checkExist = setInterval(function() {
                        var tabs = document.querySelectorAll('.optitabs a[href^="#tab"]');
                        if (tabs.length > 0) {
                            clearInterval(checkExist);
                            triggerTabsAndScrape();
                        } else {
                            attempts++;
                            if (attempts > 10) { 
                                clearInterval(checkExist);
                                triggerTabsAndScrape(); 
                            }
                        }
                    }, 500);
                })();
            """.trimIndent()
            )

            val result = resolver.resolveUsingWebView(url = data)
            val foundUrls = mutableSetOf<String>()

            result?.second?.forEach { req ->
                var url = req.url.toString()

                if (url.contains("yoturkish_dl?url=")) {
                    val decodedUrl = java.net.URLDecoder.decode(url.substringAfter("url="), "UTF-8")
                    if (!decodedUrl.contains("pubadx") && !decodedUrl.contains("yandex") && !decodedUrl.contains("sharethis") && !decodedUrl.contains("a-ads")) {
                        foundUrls.add(decodedUrl)
                    }
                }
                else if (
                    url.contains(".m3u8") ||
                    url.contains("/sora/") ||
                    url.contains("rufiiguta.com") ||
                    url.contains("engifuosi.com") ||
                    url.contains("tukipasti.com") ||
                    url.contains("kitraskimisi.com")
                ) {
                    if (!url.contains(Regex("""\.(js|css|png|jpg|jpeg|woff|svg|json)"""))) {
                        val cleanUrl = url.substringBefore("&").substringBefore("#")
                        if(cleanUrl.isNotEmpty()) foundUrls.add(cleanUrl)
                    }
                }
            }

            Log.d("Yoturkish", foundUrls.size.toString())

            foundUrls.forEach { url ->
                Log.d("Yoturkish", url)
                try {
                    if (url.contains(".m3u8") || url.contains("/sora/")) {
                        callback.invoke(
                            newExtractorLink(
                                name = if (url.contains("tokvoy") || url.contains("sora")) "YoTurkish (Direct)" else "YoTurkish Stream",
                                source = "YoTurkish",
                                url = url,
                                type = ExtractorLinkType.M3U8,
                                initializer = {
                                    this.referer = "$mainUrl/"
                                    this.headers = mapOf(
                                        "Origin" to mainUrl.removeSuffix("/"),
                                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                                    )
                                }
                            )
                        )
                    } else {
                        loadExtractor(url, data, subtitleCallback, callback)
                    }
                } catch (e: Exception) {
                    Log.d("Yoturkish", e.message.toString())
                }
            }

            return foundUrls.isNotEmpty()

        } catch (e: Exception) {
            Log.d("Yoturkish", e.message.toString())
        }
        return false
    }
}