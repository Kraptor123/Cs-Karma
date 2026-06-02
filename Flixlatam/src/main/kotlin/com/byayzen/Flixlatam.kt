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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.jsoup.nodes.Document
import java.security.MessageDigest

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

        val title = document.selectFirst(".data h1")!!.text().trim()
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")
            ?: document.selectFirst("div.wp-content p")?.text()?.trim()

        val year = document.selectFirst(".extra .date a")?.text()?.toIntOrNull()
        val tags = document.select(".sgeneros a").map { it.text().trim() }
        val rating = document.selectFirst(".rating-value")?.text()?.replace(",", ".")
            ?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()
        val duration =
            document.selectFirst(".runtime")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()

        val trailerUrl = document.selectFirst("iframe#iframe-trailer")?.attr("src")
            ?: Regex("""embed\/(.*?)[\?|\"]""").find(html)?.groupValues?.get(1)
                ?.let { "https://www.youtube.com/embed/$it" }

        val recommendations = document.select(".srelacionados .item").map { element ->
            val title = element.selectFirst(".data h3 a")!!.text()
            val href = fixUrl(element.selectFirst("a")!!.attr("href"))
            val poster = fixUrlNull(element.selectFirst("img")?.attr("src"))
            val type = if (href.contains("/pelicula/")) {
                TvType.Movie
            } else if (href.contains("/serie/")) {
                TvType.TvSeries
            } else {
                TvType.Anime
            }

            newMovieSearchResponse(title, href, type) {
                this.posterUrl = poster
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
            this.score = rating?.let { Score.from(it, 10) }
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
        val response = app.get(data, headers = mapOf("Referer" to mainUrl))
        val iframeUrl = response.document.selectFirst("div.play iframe")?.attr("src")
            ?: response.document.selectFirst("iframe[src*='embed69']")?.attr("src")
            ?: response.document.selectFirst("iframe[src*='/vidurl/']")?.attr("src")
            ?: return false

        val finalIframeUrl = fixUrlNull(iframeUrl) ?: return false
        return resolveEmbed69(finalIframeUrl, data, subtitleCallback, callback)
    }

    private suspend fun resolveEmbed69(
        url: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val html = app.get(url, headers = mapOf("Referer" to referer)).text

            val powChallenge = Regex("""const POW_CHALLENGE\s*=\s*'([^']+)';""").find(html)?.groupValues?.get(1) ?: return false
            val powDifficulty = Regex("""const POW_DIFFICULTY\s*=\s*(\d+);""").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 3
            val powSalt = Regex("""const POW_SALT\s*=\s*'([^']+)';""").find(html)?.groupValues?.get(1) ?: return false

            Log.d(name, "PoW: $powChallenge | $powDifficulty | $powSalt")

            val aesKey = withContext(Dispatchers.Default) {
                solvePow(powChallenge, powDifficulty, powSalt)
            }

            val dataLinkJson = Regex("""let\s+dataLink\s*=\s*(\[[\s\S]*?\]);""").find(html)?.groupValues?.get(1) ?: return false
            val dataList = AppUtils.parseJson<List<Map<String, Any>>>(dataLinkJson)
            var foundAny = false

            dataList.forEach { entry ->
                val allEmbeds = (entry["sortedEmbeds"] as? List<*>) ?: listOf<Any>()
                val downloadEmbeds = (entry["downloadEmbeds"] as? List<*>) ?: listOf<Any>()

                (allEmbeds + downloadEmbeds).forEach { item ->
                    val embed = item as? Map<String, Any>
                    val encryptedLink = embed?.get("link") as? String ?: return@forEach

                    val decryptedLink = decryptAES(encryptedLink, aesKey)

                    if (decryptedLink != null && decryptedLink.startsWith("http")) {
                        val fixedUrl = decryptedLink
                            .replace("dintezuvio.com", "vidhide.com")
                            .replace("hglink.to", "streamwish.to")
                            .replace("minochinos.com", "vidhide.com")
                            .replace("ghbrisk.com", "streamwish.to")

                        Log.d(name, fixedUrl)
                        loadExtractor(fixedUrl, url, subtitleCallback, callback)
                        foundAny = true
                    }
                }
            }
            return foundAny
        } catch (e: Exception) {
            Log.e(name, "${e.message}")
        }
        return false
    }

    private fun solvePow(challenge: String, difficulty: Int, salt: String): ByteArray {
        val prefix = "0".repeat(difficulty)
        var nonce = 0
        val digest = MessageDigest.getInstance("SHA-256")

        while (true) {
            val hashBytes = digest.digest("$challenge$nonce".toByteArray(Charsets.UTF_8))
            val hashHex = hashBytes.joinToString("") { "%02x".format(it) }

            if (hashHex.startsWith(prefix)) {
                return digest.digest("$challenge$nonce$salt".toByteArray(Charsets.UTF_8))
            }
            nonce++
        }
    }

    private fun decryptAES(encryptedBase64: String, aesKey: ByteArray): String? {
        return try {
            val decodedBytes = android.util.Base64.decode(encryptedBase64, android.util.Base64.DEFAULT)
            if (decodedBytes.size <= 16) return null

            val iv = decodedBytes.copyOfRange(0, 16)
            val ciphertext = decodedBytes.copyOfRange(16, decodedBytes.size)
            val key = aesKey.copyOfRange(0, 32)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}