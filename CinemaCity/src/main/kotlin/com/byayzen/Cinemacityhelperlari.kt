package com.byayzen

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.nicehttp.NiceResponse
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

data class Video(
    val url: String,
    val subtitles: String
)

data class VideoWrapper(
    val data: String
)

data class TmdbMainResponse(
    val results: List<TmdbResult>,
    val page: Int?,
    @JsonProperty("total_pages") val total_pages: Int?,
    @JsonProperty("total_results") val total_results: Int?
)

data class TmdbResult(
    val id: Int?,
    val title: String?,
    val name: String?,
    @JsonProperty("original_title") val original_title: String?,
    @JsonProperty("original_name") val original_name: String?,
    @JsonProperty("poster_path") val poster_path: String?,
    @JsonProperty("backdrop_path") val backdrop_path: String?,
    @JsonProperty("media_type") val media_type: String?,
    @JsonProperty("vote_average") val vote_average: Double?
)

data class TmdbCast(
    val name: String?,
    @JsonProperty("profile_path") val profile_path: String?,
    val character: String?
)

data class TmdbEpisode(
    @JsonProperty("episode_number") val episode_number: Int?,
    @JsonProperty("season_number") val season_number: Int?,
    val name: String?,
    val overview: String?,
    @JsonProperty("still_path") val still_path: String?,
    @JsonProperty("vote_average") val vote_average: Double?,
    val runtime: Int?,
    @JsonProperty("air_date") val air_date: String?
)

data class TmdbSeasonDetail(
    val episodes: List<TmdbEpisode>?
)

data class TmdbDetailResponse(
    val id: Int?,
    val title: String?,
    val name: String?,
    @JsonProperty("original_title") val original_title: String?,
    @JsonProperty("original_name") val original_name: String?,
    val overview: String?,
    @JsonProperty("poster_path") val poster_path: String?,
    @JsonProperty("backdrop_path") val backdrop_path: String?,
    @JsonProperty("release_date") val release_date: String?,
    @JsonProperty("first_air_date") val first_air_date: String?,
    @JsonProperty("vote_average") val vote_average: Double?,
    val genres: List<TmdbGenre>?,
    val credits: TmdbCredits?,
    val recommendations: TmdbMainResponse?,
    val videos: TmdbVideoResponse?,
    val seasons: List<TmdbSeason>?,
    val images: TmdbImageResponse?,
    val status: String?
)

data class TmdbImageResponse(
    val logos: List<TmdbLogo>?
)

data class TmdbLogo(
    @JsonProperty("file_path") val file_path: String?,
    @JsonProperty("iso_639_1") val iso_639_1: String?
)

data class TmdbGenre(
    val id: Int?,
    val name: String?
)

data class TmdbCredits(
    val cast: List<TmdbCast>?
)

data class TmdbVideoResponse(
    val results: List<TmdbVideo>?
)

data class TmdbVideo(
    val key: String?,
    val site: String?,
    val type: String?
)

data class TmdbSeason(
    @JsonProperty("season_number") val season_number: Int?,
    @JsonProperty("episode_count") val episode_count: Int?,
    val name: String?,
    @JsonProperty("poster_path") val poster_path: String?
)


val tmdbkey = "c4ffcab48dfaa7b41625ac13d61aec31"
val tmdbbase = "https://api.themoviedb.org/3"
val tmdbimg500 = "https://image.tmdb.org/t/p/w500"
val tmdbimg1280 = "https://image.tmdb.org/t/p/w1280"
val tmdbimg185 = "https://image.tmdb.org/t/p/w185"

val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

val protectionHeaders = mapOf(
    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)

var dynamicCookies: Map<String, String> = mapOf(
    "PHPSESSID" to "",
    "dle_user_id" to "32729",
    "dle_password" to "894171c6a8dab18ee594d5c652009a35",
    "cf_clearance" to "",
    "dle_newpm" to "0",
    "viewed_ids" to "186,218,412,477,376,17,312,470,471"
)

val cloudflareKiller by lazy { CloudflareKiller() }
val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

class CloudflareInterceptor(private val cloudflarekiller: CloudflareKiller) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 403 || response.code == 503) {
            val doc = Jsoup.parse(response.peekBody(1024 * 1024).string())
            if (doc.html().contains("Just a moment") || doc.html().contains("cf-browser-verification")) {
                Log.d("ByAyzen_CinemaCity", "Cloudflare")
                response.close()
                return cloudflarekiller.intercept(chain)
            }
        }

        return response
    }
}



private fun getCurrentLocale(): String {
    return Locale.getDefault().toLanguageTag()
}

