// ! Bu araç @ByAyzen tarafından | @Kimseninokuyamadığıcskarmanotu için yazılmıştır.

package com.byayzen

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Dubbindo : MainAPI() {
    override var mainUrl = "https://www.dubbindo.site"
    override var name = "Dubbindo"
    override val hasMainPage = true
    override var lang = "id"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "${mainUrl}/videos/latest" to "Latest Videos",
        "${mainUrl}/videos/trending" to "Trending Videos",
        "${mainUrl}/videos/top" to "Top Videos"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}?page_id=$page"

        Log.d("MainPage", "URL: $url | Page: $page")

        val document = app.get(url).document
        val home = document.select("div.container div.col-md-3").mapNotNull {
            it.toMainPageResult()
        }

        Log.d("MainPage", "Results: ${home.size}")

        val pagination = document.select(".pagination")
        val nextButton = pagination.select("a").any {
            it.text().contains("next", ignoreCase = true) ||
                    it.attr("href").contains("page_id=${page + 1}")
        }

        Log.d("MainPage", "Has next: $nextButton")

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = nextButton
        )
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("a h4")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.AsianDrama) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val document = if (page == 1) {
            app.get("${mainUrl}/search?keyword=${query}").document
        } else {
            app.get("${mainUrl}/page/$page/search?keyword=${query}").document
        }

        val aramaCevap = document.select("div.col-md-12").mapNotNull { element ->
            element.toSearchResult()
        }

        return newSearchResponseList(aramaCevap, hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h4")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.AsianDrama) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = document.selectFirst("div.watch-video-description p")?.text()?.trim()
        val tags = document.select("div.video-published a").map {
            it.text().substringBefore("A").trim()
        }
        val duration =
            document.selectFirst("span.runtime")?.text()?.split(" ")?.first()?.trim()?.toIntOrNull()
        val seen = mutableSetOf<Pair<String, String>>()
        val recommendations = document.select("div.related-video-wrapper")
            .mapNotNull { it.toRecommendationResult() }
            .filter { seen.add(it.name to it.url) }

        return newMovieLoadResponse(title, url, TvType.AsianDrama, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.duration = duration
            this.recommendations = recommendations
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title = this.selectFirst("a img")?.attr("alt") ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.ra-thumb a img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.AsianDrama) { this.posterUrl = posterUrl }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        val mp4s = doc.select("script")
            .mapNotNull { it.data() }
            .flatMap { Regex("""https?://[^\s'"]+\.mp4""").findAll(it).map { m -> m.value } }
            .distinct()

        if (mp4s.isEmpty()) return false

        mp4s.forEach { url ->
            val res = Regex("""(?i)(\d{3,4})p""").find(url)?.groupValues?.get(1)

            val quality = when (res) {
                "2160" -> Qualities.P2160.value
                "1440" -> Qualities.P1440.value
                "1080" -> Qualities.P1080.value
                "720"  -> Qualities.P720.value
                "480"  -> Qualities.P480.value
                "360"  -> Qualities.P360.value
                "240"  -> Qualities.P240.value
                "144"  -> Qualities.P144.value
                else   -> Qualities.Unknown.value
            }

            callback(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = fixUrl(url),
                    type = ExtractorLinkType.VIDEO
                ) {
                    headers = mapOf("Referer" to "$mainUrl/")
                    this.quality = quality
                }
            )
        }

        return true
    }
}