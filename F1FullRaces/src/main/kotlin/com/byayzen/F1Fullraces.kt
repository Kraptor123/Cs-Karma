// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class F1Fullraces : MainAPI() {
    override var mainUrl = "https://f1fullraces.com"
    override var name = "F1Fullraces"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        mainUrl to "Latest",
        "${mainUrl}/watch-f1-free/category/full-races/2020s/2025/" to "2025",
        "${mainUrl}/watch-f1-free/category/full-races/2020s/2024/" to "2024",
        "${mainUrl}/watch-f1-free/category/full-races/2020s/2023/" to "2023",
        "${mainUrl}/watch-f1-free/category/full-races/2020s/2022/" to "2022",
        "${mainUrl}/watch-f1-free/category/full-races/2020s/2021/" to "2021",
        "${mainUrl}/watch-f1-free/category/full-races/2020s/2020/" to "2020",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2019/" to "2019",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2018/" to "2018",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2017/" to "2017",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2016/" to "2016",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2015/" to "2015",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2014/" to "2014",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2013/" to "2013",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2012/" to "2012",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2011/" to "2011",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2010/" to "2010"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url =
            if (page <= 1) "${request.data.trimEnd('/')}/" else "${request.data.trimEnd('/')}/page/$page/"
        val home =
            app.get(url).document.select("div#masonry article").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(
            list = HomePageList(request.name, home, isHorizontalImages = true),
            hasNext = home.isNotEmpty()
        )
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val titleElement = this.selectFirst("h2.entry-title a")
        val title = titleElement?.text()?.trim() ?: return null
        val href = fixUrlNull(titleElement?.attr("href")) ?: return null

        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page <= 1) "$mainUrl/?s=$query" else "$mainUrl/page/$page/?s=$query"
        val results =
            app.get(url).document.select("div#masonry article").mapNotNull { it.toMainPageResult() }

        return newSearchResponseList(results, hasNext = results.isNotEmpty())
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property='og:image']")?.attr("content"))
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = coroutineScope {
        Log.d("f1fullraces", data)
        val document = app.get(data).document
        val jobs = mutableListOf<Job>()

        document.select("div#netu iframe[src*='lulu']").forEach { iframe ->
            val src = iframe.attr("src")
            val rawText = iframe.parent()?.ownText()?.trim() ?: ""
            val name = when {
                rawText.contains("Pre-Race", true) -> "Yarış Öncesi Analiz"
                rawText.contains("Race Session", true) -> "Yarış Seansı"
                rawText.contains("Post-Race", true) -> "Yarış Sonrası Analiz"
                else -> "Yarış Seansı"
            }
            Log.d("f1fullraces", src)
            jobs += launch {
                loadCustomExtractor(src, data, name, subtitleCallback, callback)
            }
        }

        document.select("div#mixdrop div[id^=07b022]").forEach { div ->
            val decodedId = mixdropIdCoz(div.attr("id")).replace("\"", "").trim()
            if (decodedId.isNotBlank()) {
                val url = "https://mixdrop.co/f/$decodedId"
                Log.d("f1fullraces", url)
                jobs += launch {
                    loadCustomExtractor(url, data, "Yarış Seansı", subtitleCallback, callback)
                }
            }
        }

        document.select("div#drive a[href], div#gofile a[href], div#mega a[href]").forEach { link ->
            val href = link.attr("href")
            if (href.isNotBlank()) {
                Log.d("f1fullraces", href)
                jobs += launch {
                    loadCustomExtractor(href, data, "Yarış Seansı", subtitleCallback, callback)
                }
            }
        }

        jobs.joinAll()
        return@coroutineScope true
    }

    private suspend fun loadCustomExtractor(
        url: String,
        referer: String,
        customName: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) = coroutineScope {
        loadExtractor(url, referer, subtitleCallback) { link ->
            launch {
                Log.d("f1fullraces", link.url)
                callback.invoke(
                    newExtractorLink(
                        source = link.source,
                        name = "${link.source}-$customName",
                        url = link.url,
                        type = link.type,
                        initializer = {
                            this.referer = link.referer
                            this.headers = link.headers
                        }
                    )
                )
            }
        }
    }

    private fun mixdropIdCoz(encodedId: String): String {
        return try {
            val cleanedId = encodedId.removePrefix("07b022").removeSuffix("07d").replace("02203a022", "")
            val decodedChars = cleanedId.chunked(3).map { it.toInt(16).toChar() }
            decodedChars.joinToString("")
        } catch (e: Exception) {
            Log.d("f1fullraces", "${e.message}")
            ""
        }
    }
}