// ! Bu araç @Kraptor123 tarafından | @cs-karma için yazılmıştır.

package com.kraptor

import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class AnimeAV : MainAPI() {
    override var mainUrl = "https://animeav1.com"
    override var name = "AnimeAV"
    override val hasMainPage = true
    override var lang = "es"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Anime)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    private val categoryUrl = "${mainUrl}/catalogo?genre="

    override val mainPage = mainPageOf(
        mainUrl to "Episodios Recientemente Actualizado",
        "Acción" to "Acción",
        "Aventura" to "Aventura",
        "Ciencia Ficción" to "Ciencia Ficción",
        "Comedia" to "Comedia",
        "Deportes" to "Deportes",
        "Drama" to "Drama",
        "Fantasía" to "Fantasía",
        "Misterio" to "Misterio",
        "Recuentos de la Vida" to "Recuentos de la Vida",
        "Romance" to "Romance",
        "Seinen" to "Seinen",
        "Shoujo" to "Shoujo",
        "Shounen" to "Shounen",
        "Sobrenatural" to "Sobrenatural",
        "Suspenso" to "Suspenso",
        "Terror" to "Terror",
        "Antropomórfico" to "Antropomórfico",
        "Artes Marciales" to "Artes Marciales",
        "Carreras" to "Carreras",
        "Detectives" to "Detectives",
        "Ecchi" to "Ecchi",
        "Elenco Adulto" to "Elenco Adulto",
        "Escolares" to "Escolares",
        "Espacial" to "Espacial",
        "Gore" to "Gore",
        "Gourmet" to "Gourmet",
        "Harem" to "Harem",
        "Histórico" to "Histórico",
        "Idols (Hombre)" to "Idols (Hombre)",
        "Idols (Mujer)" to "Idols (Mujer)",
        "Infantil" to "Infantil",
        "Isekai" to "Isekai",
        "Josei" to "Josei",
        "Juegos Estrategia" to "Juegos Estrategia",
        "Mahou Shoujo" to "Mahou Shoujo",
        "Mecha" to "Mecha",
        "Militar" to "Militar",
        "Mitología" to "Mitología",
        "Música" to "Música",
        "Parodia" to "Parodia",
        "Psicológico" to "Psicológico",
        "Samurai" to "Samurai",
        "Shoujo Ai" to "Shoujo Ai",
        "Shounen Ai" to "Shounen Ai",
        "Superpoderes" to "Superpoderes",
        "Vampiros" to "Vampiros",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
       if (request.data.contains("$mainUrl")){
           val document = app.get(request.data).document

           val home = document.select("section:has(h2:contains(episo)) div.grid article").mapNotNull { it.toMainPageResult() }

           return newHomePageResponse(request.name, home, false)
       } else {
           val document = if (page == 1) {
               app.get("$categoryUrl${request.data.lowercase()}").document
           } else {
               app.get("$categoryUrl${request.data.lowercase()}&page=$page").document
           }
           val home = document.select("div.grid.grid-cols-2 article.group\\/item").mapNotNull { it.toMainPageResult() }

           return newHomePageResponse(request.name, home)
       }
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("h3")?.text() ?: this.selectFirst("span.sr-only")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val document = if (page == 1) {
            app.get("${mainUrl}/catalogo?search=${query}&page=$page").document
        } else {
            app.get("${mainUrl}/catalogo?search=${query}&page=$page").document
        }

        val aramaCevap =
            document.select("div.grid.grid-cols-2 article.group\\/item").mapNotNull { it.toMainPageResult() }

        return newSearchResponseList(aramaCevap, hasNext = true)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val episodeOrNot = url.substringAfter("media/").substringAfter("/").isNotEmpty()
        val document = if (episodeOrNot){
            app.get(url.substringBeforeLast("/")).document
        } else {
            app.get(url).document
        }

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("img.aspect-poster")?.attr("src"))
        val description = document.selectFirst("div.entry.text-lead p")?.text()?.trim()
        val year = document.selectFirst("div.text-sm span:contains(0)")?.text()?.trim()?.toIntOrNull()
        val tags = document.select("div.flex-wrap.gap-2 a[href*=genre]").map { it.text() }
        val rating = document.selectFirst("div.flex-wrap div.text-lead")?.text()?.trim()?.toIntOrNull()
        val duration = document.selectFirst("span.runtime")?.text()?.split(" ")?.first()?.trim()?.toIntOrNull()
        val recommendations = document.select("article.bg-mute").mapNotNull { it.toRecommendationResult() }
        val actors = document.select("span.valor a").map { Actor(it.text()) }
        val trailer = Regex("""embed\/(.*)\?rel""").find(document.html())?.groupValues?.get(1)
            ?.let { "https://www.youtube.com/embed/$it" }
        val episodes = document.select("div.grid.grid-cols-2 article.group\\/item").map { episode ->
            val href = fixUrlNull(episode.selectFirst("a")?.attr("href")) ?: return null
            val name = episode.selectFirst("div.bg-line")?.text()
            val poster = episode.selectFirst("img")?.attr("src")
            val episodeN = episode.selectFirst("div.bg-line span")?.text()?.toIntOrNull()
            val season =
                title.substringAfter("season").substringAfter("part").replace(" ", "").trim().toIntOrNull() ?: 1
            newEpisode(href, {
                this.name = name
                this.posterUrl = poster
                this.episode = episodeN
                this.season = season
            })
        }

        return newAnimeLoadResponse(title, url, TvType.Anime, true) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            this.score = Score.from10(rating)
            this.episodes = mutableMapOf(DubStatus.None to episodes)
            this.duration = duration
            this.recommendations = recommendations
            addActors(actors)
            addTrailer(trailer)
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title = this.selectFirst("h3")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) { this.posterUrl = posterUrl }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val script = document.selectFirst("script:containsData(__sveltekit)")?.data() ?: return false

        val embedsData = script.substringAfter("embeds:{", "").substringBefore("},downloads", "")

        if (embedsData.isEmpty()) return false

        listOf("DUB", "SUB").forEach { type ->

            val listPattern = Regex("""$type:\[(.*?)\]""")
            val listMatch = listPattern.find(embedsData)?.groupValues?.get(1)

            if (listMatch != null) {
                val itemPattern = Regex("""\{server:"([^"]+)",\s*url:"([^"]+)"""")

                itemPattern.findAll(listMatch).forEach { match ->
                    val server = match.groupValues[1]
                    val url = match.groupValues[2]

                    Log.d("kraptor_${this.name}", "Type: $type, Server: $server, URL: $url")

                    if (server.equals("PDrain", ignoreCase = true)) {
                        PixelDrain().getUrl("$url|$type", subtitleCallback = subtitleCallback, callback = callback)
                    } else if (server.equals("MP4Upload", ignoreCase = true)) {
                        Mp4Upload().getUrl("$url|$type", subtitleCallback = subtitleCallback, callback = callback)
                    } else if (server.equals("HLS", ignoreCase = true)) {
                        PlayerZilla().getUrl("$url|$type", subtitleCallback = subtitleCallback, callback = callback)
                    } else {
                        loadExtractor(url, subtitleCallback, callback)
                    }
                }
            }
        }

        return true
    }
}