// ! Bu araç @Kraptor123 tarafından | @cs-karma için yazılmıştır.

package com.kraptor

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AnimeAV : MainAPI() {
    override var mainUrl        = "https://animeav1.com"
    private val cdnUrl          = "https://cdn.animeav1.com"
    override var name           = "AnimeAV"
    override val hasMainPage    = true
    override var lang           = "mx"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Anime)

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
        if (request.data.contains(mainUrl)) {
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
        val title     = this.selectFirst("h3")?.text() ?: this.selectFirst("span.sr-only")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val document = if (page == 1) {
            app.get("${mainUrl}/catalogo?search=${query}").document
        } else {
            app.get("${mainUrl}/catalogo?search=${query}&page=$page").document
        }

        val aramaCevap = document.select("div.grid.grid-cols-2 article.group\\/item")
            .mapNotNull { it.toMainPageResult() }

        return newSearchResponseList(aramaCevap, hasNext = true)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val afterMedia      = url.substringAfter("media/", "")
        val isEpisode       = afterMedia.contains("/")
        val requestUrl      = if (isEpisode) url.substringBeforeLast("/") else url
        val document        = app.get(requestUrl, referer = "$mainUrl/").document

        val sveltekitScript = document.selectFirst("script:containsData(sveltekit)")?.data()

        val mediaId         = sveltekitScript?.let { Regex("""media:\{id:(\d+)""").find(it)?.groupValues?.get(1) }
        val malId           = sveltekitScript?.let { Regex("""malId:(\d+)""").find(it)?.groupValues?.get(1) }
        val title           = sveltekitScript?.let { Regex("""title:"([^"]+)"""").find(it)?.groupValues?.get(1) }
            ?: document.selectFirst("h1")?.text()?.trim() ?: return null

        val description     = sveltekitScript?.let { Regex("""synopsis:"([^"]+)"""").find(it)?.groupValues?.get(1) }
            ?: document.selectFirst("div.entry.text-lead p")?.text()?.trim()

        val metaText        = document.select("div.flex-wrap.items-center.gap-2.text-sm span").text()
        val year            = sveltekitScript?.let { Regex("""startDate:"(\d{4})""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: Regex("""\b(19|20)\d{2}\b""").find(metaText)?.value?.toIntOrNull()

        val statusNum       = sveltekitScript?.let { Regex("""status:(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val showStatus      = when (statusNum) {
            1 -> ShowStatus.Completed
            0 -> ShowStatus.Ongoing
            else -> when {
                metaText.contains("Finalizado", ignoreCase = true) -> ShowStatus.Completed
                metaText.contains("emisión", ignoreCase = true) || metaText.contains("emision", ignoreCase = true) -> ShowStatus.Ongoing
                else -> null
            }
        }

        val tags            = document.select("div.flex-wrap.gap-2 a[href*=genre]").map { it.text() }
        val rating          = sveltekitScript?.let { Regex("""score:([\d.]+)""").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
            ?: document.selectFirst("div.ic-star-solid div.text-lead")?.text()?.trim()?.toDoubleOrNull()

        val duration        = document.selectFirst("span.runtime")?.text()?.split(" ")?.first()?.trim()?.toIntOrNull()
        val recommendations = document.select("article.bg-mute").mapNotNull { it.toRecommendationResult() }

        val poster          = fixUrlNull(document.selectFirst("img.aspect-poster, img[src*=covers]")?.attr("src"))
            ?: mediaId?.let { "$cdnUrl/covers/$it.jpg" }
        Log.d("AnimeAV", "Poster URL: $poster")

        val trailerId       = sveltekitScript?.let { Regex("""trailer:"([^"]+)"""").find(it)?.groupValues?.get(1) }
        val trailer         = if (!trailerId.isNullOrBlank()) "https://www.youtube.com/embed/$trailerId" else null

        val slug            = requestUrl.substringAfterLast("/")
        val episodes        = if (sveltekitScript != null && mediaId != null) {
            val episodesIndex = sveltekitScript.indexOf("episodes:[")
            if (episodesIndex != -1) {
                Regex("""\{id:\d+,number:(\d+)\}""").findAll(sveltekitScript.substring(episodesIndex))
                    .mapNotNull { it.groupValues[1].toIntOrNull() }
                    .map { epNum ->
                        newEpisode(fixUrl("/media/$slug/$epNum")) {
                            this.name      = "Episode $epNum"
                            this.episode   = epNum
                            this.season    = 1
                            this.posterUrl = "$cdnUrl/screenshots/$mediaId/$epNum.jpg"
                        }
                    }.toList()
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        return newAnimeLoadResponse(title, requestUrl, TvType.Anime, true) {
            this.posterUrl       = poster
            this.plot            = description
            this.year            = year
            this.showStatus      = showStatus
            this.tags            = tags
            this.score           = Score.from10(rating)
            this.episodes        = mutableMapOf(DubStatus.Subbed to episodes.distinctBy { it.episode }.sortedBy { it.episode })
            this.duration        = duration
            this.recommendations = recommendations
            addTrailer(trailer)
            malId?.toIntOrNull()?.let { addMalId(it) }
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title     = this.selectFirst("h3")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
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

        var hasLinks = false

        for (type in listOf("SUB", "DUB")) {
            val typeData = embedsData.substringAfter("$type:[", "").substringBefore("]")
            if (typeData.isBlank()) continue

            val itemPattern = Regex("""server:"([^"]+)",url:"([^"]+)"""")
            itemPattern.findAll(typeData).forEach { match ->
                val server = match.groupValues[1]
                var url    = match.groupValues[2]
                if (url.startsWith("//")) url = "https:$url"
                Log.d("AnimeAV", "Linkler : server=$server, type=$type, url=$url")
                if (url.isBlank()) return@forEach
                val name   = "$server - $type"
                loadCustomExtractor(
                    name             = name,
                    url              = url,
                    referer          = "$mainUrl/",
                    subtitleCallback = subtitleCallback,
                    callback         = callback
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
                            this.quality       = quality ?: link.quality
                            this.type          = link.type
                            this.referer       = link.referer
                            this.headers       = link.headers
                            this.extractorData = link.extractorData
                        }
                    )
                }
            }
        }
    }
}