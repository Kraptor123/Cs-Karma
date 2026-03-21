// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class IWTO : MainAPI() {
    override var mainUrl = "https://iwatchtheoffice.cc"
    override var name = "IWatchOffice"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/episodes" to "Episodes",
        "${mainUrl}/season/the-office-1" to "Season 1",
        "${mainUrl}/season/the-office-2" to "Season 2",
        "${mainUrl}/season/the-office-3" to "Season 3",
        "${mainUrl}/season/the-office-4" to "Season 4",
        "${mainUrl}/season/the-office-5" to "Season 5",
        "${mainUrl}/season/the-office-6" to "Season 6",
        "${mainUrl}/season/the-office-7" to "Season 7",
        "${mainUrl}/season/the-office-8" to "Season 8",
        "${mainUrl}/season/the-office-9" to "Season 9",
        "${mainUrl}/season/the-office-extended-1" to "Season 1 (Extended)",
        "${mainUrl}/season/the-office-extended-2" to "Season 2 (Extended)",
        "${mainUrl}/season/the-office-extended-3" to "Season 3 (Extended)",
        "${mainUrl}/season/the-office-extended-4" to "Season 4 (Extended)",
        "${mainUrl}/season/the-office-extended-5" to "Season 5 (Extended)",
        "${mainUrl}/season/the-office-extended-6" to "Season 6 (Extended)",
        "${mainUrl}/season/the-office-extended-7" to "Season 7 (Extended)",
        "${mainUrl}/season/the-office-extended-8" to "Season 8 (Extended)",
        "${mainUrl}/season/the-office-extended-9" to "Season 9 (Extended)",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data.removeSuffix("/")}/page/$page/"
        val document = app.get(url).document
        val home = document.select("ul.post-lst li article").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(
            list = HomePageList(request.name, home, isHorizontalImages = true),
            hasNext = document.selectFirst("a.next.page-numbers") != null
        )
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = selectFirst("h2")?.text() ?: return null
        val href = fixUrlNull(selectFirst("a.lnk-blk")?.attr("href")) ?: return null
        val type = if (title.contains("Season", true)) TvType.TvSeries else TvType.Movie

        return if (type == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = fixUrlNull(selectFirst("img")?.attr("src")) }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = fixUrlNull(selectFirst("img")?.attr("src")) }
        }
    }

    data class StaticSearchData(
        @JsonProperty("name") val name: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("poster") val poster: String
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("https://raw.githubusercontent.com/Kraptor123/Cs-Karma/refs/heads/master/IWTOSearch").text
        val data = AppUtils.parseJson<List<StaticSearchData>>(response)

        return data.filter { it.name.contains(query, ignoreCase = true) }.sortedBy { it.name }.map {
                val isTv = it.name.contains("Season", true)
                if (isTv) {
                    newTvSeriesSearchResponse(it.name, it.url, TvType.TvSeries) { this.posterUrl = it.poster }
                } else {
                    newMovieSearchResponse(it.name, it.url, TvType.Movie) { this.posterUrl = it.poster }
                }
            }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val isTv = title.contains("Season", true)

        val recommendations = document.select("ul#episode_by_temp li").mapNotNull { li ->
            val epHref = li.selectFirst("a.lnk-blk")?.attr("href") ?: return@mapNotNull null
            val epTitle = li.selectFirst("h2")?.text() ?: ""
            newMovieSearchResponse(epTitle, epHref, TvType.Movie) {
                this.posterUrl = fixUrlNull(li.selectFirst("img")?.attr("src"))
            }
        }

        return if (isTv) {
            val episodes = document.select("ul#episode_by_temp li").mapNotNull { li ->
                val epHref = li.selectFirst("a.lnk-blk")?.attr("href") ?: return@mapNotNull null
                newEpisode(epHref) {
                    this.name = li.selectFirst("h2")?.text()
                    this.posterUrl = fixUrlNull(li.selectFirst("img")?.attr("src"))
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = fixUrlNull(document.selectFirst("meta[property='og:image']")?.attr("content"))
                this.plot = document.selectFirst("article.post-single-video div.description")?.text()?.trim()
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = fixUrlNull(document.selectFirst("meta[property='og:image']")?.attr("content"))
                this.plot = document.selectFirst("article.post-single-video div.description")?.text()?.trim()
                this.recommendations = recommendations
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("aside#aa-options div.video iframe").forEach { iframe ->
            val link = iframe.attr("data-src").ifBlank { iframe.attr("src") }
            if (link.isNotBlank())
                loadExtractor(link, "$mainUrl/", subtitleCallback, callback)
        }
        return true
    }
}