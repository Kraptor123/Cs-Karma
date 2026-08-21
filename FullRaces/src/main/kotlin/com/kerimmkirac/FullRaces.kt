// ! Bu araç @kerimmkirac tarafından | @CS-Karma için yazılmıştır!

package com.kerimmkirac

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class FullRaces : MainAPI() {
    override var mainUrl = "https://fullraces.com"
    override var name = "FullRaces"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.Others)

    override val mainPage = mainPageOf(
        mainUrl to "F1 Races",
        "${mainUrl}/2026" to "2026",
        "${mainUrl}/f2-full-races" to "F2 Races",
        "${mainUrl}/f3-full-races" to "F3 Races",
        "${mainUrl}/nascar" to "Nascar Races"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/?page$page").document
        val home = document.select("div.short_item").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val anchor = this.selectFirst("div.short_content h3 a") ?: return null
        val title = anchor.text().trim()
        val href = fixUrlNull(anchor.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.poster img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Others) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search/?q=$query").document
        return document.select("div.statvidp").mapNotNull { it.toSearchResult() }
    }


    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = this.selectFirst("div.tit33fdsq a") ?: return null
        val title = anchor.text().trim()
        val href = fixUrlNull(anchor.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.fhkds54sa img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Others) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster =
            fixUrlNull(document.selectFirst("div.full_img img")?.attr("src")?.ifEmpty { null })
        val description =
            document.select("div.gp-top p, div.gp-top h2").joinToString("\n") { it.text().trim() }

        val recommendation =
            document.select("div.full_info table.infTable td.infTd table.eewwffa2").mapNotNull {
                it.toRecommendationResult()
            }

        return newMovieLoadResponse(title, url, TvType.Others, url) {
            this.posterUrl = poster
            this.plot = description
            this.recommendations = recommendation
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val href = fixUrlNull(this.selectFirst("div.wxxxx34hg a")?.attr("href")?.ifEmpty { null })
            ?: return null
        val img = this.selectFirst("div.wxxxx34hg a img") ?: return null
        val title = img.attr("alt").ifEmpty { return null }.trim()
        val posterUrl = fixUrlNull(img.attr("src").ifEmpty { null })

        return newMovieSearchResponse(title, href, TvType.Others) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("STF", "data » $data")
        val document = app.get(data).document

        val sources = document.select("nav.gp-bar a.gp-src").mapNotNull {
            it.attr("href").ifEmpty { null }?.let(::httpsify)
        }

        for (source in sources) {
            Log.d("STF", "Source » $source")
            loadExtractor(source, data, subtitleCallback, callback)
        }

        return true
    }
}