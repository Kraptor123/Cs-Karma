// ! Bu araç @kerimmkirac tarafından | @SporKeyfi için yazılmıştır.

package com.kerimmkirac

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class FullReplays : MainAPI() {
    override var mainUrl              = "https://www.fullreplays.com"
    override var name                 = "FullReplays"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)
    private val posterCache = mutableMapOf<String, String>()
    override val mainPage = mainPageOf(
    "${mainUrl}" to "All Football Replays",
    "${mainUrl}/england/premier-league" to "Premier League",
    "${mainUrl}/spain/laliga" to "Laliga",
    "${mainUrl}/uefa/champions-league" to "Champions League",
    "${mainUrl}/uefa/europa-league" to "Europa League",
    "${mainUrl}/uefa/conference-league" to "Conference League",
    "${mainUrl}/uefa/uefa-super-cup" to "UEFA Super Cup",
    "${mainUrl}/uefa/euro" to "Euro",
    "${mainUrl}/uefa/uefa-nations-league" to "UEFA Nations League",
    "${mainUrl}/italy/serie-a" to "Serie A",
    "${mainUrl}/germany/bundesliga" to "Bundesliga",
    "${mainUrl}/france/ligue-1" to "Ligue 1",
    "${mainUrl}/world-cup-2026-qualification" to "World Cup 2026 Qualification",
    
)


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val document = app.get("${request.data}/page/$page").document
    val home = document.select("article.vlog-lay-g.vlog-post").mapNotNull { it.toMainPageResult() }
    
    return newHomePageResponse(request.name, home)
}

private fun Element.toMainPageResult(): SearchResponse? {
    val title = this.selectFirst("h2.entry-title a")?.text() ?: return null
    val href = fixUrlNull(this.selectFirst("div.entry-image a")?.attr("href")) ?: return null
    val posterUrl = fixUrlNull(this.selectFirst("div.entry-image img")?.attr("src"))
    posterUrl?.let { posterCache[href] = it }
    
    val categories = this.select("span.entry-category a").map { it.text() }
    
    return newMovieSearchResponse(title, href, TvType.Movie) { 
        this.posterUrl = posterUrl
        
    }
}

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}").document

        return document.select("article.vlog-lay-g.vlog-post").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title a")?.text() ?: return null
    val href = fixUrlNull(this.selectFirst("div.entry-image a")?.attr("href")) ?: return null
    val posterUrl = fixUrlNull(this.selectFirst("div.entry-image img")?.attr("src"))
    
    
    val categories = this.select("span.entry-category a").map { it.text() }
    
    return newMovieSearchResponse(title, href, TvType.Movie) { 
        this.posterUrl = posterUrl
        
    }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
    val document = app.get(url).document

    val title = document.selectFirst("h1")?.text()?.trim() ?: return null
    val poster = posterCache[url]
    val description = document.selectFirst("p.frc_first_para_match_dt")?.text()?.trim()
    
    
    
    val tags = document.select("div.meta-tags a").take(5).map { it.text() }
    
    
    
    
    val recommendations = document.select("article.vlog-lay-g.vlog-post").mapNotNull { it.toRecommendationResult() }
    
    

    return newMovieLoadResponse(title, url, TvType.Movie, url) {
        this.posterUrl = poster
        this.plot = description
        
        this.tags = tags
        
        this.recommendations = recommendations
        
    }
}

private fun Element.toRecommendationResult(): SearchResponse? {
    val title = this.selectFirst("h2.entry-title a")?.text() ?: return null
    val href = fixUrlNull(this.selectFirst("div.entry-image a")?.attr("href")) ?: return null
    val posterUrl = fixUrlNull(this.selectFirst("div.entry-image img")?.attr("src"))

    return newMovieSearchResponse(title, href, TvType.Movie) { 
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

    val videoLink = document.selectFirst("p.frc-cdt-para a[href]")?.attr("href") ?: return false

    Log.d("STF", " embed link: $videoLink")

    loadExtractor(videoLink, "$mainUrl/", subtitleCallback, callback)
    return true
}
}