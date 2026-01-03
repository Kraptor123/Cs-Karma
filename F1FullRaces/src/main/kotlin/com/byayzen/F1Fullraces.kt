// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class F1Fullraces : MainAPI() {
    override var mainUrl = "https://f1fullraces.com"
    override var name = "F1Fullraces"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
           mainUrl                                                 to "Latest",
        "${mainUrl}/watch-f1-free/category/full-races/2020s/2025/" to "2025",
        "${mainUrl}/watch-f1-free/category/full-races/2020s/2024/" to "2024",
        "${mainUrl}/watch-f1-free/category/full-races/2020s/2023/" to "2023",
        "${mainUrl}/watch-f1-free/category/full-races/2020s/2022/" to "2022",
        "${mainUrl}/watch-f1-free/category/full-races/2020s/2021/" to "2021",
        "${mainUrl}/watch-f1-free/category/full-races/2020s/2020/" to "2020",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2019/" to "2019",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2018/" to "2018",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2017/" to "2017",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2016/" to "2016",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2015/" to "2015",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2014/" to "2014",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2013/" to "2013",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2012/" to "2012",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2011/" to "2011",
        "${mainUrl}/watch-f1-free/category/full-races/2010s/2010/" to "2010"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) "${request.data.trimEnd('/')}/" else "${request.data.trimEnd('/')}/page/$page/"
        val home = app.get(url).document.select("div#masonry article").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(
            list = HomePageList(request.name, home, isHorizontalImages = true),
            hasNext = home.isNotEmpty()
        )
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val titleElement = this.selectFirst("h2.entry-title a")
        val title = titleElement?.text()?.trim() ?: return null
        val href = fixUrlNull(titleElement?.attr("href")) ?: return null

        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page <= 1) "$mainUrl/?s=$query" else "$mainUrl/page/$page/?s=$query"
        val results = app.get(url).document.select("div#masonry article").mapNotNull { it.toMainPageResult() }

        return newSearchResponseList(results, hasNext = results.isNotEmpty())
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property='og:image']")?.attr("content"))
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("F1Races", "İstenen sayfa: $data")
        val document = app.get(data).document

        val luluvidMainUrl = "https://luluvid.com"

        val firstIframe = document.selectFirst("div#netu > iframe[src*='luluvid.com']")
        firstIframe?.let {
            val src = it.attr("src")
            val name = "Pre-Race Build Up"
            processLuluvidLink(src, name, data, luluvidMainUrl, callback)
        }

        document.select("div#netu p:has(iframe[src*='luluvid.com'])").forEach { pElement ->
            val name = pElement.ownText().trim()
            val src = pElement.selectFirst("iframe")?.attr("src")
            if (!src.isNullOrBlank()) {
                processLuluvidLink(src, name, data, luluvidMainUrl, callback)
            }
        }

        document.select("div#mixdrop div[id^=07b022]").forEach { div ->
            val encodedId = div.attr("id")
            val decodedId = mixdropIdCoz(encodedId)
            if (decodedId.isNotBlank()) {
                val mixdropUrl = "https://mixdrop.co/f/$decodedId"
                loadExtractor(mixdropUrl, data, subtitleCallback, callback)
            }
        }

        document.select("div#drive a[href]").forEach { linkElement ->
            val href = linkElement.attr("href")
            if (href.isNotBlank()) {
                Log.d("F1Races", "Bulunan Gofile Linki: $href")
                loadExtractor(href, data, subtitleCallback, callback)
            }
        }

        return true
    }

    private suspend fun processLuluvidLink(
        src: String,
        name: String,
        referer: String,
        mainUrl: String,
        callback: (ExtractorLink) -> Unit
    ) {
        val turkishName = when (name) {
            "Pre-Race Build Up" -> "Yarış Öncesi Analiz"
            "Race Session" -> "Yarış Seansı"
            "Post-Race Analysis" -> "Yarış Sonrası Analiz"
            else -> name
        }
        try {
            val filecode = src.substringAfterLast("/")
            val postUrl = "$mainUrl/dl"
            val post = app.post(
                postUrl,
                data = mapOf(
                    "op" to "embed",
                    "file_code" to filecode,
                    "auto" to "1",
                    "referer" to referer
                )
            ).document

            post.selectFirst("script:containsData(vplayer)")?.data()
                ?.let { script ->
                    Regex("file:\"(.*?)\"").find(script)?.groupValues?.get(1)?.let { link ->
                        callback(
                            newExtractorLink(
                                source = "Luluvid",
                                name = turkishName,
                                url = link,
                            ) {
                                this.referer = mainUrl
                                this.quality = Qualities.P1080.value
                            }
                        )
                    }
                }
        } catch (_: Exception) {
        }
    }

    private fun mixdropIdCoz(encodedId: String): String {
        return try {
            val cleanedId =
                encodedId.removePrefix("07b022").removeSuffix("07d").replace("02203a022", "")
            val charCodes = cleanedId.chunked(3)
            val decodedChars = charCodes.map { it.toInt(16).toChar() }
            decodedChars.joinToString("")
        } catch (_: Exception) {

            ""
        }
    }
}