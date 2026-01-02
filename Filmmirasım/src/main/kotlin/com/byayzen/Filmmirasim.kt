// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class Filmmirasim : MainAPI() {
    override var mainUrl = "https://filmmirasim.ktb.gov.tr"
    override var name = "Filmirasim"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Documentary)
    override val mainPage = mainPageOf(
        "${mainUrl}/tr/categories/6/" to "1895-1918",
        "${mainUrl}/tr/categories/5/" to "1918-1938",
        "${mainUrl}/tr/categories/18/" to "1938-1950",
        "${mainUrl}/tr/categories/19/" to "1950-1960",
        "${mainUrl}/tr/categories/20/" to "1960 Sonrası",
        "${mainUrl}/tr/categories/23/" to "Diğer"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) {
            request.data
        } else {
            val baseUrl = if (request.data.endsWith("/")) request.data else "${request.data}/"
            "$baseUrl$page"
        }
        val document = app.get(url).document
        val home = document.select("div.edd_download").mapNotNull { it.toMainPageResult() }
        return newHomePageResponse(request.name, home, hasNext = home.isNotEmpty())
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("a")?.attr("title")?.ifBlank { null }
            ?: this.selectFirst("a")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page == 1) {
            "${mainUrl}/tr/search/0/0/${query}"
        } else {
            "${mainUrl}/tr/search/0/0/${query}/all/all/page/${page}"
        }

        val belge = app.get(url).document
        val aramaSonuclari = belge.select("article.entry-item").mapNotNull { eleman ->
            val baslik = eleman.selectFirst("h3.entry-title a")?.text() ?: return@mapNotNull null
            val filmUrl = eleman.selectFirst("h3.entry-title a")?.attr("href") ?: return@mapNotNull null
            val poster = eleman.selectFirst("div.entry-thumb img")?.attr("src")

            newMovieSearchResponse(baslik, filmUrl, TvType.Movie) {
                this.posterUrl = fixUrlNull(poster)
            }
        }

        val sonrakiSayfaVar = belge.selectFirst("ul.pagination a.next") != null
        return newSearchResponseList(aramaSonuclari, hasNext = sonrakiSayfaVar)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: return null
        val scripts = document.select("script").map { it.html() }
        val scriptWithThumbnail = scripts.find { it.contains("var videoThumbnail") }
        val thumbnailUrl = Regex("""var videoThumbnail = "([^"]+)";""").find(scriptWithThumbnail ?: "")?.groupValues?.get(1)
        val poster = fixUrlNull(thumbnailUrl)
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        val durationText = document.selectFirst("span#ctl00_ContentPlaceHolder1_lblVideoSuresi")?.text()?.trim()
        val duration = durationText?.substringAfter("Süre :")?.trim()?.split(":")?.let {
            if (it.size >= 2) it[0].toIntOrNull()?.times(60)?.plus(it[1].toIntOrNull() ?: 0) else null
        }

        val recommendations = document.select("div.latest_slider .item").mapNotNull { element ->
            val recTitle = element.selectFirst("span.title")?.text()?.trim() ?: return@mapNotNull null
            val recHref = element.selectFirst("a.hover-link")?.attr("href")?.trim() ?: return@mapNotNull null
            val recPoster = fixUrlNull(element.selectFirst("img")?.attr("src")?.trim())

            newMovieSearchResponse(recTitle, recHref, TvType.Movie) {
                this.posterUrl = recPoster
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
            this.duration = duration
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (!data.startsWith("http")) {
            Log.e("Filmmirasim", "HATA: Geçersiz URL: $data")
            return false
        }

        val document = app.get(data).document
        val scriptWithSources = document.select("script").find { it.html().contains("var sources = JSON.parse") }?.html()

        if (scriptWithSources != null) {
            try {
                val sourcesJson = Regex("""JSON\.parse\('(.+?)'\)""").find(scriptWithSources)?.groupValues?.get(1)

                if (sourcesJson != null) {
                    val cleanedJson = sourcesJson.replace("\\'", "'")
                    val mapper = jacksonObjectMapper()
                    val sourcesList: List<Map<String, String>> = mapper.readValue(cleanedJson)

                    sourcesList.forEach { source ->
                        val videoUrl = source["src"]
                        val qualityLabel = source["label"] ?: "Bilinmeyen Kalite"

                        if (!videoUrl.isNullOrBlank()) {
                            callback.invoke(
                                newExtractorLink(
                                    source = name,
                                    name = "$name",
                                    url = videoUrl,
                                    INFER_TYPE
                                ) {
                                    this.referer = data
                                    this.headers = mapOf("Origin" to mainUrl)
                                    this.quality = getQualityFromName(qualityLabel)
                                }
                            )
                        }
                    }
                    return true
                }
            } catch (e: Exception) {
                Log.e("Filmmirasim", "Link ayrıştırma hatası: ${e.message}")
            }
        }

        return false
    }
}