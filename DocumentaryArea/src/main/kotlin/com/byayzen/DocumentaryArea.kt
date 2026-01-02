// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.
package com.byayzen

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class DocumentaryArea : MainAPI() {
    override var mainUrl = "https://www.documentaryarea.com"
    override var name = "DocumentaryArea"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Documentary)

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Newly Added",
        "$mainUrl/category/Art/" to "Art (All)", "$mainUrl/category/Architecture/" to "Art - Architecture",
        "$mainUrl/category/Cinematography/" to "Art - Cinematography", "$mainUrl/category/Music/" to "Art - Music",
        "$mainUrl/category/Culture/" to "Culture (All)", "$mainUrl/category/Politics/" to "Culture - Politics",
        "$mainUrl/category/Religions/" to "Culture - Religions", "$mainUrl/category/Travel/" to "Culture - Travel",
        "$mainUrl/category/History/" to "History (All)", "$mainUrl/category/Prehistory/" to "History - Prehistory",
        "$mainUrl/category/Ancient/" to "History - Ancient", "$mainUrl/category/Middle+Age/" to "History - Middle Age",
        "$mainUrl/category/War/" to "History - War", "$mainUrl/category/Biographies/" to "History - Biographies",
        "$mainUrl/category/Medicine/" to "Medicine (All)", "$mainUrl/category/Health/" to "Medicine - Health",
        "$mainUrl/category/The+Brain/" to "Medicine - The Brain", "$mainUrl/category/Nature/" to "Nature (All)",
        "$mainUrl/category/Wildlife/" to "Nature - Wildlife", "$mainUrl/category/The+Universe/" to "Nature - Universe",
        "$mainUrl/category/Climate/" to "Nature - Climate", "$mainUrl/category/Science/" to "Science (All)",
        "$mainUrl/category/Astronomy/" to "Science - Astronomy", "$mainUrl/category/Physics/" to "Science - Physics",
        "$mainUrl/category/Biology/" to "Science - Biology", "$mainUrl/category/Technology/" to "Technology (All)",
        "$mainUrl/category/Space/" to "Technology - Space", "$mainUrl/category/The+Future/" to "Technology - Future"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest) = newHomePageResponse(
        request.name, fetchData(request.data, page), true
    )

    override suspend fun search(query: String, page: Int) = newSearchResponseList(
        fetchData("$mainUrl/results/search=$query", page, true), true
    )

    override suspend fun quickSearch(query: String) = search(query)

    private suspend fun fetchData(baseUrl: String, page: Int, isSearch: Boolean = false): List<SearchResponse> {
        val startPage = ((page - 1) * 2) + 1
        val results = mutableListOf<SearchResponse>()
        val isHome = baseUrl.removeSuffix("/") == mainUrl

        for (p in startPage..startPage + 1) {
            val url = when {
                p == 1 -> "$baseUrl/"
                isHome -> "$mainUrl/?pageNum_Recordset1=${p - 1}"
                else -> "${baseUrl.removeSuffix("/")}/page/$p/"
            }
            val items = app.get(url).document.select("div.col-md-8 article").mapNotNull { it.toMainPageResult() }
            if (items.isEmpty()) break
            results.addAll(items)
        }
        return results.distinctBy { it.url }
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val a = selectFirst("h2 a") ?: return null
        val title = a.text().trim().takeIf { it.isNotBlank() } ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val img = selectFirst("img")
        val poster = img?.attr("data-src")?.takeIf { it.isNotBlank() } ?: img?.attr("src")

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = fixUrlNull(poster)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val res = app.get(url)
        val doc = res.document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: return null

        val poster = doc.selectFirst("div#imagen img")?.attr("data-src")
            ?: doc.selectFirst("meta[property=\"og:image\"]")?.attr("content")

        val recs = doc.select("div.news-right-bottom-bg div.item").mapNotNull { item ->
            val href = item.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val img = item.selectFirst("img")
            val pUrl = img?.attr("data-src")?.takeIf { !it.isNullOrBlank() } ?: img?.attr("src")

            newMovieSearchResponse(
                item.selectFirst("h3")?.text() ?: item.selectFirst("img")?.attr("alt") ?: "Video",
                fixUrlNull(href)!!, TvType.Movie
            ) {
                this.posterUrl = fixUrlNull(pUrl)
            }
        }.distinctBy { it.url }

        val cookie = res.cookies["PHPSESSID"] ?: ""
        return newMovieLoadResponse(title, url, TvType.Movie, "$url|$cookie") {
            this.posterUrl = fixUrlNull(poster)
            this.plot = doc.selectFirst("div.comments")?.text()?.trim()
            this.year = doc.selectFirst("div.s-author p")?.text()?.let { Regex("\\d{4}").find(it)?.value?.toIntOrNull() }
            this.recommendations = recs
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        if (data.isEmpty()) return false
        try {
            val parts = data.split("|")
            val pageUrl = parts.getOrNull(0) ?: ""
            val cookieValue = parts.getOrNull(1) ?: ""

            val headersMap = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Cookie" to "PHPSESSID=$cookieValue", "Referer" to pageUrl
            )

            val responseText = app.get(pageUrl, headers = headersMap).text

            """file:\s*"(.*?\.srt)",.*?label:\s*"(.*?)"""".toRegex().findAll(responseText).forEach { match ->
                subtitleCallback.invoke(SubtitleFile(match.groupValues[2], match.groupValues[1]))
            }

            val videoRegex = """file:\s*"(https?://.*?video(?:HD)?\.php.*?)"""".toRegex()
            val finalVideoUrl = videoRegex.find(responseText)?.groupValues?.get(1) ?: "https://www.documentaryarea.com/videoHD.php"

            callback.invoke(newExtractorLink(source = this.name, name = "DocumentaryArea - HD", url = finalVideoUrl, type = ExtractorLinkType.VIDEO) {
                this.referer = pageUrl; this.headers = headersMap
            })

            if (finalVideoUrl.contains("videoHD.php")) {
                callback.invoke(newExtractorLink(source = this.name, name = "DocumentaryArea", url = finalVideoUrl.replace("videoHD.php", "video.php"), type = ExtractorLinkType.VIDEO) {
                    this.referer = pageUrl; this.headers = headersMap
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }
}