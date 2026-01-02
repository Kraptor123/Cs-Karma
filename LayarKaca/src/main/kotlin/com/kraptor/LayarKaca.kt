// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.

package com.kraptor

import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.json.JSONObject
import java.net.URI

class LayarKaca : MainAPI() {
    override var mainUrl              = "https://tv7.lk21official.cc"
    override var name                 = "LayarKaca"
    override val hasMainPage          = true
    override var lang                 = "id"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    private var seriesUrl = "https://series.lk21.de"

    override val mainPage = mainPageOf(
        "${mainUrl}/populer"  to "Popular",
        "$seriesUrl/latest-series/page/"  to "Series Terbaru",
        "${mainUrl}/quality/bluray"       to "Bluray",
        "${mainUrl}/country/japan"        to "Jepang",
        "${mainUrl}/country/china"        to "Cina",
        "${mainUrl}/genre/action"         to "Action",
        "${mainUrl}/genre/adventure"      to "Adventure",
        "${mainUrl}/genre/animation"      to "Animation",
        "${mainUrl}/genre/biography"      to "Biography",
        "${mainUrl}/genre/comedy"         to "Comedy",
        "${mainUrl}/genre/crime"          to "Crime",
        "${mainUrl}/genre/documentary"    to "Documentary",
        "${mainUrl}/genre/drama"          to "Drama",
        "${mainUrl}/genre/family"         to "Family",
        "${mainUrl}/genre/fantasy"        to "Fantasy",
        "${mainUrl}/genre/history"        to "History",
        "${mainUrl}/genre/horror"         to "Horror",
        "${mainUrl}/genre/musical"        to "Musical",
        "${mainUrl}/genre/mystery"        to "Mystery",
        "${mainUrl}/genre/romance"        to "Romance",
        "${mainUrl}/genre/sci-fi"         to "Sci-Fi",
        "${mainUrl}/genre/sport"          to "Sport",
        "${mainUrl}/genre/thriller"       to "Thriller",
        "${mainUrl}/genre/war"            to "War",
        "${mainUrl}/genre/western"        to "Western",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/page/$page").document
        val home     = document.select("div.gallery-grid article a").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("h3")?.text() ?: return null
        val href      = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val score     = this.selectFirst("span[itemprop=ratingValue]")?.text()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.score     = Score.from10(score)
        }
    }


    override suspend fun search(query: String, page: Int): SearchResponseList {
        val document = app.get("https://gudangvape.com/search.php?s=$query&page=$page", referer = "${mainUrl}/").text

        val mapper = mapper.readValue<SearchApi>(document)

        val aramaCevap = mapper.data.mapNotNull { it.toSearchResult() }

        return newSearchResponseList(aramaCevap, hasNext = true)
    }

    private fun Data.toSearchResult(): SearchResponse? {
        val title     = this.title
        val href      = fixUrlNull(this.slug) ?: return null
        val posterUrl = "https://poster.lk21.party/wp-content/uploads/${this.poster}"
        val score     = this.rating

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.score     = Score.from10(score)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse {
        val fixUrl = getProperLink(url)
        val document = app.get(fixUrl).documentLarge
        val baseurl=fetchURL(fixUrl)
        val title = document.selectFirst("div.movie-info h1")?.text()?.trim().toString()
        val poster = document.select("meta[property=og:image]").attr("content")
        val tags = document.select("div.tag-list span").map { it.text() }
        val posterheaders= mapOf("Referer" to getBaseUrl(poster))

        val year = Regex("\\d, (\\d+)").find(
            document.select("div.movie-info h1").text().trim()
        )?.groupValues?.get(1).toString().toIntOrNull()
        val tvType = if (document.selectFirst("#season-data") != null) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst("div.meta-info")?.text()?.trim()
        val trailer = document.selectFirst("ul.action-left > li:nth-child(3) > a")?.attr("href")
        val rating = document.selectFirst("div.info-tag strong")?.text()

        val recommendations = document.select("li.slider article").map {
            val recName = it.selectFirst("h3")?.text()?.trim().toString()
            val recHref = baseurl+it.selectFirst("a")!!.attr("href")
            val recPosterUrl = fixUrl(it.selectFirst("img")?.attr("src").toString())
            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
                this.posterHeaders = posterheaders
            }
        }

        return if (tvType == TvType.TvSeries) {
            val json = document.selectFirst("script#season-data")?.data()
            val episodes = mutableListOf<Episode>()
            if (json != null) {
                val root = JSONObject(json)
                root.keys().forEach { seasonKey ->
                    val seasonArr = root.getJSONArray(seasonKey)
                    for (i in 0 until seasonArr.length()) {
                        val ep = seasonArr.getJSONObject(i)
                        val href = fixUrl("$baseurl/"+ep.getString("slug"))
                        val episodeNo = ep.optInt("episode_no")
                        val seasonNo = ep.optInt("s")
                        episodes.add(
                            newEpisode(href) {
                                this.name = "Episode $episodeNo"
                                this.season = seasonNo
                                this.episode = episodeNo
                            }
                        )
                    }
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.posterHeaders = posterheaders
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.posterHeaders = posterheaders
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("kraptor_$name", "data = ${data}")
        val document = app.get(data).document

        val videolar = document.select("ul#player-list a")

        videolar.forEach { video ->

            val player = video.attr("href")

            Log.d("kraptor_$name", "player = ${player}")

            val playerAl = app.get(player, referer = "${mainUrl}/").document

            val iframe = playerAl.selectFirst("iframe")?.attr("src").toString()

            if (iframe.contains("https://short.icu")) {
                val iframe =  app.get(iframe, allowRedirects = true).url
                Log.d("kraptor_$name", "iframe » $iframe")
                loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback)
            } else
                Log.d("kraptor_$name", "iframe » $iframe")
            loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback)

        }
        return true
    }

    private suspend fun fetchURL(url: String): String {
        val res = app.get(url, allowRedirects = false)
        val href = res.headers["location"]

        return if (href != null) {
            val it = URI(href)
            "${it.scheme}://${it.host}"
        } else {
            url
        }
    }

    fun getBaseUrl(url: String?): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

    private suspend fun getProperLink(url: String): String {
        if (url.startsWith(seriesUrl)) return url
        val res = app.get(url).documentLarge
        return if (res.select("title").text().contains("Nontondrama", true)) {
            res.selectFirst("a#openNow")?.attr("href")
                ?: res.selectFirst("div.links a")?.attr("href")
                ?: url
        } else {
            url
        }
    }

}

data class SearchApi(
    val data: List<Data>
)

data class Data(
    val slug: String,
    val title: String,
    val poster: String,
    val rating: Double?,
)

