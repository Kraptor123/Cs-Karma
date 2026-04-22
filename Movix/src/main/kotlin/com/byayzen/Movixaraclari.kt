package com.byayzen

import com.lagradost.api.Log
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.lagradost.cloudstream3.app
import android.content.Context


object MovixHelper {
    var dynamicurl = "https://www.movix.cash"
    val prefname = "movix_prefs"
    val domainkey = "movix_domain"

    suspend fun updatemainurl() {
        val prefs = CloudStreamApp.context?.getSharedPreferences(prefname, Context.MODE_PRIVATE)
        val savedurl = prefs?.getString(domainkey, null)
        if (savedurl != null && !savedurl.contains("movix.health")) {
            try {
                val checkresponse = app.get(savedurl, timeout = 5)
                if (checkresponse.code in 200..299) {
                    dynamicurl = savedurl
                    Log.d("MovixHelper", "$dynamicurl")
                    return
                }
            } catch (e: Exception) {
            }
        }

        try {
            val healthresponse = app.get("https://www.movix.health/")
            val healthtext = healthresponse.text
            val regex = """Accéder à\s+([a-zA-Z0-9.-]+\.[a-z]{2,})""".toRegex()
            val match = regex.find(healthtext)

            if (match != null) {
                val extractedDomain = "https://" + match.groupValues[1].trim()
                if (!extractedDomain.contains("movix.health")) {
                    dynamicurl = extractedDomain
                    prefs?.edit()?.putString(domainkey, dynamicurl)?.apply()
                    Log.d("MovixHelper", " $dynamicurl")
                }
            }
        } catch (e: Exception) {
            Log.d("MovixHelper", " ${e.message}")
        }
    }
}

const val tmdbkey  = "f3d757824f08ea2cff45eb8f47ca3a1e" // Website's own api key.
const val tmdblang = "fr-FR"
const val tmdbbase = "https://api.themoviedb.org/3"
const val tmdbimg500  = "https://image.tmdb.org/t/p/w500"
const val tmdbimg1280 = "https://image.tmdb.org/t/p/w1280"
const val tmdbimg185  = "https://image.tmdb.org/t/p/w185"

data class DownloadSource(val src: String?, val quality: String?, val language: String?, val m3u8: String?)

data class GenericSource(
    val url: String?
)

data class CpasmalRes(
    val links: Map<String, List<GenericSource>>?
)

data class FstreamEpisode(val languages: Map<String, List<FstreamLink>>?)
data class MovixMovieLinksResponse(
    val data: MovixLinkData?
)

data class MovixTvLinksResponse(
    val data: List<MovixLinkData>?
)

data class MovixLinkData(
    val links: List<String>?
)

data class MovixTmdbResponse(
    val iframe_src      : String?               = null,
    val player_links    : List<MovixPlayerLink>? = null,
    val current_episode : MovixCurrentEpisode?  = null
)

data class MovixCurrentEpisode(
    val iframe_src   : String?               = null,
    val player_links : List<MovixPlayerLink>? = null
)

data class MovixPlayerLink(
    val decoded_url: String? = null
)

data class TmdbMainResponse(
    val results       : List<TmdbResult>,
    val page          : Int?,
    val total_pages   : Int?,
    val total_results : Int?
)

data class TmdbResult(
    val id             : Int?,
    val title          : String?,
    val name           : String?,
    val original_title : String?,
    val original_name  : String?,
    val poster_path    : String?,
    val backdrop_path  : String?,
    val media_type     : String?,
    val vote_average   : Double?
)

data class TmdbCast(
    val name         : String?,
    val profile_path : String?,
    val character    : String?
)

data class TmdbEpisode(
    val episode_number : Int?,
    val season_number  : Int?,
    val name           : String?,
    val overview       : String?,
    val still_path     : String?,
    val vote_average   : Double?,
    val runtime        : Int?,
    val air_date       : String?
)

data class TmdbSeasonDetail(
    val episodes: List<TmdbEpisode>?
)

data class TmdbDetailResponse(
    val id              : Int?,
    val title           : String?,
    val name            : String?,
    val original_title  : String?,
    val original_name   : String?,
    val overview        : String?,
    val poster_path     : String?,
    val backdrop_path   : String?,
    val release_date    : String?,
    val first_air_date  : String?,
    val vote_average    : Double?,
    val genres          : List<TmdbGenre>?,
    val credits         : TmdbCredits?,
    val recommendations : TmdbMainResponse?,
    val videos          : TmdbVideoResponse?,
    val seasons         : List<TmdbSeason>?,
    val images          : TmdbImageResponse?,
    val status          : String?
)

data class TmdbImageResponse(
    val logos: List<TmdbLogo>?
)

data class TmdbLogo(
    val file_path : String?,
    val iso_639_1 : String?
)

data class TmdbGenre(
    val id   : Int?,
    val name : String?
)

data class TmdbCredits(
    val cast: List<TmdbCast>?
)

data class TmdbVideoResponse(
    val results: List<TmdbVideo>?
)

data class TmdbVideo(
    val key  : String?,
    val site : String?,
    val type : String?
)

data class TmdbSeason(
    val season_number : Int?,
    val episode_count : Int?,
    val name          : String?,
    val poster_path   : String?
)

data class MovixDownloadResponse(val sources: List<DownloadSource>?)


data class MovixImdbResponse(val series: List<ImdbSeries>?)
data class ImdbSeries(val seasons: List<ImdbSeason>?)
data class ImdbSeason(val episodes: List<ImdbEpisode>?)
data class ImdbEpisode(val number: String?, val versions: Map<String, ImdbVersion>?)
data class ImdbVersion(val players: List<ImdbPlayer>?)
data class ImdbPlayer(val link: String?)

data class MovixFstreamResponse(val links: Map<String, List<FstreamLink>>?, val episodes: Map<String, FstreamEpisode>?)

data class FstreamLink(val url: String?)

data class MovixPurstreamResponse(val sources: List<PurstreamSource>?)
data class PurstreamSource(val url: String?, val name: String?, val format: String?)



suspend fun loadcustomextractor(
    brand: String,
    url: String,
    referer: String,
    subtitlecallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
) = coroutineScope {
    try {
        if (url.contains(".m3u8") || url.contains(".mp4") || url.contains(".mkv")) {
            val ism3u8 = url.contains(".m3u8")
            callback.invoke(
                newExtractorLink(
                    source = brand,
                    name = brand,
                    url = url,
                    type = if (ism3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) { this.referer = referer }
            )
            return@coroutineScope
        }

        loadExtractor(url, referer, subtitlecallback) { link ->
            launch {
                callback.invoke(
                    newExtractorLink(
                        brand,
                        if (link.name.isBlank()) brand else "$brand - ${link.name}",
                        link.url,
                    ) {
                        this.quality = link.quality
                        this.type = link.type
                        this.referer = link.referer
                        this.headers = link.headers
                        this.extractorData = link.extractorData
                    }
                )
            }
        }
    } catch (e: Exception) {
        Log.d("Movix", e.message.toString())
    }
}