// ! Bu araç @ByAyzen tarafından | @cs-kraptor için yazılmıştır.

package com.byayzen

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.WebViewResolver

class Wcoflix : MainAPI() {
    override var mainUrl = "https://www.wcoforever.net"
    override var name = "WCoflix"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Anime, TvType.Cartoon)

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Son Çıkanlar",
        "${mainUrl}/#dubbed" to "Dubbed Anime",
        "${mainUrl}/#cartoon" to "Cartoon",
        "${mainUrl}/#subbed" to "Subbed Anime",
        "${mainUrl}/#movies" to "Movies"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val category = request.data.substringAfterLast("#", "latest")

        val elements = when (category) {
            "dubbed" -> document.select("div.recent-release:has(a[name=dubbed]) + div#sidebar_right ul.items li")
            "cartoon" -> document.select("div.recent-release:has(a[name=cartoon]) + div#sidebar_right ul.items li")
            "subbed" -> document.select("div.recent-release:has(a[name=subbed]) + div#sidebar_right ul.items li")
            else -> {
                document.select("div.recent-release-main > div#sidebar_right").first()?.select("ul.items li")
            }
        }

        val home = elements?.mapNotNull { it.toMainPageResult() } ?: emptyList()
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val titleElement = this.selectFirst("div.recent-release-episodes a")
        val title = titleElement?.text()?.trim() ?: return null

        val rawHref = titleElement.attr("href")
        val href = fixUrl(rawHref)

        val posterUrl = fixUrlNull(this.selectFirst("div.img img")?.attr("src"))

        val type = when {
            title.contains("Movie", ignoreCase = true) || href.contains("/movie/", ignoreCase = true) -> TvType.Movie
            else -> TvType.TvSeries
        }

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        if (page > 1) return newSearchResponseList(emptyList(), hasNext = false)

        val url = "https://www.wcoflix.tv/search"
        val body = mapOf(
            "catara" to query,
            "konuara" to "series"
        )

        val document = app.post(url, data = body).document

        val aramaCevap = document.select("ul.items li").mapNotNull { it.toSearchResult() }

        return newSearchResponseList(aramaCevap, hasNext = false)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = this.selectFirst("div.recent-release-episodes a") ?: return null
        val title = anchor.text() ?: return null
        val href = fixUrlNull(anchor.attr("href")) ?: return null

        val posterUrl = fixUrlNull(this.selectFirst("div.img img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        var document = app.get(url).document
        var finalUrl = url

        if (!url.contains("/anime/")) {
            val seriesPageHref = document.selectFirst("div.header-tag h2 a")?.attr("href")

            if (seriesPageHref != null) {
                finalUrl = fixUrl(seriesPageHref)
                document = app.get(finalUrl).document
            }
        }

        val title = document.selectFirst("h2 span.film-name")?.text()?.trim()
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: return null

        val poster = fixUrlNull(document.selectFirst("div#sidebar_cat img.img5")?.attr("src"))
        val description = document.selectFirst("div#sidebar_cat p")?.text()?.trim()
        val tags = document.select("div#sidebar_cat a.genre-buton").map { it.text() }

        val episodeList = document.select("div#episodeList a.dark-episode-item, div#sidebar_right3 div.cat-eps a").mapNotNull { ep ->
            val epHref = fixUrlNull(ep.attr("href")) ?: return@mapNotNull null

            val epName = ep.selectFirst("span")?.text() ?: ep.text()

            val epNumber = Regex("Episode (\\d+)").find(epName)?.groupValues?.get(1)?.toIntOrNull()
            newEpisode(epHref) { name = epName; episode = epNumber }
        }

        return newAnimeLoadResponse(title, finalUrl, TvType.TvSeries) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.episodes = mutableMapOf(DubStatus.Subbed to episodeList)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("kraptor_WcoFLix","data = $data")
        val episodeDocument = app.get(data).document

        val embedUrl = episodeDocument.selectFirst("iframe")?.attr("src") ?: ""

        loadExtractor(embedUrl, referer = "${mainUrl}/", subtitleCallback, callback)

        return true
    }
}