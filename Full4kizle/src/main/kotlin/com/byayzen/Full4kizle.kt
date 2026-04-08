// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Full4kizle : MainAPI() {
    override var mainUrl = "https://izleplus.com"
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
        "$mainUrl/Kategori/yabanci-diziler/" to "Yabancı Diziler",
        "$mainUrl/Kategori/tur/18-erotik-filmler/" to "Erotik"

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
        val title = titleElement.text().replace("(?i) izle".toRegex(), "").trim()
        val href = fixUrl(titleElement.attr("href"))

        val posterUrl = this.selectFirst(".movie-poster img")?.let { img ->
            if (img.hasAttr("data-src")) img.attr("abs:data-src") else img.attr("abs:src")
        }

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page == 1) {
            "$mainUrl/?s=$query"
        } else {
            "$mainUrl/page/$page/?s=$query"
        }

        val document = app.get(url).document

        val searchResults = document.select(".v50-card").mapNotNull {
            it.toSearchResult()
        }

        val hasNext = document.selectFirst(".v50-pagination a.next, .v50-pagination .current + a") != null

        return newSearchResponseList(searchResults, hasNext)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("h3") ?: return null
        val title = titleElement.text().replace("(?i)izle|altyazılı|dublaj".toRegex(), "").trim()
        val href = this.selectFirst("a")?.attr("abs:href") ?: return null

        val posterUrl = this.selectFirst(".v50-poster-box img")?.let { img ->
            img.attr("abs:data-src").ifBlank { img.attr("abs:src") }
        }

        val typeText = this.selectFirst(".v50-details span")?.text()?.lowercase() ?: ""
        val type = if (typeText.contains("dizi") || title.lowercase().contains("sezon")) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }

        return if (type == TvType.Movie) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)


    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst(".p-v12-main-title")?.text()
            ?.replace("(?i)izle|altyazılı|dublaj|dizisi".toRegex(), "")
            ?.trim() ?: return null

        val poster = document.selectFirst(".p-v12-poster-main img")?.let { img ->
            img.attr("abs:data-src").ifBlank { img.attr("abs:src") }
        }

        val description = document.selectFirst(".p-v12-story-content")?.text()?.trim()
        val year = document.selectFirst(".p-v12-meta-info .meta-item")?.text()?.trim()?.toIntOrNull()
        val rating = document.selectFirst(".p-v12-imdb-badge")?.text()?.replace("★", "")?.trim()?.toDoubleOrNull()

        val tags = document.select(".p-v12-meta-info a[rel='category tag']").map { it.text() }

        val recommendations = document.select("div.movie-preview").mapNotNull {
            val linkElement = it.selectFirst(".movie-title a") ?: return@mapNotNull null
            val recTitle = linkElement.text().trim()
            val recHref = linkElement.attr("abs:href")
            val recPoster = it.selectFirst(".movie-poster img")?.let { img ->
                img.attr("abs:data-src").ifBlank { img.attr("abs:src") }
            }

            newMovieSearchResponse(recTitle, recHref, TvType.Movie) {
                this.posterUrl = recPoster
            }
        }

        val episodeElements = document.select(".parts-middle a, .parts-middle .part.active")

        return if (episodeElements.isNotEmpty()) {
            val episodes = episodeElements.mapIndexed { index, element ->
                val epName = element.selectFirst(".part-name")?.text()?.trim() ?: "Bölüm ${index + 1}"
                val epHref = element.attr("abs:href").ifBlank { url }

                val seasonNum = epName.substringBefore(".").filter { it.isDigit() }.toIntOrNull() ?: 1
                val episodeNum = epName.substringAfter("Sezon").filter { it.isDigit() }.toIntOrNull() ?: (index + 1)

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
                this.tags = tags
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.score = rating?.let { Score.from10(it) }
                this.tags = tags
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
        val iframe = document.selectFirst(".p-v12-video-frame iframe")?.attr("abs:src")
        if (iframe != null) {
            loadExtractor(iframe, data, subtitleCallback, callback)
        }

        return true
    }
    }