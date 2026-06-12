// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class Footreplays : MainAPI() {
    override var mainUrl = "https://www.footreplays.com"
    override var name = "FootReplays"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Others)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "${mainUrl}/england/" to "England",
        "${mainUrl}/spain/" to "Spain",
        "${mainUrl}/italy/" to "Italy",
        "${mainUrl}/germany/" to "Germany",
        "${mainUrl}/france/" to "France",
        "${mainUrl}/portugal/" to "Portugal",
        "${mainUrl}/uefa/" to "UEFA",
        "${mainUrl}/international/" to "International",
        "${mainUrl}/other/" to "Other"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val siteurl =
            if (page > 1) "${request.data.removeSuffix("/")}/page/$page/" else request.data
        val document = app.get(siteurl).document
        val home = document.select("div.p-wrap").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            )
        )
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val isnot = this.selectFirst("a.p-category")?.attr("href")?.contains("/news/") == true
        val categoryId = this.selectFirst("a.p-category")?.className()
        if (isnot || categoryId?.contains("category-id-283") == true) return null

        return toRecommendationResult()
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page == 1) {
            "$mainUrl/?s=$query"
        } else {
            "$mainUrl/page/$page/?s=$query"
        }

        val document = app.get(url).document
        val aramaCevap = document.select("div.p-wrap").mapNotNull { it.toMainPageResult() }

        return newSearchResponseList(aramaCevap, hasNext = true)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.s-title")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.s-feat img")?.attr("src"))
        val description =
            document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        val year = document.selectFirst("time.updated-date")?.attr("datetime")?.substringBefore("-")
            ?.toIntOrNull()
        val tags = document.select("div.efoot-bar.tag-bar a").map { it.text() }
        val recommendations =
            document.select("div.p-wrap.p-grid").mapNotNull { it.toRecommendationResult() }

        Log.d("Ayzen", "Title: $title")
        Log.d("Ayzen", "Url: $url")

        val episodes = mutableListOf<Episode>()
        document.select("table.video-table").forEach { table ->
            val sourceName = table.selectFirst("thead tr th[colspan]")?.text()?.trim() ?: "Source"
            table.select("tbody tr").forEach { tr ->
                val part = tr.select("td").firstOrNull()?.text()?.trim() ?: "Video"
                val onclickAttr = tr.selectFirst("a.play-button")?.attr("onclick") ?: return@forEach
                val regex = Regex("""loadVideo\('([^']+)'\)""")
                val videoUrl = regex.find(onclickAttr)?.groupValues?.get(1) ?: return@forEach

                val episodeData = "$videoUrl|$sourceName - $part"
                val currentEpisodeSize = episodes.size

                episodes.add(
                    newEpisode(data = episodeData) {
                        this.name = "$sourceName - $part"
                        this.episode = currentEpisodeSize + 1
                    }
                )
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.Others, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            this.recommendations = recommendations
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title = this.selectFirst("h4.entry-title a, a.p-flink")?.attr("title")
            ?.takeIf { it.isNotBlank() } ?: this.selectFirst("h4.entry-title a")?.text()?.trim()
        ?: return null
        val href = fixUrlNull(this.selectFirst("a.p-flink, h4.entry-title a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.p-featured img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = data.split("|")
        val videoUrl = parts.getOrNull(0) ?: return false
        val customName = parts.getOrNull(1) ?: "Video"
        val iframeUrl = if (videoUrl.startsWith("//")) "https:$videoUrl" else videoUrl
        Log.d("Ayzen", "Iframe Url: $iframeUrl")

        loadExtractor(iframeUrl, "$mainUrl/", subtitleCallback) { link ->
            val extractedLink = kotlinx.coroutines.runBlocking {
                newExtractorLink(
                    source = customName,
                    name = customName,
                    url = link.url,
                    type = link.type
                )
            }
            callback(extractedLink)
        }

        return true
    }
}