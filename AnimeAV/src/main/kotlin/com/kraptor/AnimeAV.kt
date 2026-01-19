// ! Bu araç @Kraptor123 tarafından | @cs-karma için yazılmıştır.

package com.kraptor

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Document

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
        val hamUrl = if (url.contains("media/")) {
            val parcalar = url.split("/")
            if (parcalar.size > 5) url.substringBeforeLast("/") else url
        } else url

        val cevap = app.get(hamUrl.removeSuffix("/"))
        val doc = cevap.document
        val baslik = doc.selectFirst("h1")?.text()?.trim() ?: return null

        val poster = fixUrlNull(doc.selectFirst("img.aspect-poster")?.attr("src"))
        val ozet = doc.selectFirst("div.entry p")?.text()?.trim()

        val bolumler = jsondanBolumleriAl(hamUrl, baslik, poster).ifEmpty { htmldenBolumleriAl(doc, baslik) }
        bolumler.sortBy { it.episode }

        return newAnimeLoadResponse(baslik, url, TvType.Anime, true) {
            this.posterUrl = poster
            this.plot = ozet
            this.year = doc.select("div.flex-wrap span").find { it.text().any { c -> c.isDigit() } }?.text()?.filter { it.isDigit() }?.toIntOrNull()
            this.tags = doc.select("a[href*=genre]").map { it.text() }
            this.score = Score.from10(doc.selectFirst("div.text-lead.text-2xl")?.text()?.trim()?.toDoubleOrNull())
            this.episodes = mutableMapOf(DubStatus.None to bolumler)
            this.recommendations = doc.select("article.bg-mute").mapNotNull { rec ->
                val rbaslik = rec.selectFirst("h3")?.text() ?: return@mapNotNull null
                val rlink = fixUrl(rec.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
                newAnimeSearchResponse(rbaslik, rlink, TvType.Anime) {
                    this.posterUrl = fixUrlNull(rec.selectFirst("img")?.attr("src"))
                }
            }

        }
    }

    private suspend fun jsondanBolumleriAl(temelUrl: String, baslik: String, poster: String?): MutableList<Episode> {
        val liste = mutableListOf<Episode>()
        try {
            val jsonUrl = "${temelUrl.removeSuffix("/")}/__data.json?x-sveltekit-invalidated=0010"
            val cevap = app.get(jsonUrl, referer = temelUrl).text
            val json = JSONObject(cevap)
            val veri = json.getJSONArray("nodes").getJSONObject(2).getJSONArray("data")
            val sezon = baslik.substringAfter("season").substringAfter("part").filter { it.isDigit() }.toIntOrNull() ?: 1

            """\{"id":(\d+),"number":(\d+)\}""".toRegex().findAll(cevap).forEach { m ->
                val idIdx = m.groupValues[1].toInt()
                val noIdx = m.groupValues[2].toInt()
                val epId = veri.getString(idIdx)
                val epNo = veri.getString(noIdx)

                liste.add(newEpisode("$mainUrl/watch/$epId") {
                    this.name = "Episodio $epNo"
                    this.episode = epNo.toIntOrNull()
                    this.season = sezon
                    this.posterUrl = poster
                })
            }
        } catch (e: Exception) {
            println("JSON Hatası: ${e.message}")
        }
        return liste
    }

    private fun htmldenBolumleriAl(doc: Document, baslik: String): MutableList<Episode> {
        val sezon = baslik.substringAfter("season").substringAfter("part").filter { it.isDigit() }.toIntOrNull() ?: 1
        return doc.select("article.group\\/item").mapNotNull { ep ->
            val link = fixUrlNull(ep.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val no = ep.selectFirst("div.bg-line span")?.text()?.toIntOrNull()

            newEpisode(link) {
                this.name = "Episodio $no"
                this.posterUrl = ep.selectFirst("img")?.attr("src")
                this.episode = no
                this.season = sezon
            }
        }.toMutableList()
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