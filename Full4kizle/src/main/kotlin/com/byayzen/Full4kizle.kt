// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Full4kizle : MainAPI() {
    override var mainUrl = "https://izlefilm4k.cc"
    override var name = "Full4Kİzle"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "$mainUrl/Kategori/anime-izle/" to "Anime izle",
        "$mainUrl/Kategori/blutv-filmleri/" to "BluTV Filmleri",
        "$mainUrl/Kategori/cizgi-filmler/" to "Çizgi Filmler",
        "$mainUrl/Kategori/en-populer-filmler/" to "En Popüler Filmler",
        "$mainUrl/Kategori/filmakinesi/" to "Filmakinesi",
        "$mainUrl/Kategori/hdfilmcehennemi/" to "Hdfilmcehennemi",
        "$mainUrl/Kategori/hdfilmcenneti/" to "Hdfilmcenneti",
        "$mainUrl/Kategori/imdb-top-250/" to "IMDB TOP 250",
        "$mainUrl/Kategori/k-drama-reels-asya-dizileri/" to "Asya Dizileri (K-Drama)",
        "$mainUrl/Kategori/marvel-filmleri/" to "Marvel Filmleri",
        "$mainUrl/Kategori/netflix-dizileri/" to "Netflix Dizileri",
        "$mainUrl/Kategori/netflix-filmleri-izle/" to "Netflix Filmleri",
        "$mainUrl/Kategori/passionflix-filmleri/" to "PassionFlix Filmleri",
        "$mainUrl/Kategori/spor/" to "Spor",
        "$mainUrl/Kategori/tur/" to "Türler / Yerli",
        "$mainUrl/Kategori/yabanci-diziler/" to "Yabancı Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) {
            request.data
        } else {
            val base = request.data.removeSuffix("/")
            "$base/page/$page/"
        }

        val document = app.get(url).document
        val home = document.select("div.movie-preview").mapNotNull {
            it.toMainPageResult()
        }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val titleElement = this.selectFirst(".movie-title a") ?: return null
        // "izle" kelimesini temizliyoruz
        val title = titleElement.text().replace("(?i) izle".toRegex(), "").trim()
        val href = fixUrl(titleElement.attr("href"))
        val posterUrl = this.selectFirst(".movie-poster img")?.attr("src")

        return newMovieSearchResponse(title, href, TvType.Movie) {
            val imgHeader =
                "{\"referer\":\"https://izleonlineplus.cc/\",\"user-agent\":\"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0\"}"
            this.posterUrl = posterUrl?.let { "$it?headers=$imgHeader" }
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page == 1) {
            "${mainUrl}/?s=${query}"
        } else {
            "${mainUrl}/page/$page/?s=${query}"
        }

        val document = app.get(url).document

        val aramaCevap = document.select("div.movie-preview").mapNotNull {
            it.toMainPageResult()
        }

        return newSearchResponseList(aramaCevap, hasNext = true)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)


    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()
            ?.replace("(?i)izle".toRegex(), "")
            ?.trim() ?: return null

        val headerJson =
            "{\"referer\":\"https://izleonlineplus.cc/\",\"user-agent\":\"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0\"}"

        val posterRaw = document.selectFirst(".poster img")?.attr("src")
        val poster = posterRaw?.let { "$it?headers=$headerJson" }

        val description = document.selectFirst(".excerpt p")?.text()?.trim()

        val year = document.selectFirst(".release a")?.text()?.trim()?.toIntOrNull()
        val rating = document.selectFirst(".imdb-rating")?.text()?.replace("IMDB Puanı", "")?.trim()
            ?.toDoubleOrNull()

        val recommendations = document.select("div.movie-preview").mapNotNull {
            val linkElement = it.selectFirst(".movie-title a")
            val recTitle = linkElement?.text()?.trim() ?: return@mapNotNull null
            val recHref = linkElement.attr("href") ?: return@mapNotNull null
            val recPosterRaw = it.selectFirst(".movie-poster img")?.attr("src")
            val recPoster = recPosterRaw?.let { p -> "$p?headers=$headerJson" }

            newMovieSearchResponse(recTitle, recHref, TvType.Movie) {
                this.posterUrl = recPoster
            }
        }

        val episodeElements = document.select(".parts-middle a, .parts-middle .part.active")

        return if (episodeElements.isNotEmpty()) {
            val episodes = episodeElements.mapIndexed { index, element ->
                val epName =
                    element.selectFirst(".part-name")?.text()?.trim() ?: "Bölüm ${index + 1}"
                val epHref = element.attr("href")
                    .ifBlank { url }


                val seasonNum = epName.find { it.isDigit() }?.toString()?.toIntOrNull() ?: 1
                val episodeNum =
                    epName.substringAfter("Sezon").find { it.isDigit() }?.toString()?.toIntOrNull()
                        ?: (index + 1)

                newEpisode(epHref) {
                    this.name = epName
                    this.episode = episodeNum
                    this.season = seasonNum
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.score = rating?.let { Score.from10(it) }
                this.recommendations = recommendations
            }
        } else {
            // MOVIE (Film) Yanıtı
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.score = rating?.let { Score.from10(it) }
                this.recommendations = recommendations
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val iframe = document.selectFirst(".center-container iframe")?.attr("src")
            ?: document.selectFirst("iframe[src*='hotstream.club']")?.attr("src")
        if (iframe != null) {
            loadExtractor(iframe, data, subtitleCallback, callback)
        }

        return true
    }
    }