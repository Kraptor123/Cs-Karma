package com.byayzen

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.AppUtils
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class GnulaHD : MainAPI() {
    override var mainUrl = "https://ww3.gnulahd.nu"
    override var name = "GnulaHD"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "mx"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/ver/?type=Pelicula&order=latest" to "Últimas Películas",
        "$mainUrl/ver/?type=Serie&order=latest" to "Últimas Series",
        "$mainUrl/ver/?type=Anime&order=latest" to "Últimos Animes",
        "$mainUrl/ver/?type=Pelicula&order=popular" to "Películas Populares",
        "$mainUrl/ver/?type=Serie&order=popular" to "Series Populares",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) {
            request.data
        } else {
            if (request.data.contains("?")) {
                "${request.data}&page=$page"
            } else {
                "${request.data.removeSuffix("/")}/page/$page/"
            }
        }

        val response = app.get(
            url,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            ),
            timeout = 45
        )

        val document = response.document
        val elements = document.select("div.postbody div.listupd article.bs")

        val home = elements.mapNotNull {
            it.toSearchResult()
        }

        val hasNext = document.select("div.hpage a.r").isNotEmpty() ||
                document.select("div.pagination a.next").isNotEmpty()

        return newHomePageResponse(request.name, home, hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"

        val response = app.get(
            url,
            referer = mainUrl,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
        )

        val document = response.document
        val elements = document.select("div.listupd article.bs")

        return elements.mapNotNull {
            it.toSearchResult()
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("div.bsx > a")
        var title = titleElement?.attr("title")?.trim() ?: ""

        if (title.isEmpty()) {
            title = this.selectFirst("div.tt h2")?.text()?.trim() ?: "Unknown Title"
        }

        if (this.hasClass("styleegg")) return null

        val typeText = this.selectFirst("div.typez")?.text() ?: ""
        val type = when {
            typeText.contains("Serie", true) -> TvType.TvSeries
            typeText.contains("Anime", true) -> TvType.Anime
            else -> TvType.Movie
        }

        val aTag = this.selectFirst("div.bsx > a") ?: return null
        val href = fixUrl(aTag.attr("href"))

        if (href.contains("/blog/")) return null

        if (title.contains("Mejores", ignoreCase = true) || title.contains(
                "Cronología",
                ignoreCase = true
            )
        ) {
            return null
        }

        val imgElement = this.selectFirst("img.ts-post-image")
            ?: this.selectFirst("img.wp-post-image")
            ?: this.selectFirst("div.limit img")

        val rawPoster = imgElement?.attr("src")?.ifEmpty { null }
            ?: imgElement?.attr("data-src")?.ifEmpty { null }
            ?: imgElement?.attr("data-lazy-src")?.ifEmpty { null }

        val posterUrl = rawPoster?.substringBefore("?")

        return when (type) {
            TvType.TvSeries -> newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = fixUrlNull(posterUrl)
            }

            TvType.Anime -> newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = fixUrlNull(posterUrl)
            }

            else -> newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = fixUrlNull(posterUrl)
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url, timeout = 60)
        val document = response.document

        val title = document.selectFirst("h1.gnpv-title")?.text()?.trim() ?: return null

        val poster = document.selectFirst("div.gnpv-poster img")?.attr("src")
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")

        val posterurl = fixUrlNull(poster?.substringBefore("?"))

        val description = document.selectFirst("div.gnpv-syn-text")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")

        val badge = document.selectFirst("div.gnpv-badge")?.text() ?: ""
        val year = Regex("\\d{4}").find(badge)?.value?.toIntOrNull()

        val durationtext = document.selectFirst("span.gnpv-pill:contains(hr), span.gnpv-pill:contains(min)")?.text()
        val duration = durationtext?.replace(Regex("\\D"), "")?.toIntOrNull()

        val actors = document.select("div.gnpv-mb-item:contains(Reparto) a").map { it.text() }
        val tags = document.select("div.gnpv-genres a").map { it.text() }

        val isanime = tags.any { it.contains("Anime", true) }
        val isseries = badge.contains("Serie", true) || isanime || document.selectFirst("div.eplister") != null

        val tvtype = if (isanime) TvType.Anime else if (isseries) TvType.TvSeries else TvType.Movie

        val scoretext = document.selectFirst("span.gnpv-rating")?.text()?.replace("★", "")?.trim()
        val scoreval = scoretext?.toDoubleOrNull()

        val recommendations = document.select("a.gnpv-related-card").mapNotNull {
            val rectitle = it.selectFirst("span.gnpv-related-title")?.text() ?: return@mapNotNull null
            val rechref = fixUrl(it.attr("href"))
            val recposter = it.selectFirst("img")?.attr("src")

            newMovieSearchResponse(rectitle, rechref, TvType.Movie) {
                this.posterUrl = recposter
            }
        }

        if (isseries) {
            val episodes = document.select("div.eplister ul li").mapNotNull { li ->
                val a = li.selectFirst("a") ?: return@mapNotNull null
                val href = fixUrl(a.attr("href"))
                val epnumtext = a.selectFirst("div.epl-num")?.text()?.trim() ?: ""
                val eptitle = a.selectFirst("div.epl-title")?.text()?.trim()

                val regex = Regex("(\\d+)x(\\d+)")
                val match = regex.find(epnumtext)

                if (match != null) {
                    val s = match.groupValues[1].toIntOrNull()
                    val e = match.groupValues[2].toIntOrNull()

                    newEpisode(href) {
                        this.name = eptitle
                        this.season = s
                        this.episode = e
                        this.posterUrl = posterurl
                    }
                } else {
                    newEpisode(href) {
                        this.name = eptitle ?: epnumtext
                        this.season = 1
                        this.posterUrl = posterurl
                    }
                }
            }.reversed()

            return newTvSeriesLoadResponse(title, url, tvtype, episodes) {
                this.posterUrl = posterurl
                this.plot = description
                this.year = year
                this.duration = duration
                this.score = Score.from(scoreval, 10)
                this.tags = tags
                this.recommendations = recommendations
                this.addActors(actors)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = posterurl
                this.plot = description
                this.year = year
                this.duration = duration
                this.score = Score.from(scoreval, 10)
                this.tags = tags
                this.recommendations = recommendations
                this.addActors(actors)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("Gnulahd", data)

        val res = app.get(data).text
        val regex = Regex("""var\s+(_gnpv_ep_langs|_gd)\s*=\s*(\[.*]);""")
        val match = regex.find(res)

        if (match != null) {
            val json = match.groupValues[2]
            try {
                val langs = AppUtils.parseJson<List<GnulaLang>>(json)

                langs.forEach { langobj ->
                    val label = langobj.label
                    langobj.servers.forEach { srv ->
                        val src = srv.src
                        if (src.isNotBlank()) {
                            val cleanurl = src.replace("\\/", "/")
                            Log.d("Gnulahd", "$label | $cleanurl")
                            loadCustomExtractor(label, cleanurl, data, subtitleCallback, callback)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("Gnulahd", "JSON Error: ${e.message}")
            }
        }
        return true
    }

    private suspend fun loadCustomExtractor(
        label: String,
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        quality: Int? = null,
    ) {
        loadExtractor(url, referer, subtitleCallback) { ex ->
            CoroutineScope(Dispatchers.IO).launch {
                callback.invoke(
                    newExtractorLink(
                        source = "${ex.source} - $label",
                        name = "${ex.name} - $label",
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


data class GnulaLang(
    val label: String,
    val servers: List<GnulaServer>
)

data class GnulaServer(
    val title: String,
    val src: String
)