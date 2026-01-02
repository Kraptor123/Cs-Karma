// ! Bu araç @kerimmkirac tarafından | @SporKeyfi için yazılmıştır.

package com.kerimmkirac

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class FullRaces : MainAPI() {
    override var mainUrl              = "https://fullraces.com"
    override var name                 = "FullRaces"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}"      to "F1 Races",
        "${mainUrl}/f2"   to "F2 Races",
        "${mainUrl}/f3" to "F3 Races",
        "${mainUrl}/nascar"  to "Nascar Races"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/?page$page").document
        val home     = document.select("div.short_item").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
    val anchor = this.selectFirst("div.short_content h3 a") ?: return null
    val title = anchor.text().trim()
    val href = fixUrlNull(anchor.attr("href")) ?: return null
    val posterUrl = fixUrlNull(this.selectFirst("div.poster img")?.attr("src"))

    return newMovieSearchResponse(title, href, TvType.Movie) {
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

    return newMovieSearchResponse(title, href, TvType.Movie) {
        this.posterUrl = posterUrl
    }
}


    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
    val document = app.get(url).document

    val title = document.selectFirst("h1")?.text()?.trim() ?: return null
    val poster = fixUrlNull(document.selectFirst("div.full_img img")?.attr("src"))

    
    val description = document.select("div[align=center]").joinToString("\n") { it.text().trim() }

    
    return newMovieLoadResponse(title, url, TvType.Movie, url) {
        this.posterUrl = poster
        this.plot = description
    }
}


    private fun Element.toRecommendationResult(): SearchResponse? {
        val title     = this.selectFirst("a img")?.attr("alt") ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a img")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    Log.d("STF", "data » $data")
    val document = app.get(data).document

    val iframeUrls = document.select("iframe[src]").mapNotNull {
        it.attr("src")?.takeIf { src -> src.startsWith("http") }
    }

    for (iframe in iframeUrls) {
        loadExtractor(iframe, data, subtitleCallback, callback)
    }

    return true
}

}