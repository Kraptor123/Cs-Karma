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
        "?order=latest_released" to "Últimos Estrenos",
        "?order=latest_added" to "Recién añadidos",
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
        if (request.data.contains("$mainUrl")) {
            val document = app.get(request.data).document

            val home = document.select("section:has(h2:contains(episo)) div.grid article")
                .mapNotNull { it.toMainPageResult() }

            return newHomePageResponse(HomePageList(request.name, home, true), false)
        } else {
            val document = if (page == 1) {
                app.get("$categoryUrl${request.data.lowercase()}").document
            } else {
                app.get("$categoryUrl${request.data.lowercase()}&page=$page").document
            }
            val home = document.select("div.grid.grid-cols-2 article.group\\/item")
                .mapNotNull { it.toMainPageResult() }

            return newHomePageResponse(request.name, home)
        }
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("h3")?.text() ?: this.selectFirst("span.sr-only")?.text()
        ?: return null
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
            document.select("div.grid.grid-cols-2 article.group\\/item")
                .mapNotNull { it.toMainPageResult() }

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
        val year =
            document.selectFirst("div.text-sm span:contains(0)")?.text()?.trim()?.toIntOrNull()
        val tags = document.select("div.flex-wrap.gap-2 a[href*=genre]").map { it.text() }
        val rating =
            document.selectFirst("div.flex-wrap div.text-lead")?.text()?.trim()?.toIntOrNull()
        val duration =
            document.selectFirst("span.runtime")?.text()?.split(" ")?.first()?.trim()?.toIntOrNull()
        val recommendations =
            document.select("article.bg-mute").mapNotNull { it.toRecommendationResult() }
        val actors = document.select("span.valor a").map { Actor(it.text()) }
        val trailer = Regex("""embed\/(.*)\?rel""").find(document.html())?.groupValues?.get(1)
            ?.let { "https://www.youtube.com/embed/$it" }

        val sveltekitScript =
            document.selectFirst("script:containsData(sveltekit)")?.data() ?: return null

        val slug = requestUrl.substringAfterLast("/")
        val mediaId =
            Regex("""media:\{id:(\d+)""").find(sveltekitScript)?.groupValues?.get(1) ?: return null

        val episodesIndex = sveltekitScript.indexOf("episodes:[")
        val episodes = if (episodesIndex != -1) {
            Regex("""\{id:\d+,number:(\d+)\}""").findAll(sveltekitScript.substring(episodesIndex))
                .mapNotNull {
                    it.groupValues[1].toIntOrNull()
                }.map { epNum ->
                newEpisode(fixUrl("/media/$slug/$epNum")) {
                    this.name = "Episode $epNum"
                    this.episode = epNum
                    this.season = 1
                    this.posterUrl = "https://cdn.animeav1.com/screenshots/$mediaId/$epNum.jpg"
                }
            }.toList()
        } else {
            emptyList()
        }

        return newAnimeLoadResponse(title, requestUrl, TvType.Anime, true) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            this.score = Score.from10(rating)
            this.episodes = mutableMapOf(DubStatus.Subbed to episodes.distinctBy { it.episode }.sortedBy { it.episode })
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

        val script = document.select("script").mapNotNull { it.data() }
            .firstOrNull { it.contains("embeds:") }
            ?: return false

        val embedsData = script.substringAfter("embeds:{").substringBefore("},downloads")
        if (embedsData.isBlank()) return false

        val itemPattern = Regex("""server:"([^"]+)",url:"([^"]+)"""")
        val matches = itemPattern.findAll(embedsData).toList()

        var hasLinks = false

        matches.forEach { match ->
            val server = match.groupValues[1]
            var url = match.groupValues[2]
            if (url.startsWith("//")) url = "https:$url"
            if (url.isBlank()) return@forEach

            if (server.equals("HLS", ignoreCase = true)) {
                val m3u8Url = url.replace("/play/", "/m3u8/")
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = "AnimeAV1 - HLS",
                        url = m3u8Url,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.quality = Qualities.Unknown.value
                        this.headers = mutableMapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:152.0) Gecko/20100101 Firefox/152.0"
                        )
                    }
                )
                hasLinks = true
            } else {
                loadCustomExtractor(
                    name = "$server - ${this.name}",
                    url = url,
                    referer = "$mainUrl/",
                    subtitleCallback = subtitleCallback,
                    callback = callback
                )
                hasLinks = true
            }
        }

        return hasLinks
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
            if (link.url.isNotBlank() && (link.url.startsWith("http") || link.url.startsWith("https"))) {
                CoroutineScope(Dispatchers.IO).launch {
                    callback.invoke(
                        newExtractorLink(
                            name ?: link.source,
                            name ?: link.name,
                            link.url,
                        ) {
                            this.quality = quality ?: link.quality
                            this.type = link.type
                            this.referer = link.referer
                            this.headers = link.headers
                            this.extractorData = link.extractorData
                        }
                    )
                }
            }
        }
    }
}