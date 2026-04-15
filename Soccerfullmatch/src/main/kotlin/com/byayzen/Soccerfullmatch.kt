// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.StringUtils.decodeUri
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64


class Soccerfullmatch : MainAPI() {
    override var mainUrl = "https://soccerfullmatch.com"
    override var name = "Soccerfullmatch"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    private val mainHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
        "Referer" to "$mainUrl/",
        "Upgrade-Insecure-Requests" to "1"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Latest Matches",
        "$mainUrl/premier-league/" to "Premier League",
        "$mainUrl/la-liga/" to "La Liga",
        "$mainUrl/serie-a/" to "Serie A"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) "${request.data}/" else "${request.data}/page/$page/"
        val document = app.get(url, headers = mainHeaders).document

        val elements = document.select("ul.g1-collection-items li.g1-collection-item")

        val home = elements.mapNotNull { el ->
            val link = el.selectFirst("h3 a.bookmark") ?: el.selectFirst("h3 a") ?: el.selectFirst("a[href]")
            val title = link?.text()?.trim() ?: return@mapNotNull null
            val href = fixUrlNull(link.attr("href")) ?: return@mapNotNull null

            newMovieSearchResponse(title, href, TvType.Live) {
                this.posterUrl = "https://cdn1.dotesports.com/wp-content/uploads/2022/02/16180311/Match-Replay-1024x559.png"
            }
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page == 1) "${mainUrl}/?s=${query}" else "${mainUrl}/page/$page/?s=${query}"
        val document = app.get(url, headers = mainHeaders).document

        val elements = document.select("ul.g1-collection-items li.g1-collection-item")
        val results = elements.mapNotNull { el ->
            val link = el.selectFirst("h3 a.bookmark") ?: el.selectFirst("h3 a") ?: el.selectFirst("a[href]")
            val title = link?.text()?.trim() ?: return@mapNotNull null
            val href = fixUrlNull(link.attr("href")) ?: return@mapNotNull null
            val posterUrl = fixUrlNull(el.selectFirst("img")?.attr("src"))

            newMovieSearchResponse(title, href, TvType.Live) {
                this.posterUrl = posterUrl
            }
        }

        return newSearchResponseList(results, hasNext = true)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = "https://cdn1.dotesports.com/wp-content/uploads/2022/02/16180311/Match-Replay-1024x559.png"
        val description = document.selectFirst("div.g1-content-narrow p")?.text()?.trim()
        val tags = document.select("div.sgeneros a").map { it.text() }

        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("kraptor_$name", data)
        val document = app.get(data).document
        val videolar = document.select("a[href*=em=], a[href*=\"dl=\"]")

        Log.d("kraptor_$name", videolar.size.toString())

        videolar.forEach { video ->
            val href = video.attr("href")
            Log.d("kraptor_$name", href)

            val part = Regex("pt=([^&]+)").find(href)?.groupValues?.get(1)?.replace("%20", " ") ?: ""
            val channel = Regex("ch=([^&]+)").find(href)?.groupValues?.get(1)?.replace("%20", " ") ?: ""

            val sifreliKisim = href.substringAfter("=").substringBefore("&")

            val sifreliKisimDevam = sifreliKisim.let { encoded ->
                val sifrecozen = (4 - (encoded.length % 4)) % 4
                encoded + "=".repeat(sifrecozen)
            }

            try {
                val sifreCoz = base64Decode(sifreliKisimDevam)
                Log.d("kraptor_$name", sifreCoz)

                if (sifreCoz.contains("mp4") || sifreCoz.contains("m3u8")) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val sourceName = sifreCoz.split("/")[2].uppercase()
                        Log.d("kraptor_$name", sourceName)

                        val linkName = listOf(part, sourceName, channel).filter { it.isNotBlank() }.joinToString(" - ")

                        callback.invoke(
                            newExtractorLink(
                                source = sourceName,
                                name = linkName,
                                url = sifreCoz,
                                type = INFER_TYPE
                            ) {
                                this.referer = sifreCoz
                            }
                        )
                    }
                } else {
                    loadCustomExtractor(sifreCoz, data, part, channel, subtitleCallback, callback)
                }
            } catch (e: Exception) {
                Log.d("kraptor_$name", e.message.toString())
            }
        }
        return true
    }

    private suspend fun loadCustomExtractor(
        url: String,
        referer: String? = null,
        part: String,
        channel: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        quality: Int? = null,
    ) {
        Log.d("kraptor_$name", url)
        loadExtractor(url, referer, subtitleCallback) { ex ->
            if (ex.url.isNotBlank() && (ex.url.startsWith("http") || ex.url.startsWith("https"))) {
                Log.d("kraptor_$name", ex.url)
                CoroutineScope(Dispatchers.IO).launch {
                    val linkName = listOf(part, ex.source, channel).filter { it.isNotBlank() }.joinToString(" - ")

                    callback.invoke(
                        newExtractorLink(
                            source = ex.source,
                            name = linkName,
                            url = ex.url,
                            type = ex.type
                        ) {
                            this.quality = quality ?: ex.quality
                            this.referer = ex.referer
                            this.headers = ex.headers
                            this.extractorData = ex.extractorData
                        }
                    )
                }
            }
        }
    }
}