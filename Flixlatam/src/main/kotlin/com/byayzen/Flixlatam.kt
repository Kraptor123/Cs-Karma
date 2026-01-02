// ! Bu araç @ByAyzen tarafından | @kekikanime için yazılmıştır.

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
    override var lang = "es"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama, TvType.Anime)

    // --- DİNAMİK COOKIE DEPOSU ---
    private var dynamicCookies: Map<String, String> = emptyMap()

    // --- HEADER AYARLARI ---
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
        val document = app.get("${request.data}").document
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
        val hasNext = document.selectFirst(".pagination .next, .pagination a.next") != null
        return newSearchResponseList(results, hasNext = hasNext)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val requestHeaders = protectionHeaders.toMutableMap()
        requestHeaders["Referer"] = "$mainUrl/"

        val response = app.get(url, headers = requestHeaders)

        // --- LOG COOKIE ---
        // Sunucudan gelen cookie'leri yazdırıyoruz
        if (response.cookies.isNotEmpty()) {
            dynamicCookies = response.cookies
            Log.d("ByAyzen", "🍪 Load Kısmında ALINAN Cookie'ler: ${response.cookies}")
        } else {
            Log.d("ByAyzen", "⚠️ Load Kısmında sunucu hiç Cookie göndermedi!")
        }

        val document = response.document
        val html = response.text

        if (html.contains("Bot Verification") || html.contains("hcaptcha")) {
            Log.e("ByAyzen", "⛔ Load Fonksiyonunda Bot Koruması Algılandı!")
        }

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")
            ?.replace(Regex("(?i)▷? ?Ver | ?Audio Latino| ?Online| - Series Latinoamerica| - FlixLatam"), "")
            ?.trim() ?: return null

        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")
            ?: document.selectFirst("div.wp-content p")?.text()?.trim()

        val year = Regex("""datePublished":"(\d{4})""").find(html)?.groupValues?.get(1)?.toIntOrNull()
        val tags = document.select(".sgeneros a").map { it.text().trim() }
        val rating = document.selectFirst(".dt_rating_vgs")?.text()?.replace(",", ".")?.toDoubleOrNull()
        val duration = document.selectFirst(".runtime")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()

        val trailerUrl = document.selectFirst("iframe#iframe-trailer")?.attr("src")
            ?: Regex("""embed\/(.*?)[\?|\"]""").find(html)?.groupValues?.get(1)?.let { "https://www.youtube.com/embed/$it" }

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
        val isAsian = tags.any { it.contains("Doramas", ignoreCase = true) || it.contains("Asiatica", ignoreCase = true) }
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

        return when {
            isAnime -> newAnimeLoadResponse(title, url, TvType.Anime) {
                this.episodes = mutableMapOf(DubStatus.None to episodesList)
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = rating?.let { Score.from10(it) }
                this.recommendations = recommendations
                if (trailerUrl != null) addTrailer(trailerUrl)
            }
            isAsian -> newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodesList) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = rating?.let { Score.from10(it) }
                this.recommendations = recommendations
                if (trailerUrl != null) addTrailer(trailerUrl)
            }
            isTvSeries -> newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesList) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = rating?.let { Score.from10(it) }
                this.recommendations = recommendations
                if (trailerUrl != null) addTrailer(trailerUrl)
            }
            else -> newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = rating?.let { Score.from10(it) }
                this.duration = duration
                this.recommendations = recommendations
                if (trailerUrl != null) addTrailer(trailerUrl)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            Log.d("ByAyzen", "LoadLinks: $data")
            // İsteği at ve cookie gelirse anında güncelle
            val res = app.get(data, headers = protectionHeaders + ("Referer" to mainUrl), cookies = dynamicCookies).also {
                if (it.cookies.isNotEmpty()) dynamicCookies = it.cookies
            }

            if (res.text.contains("Bot Verification") || res.text.contains("hcaptcha")) {
                Log.e("ByAyzen", "⛔ Bot Koruması!")
                return false
            }

            // ID'yi bulamazsa loglayıp çık
            val id = findPostId(res.document, res.text) ?: return false.also { Log.e("ByAyzen", "⛔ ID Bulunamadı") }
            Log.d("ByAyzen", "🎯 ID: $id")

            val type = if (data.contains("episodio") || data.contains("/series/") || data.contains("/tv/")) "tv" else "movie"
            var found = false

            // Sunucuları tara (1..6)
            for (i in 1..6) {
                if (processDooplayLink(id, type, "$i", data, subtitleCallback, callback)) found = true
                // İlk 3 denemede sonuç yoksa döngüyü kes (Boşuna deneme)
                if (!found && i >= 3) break
            }
            return found
        } catch (e: Exception) {
            Log.e("ByAyzen", "Err: ${e.message}")
            return false
        }
    }

    // Tek satırlık zincirleme kontrol (Elvis Operatörü)
    private fun findPostId(doc: Document, html: String) =
        doc.selectFirst("link[rel*='shortlink']")?.attr("href")?.let { Regex("""[?&]p=(\d+)""").find(it)?.groupValues?.get(1) }
            ?: Regex(""""postId":\s*"(\d+)"""").find(html)?.groupValues?.get(1)
            ?: doc.selectFirst("input[name=postid]")?.attr("value")
            ?: Regex("""postid-(\d+)""").find(doc.body().className())?.groupValues?.get(1)
            ?: doc.selectFirst("[data-post]")?.attr("data-post")

    private suspend fun processDooplayLink(id: String, type: String, num: String, ref: String, sub: (SubtitleFile) -> Unit, cb: (ExtractorLink) -> Unit): Boolean {
        return try {
            val url = "$mainUrl/wp-json/dooplayer/v2/$id/$type/$num"
            val headers = protectionHeaders + mapOf("Referer" to ref, "X-Requested-With" to "XMLHttpRequest", "Content-Type" to "application/json")

            val json = app.get(url, headers = headers, cookies = dynamicCookies).text
            if (json.contains("server_error") || json.trim() == "0") return false

            val embedUrl = AppUtils.parseJson<DooPlayerResponse>(json).embedUrl?.let { if (it.startsWith("//")) "https:$it" else it } ?: return false
            Log.d("ByAyzen", "✅ Server $num: $embedUrl")
            resolveEmbed69(embedUrl, ref, sub, cb)
            true
        } catch (e: Exception) { false }
    }

    private suspend fun resolveEmbed69(url: String, ref: String, sub: (SubtitleFile) -> Unit, cb: (ExtractorLink) -> Unit) {
        try {
            val ua = protectionHeaders["User-Agent"]!!
            val res = app.get(url, headers = mapOf("User-Agent" to ua, "Referer" to ref))

            if (res.url != url && !res.url.contains("embed69") && !res.url.contains("dintezuvio")) {
                loadExtractor(res.url, ref, sub, cb); return
            }
            val json = Regex("""let\s+dataLink\s*=\s*(\[\{.*?\}\]);""").find(res.text)?.groupValues?.get(1)
            if (json != null) {
                val links = AppUtils.parseJson<List<Embed69File>>(json)
                    .flatMap { it.sortedEmbeds ?: emptyList() }
                    .mapNotNull { it.link ?: it.download }

                if (links.isNotEmpty()) {
                    val decrypted = app.post("https://embed69.org/api/decrypt",
                        headers = mapOf("User-Agent" to ua, "Content-Type" to "application/json", "X-Requested-With" to "XMLHttpRequest", "Referer" to res.url, "Origin" to "https://embed69.org"),
                        json = mapOf("links" to links)
                    ).text
                    AppUtils.parseJson<Embed69ApiResponse>(decrypted).links?.forEach { it.link?.let { l -> loadExtractor(l, res.url, sub, cb) } }
                }
            } else {
                // Iframe Kontrolü
                res.document.selectFirst("iframe")?.attr("src")?.let {
                    val fixed = if (it.startsWith("//")) "https:$it" else it
                    if (fixed.contains("embed69") || fixed.contains("dintezuvio")) {
                        if (fixed != url) resolveEmbed69(fixed, res.url, sub, cb)
                    } else loadExtractor(fixed, res.url, sub, cb)
                }
            }
        } catch (e: Exception) { Log.e("ByAyzen", "Embed Err: ${e.message}") }
    }

    data class Embed69File(
        @JsonProperty("video_language") val videoLanguage: String?,
        @JsonProperty("sortedEmbeds") val sortedEmbeds: List<Embed69Server>?
    )

    data class Embed69Server(
        @JsonProperty("servername") val servername: String?,
        @JsonProperty("link") val link: String?,
        @JsonProperty("download") val download: String?
    )

    data class Embed69ApiResponse(
        @JsonProperty("success") val success: Boolean?,
        @JsonProperty("links") val links: List<Embed69LinkItem>?
    )

    data class Embed69LinkItem(
        @JsonProperty("link") val link: String?,
        @JsonProperty("index") val index: Int?
    )

    data class DooPlayerResponse(
        @JsonProperty("embed_url") val embedUrl: String?,
        @JsonProperty("type") val type: String?
    )
}