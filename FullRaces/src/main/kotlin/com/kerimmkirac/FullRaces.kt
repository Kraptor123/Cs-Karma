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
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        "${mainUrl}/f1-race-replays" to "All F1 Races",
        "${mainUrl}/2026" to "Formula 1 2026",
        "${mainUrl}/2025" to "Formula 1 2025",
        "${mainUrl}/watch/formula_1/formula_1_2024/21" to "Formula 1 2024",
        "${mainUrl}/f1-2023" to "Formula 1 2023",
        "${mainUrl}/formula1-2022" to "Formula 1 2022",
        "${mainUrl}/formula1-2021" to "Formula 1 2021",
        "${mainUrl}/f1-2020" to "Formula 1 2020",
        "${mainUrl}/f1-2019" to "Formula 1 2019",
        "${mainUrl}/f1-archive-races" to "F1 Archive Races 2000-2018",
        "${mainUrl}/f2-full-races" to "F2 Races",
        "${mainUrl}/f3-full-races" to "F3 Races",
        "${mainUrl}/nascar" to "Nascar Races",
        "${mainUrl}/indycar" to "Indycar Races",
        "${mainUrl}/formula-e" to "Formula E Races"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/?page$page").document
        val home = document.select("div.short_item").mapNotNull { it.toMainPageResult() }
        return newHomePageResponse(HomePageList(request.name, home, true))
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


        val description =
            document.select("div[align=center]").joinToString("\n") { it.text().trim() }


        return newMovieLoadResponse(title, url, TvType.Video, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }




    private suspend fun loadCustomExtractor(
        name: String,
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        loadExtractor(url, referer, subtitleCallback) { link ->
            val formattedName = "${link.source} | $name"
            val updatedLink   = ExtractorLink(
                source  = link.source,
                name    = formattedName,
                url     = link.url,
                referer = link.referer,
                quality = link.quality,
                type    = link.type,
                headers = link.headers,
                extractorData = link.extractorData
            )
            callback(updatedLink)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("STF", "data » $data")
        val document   = app.get(data).document
        var linksFound = false

        val iframeElements = document.select("div.video-responsive iframe")
        for (iframe in iframeElements) {
            val src = iframe.attr("src").ifEmpty { continue }
            val cleanUrl = fixUrl(src)
            Log.d("STF", "iframe bulundu » $cleanUrl")
            loadCustomExtractor(
                name             = "Full Part",
                url              = cleanUrl,
                referer          = data,
                subtitleCallback = subtitleCallback,
                callback         = { link ->
                    linksFound = true
                    callback(link)
                }
            )
        }

        val buttonElements = document.select("a.su-button")
        for (button in buttonElements) {
            val href = button.attr("href").ifEmpty { continue }
            val cleanUrl = fixUrl(href)
            val partText = button.text().trim().let { text ->
                if (text.equals("Watch", ignoreCase = true)) "Full Part" else text
            }
            Log.d("STF", "Link bulundu » $cleanUrl ($partText)")
            loadCustomExtractor(
                name             = partText,
                url              = cleanUrl,
                referer          = data,
                subtitleCallback = subtitleCallback,
                callback         = { link ->
                    linksFound = true
                    callback(link)
                }
            )
        }

        return linksFound
    }
}