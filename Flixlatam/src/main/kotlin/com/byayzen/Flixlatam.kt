// ! Bu araç @ByAyzen tarafından | @CS-Karma için yazılmıştır.

package com.byayzen

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Document

class Flixlatam : MainAPI() {
    override var mainUrl = "https://flixlatam.com"
    override var name = "FlixLatam"
    override val hasMainPage = true
    override var lang = "mx"
    override val hasQuickSearch = false
    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama, TvType.Anime)
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
        "${mainUrl}/pelicula/" to "Películas",
        "${mainUrl}/genero/series/" to "Series",
        "${mainUrl}/genero/anime/" to "Anime",
        "${mainUrl}/genero/dibujo-animado/" to "Cartoons",
        "${mainUrl}/lanzamiento/2025/" to "Estrenos 2025",
        "${mainUrl}/genero/tv-asiatica/" to "Doramas",
        "${mainUrl}/genero/tv-latina/" to "TV Latina"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home = document.select("article.item").mapNotNull { it.toMainPageResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("div.data h3 a")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("div.data h3 a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.poster img")?.attr("src"))
        val isTvSeries = this.hasClass("tvshows") || href.contains("/series/")
        val type = if (isTvSeries) TvType.TvSeries else TvType.Movie

        return if (isTvSeries) {
            newTvSeriesSearchResponse(title, href, type) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, type) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page <= 1) "$mainUrl/?s=$query" else "$mainUrl/page/$page/?s=$query"
        val document = app.get(url).document

        val results = document.select(".result-item article").mapNotNull {
            val titleElement = it.selectFirst(".title a") ?: return@mapNotNull null
            val title = titleElement.text().trim()
            val href = fixUrlNull(titleElement.attr("href")) ?: return@mapNotNull null
            val poster = fixUrlNull(it.selectFirst(".image img")?.attr("src"))
            val isTv = it.select(".image span.tvshows").isNotEmpty()
            val year = it.selectFirst(".meta .year")?.text()?.trim()?.toIntOrNull()

            if (isTv) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = poster
                    this.year = year
                }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = poster
                    this.year = year
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

        if (html.contains("Bot Verification") || html.contains("hcaptcha")) {
            Log.e("ByAyzen", "⛔ Load Fonksiyonunda Bot Koruması Algılandı!")
        }

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
        Log.d("ByAyzen", "LoadLinks: $data")

        val sayfayaniti = app.get(
            data,
            headers = protectionHeaders + ("Referer" to mainUrl),
            cookies = dynamicCookies
        ).also {
            if (it.cookies.isNotEmpty()) dynamicCookies = it.cookies
        }

        val icerikid = postIdBul(sayfayaniti.document, sayfayaniti.text) ?: return false
        val iceriktipi = if (data.contains(Regex("episodio|/series/|/tv/"))) "tv" else "movie"

        var bulundu = false
        val islenenadresler = mutableSetOf<String>()

        for (i in 1..6) {
            val apiadresi = "$mainUrl/wp-json/dooplayer/v2/$icerikid/$iceriktipi/$i"
            val sunucuyaniti = app.get(
                apiadresi,
                headers = protectionHeaders + mapOf(
                    "Referer" to data,
                    "X-Requested-With" to "XMLHttpRequest"
                ),
                cookies = dynamicCookies
            ).text

            if (sunucuyaniti.contains("server_error") || sunucuyaniti.trim() == "0") continue

            val embedurl = AppUtils.parseJson<DooPlayerResponse>(sunucuyaniti).embedUrl?.let {
                if (it.startsWith("//")) "https:$it" else it
            } ?: continue

            if (islenenadresler.add(embedurl)) {
                Log.d("ByAyzen", "Server $i: $embedurl")
                if (embedurl.contains(Regex("embed69|dintezuvio"))) {
                    resolveEmbed69(embedurl, data, subtitleCallback, callback)
                    bulundu = true
                } else {
                    if (loadExtractor(embedurl, data, subtitleCallback, callback)) bulundu = true
                }
            }
            if (!bulundu && i >= 3) break
        }
        return bulundu
    }

    private fun postIdBul(dokuman: Document, htmlicerik: String): String? {
        return dokuman.selectFirst("link[rel*='shortlink']")?.attr("href")
            ?.let { Regex("""[?&]p=(\d+)""").find(it)?.groupValues?.get(1) }
            ?: Regex(""""postId":\s*"(\d+)"""").find(htmlicerik)?.groupValues?.get(1)
            ?: dokuman.selectFirst("input[name=postid]")?.attr("value")
            ?: Regex("""postid-(\d+)""").find(dokuman.body().className())?.groupValues?.get(1)
    }

    private suspend fun resolveEmbed69(
        url: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val yanit = app.get(url, headers = mapOf("Referer" to referer))

            if (yanit.url != url && !yanit.url.contains(Regex("embed69|dintezuvio"))) {
                loadExtractor(yanit.url, referer, subtitleCallback, callback)
                return
            }

            val veriblogu = Regex("""let\s+dataLink\s*=\s*(\[\{.*?\}\]);""").find(yanit.text)?.groupValues?.get(1) ?: return
            val diller = AppUtils.parseJson<List<Embed69Language>>(veriblogu)

            val sifrelilinkler = diller.flatMap { dil ->
                dil.sortedEmbeds?.mapNotNull { it.link ?: it.download } ?: emptyList()
            }.distinct()

            if (sifrelilinkler.isNotEmpty()) {
                val cozmeyaniti = app.post(
                    "https://embed69.org/api/decrypt",
                    headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest",
                        "Referer" to url,
                        "Origin" to "https://embed69.org"
                    ),
                    json = mapOf("links" to sifrelilinkler)
                )

                if (cozmeyaniti.code == 200) {
                    AppUtils.parseJson<Embed69ApiResponse>(cozmeyaniti.text).links?.forEach { oge ->
                        oge.link?.let { hamlink ->
                            val temizlink = hamlink.replace("`", "").trim()
                            val yonlendirilmislink = temizlink
                                .replace("dintezuvio.com", "vidhide.com")
                                .replace("hglink.to", "streamwish.to")

                            Log.d("ByAyzen", "Extractor: $yonlendirilmislink")
                            loadExtractor(yonlendirilmislink, url, subtitleCallback, callback)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("ByAyzen", "Resolve Error: ${e.message}")
        }
    }

    data class Embed69Language(val video_language: String?, val sortedEmbeds: List<Embed69Link>?)
    data class Embed69Link(val link: String?, val download: String?)
    data class DecryptedLink(val link: String?)
    data class Embed69ApiResponse(val links: List<DecryptedLink>?)
    data class DooPlayerResponse(@JsonProperty("embed_url") val embedUrl: String?)
}