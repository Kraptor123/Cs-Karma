// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.util.ArrayList
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class KissKH : MainAPI() {
    override var mainUrl = "https://kisskh.id"
    override var name = "KissKH"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "&type=0&sub=0&country=0&status=0&order=2" to "Latest Releases",
        "&type=0&sub=0&country=2&status=0&order=1" to "Best Korean Dramas",
        "&type=0&sub=0&country=1&status=0&order=1" to "Best Chinese Dramas",
        "&type=2&sub=0&country=2&status=0&order=1" to "Popular Movies",
        "&type=2&sub=0&country=2&status=0&order=2" to "Latest Updated Movies",
        "&type=1&sub=0&country=2&status=0&order=1" to "Popular TV Series",
        "&type=1&sub=0&country=2&status=0&order=2" to "Latest Updated TV Series",
        "&type=3&sub=0&country=0&status=0&order=1" to "Popular Anime",
        "&type=3&sub=0&country=0&status=0&order=2" to "Latest Updated Anime",
        "&type=4&sub=0&country=0&status=0&order=1" to "Popular Hollywood",
        "&type=4&sub=0&country=0&status=0&order=2" to "Latest Updated Hollywood",
        "&type=0&sub=0&country=0&status=3&order=2" to "Coming Soon"
    )


    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val home = app.get("$mainUrl/api/DramaList/List?page=$page${request.data}")
            .parsedSafe<Responses>()?.data
            ?.mapNotNull { media ->
                media.toSearchResponse()
            } ?: throw ErrorLoadingException("Invalid Json reponse")
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Media.toSearchResponse(): SearchResponse? {
        if (!settingsForProvider.enableAdult && this.label!!.contains("RAW")) {
            // Skip RAW entries when adult is disabled
            return null
        }



        return newAnimeSearchResponse(
            title ?: return null,
            "$title/$id",
            TvType.TvSeries,
        ) {
            this.posterUrl = thumbnail
            this.posterHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
            )
            addSub(episodesCount)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse =
            app.get("$mainUrl/api/DramaList/Search?q=$query&type=0", referer = "$mainUrl/").text
        return tryParseJson<ArrayList<Media>>(searchResponse)?.mapNotNull { media ->
            media.toSearchResponse()
        } ?: throw ErrorLoadingException("Invalid Json reponse")
    }


    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)


    override suspend fun load(url: String): LoadResponse? {
        val id = url.split("/")
        val res = app.get(
            "$mainUrl/api/DramaList/Drama/${id.last()}?isq=false",
            referer = "$mainUrl/Drama/${getTitle(id.first())}?id=${id.last()}"
        ).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json response")

        val episodes = res.episodes?.map { eps ->
            val displayNumber = eps.number?.let { num ->
                if (num % 1.0 == 0.0) num.toInt().toString() else num.toString()
            } ?: ""

            newEpisode(Data(res.title, eps.number?.toInt(), res.id, eps.id).toJson()) {
                this.name = "Episode $displayNumber"
            }
        } ?: throw ErrorLoadingException("No Episode")

        var posterUrl = res.thumbnail?.trim()
        var plot = res.description
        val diziFilm = if (res.type == "Movie" || episodes.size == 1) "Movie" else "TvSeries"

        val icerikTitle = if (res.title?.contains("- season", ignoreCase = true) == true){
            res.title.substringBefore("(").substringBefore("-").trim()
        } else {
            res.title?.substringBefore("(")?.trim()
        }

        try {
            val tmdbUrl = if (diziFilm == "Movie") {
                "https://api.themoviedb.org/3/search/movie?language=en-US&query=${icerikTitle}&year=${res.releaseDate?.substringBefore("-")}&api_key=84259f99204eeb7d45c7e3d8e36c6123"
            } else {
                "https://api.themoviedb.org/3/search/tv?language=en-US&query=${icerikTitle}&year=${res.releaseDate?.substringBefore("-")}&api_key=84259f99204eeb7d45c7e3d8e36c6123"
            }

            val searchResponse = app.get(tmdbUrl, timeout = 10000)
                .parsedSafe<TmdbSearchResponse>()


            val tmdbData = searchResponse?.results?.firstOrNull { result ->
                val tmdbTitles = listOfNotNull(
                    result.title,
                    result.name,
                    result.originalTitle,
                    result.originalName
                )
                val sourceTitle = icerikTitle.toString()

                // Her başlık için karşılaştırma yap
                tmdbTitles.any { tmdbTitle ->
                    val normalizedTmdb = tmdbTitle.trim().lowercase().replace(Regex("\\s+"), " ")
                    val normalizedSource = sourceTitle.trim().lowercase().replace(Regex("\\s+"), " ")

                    // Tam eşleşme veya %85+ benzerlik
                    normalizedTmdb == normalizedSource ||
                            calculateSimilarity(normalizedTmdb, normalizedSource) >= 0.85
                }
            }

//            Log.d("kraptor_Tmdb", "Matched tmdbRes = $tmdbData")

            // Eşleşme bulunduysa, Türkçe detayları çek
            if (tmdbData != null) {
                val detailUrl = if (diziFilm == "Movie") {
                    "https://api.themoviedb.org/3/movie/${tmdbData.id}?language=tr&api_key=84259f99204eeb7d45c7e3d8e36c6123"
                } else {
                    "https://api.themoviedb.org/3/tv/${tmdbData.id}?language=tr&api_key=84259f99204eeb7d45c7e3d8e36c6123"
                }

                val turkishDetail = app.get(detailUrl, timeout = 10000)
                    .parsedSafe<TmdbDetail>()

                // Türkçe açıklamayı kullan
                if (turkishDetail?.overview?.isNotBlank() == true) {
                    plot = turkishDetail.overview
                }

                // Türkçe detaydan backdrop al (varsa)
                if (!turkishDetail?.backdropPath.isNullOrBlank()) {
                    posterUrl = getOriImageUrl(turkishDetail.backdropPath)
                } else if (!tmdbData.backdropPath.isNullOrBlank()) {
                    // Türkçe yoksa İngilizce TMDB backdrop'ını kullan
                    posterUrl = getOriImageUrl(tmdbData.backdropPath)
                }
            }
        } catch (e: Exception) {
//            Log.e("kraptor_Tmdb", "TMDB fetch error: ${e.message}", e)
            // Hata durumunda orijinal verileri kullan
        }

        val country = when (res.country){
            "South Korea" -> "Güney Kore"
            "China" -> "Çin"
            "Thailand" -> "Tayland"
            "Japan" -> "Japonya"
            "United States" -> "Amerika"
            else -> {
                res.country
            }
        }

        return newTvSeriesLoadResponse(
            res.title ?: return null,
            url,
            if (res.type == "Movie" || episodes.size == 1) TvType.Movie else TvType.TvSeries,
            episodes.reversed()
        ) {
            this.posterUrl = posterUrl
            this.posterHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
                "Referer" to "${mainUrl}/"
            )
            this.year = res.releaseDate?.split("-")?.first()?.toIntOrNull()
            this.plot = plot
            this.tags = listOf("$country", "${res.status?.replace("Ongoing","Devam Ediyor")?.replace("Completed","Tamamlandı")?.replace("Upcoming","Yakında")}", "${res.type?.replace("TVSeries","Dizi")?.replace("Movie","Film")}")
            this.showStatus = when (res.status) {
                "Completed" -> ShowStatus.Completed
                "Ongoing" -> ShowStatus.Ongoing
                else -> null
            }
        }
    }


    private fun calculateSimilarity(s1: String, s2: String): Double {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1

        if (longer.isEmpty()) return 1.0

        val editDistance = levenshteinDistance(longer, shorter)
        return (longer.length - editDistance).toDouble() / longer.length
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        for (i in 0..s1.length) {
            var lastValue = i
            for (j in 0..s2.length) {
                if (i == 0) {
                    costs[j] = j
                } else if (j > 0) {
                    var newValue = costs[j - 1]
                    if (s1[i - 1] != s2[j - 1]) {
                        newValue = minOf(minOf(newValue, lastValue), costs[j]) + 1
                    }
                    costs[j - 1] = lastValue
                    lastValue = newValue
                }
            }
            if (i > 0) costs[s2.length] = lastValue
        }
        return costs[s2.length]
    }


    private fun getOriImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
    }




    private fun getTitle(str: String): String {
        return str.replace(Regex("[^a-zA-Z0-9]"), "-")
    }

    private fun getLanguage(str: String): String {
        return when (str) {
            "Indonesia" -> "Indonesian"
            else -> str
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("KISSKH", "loadLinks started with data: $data")
        val KisskhAPI =
            "https://script.google.com/macros/s/AKfycbzn8B31PuDxzaMa9_CQ0VGEDasFqfzI5bXvjaIZH4DM8DNq9q6xj1ALvZNz_JT3jF0suA/exec?id="
        val KisskhSub =
            "https://script.google.com/macros/s/AKfycbyq6hTj0ZhlinYC6xbggtgo166tp6XaDKBCGtnYk8uOfYBUFwwxBui0sGXiu_zIFmA/exec?id="
        val loadData = parseJson<Data>(data)

        val videoKeyUrl = "$KisskhAPI${loadData.epsId}&version=2.8.10"
        Log.d("KISSKH", "Fetching video kkey from: $videoKeyUrl")

        val kkey = app.get(videoKeyUrl, timeout = 10000).parsedSafe<Key>()?.key ?: ""
        Log.d("KISSKH", "Video kkey received: $kkey")

        val videoApiUrl = "$mainUrl/api/DramaList/Episode/${loadData.epsId}.png?err=false&ts=&time=&kkey=$kkey"
        val videoReferer = "$mainUrl/Drama/${getTitle("${loadData.title}")}/Episode-${loadData.eps}?id=${loadData.id}&ep=${loadData.epsId}&page=0&pageSize=100"

        app.get(
            videoApiUrl,
            referer = videoReferer
        ).parsedSafe<Sources>()?.let { source ->
            Log.d("KISSKH", "Sources found: video=${source.video}, thirdParty=${source.thirdParty}")
            listOf(source.video, source.thirdParty).amap { link ->
                safeApiCall {
                    if (link?.contains(".m3u8") == true) {
                        Log.d("KISSKH", "Processing M3U8: $link")
                        M3u8Helper.generateM3u8(
                            this.name,
                            fixUrl(link),
                            referer = "$mainUrl/",
                            headers = mapOf("Origin" to mainUrl)
                        ).forEach(callback)
                    } else if (link?.contains("mp4") == true) {
                        Log.d("KISSKH", "Processing MP4: $link")
                        callback.invoke(
                            newExtractorLink(
                                this.name,
                                this.name,
                                url = fixUrl(link),
                                INFER_TYPE
                            ) {
                                this.referer = mainUrl
                                this.quality = Qualities.P720.value
                            }
                        )
                    } else if (link != null) {
                        Log.d("KISSKH", "Loading Extractor for: $link")
                        loadExtractor(
                            link.substringBefore("=http"),
                            "$mainUrl/",
                            subtitleCallback,
                            callback
                        )
                    }
                }
            }
        }

        val subKeyUrl = "$KisskhSub${loadData.epsId}&version=2.8.10"
        Log.d("KISSKH", "Fetching subtitle kkey from: $subKeyUrl")
        val kkey1 = app.get(subKeyUrl, timeout = 10000).parsedSafe<Key>()?.key ?: ""
        Log.d("KISSKH", "Subtitle kkey received: $kkey1")

        val subApiUrl = "$mainUrl/api/Sub/${loadData.epsId}?kkey=$kkey1"
        app.get(subApiUrl).text.let { res ->
            Log.d("KISSKH", "Subtitle API Response: $res")
            tryParseJson<List<Subtitle>>(res)?.map { sub ->
                Log.d("KISSKH", "Processing subtitle: label=${sub.label}, src=${sub.src}")
                if (sub.src!!.contains(".txt")) {
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            getLanguage(sub.label ?: return@map),
                            sub.src
                        )
                    )
                } else {
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            getLanguage(sub.label ?: return@map),
                            sub.src
                        )
                    )
                }
            } ?: Log.e("KISSKH", "Failed to parse subtitle JSON")
        }

        return true
    }

    private val CHUNK_REGEX1 by lazy { Regex("^\\d+$", RegexOption.MULTILINE) }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        return object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val request = chain.request().newBuilder().build()
                val response = chain.proceed(request)
                val url = response.request.url.toString()

                if (url.contains(".txt")) {
                    Log.d("KISSKH_SUB", "Intercepting encrypted subtitle: $url")
                    val responseBody = response.body.string()
                    val chunks = responseBody.split(CHUNK_REGEX1)
                        .filter(String::isNotBlank)
                        .map(String::trim)

                    Log.d("KISSKH_SUB", "Total chunks found: ${chunks.size}")

                    val decrypted = chunks.mapIndexed { index, chunk ->
                        if (chunk.isBlank()) return@mapIndexed ""
                        val parts = chunk.split("\n")
                        if (parts.isEmpty()) return@mapIndexed ""

                        val header = parts.first()
                        val text = parts.drop(1)
                        val d = text.joinToString("\n") { line ->
                            try {
                                decrypt(line)
                            } catch (e: Exception) {
                                Log.e("KISSKH_SUB", "Decryption failed for line: $line | Error: ${e.message}")
                                "DECRYPT_ERROR:${e.message}"
                            }
                        }
                        listOf(index + 1, header, d).joinToString("\n")
                    }.filter { it.isNotEmpty() }
                        .joinToString("\n\n")

                    Log.d("KISSKH_SUB", "Decryption cycle completed for $url")
                    val newBody = decrypted.toResponseBody(response.body.contentType())
                    return response.newBuilder()
                        .body(newBody)
                        .build()
                }
                return response
            }
        }
    }
}


