// ! Bu araç @Kraptor123 tarafından | @cs-karma için yazılmıştır.

package com.kraptor

import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AnimeAV : MainAPI() {
    override var mainUrl = "https://animeav1.com"
    override var name = "AnimeAV"
    override val hasMainPage = true
    override var lang = "mx"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Anime)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    private val categoryUrl = "${mainUrl}/catalogo"

    override val mainPage = mainPageOf(
        mainUrl to "Episodios Recientemente Actualizado",
        "?status=emision&order=latest_released" to "Latest Released",
        "?status=emision&order=latest_added" to "Latest Added",
        "?genre=Acción" to "Acción",
        "?genre=Aventura" to "Aventura",
        "?genre=Comedia" to "Comedia",
        "?genre=Deportes" to "Deportes",
        "?genre=Drama" to "Drama",
        "?genre=Fantasía" to "Fantasía",
        "?genre=Misterio" to "Misterio",
        "?genre=Romance" to "Romance",
        "?genre=Seinen" to "Seinen",
        "?genre=Shoujo" to "Shoujo",
        "?genre=Shounen" to "Shounen",
        "?genre=Sobrenatural" to "Sobrenatural",
        "?genre=Suspenso" to "Suspenso",
        "?genre=Terror" to "Terror",
        "?genre=Carreras" to "Carreras",
        "?genre=Detectives" to "Detectives",
        "?genre=Ecchi" to "Ecchi",
        "?genre=Escolares" to "Escolares",
        "?genre=Espacial" to "Espacial",
        "?genre=Gore" to "Gore",
        "?genre=Gourmet" to "Gourmet",
        "?genre=Harem" to "Harem",
        "?genre=Infantil" to "Infantil",
        "?genre=Isekai" to "Isekai",
        "?genre=Josei" to "Josei",
        "?genre=Mecha" to "Mecha",
        "?genre=Militar" to "Militar",
        "?genre=Parodia" to "Parodia",
        "?genre=Superpoderes" to "Superpoderes",
        "?genre=Vampiros" to "Vampiros",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (request.data.contains("$mainUrl")){
            val document = app.get(request.data).document

            val home = document.select("section:has(h2:contains(episo)) div.grid article").mapNotNull { it.toMainPageResult() }

            return newHomePageResponse(HomePageList(request.name, home, true), false)
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
            app.get("${mainUrl}/catalogo?search=${query}").document
        } else {
            app.get("${mainUrl}/catalogo?search=${query}&page=$page").document
        }

        val aramaCevap =
            document.select("div.grid.grid-cols-2 article.group\\/item").mapNotNull { it.toMainPageResult() }

        return newSearchResponseList(aramaCevap, hasNext = true)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val afterMedia = url.substringAfter("media/", "")
        val isEpisode = afterMedia.contains("/")
        val requestUrl = if (isEpisode) url.substringBeforeLast("/") else url
        val document = app.get(requestUrl, referer = "$mainUrl/").document

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

        val sveltekitScript = document.selectFirst("script:containsData(sveltekit)")?.data() ?: ""

        val totalEp = Regex(pattern = "episodesCount:([0-9]+)", options = setOf(RegexOption.IGNORE_CASE)).find(sveltekitScript)?.groupValues[1]?.toIntOrNull()
            ?: 1
        val mediaId = Regex(pattern = "\\{media:\\{id:([0-9]+)", options = setOf(RegexOption.IGNORE_CASE)).find(sveltekitScript)?.groupValues[1]

        val episodes = (1..totalEp).map { episodeNum ->
            val href = "$url/$episodeNum"
            val posterUrl = "https://cdn.animeav1.com/screenshots/$mediaId/$episodeNum.jpg"

            newEpisode(href) {
                this.name = "Episode $episodeNum"
                this.posterUrl = posterUrl
                this.episode = episodeNum
                this.season = 1
            }
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
//                    Log.d("kraptor_${this.name}", "Type: $type, Server: $server, URL: $url")

                    loadCustomExtractor("${this.name} - $server - $type", url, "$mainUrl/", subtitleCallback, callback)
                }
            }
        }

        return true
    }
}

suspend fun loadCustomExtractor(
    name: String? = null,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    name ?: link.source,
                    name ?: link.name,
                    link.url,
                ) {
                    this.quality = when {
                        else -> quality ?: link.quality
                    }
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
    }
}