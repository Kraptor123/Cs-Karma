package com.byayzen

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class Hackstore : MainAPI() {
    override var mainUrl              = "https://hackstore2.com"
    override var name                 = "Hackstore"
    override val hasMainPage          = true
    override var lang                 = "es"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:154.0) Gecko/20100101 Firefox/154.0",
        "Accept"     to "*/*",
        "Referer"    to "$mainUrl/"
    )

    private val genresMap = mapOf(
        24    to "Crimen",
        25    to "Comedia",
        26    to "Romance",
        50    to "Familia",
        51    to "Fantasía",
        71    to "Animación",
        72    to "Aventura",
        96    to "Acción",
        114   to "Drama",
        115   to "Misterio",
        116   to "Suspense",
        270   to "Terror",
        293   to "Ciencia ficción",
        375   to "Historia",
        1094  to "Bélica",
        1184  to "Western",
        1968  to "Música",
        2292  to "Película de TV",
        3118  to "Documental",
        4178  to "Sci-Fi & Fantasy",
        4179  to "Action & Adventure",
        5008  to "Kids",
        7925  to "War & Politics",
        11652 to "Reality",
        18775 to "Soap",
        45999 to "Talk"
    )

    override val mainPage = mainPageOf(
        "${mainUrl}/api/rest/sliders?type=movies&listing=0" to "Películas de estreno",
        "${mainUrl}/api/rest/listing?post_type=movies"       to "Películas",
        "${mainUrl}/api/rest/listing?post_type=tvshows"      to "Series",
        "${mainUrl}/api/rest/listing?post_type=animes"       to "Animes"
    )

    private suspend inline fun <reified T : Any> getJson(url: String): T? {
        val res  = app.get(url, headers = headers)
        val text = res.text.trim()
        if (text.startsWith("<")) return null
        return AppUtils.tryParseJson<T>(text)
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val isSlider = request.data.contains("/sliders")
        val url = if (isSlider) {
            request.data
        } else {
            val sep = if (request.data.contains("?")) "&" else "?"
            "${request.data}${sep}page=$page"
        }

        val items = if (isSlider) {
            getJson<SlidersResponse>(url)?.data?.mapNotNull { it.toSearchResponse() }
        } else {
            getJson<ListingResponse>(url)?.data?.posts?.mapNotNull { it.toSearchResponse() }
        } ?: emptyList()

        return newHomePageResponse(
            list = HomePageList(
                name               = request.name,
                list               = items,
                isHorizontalImages = false
            ),
            hasNext = !isSlider && items.isNotEmpty()
        )
    }

    private fun MediaItem.toSearchResponse(): SearchResponse? {
        val title  = this.title ?: return null
        val slug   = this.slug ?: return null
        val type   = this.type ?: "movies"
        val url    = "$mainUrl/api/rest/single?post_name=$slug&post_type=$type"
        val poster = images?.poster?.let { "$mainUrl/wp-content/uploads$it" }
        val score  = rating?.toDoubleOrNull()
        val year   = releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()

        return when (type) {
            "animes"  -> newAnimeSearchResponse(title, url, TvType.Anime) {
                this.posterUrl = poster
                this.score     = Score.from10(score)
                this.year      = year
            }
            "tvshows" -> newTvSeriesSearchResponse(title, url, TvType.TvSeries) {
                this.posterUrl = poster
                this.score     = Score.from10(score)
                this.year      = year
            }
            else      -> newMovieSearchResponse(title, url, TvType.Movie) {
                this.posterUrl = poster
                this.score     = Score.from10(score)
                this.year      = year
            }
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList = coroutineScope {
        val types = listOf("movies", "tvshows", "animes")
        val results = types.map { type ->
            async {
                getJson<ListingResponse>(
                    "$mainUrl/api/rest/search?post_type=$type&query=$query&page=$page"
                )?.data?.posts?.mapNotNull { it.toSearchResponse() } ?: emptyList()
            }
        }.awaitAll().flatten()

        newSearchResponseList(results, hasNext = results.isNotEmpty())
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val apiUrl = if (url.contains("/api/rest/single")) {
            url
        } else {
            val slug = url.trimEnd('/').substringAfterLast('/')
            val type = when {
                url.contains("/peliculas") -> "movies"
                url.contains("/series")    -> "tvshows"
                url.contains("/animes")    -> "animes"
                else                       -> "movies"
            }
            "$mainUrl/api/rest/single?post_name=$slug&post_type=$type"
        }

        val item = getJson<SingleResponse>(apiUrl)?.data ?: return null

        val title       = item.title ?: return null
        val poster      = item.images?.poster?.let { "$mainUrl/wp-content/uploads$it" }
        val backdrop    = item.images?.backdrop?.let { "$mainUrl/wp-content/uploads$it" }
        val plot        = item.overview
        val year        = item.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
        val score       = item.rating?.toDoubleOrNull()
        val duration    = item.runtime?.toDoubleOrNull()?.toInt()
        val trailer     = item.trailer?.takeIf { it.isNotBlank() && it != "no" }?.let { "https://www.youtube.com/embed/$it" }
        val type        = item.type ?: "movies"
        val tags        = item.genres?.mapNotNull { genresMap[it] }

        val recUrl          = "$mainUrl/api/rest/related?post_id=${item.id}"
        val recommendations = getJson<SlidersResponse>(recUrl)?.data?.mapNotNull { it.toSearchResponse() }

        if (type == "movies") {
            val playerUrl = "$mainUrl/api/rest/player?post_id=${item.id}"
            return newMovieLoadResponse(title, url, TvType.Movie, playerUrl) {
                this.posterUrl           = poster
                this.backgroundPosterUrl = backdrop
                this.plot                = plot
                this.year                = year
                this.tags                = tags
                this.score               = Score.from10(score)
                this.duration            = duration
                this.recommendations     = recommendations
                if (trailer != null) addTrailer(trailer)
            }
        }

        val episodes = getJson<EpisodesResponse>(
            "$mainUrl/api/rest/episodes?post_id=${item.id}"
        )?.data?.mapNotNull { ep ->
            val epId = ep.id ?: return@mapNotNull null
            newEpisode("$mainUrl/api/rest/player?post_id=$epId") {
                this.name        = ep.title
                this.season      = ep.seasonNumber
                this.episode     = ep.episodeNumber
                this.posterUrl   = ep.stillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                this.description = ep.overview
                this.score       = Score.from10(ep.voteAverage?.toDoubleOrNull())
                this.runTime     = ep.runtime?.toIntOrNull()
            }
        } ?: emptyList()

        return if (type == "animes") {
            newAnimeLoadResponse(title, url, TvType.Anime) {
                this.posterUrl           = poster
                this.backgroundPosterUrl = backdrop
                this.plot                = plot
                this.year                = year
                this.tags                = tags
                this.score               = Score.from10(score)
                this.recommendations     = recommendations
                if (trailer != null) addTrailer(trailer)
                addEpisodes(DubStatus.Subbed, episodes)
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl           = poster
                this.backgroundPosterUrl = backdrop
                this.plot                = plot
                this.year                = year
                this.tags                = tags
                this.score               = Score.from10(score)
                this.recommendations     = recommendations
                if (trailer != null) addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val postId = when {
            data.contains("post_id=") -> data.substringAfter("post_id=").substringBefore("&")
            else                      -> data
        }

        var foundAny = false

        val players = getJson<PlayerResponse>("$mainUrl/api/rest/player?post_id=$postId")?.data
        Log.d("Hackstore", "Players found: ${players?.size ?: 0}")
        players?.amap { player ->
            val link = player.url ?: return@amap
            Log.d("Hackstore", "Loading player link: $link")
            if (link.startsWith("http")) {
                try {
                    loadExtractor(link, "$mainUrl/", subtitleCallback) { extractorLink ->
                        foundAny = true
                        callback(extractorLink)
                    }
                } catch (_: Throwable) {
                }
            }
        }

        val downloads = getJson<DownloadsResponse>("$mainUrl/api/rest/downloads?post_id=$postId")?.data
        downloads?.amap { dl ->
            val link = dl.url ?: return@amap
            try {
                if (link.startsWith("magnet:")) {
                    callback(
                        newExtractorLink(
                            source = name,
                            name   = "${dl.server ?: name} ${dl.quality ?: ""}".trim(),
                            url    = link,
                            type   = ExtractorLinkType.MAGNET
                        )
                    )
                    foundAny = true
                } else if (link.startsWith("http")) {
                    loadExtractor(link, "$mainUrl/", subtitleCallback) { extractorLink ->
                        foundAny = true
                        callback(extractorLink)
                    }
                }
            } catch (_: Throwable) {
            }
        }

        return foundAny
    }
}

data class MediaItem(
    @JsonProperty("_id")          val id: Int? = null,
    @JsonProperty("title")        val title: String? = null,
    @JsonProperty("overview")     val overview: String? = null,
    @JsonProperty("slug")         val slug: String? = null,
    @JsonProperty("images")       val images: MediaImages? = null,
    @JsonProperty("trailer")      val trailer: String? = null,
    @JsonProperty("rating")       val rating: String? = null,
    @JsonProperty("type")         val type: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("runtime")      val runtime: String? = null,
    @JsonProperty("genres")       val genres: List<Int>? = null
)

data class MediaImages(
    @JsonProperty("poster")   val poster: String? = null,
    @JsonProperty("backdrop") val backdrop: String? = null
)

data class SlidersResponse(
    @JsonProperty("data") val data: List<MediaItem>? = null
)

data class ListingResponse(
    @JsonProperty("data") val data: ListingData? = null
)

data class ListingData(
    @JsonProperty("posts") val posts: List<MediaItem>? = null
)

data class SingleResponse(
    @JsonProperty("data") val data: MediaItem? = null
)

data class EpisodeItem(
    @JsonProperty("_id")            val id: Int? = null,
    @JsonProperty("title")          val title: String? = null,
    @JsonProperty("overview")       val overview: String? = null,
    @JsonProperty("runtime")        val runtime: String? = null,
    @JsonProperty("still_path")     val stillPath: String? = null,
    @JsonProperty("vote_average")   val voteAverage: String? = null,
    @JsonProperty("season_number")  val seasonNumber: Int? = null,
    @JsonProperty("episode_number") val episodeNumber: Int? = null
)

data class EpisodesResponse(
    @JsonProperty("data") val data: List<EpisodeItem>? = null
)

data class PlayerItem(
    @JsonProperty("url") val url: String? = null
)

data class PlayerResponse(
    @JsonProperty("data") val data: List<PlayerItem>? = null
)

data class DownloadItem(
    @JsonProperty("quality") val quality: String? = null,
    @JsonProperty("url")     val url: String? = null,
    @JsonProperty("server")  val server: String? = null
)

data class DownloadsResponse(
    @JsonProperty("data") val data: List<DownloadItem>? = null
)