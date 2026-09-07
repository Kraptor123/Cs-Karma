// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Animejara : MainAPI() {
    override var mainUrl        = "https://animejara.com"
    override var name           = "Animejara"
    override val hasMainPage    = true
    override var lang           = "mx"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Anime, TvType.OVA)

    private val tag = "cskarma_${name}"

    data class EpisodeData(
        val numero_episodio: String?,
        val poster_episodio: String?,
        val nombre_episodio: String?
    )

    data class SeasonData(
        val numero_temporada: Int?,
        val episodios: List<EpisodeData>?
    )

    override val mainPage = mainPageOf(
        "$mainUrl/inicio" to "Episodios nuevos",
        "$mainUrl/catalogo" to "Catálogo",
        "$mainUrl/catalogo?tag=Accion" to "Acción",
        "$mainUrl/catalogo?tag=Amor" to "Amor",
        "$mainUrl/catalogo?tag=Artes+marciales" to "Artes marciales",
        "$mainUrl/catalogo?tag=Aventura" to "Aventura",
        "$mainUrl/catalogo?tag=Carreras" to "Carreras",
        "$mainUrl/catalogo?tag=Ciencia+ficcion" to "Ciencia ficción",
        "$mainUrl/catalogo?tag=Comedia" to "Comedia",
        "$mainUrl/catalogo?tag=Comidas" to "Comidas",
        "$mainUrl/catalogo?tag=Crimen" to "Crimen",
        "$mainUrl/catalogo?tag=Demonios" to "Demonios",
        "$mainUrl/catalogo?tag=Deportes" to "Deportes",
        "$mainUrl/catalogo?tag=Drama" to "Drama",
        "$mainUrl/catalogo?tag=Ecchi" to "Ecchi",
        "$mainUrl/catalogo?tag=Escolar" to "Escolar",
        "$mainUrl/catalogo?tag=Espacial" to "Espacial",
        "$mainUrl/catalogo?tag=Espadachin" to "Espadachín",
        "$mainUrl/catalogo?tag=Familia" to "Familia",
        "$mainUrl/catalogo?tag=Fantasia" to "Fantasía",
        "$mainUrl/catalogo?tag=Gore" to "Gore",
        "$mainUrl/catalogo?tag=Harem" to "Harem",
        "$mainUrl/catalogo?tag=Historico" to "Histórico",
        "$mainUrl/catalogo?tag=Isekai" to "Isekai",
        "$mainUrl/catalogo?tag=Josei" to "Josei",
        "$mainUrl/catalogo?tag=Juegos" to "Juegos",
        "$mainUrl/catalogo?tag=Magia" to "Magia",
        "$mainUrl/catalogo?tag=Mecha" to "Mecha",
        "$mainUrl/catalogo?tag=Militar" to "Militar",
        "$mainUrl/catalogo?tag=Misterio" to "Misterio",
        "$mainUrl/catalogo?tag=Musica" to "Música",
        "$mainUrl/catalogo?tag=Parodia" to "Parodia",
        "$mainUrl/catalogo?tag=Psicologico" to "Psicológico",
        "$mainUrl/catalogo?tag=Recuerdos" to "Recuerdos",
        "$mainUrl/catalogo?tag=Robots" to "Robots",
        "$mainUrl/catalogo?tag=Romance" to "Romance",
        "$mainUrl/catalogo?tag=Samurai" to "Samurai",
        "$mainUrl/catalogo?tag=Seinen" to "Seinen",
        "$mainUrl/catalogo?tag=Shoujo" to "Shoujo",
        "$mainUrl/catalogo?tag=Shounen" to "Shounen",
        "$mainUrl/catalogo?tag=Sobrenatural" to "Sobrenatural",
        "$mainUrl/catalogo?tag=Studio+ghibli" to "Studio ghibli",
        "$mainUrl/catalogo?tag=Superpoderes" to "Superpoderes",
        "$mainUrl/catalogo?tag=Suspenso" to "Suspenso",
        "$mainUrl/catalogo?tag=Terror" to "Terror",
        "$mainUrl/catalogo?tag=Vampiros" to "Vampiros",
        "$mainUrl/catalogo?tag=Yaoi" to "Yaoi",
        "$mainUrl/catalogo?tag=Yuri" to "Yuri",
        "$mainUrl/catalogo?tag=Zombies" to "Zombies"
    )

    private fun Element.SpeImageurl(cssSelector: String): String? {
        val img = selectFirst(cssSelector) ?: return null
        return fixUrlNull(img.attr("data-src").ifEmpty { img.attr("src") })
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val isHome = request.data.endsWith("/inicio")
        val url = if (isHome || page <= 1) {
            request.data
        } else {
            "${request.data}${if (request.data.contains("?")) "&" else "?"}paged=$page"
        }

        Log.d(tag, "getMainPage: ${request.name} page=$page url=$url")
        val document = app.get(url).document

        val items = if (isHome) {
            document.select("div.anime-grid a.ep-card").mapNotNull { it.toEpisodeCardResult() }
        } else {
            document.select("div.anime-grid div.anime-card-wrapper").mapNotNull { it.toCatalogCardResult() }
        }

        return newHomePageResponse(request.name, items, !isHome && items.isNotEmpty())
    }

    private fun Element.toEpisodeCardResult(): SearchResponse? {
        val title = selectFirst("div.ep-info div.ep-name")?.text()?.trim() ?: return null
        val href  = fixUrlNull(attr("href").ifEmpty { return null }) ?: return null
        val epTag = selectFirst("div.ep-poster-wrap span.ep-tag")?.text()?.trim().orEmpty()
        val poster = SpeImageurl("div.ep-poster-wrap img")
        val name  = if (epTag.isNotEmpty()) "$title - $epTag" else title

        return newAnimeSearchResponse(name, href, TvType.Anime) {
            this.posterUrl = poster
        }
    }

    private fun Element.toCatalogCardResult(): SearchResponse? {
        val linkElem = selectFirst("a.anime-card") ?: return null
        val href     = fixUrlNull(linkElem.attr("href").ifEmpty { return null }) ?: return null
        val title    = selectFirst("h3.card-title")?.text()?.trim() ?: return null
        val poster   = SpeImageurl("div.card-poster-wrapper img.card-poster")

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page <= 1) {
            "$mainUrl/catalogo/?q=$query"
        } else {
            "$mainUrl/catalogo/?q=$query&paged=$page"
        }

        Log.d(tag, "search: $query page=$page")
        val document = app.get(url).document
        val results  = document.select("div.anime-grid div.anime-card-wrapper").mapNotNull { it.toCatalogCardResult() }

        return newSearchResponseList(results, results.isNotEmpty())
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val cleanUrl = if (url.contains("/episode/")) {
            val slug = url.trimEnd('/').substringAfterLast("/episode/").replace(Regex("""-\d+x\d+$"""), "")
            fixUrl("$mainUrl/anime/$slug")
        } else {
            url
        }

        Log.d(tag, "load: $cleanUrl")
        val document = app.get(cleanUrl).document

        val title = document.selectFirst("h1.anime-title-desktop, h1.anime-title-mobile")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.poster-container img.main-poster-img")?.attr("src")?.ifEmpty { null })
        val plot   = document.selectFirst("div.anime-sinopsis-contenedor div")?.text()?.trim()
        val year   = document.selectFirst("div.anime-bottom-bar div.stat-item:has(i.fa-calendar-alt) span")?.text()?.trim()?.toIntOrNull()
        val tags   = document.select("div.anime-categorias span").map { it.text().trim() }

        val ratingText = document.selectFirst("div.anime-bottom-bar div.rating-number")?.text()?.trim()
        val score      = ratingText?.toDoubleOrNull()?.let { Score.from(it, 5) }

        val statusText = document.selectFirst("div.poster-container div.label-poster")?.text()?.trim()
        val status = when {
            statusText?.contains("EMISION", ignoreCase = true) == true    -> ShowStatus.Ongoing
            statusText?.contains("FINALIZADO", ignoreCase = true) == true -> ShowStatus.Completed
            else -> null
        }

        val scripts = document.select("script").map { it.data() }
        val slug = scripts.firstNotNullOfOrNull { script ->
            Regex("""const\s+ANIME_SLUG\s*=\s*'([^']+)'""").find(script)?.groupValues?.get(1)
        } ?: cleanUrl.trimEnd('/').substringAfterLast('/')

        val jsonRaw = scripts.firstNotNullOfOrNull { script ->
            Regex("""const\s+TEMPORADAS_DATA\s*=\s*(\[\{.*?\}\]);""", RegexOption.DOT_MATCHES_ALL).find(script)?.groupValues?.get(1)
        }

        val episodes = if (jsonRaw != null) {
            val seasons = AppUtils.tryParseJson<List<SeasonData>>(jsonRaw) ?: emptyList()
            seasons.flatMap { season ->
                val sNum = season.numero_temporada ?: 1
                season.episodios?.mapNotNull { ep ->
                    val epNum = ep.numero_episodio?.toIntOrNull() ?: return@mapNotNull null
                    val epUrl = fixUrl("$mainUrl/episode/$slug-${sNum}x$epNum/")
                    val epName = ep.nombre_episodio?.ifEmpty { null } ?: "Temporada $sNum - Episodio $epNum"

                    newEpisode(epUrl) {
                        this.name      = epName
                        this.season    = sNum
                        this.episode   = epNum
                        this.posterUrl = fixUrlNull(ep.poster_episodio)
                    }
                } ?: emptyList()
            }
        } else {
            emptyList()
        }

        Log.d(tag, "load: ${episodes.size} episodes parsed")

        return newAnimeLoadResponse(title, cleanUrl, TvType.Anime) {
            this.posterUrl  = poster
            this.plot       = plot
            this.year       = year
            this.tags       = tags
            this.score      = score
            this.showStatus = status
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d(tag, "loadLinks: $data")
        val document  = app.get(data).document
        val iframeSrc = document.selectFirst("div#reproductor-wrapper iframe#iframe-video")?.attr("src")?.ifEmpty { null } ?: return false
        val fixedUrl  = fixUrl(iframeSrc)

        val playerDoc = app.get(fixedUrl, referer = data).document
        var found     = false

        playerDoc.select("ul#logo-list li[onclick]").forEach { elem ->
            val rawUrl = Regex("""playVideo\(["']\s*(https?://[^\s"']+)""").find(elem.attr("onclick"))?.groupValues?.get(1) ?: return@forEach
            if (loadExtractor(rawUrl, fixedUrl, subtitleCallback, callback)) found = true
        }

        return found
    }
}