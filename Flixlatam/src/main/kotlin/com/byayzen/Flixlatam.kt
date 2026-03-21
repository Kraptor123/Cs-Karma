// ! Bu araç @ByAyzen tarafından | @CS-Karma için yazılmıştır.

package com.byayzen

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import org.jsoup.nodes.Document

class Flixlatam : MainAPI() {
    override var mainUrl = "https://flixlatam.com"
    override var name = "FlixLatam"
    override val hasMainPage = true
    override var lang = "mx"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama, TvType.Anime)
    private var dynamicCookies: Map<String, String> = emptyMap()

    private val protectionHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.8,en-US;q=0.5,en;q=0.3",
        "Sec-GPC" to "1",
        "Upgrade-Insecure-Requests" to "1",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "same-origin",
        "Sec-Fetch-User" to "?1",
        "Priority" to "u=0, i",
        "Te" to "trailers"
    )

    override val mainPage = mainPageOf(
        "${mainUrl}/peliculas" to "Películas",
        "${mainUrl}/peliculas/populares" to "Películas Populares",
        "${mainUrl}/series" to "Series",
        "${mainUrl}/series/populares" to "Series Populares",
        "${mainUrl}/animes" to "Anime",
        "${mainUrl}/animes/populares" to "Anime Populares",
        "${mainUrl}/generos/dorama" to "Doramas",
        "${mainUrl}/generos/accion" to "Acción",
        "${mainUrl}/generos/animacion" to "Animación",
        "${mainUrl}/generos/aventura" to "Aventura",
        "${mainUrl}/generos/belica" to "Bélica",
        "${mainUrl}/generos/ciencia-ficcion" to "Ciencia Ficción",
        "${mainUrl}/generos/comedia" to "Comedia",
        "${mainUrl}/generos/crimen" to "Crimen",
        "${mainUrl}/generos/documental" to "Documental",
        "${mainUrl}/generos/drama" to "Drama",
        "${mainUrl}/generos/fantasia" to "Fantasía",
        "${mainUrl}/generos/familia" to "Familia",
        "${mainUrl}/generos/guerra" to "Guerra",
        "${mainUrl}/generos/historia" to "Historia",
        "${mainUrl}/generos/romance" to "Romance",
        "${mainUrl}/generos/suspense" to "Suspense",
        "${mainUrl}/generos/terror" to "Terror",
        "${mainUrl}/generos/western" to "Western",
        "${mainUrl}/generos/misterio" to "Misterio"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) {
            request.data
        } else {
            val separator = if (request.data.contains("?")) "&" else "?"
            "${request.data}${separator}page=$page"
        }

        val document = app.get(url).document
        val home = document.select("article.item").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home, hasNext = true)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val linkElement = this.selectFirst("div.data h3 a") ?: this.selectFirst("div.poster a") ?: return null
        val title = linkElement.text().ifEmpty { this.selectFirst("div.poster img")?.attr("alt") } ?: return null
        val href = fixUrlNull(linkElement.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.poster img")?.attr("src"))

        val isTvSeries = href.contains("/series/") || href.contains("/animes/") || this.hasClass("tvshows")
        val type = if (isTvSeries) TvType.TvSeries else TvType.Movie

        val ratingValue = this.selectFirst("div.rating")?.text()?.toDoubleOrNull()

        return if (isTvSeries) {
            newTvSeriesSearchResponse(title, href, type) {
                this.posterUrl = posterUrl
                this.score = Score.from10(ratingValue)
            }
        } else {
            newMovieSearchResponse(title, href, type) {
                this.posterUrl = posterUrl
                this.score = Score.from10(ratingValue)
            }
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page <= 1) "$mainUrl/search?s=$query" else "$mainUrl/search/page/$page?s=$query"
        val document = app.get(url).document

        val results = document.select("article.item").mapNotNull {
            val linkElement = it.selectFirst(".data h3 a") ?: it.selectFirst(".poster a") ?: return@mapNotNull null
            val title = linkElement.text().ifEmpty { it.selectFirst(".poster img")?.attr("alt") }?.replace("Ver ", "")?.replace(" online", "")?.trim() ?: return@mapNotNull null
            val href = fixUrlNull(linkElement.attr("href")) ?: return@mapNotNull null
            val poster = fixUrlNull(it.selectFirst(".poster img")?.attr("src"))
            val isTv = href.contains("/serie/") || href.contains("/anime/")
            val type = if (isTv) TvType.TvSeries else TvType.Movie

            val year = it.selectFirst(".data span")?.text()?.trim()?.toIntOrNull()
            val ratingValue = it.selectFirst(".rating")?.text()?.toDoubleOrNull()

            if (isTv) {
                newTvSeriesSearchResponse(title, href, type) {
                    this.posterUrl = poster
                    this.year = year
                    this.score = Score.from10(ratingValue)
                }
            } else {
                newMovieSearchResponse(title, href, type) {
                    this.posterUrl = poster
                    this.year = year
                    this.score = Score.from10(ratingValue)
                }
            }
        }

        return newSearchResponseList(results, hasNext = results.isNotEmpty())
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val requestHeaders = protectionHeaders.toMutableMap()
        requestHeaders["Referer"] = "$mainUrl/"

        val response = app.get(url, headers = requestHeaders)

        if (response.cookies.isNotEmpty()) {
            dynamicCookies = response.cookies
        }

        val document = response.document
        val html = response.text
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")
            ?.replace(
                Regex("(?i)▷? ?Ver | ?Audio Latino| ?Online| - Series Latinoamerica| - FlixLatam"),
                ""
            )
            ?.trim() ?: return null

        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")
            ?: document.selectFirst("div.wp-content p")?.text()?.trim()

        val year =
            Regex("""datePublished":"(\d{4})""").find(html)?.groupValues?.get(1)?.toIntOrNull()
        val tags = document.select(".sgeneros a").map { it.text().trim() }
        val rating =
            document.selectFirst(".dt_rating_vgs")?.text()?.replace(",", ".")?.toDoubleOrNull()
        val duration =
            document.selectFirst(".runtime")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()

        val trailerUrl = document.selectFirst("iframe#iframe-trailer")?.attr("src")
            ?: Regex("""embed\/(.*?)[\?|\"]""").find(html)?.groupValues?.get(1)
                ?.let { "https://www.youtube.com/embed/$it" }

        val recommendations =
            document.select(".srelacionados article, #single_relacionados article")
                .mapNotNull { element ->
                    val recTitle =
                        element.selectFirst("img")?.attr("alt") ?: element.selectFirst(".data h3 a")
                            ?.text() ?: return@mapNotNull null
                    val recHref =
                        fixUrlNull(element.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                    val recPoster = fixUrlNull(element.selectFirst("img")?.attr("src"))

                    newMovieSearchResponse(recTitle, recHref, TvType.Movie) {
                        this.posterUrl = recPoster
                    }
                }

        val isAnime = tags.any { it.contains("Anime", ignoreCase = true) }
        val isAsian = tags.any {
            it.contains("Doramas", ignoreCase = true) || it.contains(
                "Asiatica",
                ignoreCase = true
            )
        }
        val isTvSeries = url.contains("/series/") || document.select("#seasons").isNotEmpty()

        val episodesList = if (isTvSeries || isAnime || isAsian) {
            document.select("ul.episodios li").mapNotNull { li ->
                val epLink = li.selectFirst(".episodiotitle a")
                val epHref = fixUrlNull(epLink?.attr("href")) ?: return@mapNotNull null
                val epName = epLink?.text()?.trim()
                val epThumb = fixUrlNull(li.selectFirst(".imagen img")?.attr("src"))
                val numerando = li.selectFirst(".numerando")?.text() ?: "1-1"
                val seasonNum = numerando.substringBefore("-").trim().toIntOrNull() ?: 1
                val episodeNum = numerando.substringAfter("-").trim().toIntOrNull() ?: 1

                newEpisode(epHref) {
                    this.name = epName
                    this.season = seasonNum
                    this.episode = episodeNum
                    this.posterUrl = epThumb
                }
            }
        } else {
            emptyList()
        }

        val loadResponse = when {
            isAnime -> newAnimeLoadResponse(title, url, TvType.Anime) {
                this.episodes = mutableMapOf(DubStatus.None to episodesList)
            }

            isAsian -> newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodesList)
            isTvSeries -> newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesList)
            else -> newMovieLoadResponse(title, url, TvType.Movie, url)
        }

        return loadResponse.apply {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            this.score = rating?.let { Score.from10(it) }
            this.recommendations = recommendations
            if (trailerUrl != null) addTrailer(trailerUrl)
            if (this is MovieLoadResponse) this.duration = duration
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("Cloudstream", "Yüklüyo: $data")

        val response = app.get(data, headers = mapOf("Referer" to mainUrl))
        val iframeUrl = response.document.selectFirst("div.play iframe")?.attr("src")
            ?: response.document.selectFirst("iframe[src*='embed69']")?.attr("src")

        if (iframeUrl == null) return false

        val finalIframeUrl = if (iframeUrl.startsWith("//")) "https:$iframeUrl" else iframeUrl
        return resolveEmbed69(finalIframeUrl, data, subtitleCallback, callback)
    }

    private suspend fun resolveEmbed69(
        url: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val response = app.get(url, headers = mapOf("Referer" to referer))
            val html = response.text

            val tokenlar = Regex("""eyJ[a-zA-Z0-9._-]+""")
                .findAll(html)
                .map { it.value }
                .filter { it.length > 50 }
                .distinct()
                .toList()

            if (tokenlar.isNotEmpty()) {
                val host = java.net.URI(url).host ?: "embed69.org"
                val decryptApi = "https://$host/api/decrypt"

                val decryptResponse = app.post(
                    decryptApi,
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Referer" to url,
                        "Origin" to "https://$host",
                        "X-Requested-With" to "XMLHttpRequest"
                    ),
                    json = mapOf("links" to tokenlar)
                )

                if (decryptResponse.code == 200) {
                    val json = AppUtils.parseJson<Map<String, Any>>(decryptResponse.text)
                    if (json["success"] == true) {
                        val linkListesi = json["links"] as? List<*>

                        linkListesi?.forEach { item ->
                            val hamLink = when (item) {
                                is Map<*, *> -> item["link"] as? String
                                is String -> item
                                else -> null
                            }

                            hamLink?.let {
                                val temizLink = it.replace("`", "").trim()

                                if (temizLink.contains(Regex("embed69|dintezuvio"))) {
                                    Log.d("Cloudstream", "Nested: $temizLink")
                                    ioSafe { resolveEmbed69(temizLink, url, subtitleCallback, callback) }
                                } else {
                                    val fixedUrl = temizLink
                                        .replace("dintezuvio.com", "vidhide.com")
                                        .replace("hglink.to", "streamwish.to")
                                        .replace("minochinos.com", "vidhide.com")
                                        .replace("ghbrisk.com", "streamwish.to")

                                    Log.d("Cloudstream", "Link: $fixedUrl")
                                    loadExtractor(fixedUrl, url, subtitleCallback, callback)
                                }
                            }
                        }
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("Cloudstream", "Hata: ${e.message}")
        }
        return false
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun ioSafe(block: suspend () -> Unit) {
        try {
            kotlinx.coroutines.GlobalScope.launch { block() }
        } catch (e: Exception) {
            Log.d("Cloudstream", "Coroutinehatası: ${e.message}")
        }
    }
}