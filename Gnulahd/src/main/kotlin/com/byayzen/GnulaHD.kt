package com.byayzen

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import kotlinx.coroutines.*
import org.json.JSONObject
import org.jsoup.nodes.Element

class GnulaHD : MainAPI() {
    override var mainUrl        = "https://ww3.gnulahd.nu"
    override var name           = "GnulaHD"
    override var lang           = "mx"
    override val hasMainPage    = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    private val tag = "gnula_${name}"


    override val mainPage = mainPageOf(
        "$mainUrl/ver/?type=Pelicula&order=latest" to "Últimas Películas",
        "$mainUrl/ver/?type=Serie&order=latest" to "Últimas Series",
        "$mainUrl/ver/anime/" to "Últimos Animes",
        "$mainUrl/ver/?type=Pelicula&order=popular" to "Películas Populares",
        "$mainUrl/ver/?type=Serie&order=popular" to "Series Populares"
    )

    private fun requestHeaders(referer: String) = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept"     to "*/*",
        "Referer"    to referer
    )

    private fun newSearchResponseByType(title: String, href: String, posterRaw: String?, type: TvType): SearchResponse {
        val poster = fixUrlNull(posterRaw)
        return when (type) {
            TvType.TvSeries -> newTvSeriesSearchResponse(title, href, type) { this.posterUrl = poster }
            TvType.Anime    -> newAnimeSearchResponse(title, href, type) { this.posterUrl = poster }
            else            -> newMovieSearchResponse(title, href, type) { this.posterUrl = poster }
        }
    }

    private fun Element.toCardSearchResponse(type: TvType): SearchResponse? {
        val title  = this.attr("title").ifEmpty { return null }
        val href   = this.attr("href").ifEmpty { return null }
        val poster = this.selectFirst("img")?.attr("src")?.substringBefore("?")

        return newSearchResponseByType(title, fixUrl(href), poster, type)
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) {
            request.data
        } else if (request.data.contains("?")) {
            "${request.data}&page=$page"
        } else {
            "${request.data.removeSuffix("/")}?page=$page"
        }

        val home = app.get(url).document.select("div.gnrd-grid a.gnrd-card")
            .mapNotNull { it.toCardSearchResponse(TvType.Movie) }

        return newHomePageResponse(request.name, home, hasNext = true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url     = "$mainUrl/wp-json/gnrd/v1/search?q=$query"
        val results = JSONObject(app.get(url, headers = requestHeaders("$mainUrl/ver/anime/")).text).getJSONArray("results")

        return (0 until results.length()).mapNotNull { i ->
            val item    = results.getJSONObject(i)
            val title   = item.getString("title").ifEmpty { return@mapNotNull null }
            val href    = item.getString("url").ifEmpty { return@mapNotNull null }
            val poster  = item.optString("img").ifEmpty { null }?.substringBefore("?")
            val typeRaw = item.optString("type", "Pelicula")
            val type = when {
                typeRaw.contains("Serie", true) -> TvType.TvSeries
                typeRaw.contains("Anime", true) -> TvType.Anime
                else -> TvType.Movie
            }

            newSearchResponseByType(title, fixUrl(href), poster, type)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        Log.d(tag, "Load: $url")
        val document = app.get(url, timeout = 60).document

        val titleElement = document.selectFirst("h1.gnrd-fi-title")
        val title = titleElement?.selectFirst("span.gnrd-sr")?.text()?.trim()?.ifEmpty { titleElement.text()?.trim() }
            ?: return null
        val logoUrl = titleElement.selectFirst("img.gnrd-fi-logo")?.attr("src")

        val posterUrl   = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content")?.substringBefore("?"))
        val description = document.selectFirst("p.gnrd-fi-syn")?.text()?.trim()

        val eyebrow = document.selectFirst("span.gnrd-eyebrow")?.text()
        val year = (eyebrow ?: document.selectFirst("div.qf:has(span.qf-l:contains(Año)) span.qf-v")?.text())
            ?.let { Regex("\\d{4}").find(it)?.value }?.toIntOrNull()

        val duration = document.selectFirst("div.qf:has(span.qf-l:contains(Duración)) span.qf-v")?.text()
            ?.replace(Regex("\\D"), "")?.toIntOrNull()

        val tags = document.select("div.gnrd-fi-genres a, div.gnrd-info-row:has(span.gnrd-info-k:contains(Géneros)) a").map { it.text() }

        val isAnime  = eyebrow?.contains("Anime", true) == true
        val isSeries = isAnime || document.selectFirst("div.eplister, div.gnrd-eplist") != null
        val tvtype   = if (isAnime) TvType.Anime else if (isSeries) TvType.TvSeries else TvType.Movie

        val score      = document.selectFirst("span.gnrd-m-rating meta[itemprop=ratingValue]")?.attr("content")?.toDoubleOrNull()
        val actors     = document.select("a.gnrd-castc .gnrd-castc-name").map { it.text() }
        val trailerUrl = document.selectFirst("div.gnrd-trailer")?.attr("data-yt")?.ifEmpty { null }
            ?.let { "https://www.youtube.com/watch?v=$it" }

        val recommendations = document.select("a.gnrd-card").mapNotNull { it.toCardSearchResponse(tvtype) }

        if (isSeries) {
            val episodeElements = document.select("div.eplister ul li, a.gnrd-epc")
            val episodes = episodeElements.mapNotNull { li ->
                val a = if (li.tagName() == "a") li else li.selectFirst("a") ?: return@mapNotNull null

                val epId    = a.attr("data-id")
                val href    = if (epId.isNotEmpty()) epId else fixUrl(a.attr("href"))
                val epNum   = a.selectFirst("div.epl-num, span.gnrd-epc-n")?.text()?.trim() ?: ""
                val epName  = a.selectFirst("div.epl-title, span.gnrd-epc-title")?.text()?.trim()
                val epThumb = a.selectFirst("div.gnrd-epc-thumb")?.attr("style")?.let { style ->
                    Regex("url\\(['\"]?(.*?)['\"]?\\)").find(style)?.groupValues?.get(1)
                }

                val match      = Regex("(\\d+)x(\\d+)").find(epNum)
                val season     = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val episodeNum = match?.groupValues?.get(2)?.toIntOrNull()
                val finalName  = if (match != null) epName else (epName ?: epNum)

                newEpisode(href) {
                    this.name      = finalName
                    this.season    = season
                    this.episode   = episodeNum
                    this.posterUrl = epThumb ?: posterUrl
                }
            }.reversed()

            return if (tvtype == TvType.Anime) {
                newAnimeLoadResponse(title, url, tvtype) {
                    this.posterUrl       = posterUrl
                    this.plot            = description
                    this.year            = year
                    this.duration        = duration
                    this.score           = Score.from(score, 10)
                    this.tags            = tags
                    this.recommendations = recommendations
                    this.logoUrl         = logoUrl
                    addActors(actors)
                    addEpisodes(DubStatus.Subbed, episodes)
                    addTrailer(trailerUrl)
                }
            } else {
                newTvSeriesLoadResponse(title, url, tvtype, episodes) {
                    this.posterUrl       = posterUrl
                    this.plot            = description
                    this.year            = year
                    this.duration        = duration
                    this.score           = Score.from(score, 10)
                    this.tags            = tags
                    this.recommendations = recommendations
                    this.logoUrl         = logoUrl
                    addActors(actors)
                    addTrailer(trailerUrl)
                }
            }
        }

        val movieId = document.selectFirst("div.gnrd-player, #player")?.attr("data-id")
        return newMovieLoadResponse(title, url, tvtype, movieId ?: url) {
            this.posterUrl       = posterUrl
            this.plot            = description
            this.year            = year
            this.duration        = duration
            this.score           = Score.from(score, 10)
            this.tags            = tags
            this.recommendations = recommendations
            this.logoUrl         = logoUrl
            addActors(actors)
            addTrailer(trailerUrl)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d(tag, data)
        val headers = requestHeaders("$mainUrl/")

        val id   = data.removeSuffix("/").substringAfterLast("/")
        val isId = id.isNotEmpty() && id.all { it.isDigit() }

        val jsonString = if (isId) {
            val idUrl = "$mainUrl/wp-json/gnrd/v1/ep-player?id=$id"
            try {
                JSONObject(app.get(idUrl, headers = headers).text).optJSONArray("langs")?.toString()
            } catch (e: Exception) {
                null
            }
        } else if (data.startsWith("http")) {
            val res = app.get(data, headers = headers).text
            Regex("""var\s+(_gnpv_ep_langs|_gd)\s*=\s*(\[.*]);""").find(res)?.groupValues?.get(2)
        } else null

        val langs = jsonString?.let {
            try {
                AppUtils.parseJson<List<GnulaLang>>(it)
            } catch (e: Exception) {
                null
            }
        }

        langs?.forEach { langObj ->
            val label = langObj.label
            langObj.servers.forEach { srv ->
                val src = srv.src
                if (src.isNotBlank() && !src.contains("aviso.mp4")) {
                    var cleanUrl = src.replace("\\/", "/")
                    if (cleanUrl.startsWith("//")) cleanUrl = "https:$cleanUrl"

                    Log.d(tag, cleanUrl)
                    loadCustomExtractor(label, cleanUrl, data, subtitleCallback, callback)
                }
            }
        }

        return langs != null
    }

    private suspend fun loadCustomExtractor(
        label: String,
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        quality: Int? = null
    ) {
        Log.d(tag, url)
        loadExtractor(url, referer, subtitleCallback) { ex ->
            if (ex.url.isNotBlank() && (ex.url.startsWith("http") || ex.url.startsWith("https"))) {
                Log.d(tag, ex.url)
                CoroutineScope(Dispatchers.IO).launch {
                    callback.invoke(
                        newExtractorLink(
                            source = "${ex.source} - $label",
                            name   = "${ex.name} - $label",
                            url    = ex.url,
                            type   = ex.type
                        ) {
                            this.quality       = quality ?: ex.quality
                            this.referer       = ex.referer
                            this.headers       = ex.headers
                            this.extractorData = ex.extractorData
                        }
                    )
                }
            }
        }
    }
}


data class GnulaLang(
    @param:JsonProperty("label") val label: String,
    @param:JsonProperty("servers") val servers: List<GnulaServer>
)

data class GnulaServer(
    @param:JsonProperty("title") val title: String,
    @param:JsonProperty("src") val src: String
)