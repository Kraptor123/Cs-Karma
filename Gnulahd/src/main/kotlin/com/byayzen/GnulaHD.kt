package com.byayzen

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
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

    data class PlayerResponse(val p: String? = null)
    data class GnulaLang(val label: String, val servers: List<GnulaServer>)
    data class GnulaServer(val title: String, val src: String)

    override val mainPage = mainPageOf(
        "$mainUrl/ver/?type=Pelicula&order=latest" to "Últimas Películas",
        "$mainUrl/ver/?type=Serie&order=latest" to "Últimas Series",
        "$mainUrl/ver/anime/" to "Últimos Animes",
        "$mainUrl/ver/?type=Pelicula&order=popular" to "Películas Populares",
        "$mainUrl/ver/?type=Serie&order=popular" to "Series Populares"
    )

    private fun Element.TypeSearchResponse(type: TvType): SearchResponse? {
        val title  = this.attr("title").ifEmpty { return null }
        val href   = fixUrl(this.attr("href").ifEmpty { return null })
        val poster = fixUrlNull(this.selectFirst("img")?.attr("src")?.substringBefore("?"))

        return when (type) {
            TvType.TvSeries -> newTvSeriesSearchResponse(title, href, type) { this.posterUrl = poster }
            TvType.Anime    -> newAnimeSearchResponse(title, href, type) { this.posterUrl = poster }
            else            -> newMovieSearchResponse(title, href, type) { this.posterUrl = poster }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) {
            request.data
        } else if (request.data.contains("?")) {
            "${request.data}&page=$page"
        } else {
            "${request.data.removeSuffix("/")}?page=$page"
        }

        Log.d(tag, "getMainPage: ${request.name} page=$page")
        val type = when {
            request.data.contains("/anime/") -> TvType.Anime
            request.data.contains("type=Serie") -> TvType.TvSeries
            else -> TvType.Movie
        }

        val home = app.get(url).document.select("div.gnrd-grid a.gnrd-card")
            .mapNotNull { it.TypeSearchResponse(type) }

        return newHomePageResponse(request.name, home, hasNext = home.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/wp-json/gnrd/v1/search?q=$query"
        Log.d(tag, "search: $query")
        val results = JSONObject(
            app.get(
                url,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Accept" to "*/*",
                    "Referer" to "$mainUrl/ver/anime/"
                )
            ).text
        ).getJSONArray("results")

        return (0 until results.length()).mapNotNull { i ->
            val item    = results.getJSONObject(i)
            val title   = item.getString("title").ifEmpty { return@mapNotNull null }
            val href    = fixUrl(item.getString("url").ifEmpty { return@mapNotNull null })
            val poster  = fixUrlNull(item.optString("img").ifEmpty { null }?.substringBefore("?"))
            val typeRaw = item.optString("type", "Pelicula")
            val type = when {
                typeRaw.contains("Serie", true) -> TvType.TvSeries
                typeRaw.contains("Anime", true) -> TvType.Anime
                else -> TvType.Movie
            }

            when (type) {
                TvType.TvSeries -> newTvSeriesSearchResponse(title, href, type) { this.posterUrl = poster }
                TvType.Anime    -> newAnimeSearchResponse(title, href, type) { this.posterUrl = poster }
                else            -> newMovieSearchResponse(title, href, type) { this.posterUrl = poster }
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        Log.d(tag, "load: $url")
        val document = app.get(url, timeout = 60).document

        val titleElement = document.selectFirst("h1.gnrd-fi-title")
        val title = titleElement?.text()?.trim()?.ifEmpty { null } ?: return null
        val logoUrl = fixUrlNull(document.selectFirst("img.gnrd-fi-logo")?.attr("src")?.ifEmpty { null })
        val posterUrl = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content")?.ifEmpty { null })
        val description = document.selectFirst("p.gnrd-fi-syn")?.text()?.trim()?.ifEmpty { null }
        val eyebrow = document.selectFirst("span.gnrd-eyebrow")?.text()?.trim()

        val year = Regex("""\d{4}""").find(eyebrow.orEmpty())?.value?.toIntOrNull()
            ?: document.selectFirst("div.gnrd-qf:has(span.gnrd-qf-l:matchesOwn((?i)a[ñn]o)) span.gnrd-qf-v")
                ?.text()?.let { Regex("""\d{4}""").find(it)?.value?.toIntOrNull() }

        val duration = document.selectFirst("div.gnrd-qf:has(span.gnrd-qf-l:matchesOwn((?i)duraci[oó]n)) span.gnrd-qf-v")
            ?.text()?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }

        val tags = document.select("div.gnrd-fi-genres a").map { it.text().trim() }
        val isAnime  = eyebrow?.contains("Anime", ignoreCase = true) == true
        val isSeries = isAnime || document.selectFirst("div.gnrd-eplist, a.gnrd-epc, div.eplister") != null
        val tvType   = if (isAnime) TvType.Anime else if (isSeries) TvType.TvSeries else TvType.Movie

        val score = document.selectFirst("span.gnrd-m-rating meta[itemprop=ratingValue]")?.attr("content")?.toDoubleOrNull()
        val actors = document.select("a.gnrd-castc .gnrd-castc-name").map { it.text().trim() }
        val trailerId = document.selectFirst("div.gnrd-trailer")?.attr("data-yt")?.ifEmpty { null }
        val trailerUrl = trailerId?.let { "https://www.youtube.com/watch?v=$it" }
        val recommendations = document.select("a.gnrd-card").distinctBy { it.attr("href") }
            .mapNotNull { it.TypeSearchResponse(tvType) }

        if (isSeries) {
            val episodeElements = document.select("a.gnrd-epc, div.eplister ul li")
            val episodes = episodeElements.mapNotNull { element ->
                val a = if (element.tagName() == "a") element else element.selectFirst("a") ?: return@mapNotNull null
                val epHref = fixUrl(a.attr("href").ifEmpty { return@mapNotNull null })
                val epId   = a.attr("data-id")
                val epTok  = a.attr("data-t")
                val linkData = if (epId.isNotEmpty() && epTok.isNotEmpty()) "$epHref?id=$epId&t=$epTok" else epHref

                val epNum     = a.selectFirst("span.gnrd-epc-n, div.epl-num")?.text()?.trim().orEmpty()
                val epName    = a.selectFirst("span.gnrd-epc-title, div.epl-title")?.text()?.trim()
                val styleAttr = a.selectFirst("div.gnrd-epc-thumb")?.attr("style").orEmpty()
                val epThumb   = Regex("""url\(['"]?(.*?)['"]?\)""").find(styleAttr)?.groupValues?.get(1)?.let { fixUrlNull(it) }
                val match     = Regex("""(\d+)x(\d+)""").find(epNum)
                val season     = a.attr("data-s").toIntOrNull() ?: match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val episodeNum = a.attr("data-e").toIntOrNull() ?: match?.groupValues?.get(2)?.toIntOrNull()
                val finalName  = if (match != null) epName else (epName ?: epNum.ifEmpty { null })

                newEpisode(linkData) {
                    this.name      = finalName
                    this.season    = season
                    this.episode   = episodeNum
                    this.posterUrl = epThumb ?: posterUrl
                }
            }.reversed()

            return if (tvType == TvType.Anime) {
                newAnimeLoadResponse(title, url, tvType) {
                    this.posterUrl       = posterUrl
                    this.plot            = description
                    this.year            = year
                    this.duration        = duration
                    this.score           = Score.from(score, 10)
                    this.logoUrl         = logoUrl
                    if (tags.isNotEmpty()) this.tags = tags
                    if (recommendations.isNotEmpty()) this.recommendations = recommendations
                    if (actors.isNotEmpty()) addActors(actors)
                    if (trailerUrl != null) addTrailer(trailerUrl)
                    addEpisodes(DubStatus.Subbed, episodes)
                }
            } else {
                newTvSeriesLoadResponse(title, url, tvType, episodes) {
                    this.posterUrl       = posterUrl
                    this.plot            = description
                    this.year            = year
                    this.duration        = duration
                    this.score           = Score.from(score, 10)
                    this.logoUrl         = logoUrl
                    if (tags.isNotEmpty()) this.tags = tags
                    if (recommendations.isNotEmpty()) this.recommendations = recommendations
                    if (actors.isNotEmpty()) addActors(actors)
                    if (trailerUrl != null) addTrailer(trailerUrl)
                }
            }
        }

        val movieId = document.selectFirst("div.gnrd-player, #player")?.attr("data-id")?.ifEmpty { null }
        return newMovieLoadResponse(title, url, tvType, movieId ?: url) {
            this.posterUrl       = posterUrl
            this.plot            = description
            this.year            = year
            this.duration        = duration
            this.score           = Score.from(score, 10)
            this.logoUrl         = logoUrl
            if (tags.isNotEmpty()) this.tags = tags
            if (recommendations.isNotEmpty()) this.recommendations = recommendations
            if (actors.isNotEmpty()) addActors(actors)
            if (trailerUrl != null) addTrailer(trailerUrl)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d(tag, data)
        var isLinkFound = false

        val queryId  = Regex("""[?&]id=(\d+)""").find(data)?.groupValues?.get(1)
        val queryTok = Regex("""[?&]t=([a-f0-9]+)""").find(data)?.groupValues?.get(1)

        val (id, tok) = if (!queryId.isNullOrEmpty() && !queryTok.isNullOrEmpty()) {
            queryId to queryTok
        } else if (data.contains("|")) {
            val parts = data.split("|")
            parts.getOrNull(0) to parts.getOrNull(1)
        } else if (data.startsWith("http")) {
            val doc = app.get(data).document
            val epElement = doc.selectFirst("a.gnrd-epc, div.gnrd-player, #player, button[data-id]")
            epElement?.attr("data-id")?.ifEmpty { null } to epElement?.attr("data-t")?.ifEmpty { null }
        } else {
            null to null
        }

        if (id.isNullOrEmpty() || tok.isNullOrEmpty()) return false

        val apiUrl = "$mainUrl/wp-json/gnrd/v1/player?id=$id&t=$tok"
        val response = app.get(
            apiUrl,
            headers = mapOf("Referer" to "$mainUrl/", "X-Requested-With" to "XMLHttpRequest")
        ).parsedSafe<PlayerResponse>()

        val pField = response?.p ?: return false
        val langs  = decryptPlayerData(pField) ?: return false

        for (lang in langs) {
            for (srv in lang.servers) {
                var videoUrl = srv.src.replace("\\/", "/")
                if (videoUrl.startsWith("//")) videoUrl = "https:$videoUrl"

                if (videoUrl.isNotBlank() && !videoUrl.contains("aviso.mp4")) {
                    loadCustomExtractor(
                        label            = lang.label,
                        url              = videoUrl,
                        referer          = "$mainUrl/",
                        subtitleCallback = subtitleCallback
                    ) { link ->
                        isLinkFound = true
                        callback.invoke(link)
                    }
                }
            }
        }

        return isLinkFound
    }

    private suspend fun loadCustomExtractor(
        label: String,
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
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
                            this.quality       = ex.quality
                            this.referer       = ex.referer
                            this.headers       = ex.headers
                            this.extractorData = ex.extractorData
                        }
                    )
                }
            }
        }
    }

    private fun decryptPlayerData(encoded: String): List<GnulaLang>? {
        return try {
            val raw = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
            val key = byteArrayOf(103, 78, 55, 100)
            val dec = ByteArray(raw.size) { i -> (raw[i].toInt() xor key[i % 4].toInt()).toByte() }
            val jsonStr = String(dec, Charsets.UTF_8)
            val obj = JSONObject(jsonStr)
            val langsArray = obj.optJSONArray("langs")?.toString() ?: return null
            parseJson<List<GnulaLang>>(langsArray)
        } catch (e: Exception) {
            null
        }
    }
}