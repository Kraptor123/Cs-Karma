package com.byayzen

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

        if (title.contains("Mejores", ignoreCase = true) || title.contains("Cronología", ignoreCase = true)) {
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

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url, timeout = 60)
        val document = response.document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null

        val poster = document.selectFirst("div.thumbook div.thumb img")?.attr("src")
            ?: document.selectFirst("img.ts-post-image")?.attr("src")
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")

        val posterUrl = fixUrlNull(poster?.substringBefore("?"))

        val description = document.selectFirst("div.mindesc")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")

        val infoBox = document.select("div.infox div.spe span")

        val yearText = infoBox.find { it.text().contains("Estreno", true) }?.text()
        val year = Regex("\\d{4}").find(yearText ?: "")?.value?.toIntOrNull()

        val durationText = infoBox.find { it.text().contains("Duracion", true) }?.text()
        val duration = durationText?.replace(Regex("\\D"), "")?.toIntOrNull()

        val actors = infoBox.find { it.text().contains("Casts", true) }
            ?.select("a")?.map { ActorData(Actor(it.text())) } ?: emptyList()

        val tags = document.select("div.genxed a").map { it.text() }

        val typeText = infoBox.find { it.text().contains("Tipo", true) }?.text() ?: ""
        val isAnime = typeText.contains("Anime", true)
        val isSeries = typeText.contains("Serie", true) || isAnime

        val tvType = if (isAnime) TvType.Anime else if (isSeries) TvType.TvSeries else TvType.Movie

        val recommendations = document.select("div.postbody div.listupd article.bs").mapNotNull {
            it.toSearchResult()
        }

        if (isSeries) {
            val episodes = document.select("div.eplister ul li a").mapNotNull {
                val href = fixUrl(it.attr("href"))
                val epTitle = it.selectFirst("div.epl-title")?.text()?.trim()
                val epNumText = it.selectFirst("div.epl-num")?.text()?.trim() ?: ""

                val seasonEpRegex = Regex("(\\d+)x(\\d+)")
                val match = seasonEpRegex.find(epNumText)

                if (match != null) {
                    val season = match.groupValues[1].toIntOrNull()
                    val episode = match.groupValues[2].toIntOrNull()

                    newEpisode(href) {
                        this.name = epTitle
                        this.season = season
                        this.episode = episode
                        this.posterUrl = posterUrl
                    }
                } else {
                    newEpisode(href) {
                        this.name = epTitle ?: epNumText
                        this.season = 1
                        this.posterUrl = posterUrl
                    }
                }
            }.reversed()

            return newTvSeriesLoadResponse(title, url, tvType, episodes) {
                this.posterUrl = posterUrl
                this.plot = description
                this.year = year
                this.tags = tags
                this.duration = duration
                this.recommendations = recommendations
                this.addActors(actors.map { it.actor.name })
            }
        } else {
            val watchUrlElement = document.selectFirst("div.eplister ul li a")
            val realMovieUrl = if (watchUrlElement != null) {
                fixUrl(watchUrlElement.attr("href"))
            } else {
                url
            }

            return newMovieLoadResponse(title, url, TvType.Movie, realMovieUrl) {
                this.posterUrl = posterUrl
                this.plot = description
                this.year = year
                this.tags = tags
                this.duration = duration
                this.recommendations = recommendations
                this.addActors(actors.map { it.actor.name })
            }
        }
    }





    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val visited = ConcurrentHashMap.newKeySet<String>()

        supervisorScope {
            LinkHalledici(data, data, subtitleCallback, callback, visited)
        }
        return true
    }



    private suspend fun CoroutineScope.LinkHalledici(
        url: String,
        referer: String,
        subCb: (SubtitleFile) -> Unit,
        cb: (ExtractorLink) -> Unit,
        visited: MutableSet<String>
    ) {
        if (!visited.add(url)) return

        try {
            val text = app.get(url, referer = referer).text

            directUrlRegex.find(text)?.groupValues?.get(1)?.let { link ->
                loadExtractor(link, referer, subCb, cb)
            }

            iframeRegex.findAll(text).forEach { match ->
                val src = match.groupValues[1]
                if (src.contains("embed.php") || src.contains("player.php")) {
                    launch { LinkHalledici(fixUrl(src), url, subCb, cb, visited) }
                } else {
                    loadExtractor(src, referer, subCb, cb)
                }
            }

            jsonListRegex.findAll(text).forEach { match ->
                launch {
                    try {
                        AppUtils.parseJson<List<List<String>>>(match.groupValues[1]).forEach { entry ->
                            entry.getOrNull(1)?.let { link ->
                                if (link != url) {
                                    launch { LinkHalledici(link, url, subCb, cb, visited) }
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }
            }

        } catch (e: Exception) {
        }
    }
}

private val directUrlRegex = Regex("""var\s+(?:url|mainVideoUrl)\s*=\s*['"]([^'"]+)['"]""")
private val iframeRegex = Regex("""<iframe[^>]+src="([^"]+)"""")
private val jsonListRegex = Regex("""var\s+videos(?:Latino|Castellano|Subtitulado|Original)\s*=\s*(\[.*?\]);""")
