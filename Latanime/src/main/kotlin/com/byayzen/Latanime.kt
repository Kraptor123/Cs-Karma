// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Latanime : MainAPI() {
    override var mainUrl = "https://latanime.org"
    override var name = "Latanime"
    override val hasMainPage = true
    override var lang = "mx"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Anime)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        mainUrl to " Añadidos recientemente",
        "${mainUrl}/animes" to "Últimos Animes",
        "${mainUrl}/animes?fecha=false&genero=aventura&letra=false&categoria=false" to "Aventura",
        "${mainUrl}/animes?fecha=false&genero=ciencia-ficcion&letra=false&categoria=false" to "Ciencia Ficción",
        "${mainUrl}/animes?fecha=false&genero=comedia&letra=false&categoria=false" to "Comedia",
        "${mainUrl}/animes?fecha=false&genero=deportes&letra=false&categoria=false" to "Deportes",
        "${mainUrl}/animes?fecha=false&genero=drama&letra=false&categoria=false" to "Drama",
        "${mainUrl}/animes?fecha=false&genero=ecchi&letra=false&categoria=false" to "Ecchi",
        "${mainUrl}/animes?fecha=false&genero=escolares&letra=false&categoria=false" to "Escolares",
        "${mainUrl}/animes?fecha=false&genero=fantasia&letra=false&categoria=false" to "Fantasía",
        "${mainUrl}/animes?fecha=false&genero=gore&letra=false&categoria=false" to "Gore",
        "${mainUrl}/animes?fecha=false&genero=harem&letra=false&categoria=false" to "Harem",
        "${mainUrl}/animes?fecha=false&genero=horror&letra=false&categoria=false" to "Horror",
        "${mainUrl}/animes?fecha=false&genero=josei&letra=false&categoria=false" to "Josei",
        "${mainUrl}/animes?fecha=false&genero=magia&letra=false&categoria=false" to "Magia",
        "${mainUrl}/animes?fecha=false&genero=mecha&letra=false&categoria=false" to "Mecha",
        "${mainUrl}/animes?fecha=false&genero=militar&letra=false&categoria=false" to "Militar",
        "${mainUrl}/animes?fecha=false&genero=misterio&letra=false&categoria=false" to "Misterio",
        "${mainUrl}/animes?fecha=false&genero=musica&letra=false&categoria=false" to "Música",
        "${mainUrl}/animes?fecha=false&genero=parodias&letra=false&categoria=false" to "Parodias",
        "${mainUrl}/animes?fecha=false&genero=psicologico&letra=false&categoria=false" to "Psicológico",
        "${mainUrl}/animes?fecha=false&genero=recuerdos-de-la-vida&letra=false&categoria=false" to "Recuerdos de la vida",
        "${mainUrl}/animes?fecha=false&genero=seinen&letra=false&categoria=false" to "Seinen",
        "${mainUrl}/animes?fecha=false&genero=shojo&letra=false&categoria=false" to "Shojo",
        "${mainUrl}/animes?fecha=false&genero=shonen&letra=false&categoria=false" to "Shonen",
        "${mainUrl}/animes?fecha=false&genero=sobrenatural&letra=false&categoria=false" to "Sobrenatural",
        "${mainUrl}/animes?fecha=false&genero=vampiros&letra=false&categoria=false" to "Vampiros",
        "${mainUrl}/animes?fecha=false&genero=yaoi&letra=false&categoria=false" to "Yaoi",
        "${mainUrl}/animes?fecha=false&genero=yuri&letra=false&categoria=false" to "Yuri",
        "${mainUrl}/animes?fecha=false&genero=latino&letra=false&categoria=false" to "Latino",
        "${mainUrl}/animes?fecha=false&genero=espacial&letra=false&categoria=false" to "Espacial",
        "${mainUrl}/animes?fecha=false&genero=historico&letra=false&categoria=false" to "Histórico",
        "${mainUrl}/animes?fecha=false&genero=samurai&letra=false&categoria=false" to "Samurai",
        "${mainUrl}/animes?fecha=false&genero=artes-marciales&letra=false&categoria=false" to "Artes Marciales",
        "${mainUrl}/animes?fecha=false&genero=demonios&letra=false&categoria=false" to "Demonios",
        "${mainUrl}/animes?fecha=false&genero=romance&letra=false&categoria=false" to "Romance",
        "${mainUrl}/animes?fecha=false&genero=dementia&letra=false&categoria=false" to "Dementia",
        "${mainUrl}/animes?fecha=false&genero=policia&letra=false&categoria=false" to "Policía",
        "${mainUrl}/animes?fecha=false&genero=castellano&letra=false&categoria=false" to "Castellano",
        "${mainUrl}/animes?fecha=false&genero=donghua&letra=false&categoria=false" to "Donghua",
        "${mainUrl}/animes?fecha=false&genero=suspenso&letra=false&categoria=false" to "Suspenso",
        "${mainUrl}/animes?fecha=false&genero=isekai&letra=false&categoria=false" to "Isekai"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) {
            request.data
        } else {
            if (request.data.contains("?")) "${request.data}&p=$page" else "${request.data}?p=$page"
        }

        val document = app.get(url).document
        val isRecent = request.name.contains("Añadidos")
        val elements = document.select("div.col-6, div.col-md-6, div.col-md-4, div.item, article.group\\/item")

        val home = elements.mapNotNull {
            it.toCommonSearchResult()
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = isRecent
            ),
            hasNext = true
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page <= 1) "$mainUrl/buscar?q=$query" else "$mainUrl/buscar?q=$query&p=$page"
        val document = app.get(url).document
        val aramaCevap = document.select("div.col-6, div.col-md-6, div.col-md-4, div.item").mapNotNull {
            it.toCommonSearchResult()
        }
        return newSearchResponseList(aramaCevap, hasNext = true)
    }

    private fun Element.toCommonSearchResult(): SearchResponse? {
        val anchor = this.selectFirst("a")
        val rawHref = anchor?.attr("href") ?: return null

        val title = this.selectFirst("h2, h3, span.title, div.text-2xs")?.text()?.trim() ?: return null

        var href = rawHref
        if (href.contains("/ver/")) {
            href = href.replace("/ver/", "/anime/").substringBefore("-episodio")
        } else if (href.contains("/media/")) {
            val slug = href.split("/").getOrNull(2)
            href = "/anime/$slug"
        }

        val img = this.selectFirst("img")
        val posterUrl = fixUrlNull(img?.attr("data-src")?.takeIf { it.isNotEmpty() } ?: img?.attr("src"))

        val typetxt = this.select("div.info_cap span, span.opacity-75, div.bg-line").text()
        val ismovie = typetxt.contains("Pelicula", ignoreCase = true)
        val hasDub = typetxt.contains("Latino", ignoreCase = true) || typetxt.contains("Castellano", ignoreCase = true)

        return newAnimeSearchResponse(title, fixUrl(href), if (ismovie) TvType.Movie else TvType.TvSeries) {
            this.posterUrl = posterUrl
            addDubStatus(hasDub)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.col-lg-9 h2")?.text()?.trim() ?: return null

        val poster = fixUrlNull(document.selectFirst("div.serieimgficha img")?.attr("src"))

        val description = document.selectFirst("div.col-lg-9 p.opacity-75")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")
        val tags = document.select("div.col-lg-9 a div.btn").map { it.text() }

        val statusText = document.selectFirst(".btn-estado")?.text()?.lowercase() ?: ""
        val status = when {
            statusText.contains("finalizado") -> ShowStatus.Completed
            statusText.contains("emisión") -> ShowStatus.Ongoing
            else -> null
        }

        val episodes = document.select("div[style*='overflow-y: auto'] > a").mapNotNull {
            val href = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
            val name = it.text().trim()

            val img = it.selectFirst("img")
            val epPoster = fixUrlNull(img?.attr("data-src")?.takeIf { s -> s.isNotEmpty() }
                ?: img?.attr("src"))
            val episodeNum = Regex("""Capitulo\s+(\d+)""", RegexOption.IGNORE_CASE)
                .find(name)?.groupValues?.get(1)?.toIntOrNull()

            newEpisode(href) {
                this.name = name
                this.episode = episodeNum
                this.posterUrl = epPoster
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.showStatus = status
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        doc.select("ul.cap_repro li a").mapNotNull {
            it.attr("data-player").takeIf { p -> p.isNotBlank() }
        }.amap { encData ->
            val repUrl = "$mainUrl/reproductor?url=$encData"
            val ifrmSrc = runCatching {
                app.get(repUrl).document
                    .selectFirst("iframe, embed")
                    ?.attr("src")
            }.getOrNull()

            loadExtractor(fixUrl(ifrmSrc ?: base64Decode(encData)), data, subtitleCallback, callback)
        }

        doc.select("div.descarga2 div a").mapNotNull {
            it.attr("href").takeIf { h -> h.isNotBlank() }
        }.amap { dlUrl ->
            loadExtractor(fixUrl(dlUrl), data, subtitleCallback, callback)
        }

        return true
    }
}