data class Media(
    @JsonProperty("episodesCount") val episodesCount: Int?,
    @JsonProperty("thumbnail") val thumbnail: String?,
    @JsonProperty("label") val label: String?,
    @JsonProperty("id") val id: Int?,
    @JsonProperty("title") val title: String?,
)


data class Data(
    val title: String?,
    val eps: Int?,
    val id: Int?,
    val epsId: Int?,
)

data class Sources(
    @JsonProperty("Video") val video: String?,
    @JsonProperty("ThirdParty") val thirdParty: String?,
)

data class Subtitle(
    @JsonProperty("src") val src: String?,
    @JsonProperty("label") val label: String?,
)

data class Responses(
    @JsonProperty("data") val data: ArrayList<Media>? = arrayListOf(),
)

data class Episodes(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("number") val number: Double?,
    @JsonProperty("sub") val sub: Int?,
)

data class MediaDetail(
    @JsonProperty("description") val description: String?,
    @JsonProperty("releaseDate") val releaseDate: String?,
    @JsonProperty("status") val status: String?,
    @JsonProperty("type") val type: String?,
    @JsonProperty("country") val country: String?,
    @JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
    @JsonProperty("thumbnail") val thumbnail: String?,
    @JsonProperty("id") val id: Int?,
    @JsonProperty("title") val title: String?,
)

data class Key(
    val id: String,
    val version: String,
    val key: String,
)

data class TmdbSearchResponse(
    @JsonProperty("page") val page: Int? = null,
    @JsonProperty("results") val results: List<TmdbDetail>? = null,
    @JsonProperty("total_pages") val totalPages: Int? = null,
    @JsonProperty("total_results") val totalResults: Int? = null
)


data class TmdbDetail(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("imdb_id") val imdbId: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("original_title") val originalTitle: String? = null,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("runtime") val runtime: Int? = null,
    @JsonProperty("vote_average") val vote_average: Any? = null,
    @JsonProperty("original_language") val original_language: String? = null,
    @JsonProperty("status") val status: String? = null,
)