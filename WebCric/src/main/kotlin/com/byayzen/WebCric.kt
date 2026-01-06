// ! Bu araç @ByAyzentarafından | @cs-karma için yazılmıştır.

package com.byayzen

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.WebViewResolver

class WebCric: MainAPI() {
    override var mainUrl = "https://me.webcric.com"
    override var name = "WebCric"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "LIVE STREAM" to "Live Matches",
        "MATCH END" to "Finished Matches",
        "SERIES END" to "Ended Series",
        "AUCTION END" to "Completed Events"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val home = document.select("div.card.portfolio-item").mapNotNull {
            val status = it.selectFirst("button")?.text()?.uppercase() ?: ""

            if (status != request.data && request.data != "${mainUrl}/") {
                return@mapNotNull null
            }

            if (request.data == "${mainUrl}/" && status != "LIVE STREAM") {
                return@mapNotNull null
            }

            it.toMainPageResult()
        }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val titleElement = this.selectFirst("h3.card-title") ?: return null
        val eventTitle = titleElement.text()
            .replace(Regex("(?i)LIVE STREAM|MATCH END|SERIES END|AUCTION END"), "").trim()
        val category = this.selectFirst("h4.card-title")?.text()?.trim() ?: ""

        val fullTitle = if (category.isNotEmpty()) "[$category] $eventTitle" else eventTitle
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(fullTitle, href, TvType.Live) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val document = app.get(mainUrl).document
        val aramaCevap = document.select("div.card.portfolio-item").mapNotNull {
            val title = it.selectFirst("h3.card-title")?.text() ?: ""
            val category = it.selectFirst("h4.card-title")?.text() ?: ""

            if (title.contains(query, ignoreCase = true) || category.contains(
                    query,
                    ignoreCase = true
                )
            ) {
                it.toMainPageResult()
            } else {
                null
            }
        }

        return newSearchResponseList(aramaCevap)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h2 strong")?.text()?.trim()
            ?: document.selectFirst("h2")?.text()?.trim()
            ?: document.title().trim()
        val poster = fixUrlNull(document.selectFirst("div.col-lg-12.text-center img")?.attr("src"))

        val bilgi = "West Indies Tour of New Zealand 2025, the tour consists of 5 T20s, 3 ODI's & 3 Test which will be played from 5 Nov to 22 Dec in New Zealand"
        val siteDescription = document.selectFirst("h5 strong p")?.text()?.trim()

        val finalDescription = if (siteDescription.isNullOrEmpty()) {
            bilgi
        } else {
            "$bilgi\n\n$siteDescription"
        }

        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
            this.plot = finalDescription
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val targetUrl = app.get(data).document.select("iframe[src*='frame']").attr("src")
            .takeIf { it.isNotEmpty() }
            ?.let { if (it.startsWith("http")) it else "${data.substringBeforeLast("/")}/$it" }
            ?: return false

        val ua =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        val link = app.get(
            targetUrl,
            headers = mapOf("Referer" to data, "User-Agent" to ua),
            interceptor = WebViewResolver(Regex("""\.m3u8"""))
        ).url.toString()

        if (link.contains(".m3u8")) {
            callback.invoke(
                newExtractorLink(
                    "WebCric",
                    "WebCric",
                    link,
                    type = ExtractorLinkType.M3U8
                )
            )
            return true
        }
        return false
    }
}