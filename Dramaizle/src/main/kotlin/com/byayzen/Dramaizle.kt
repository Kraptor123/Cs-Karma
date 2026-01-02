// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class Dramaizle : MainAPI() {
    override var mainUrl = "https://dramaizle.net"
    override var name = "Dramaizle"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.AsianDrama)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "${mainUrl}/filmler" to "Tüm Filmler",
        "${mainUrl}/film/tur/netshort-dizileri" to "Netshort Filmleri",
        "${mainUrl}/film/tur/shortmax" to "ShortMax Filmleri",
        "${mainUrl}/film/tur/dramabox" to "DramaBox Filmleri",
        "${mainUrl}/film/tur/flextv" to "FlexTV Filmleri",
        "${mainUrl}/film/tur/dramawave" to "DramaWave Filmleri",
        "${mainUrl}/film/tur/star-dust-tv" to "StarDust TV Filmleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}/$page"
        val document = app.get(url).document
        val home = document.select("ul.flex.flex-wrap.row li").mapNotNull {
            it.toMainPageResult()
        }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("h2")?.text() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)

        val imgElement = this.selectFirst("a img")
        val posterUrl = imgElement?.attr("data-src")?.ifBlank { imgElement.attr("src") } ?: imgElement?.attr("src")

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = fixUrlNull(posterUrl)
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        if (page > 1) return newSearchResponseList(emptyList(), false)
        return try {
            val res = app.post("$mainUrl/search", data = mapOf("query" to query),
                headers = mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to "$mainUrl/")).text
            val html = AppUtils.parseJson<SearchJson>(res).theme ?: ""
            val results = Jsoup.parse(html).select("div.result-movies").mapNotNull {
                val a = it.selectFirst("div.result-movies-text a") ?: return@mapNotNull null
                newMovieSearchResponse(a.text().trim(), fixUrlNull(a.attr("href")) ?: return@mapNotNull null, TvType.Movie) {
                    posterUrl = fixUrlNull(it.selectFirst("img")?.let { i -> i.attr("data-src").ifBlank { i.attr("src") } })
                }
            }
            newSearchResponseList(results, false)
        } catch (e: Exception) { newSearchResponseList(emptyList(), false) }
    }


    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)


    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val header = doc.selectFirst("div.page-title")
        val tr = header?.selectFirst("h1")?.ownText()?.trim() ?: ""
        val en = header?.selectFirst("p")?.text()?.trim() ?: ""
        val title = if (en.isNotEmpty() && en != tr) "$en - $tr" else tr

        val infos = doc.select("div.filter-result-box ul li, div.series-profile-info ul li")
        val year = infos.find { it.text().contains("Yıl", true) }?.selectFirst("p")?.text()?.filter { it.isDigit() }?.toIntOrNull()
        val duration = infos.find { it.text().contains("Süre", true) }?.selectFirst("p")?.text()?.filter { it.isDigit() }?.toIntOrNull()
        val rating = infos.find { it.text().contains("IMDB", true) }?.selectFirst("span.color-imdb")?.text()?.toDoubleOrNull()

        val iframeSrc = doc.selectFirst("iframe[src*='/video/']")?.attr("src") ?: url

        return newMovieLoadResponse(title, url, TvType.Movie, iframeSrc) {
            this.posterUrl = fixUrlNull(doc.selectFirst("div.series-profile-image img")?.attr("src"))
            this.plot = doc.selectFirst("div.series-profile-infos-in.article p")?.text()?.trim()
            this.year = year
            this.duration = duration
            this.score = rating?.let { Score.from10(it) }
            this.tags = doc.select("div.series-profile-type a").map { it.text() }
            addActors(doc.select("div.series-profile-cast ul li").map { Actor(it.text().trim()) })
            addTrailer(doc.selectFirst("iframe[src*='youtube']")?.attr("src"))
            this.recommendations = doc.select("ul.flex.flex-wrap.row li").mapNotNull { it.toMainPageResult() }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank() || !data.contains("/video/")) return false

        try {
            val baseUrl = data.substringBefore("/video/")
            val videoId = data.substringAfter("/video/")

            val json = app.post(
                "$baseUrl/player/index.php?data=$videoId&do=getVideo",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to data)
            ).text

            val m3u8 = """securedLink"\s*:\s*"(.*?)"""".toRegex().find(json)?.groupValues?.get(1)
                ?.replace("\\/", "/")

            if (!m3u8.isNullOrBlank()) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = "Dramaİzle",
                        url = m3u8,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$baseUrl/"
                        this.headers =
                            mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    })
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
    }
}

data class SearchJson(@JsonProperty("theme") val theme: String? = null)
