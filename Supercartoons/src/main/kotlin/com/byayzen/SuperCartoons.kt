package com.byayzen

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class SuperCartoons : MainAPI() {
    override var mainUrl = "https://www.supercartoons.net"
    override var name = "SuperCartoons"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Cartoon)

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Latest Cartoons",
        "${mainUrl}/serie/tom-and-jerry/" to "Tom and Jerry",
        "${mainUrl}/serie/looney-tunes/" to "Looney Tunes",
        "${mainUrl}/serie/the-looney-tunes-show/" to "The Looney Tunes Show",
        "${mainUrl}/serie/the-pink-panther-show/" to "The Pink Panther Show",
        "${mainUrl}/serie/popeye-the-sailor/" to "Popeye the Sailor",
        "${mainUrl}/serie/disney/" to "Disney",
        "${mainUrl}/serie/duck-dodgers/" to "Duck Dodgers",
        "${mainUrl}/serie/the-tom-and-jerry-show/" to "The Tom and Jerry Show",
        "${mainUrl}/serie/tom-and-jerry-tales/" to "Tom and Jerry Tales",
        "${mainUrl}/serie/merrie-melodies/" to "Merrie Melodies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}?paged=$page"
        val home = app.get(url).document.select("section.ts-thumbnail-view article").mapNotNull {
            it.toSearchResult()
        }.distinctBy { it.url }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page <= 1) "$mainUrl/?s=$query" else "$mainUrl/?paged=$page&s=$query"
        val results = app.get(url).document.select("section.ts-thumbnail-view article").mapNotNull {
            it.toSearchResult()
        }.distinctBy { it.url }
        return newSearchResponseList(results, hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3.title")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        if (href.contains("/serie/")) return null
        return newMovieSearchResponse(title, href, TvType.Cartoon) {
            this.posterUrl = fixUrlNull(selectFirst("img")?.attr("src"))
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("meta[property=og:title]")?.attr("content") ?: document.selectFirst("h1")?.text() ?: return null

        return newMovieLoadResponse(title, url, TvType.Cartoon, url) {
            this.posterUrl = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
            this.plot = document.selectFirst("div.post-content p")?.text()?.trim()
            this.tags = document.select("ul.single-video-categories li a").map { it.text() }
            this.recommendations = document.select("section.ts-thumbnail-view article").mapNotNull { it.toRecommendationResult() }
            addActors(document.select("ul.single-video-tags li a").map { it.text() })
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title = this.selectFirst("h3.title")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        return newMovieSearchResponse(title, href, TvType.Cartoon) {
            this.posterUrl = fixUrlNull(selectFirst("img")?.attr("src"))
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = app.get(data).text
        val videoUrl = Regex("""file:\s*["']([^"']+)["']""").find(html)?.groupValues?.get(1) ?: return false

        callback.invoke(
            newExtractorLink("SuperCartoons", "SuperCartoons", videoUrl, INFER_TYPE) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }
}