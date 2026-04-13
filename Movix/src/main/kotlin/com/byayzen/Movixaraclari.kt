package com.byayzen

const val tmdbkey  = "f3d757824f08ea2cff45eb8f47ca3a1e" // Website's own api key.
const val tmdblang = "fr-FR"
const val apibase  = "https://api.movix.blog/api"
const val tmdbbase = "https://api.themoviedb.org/3"
const val tmdbimg500  = "https://image.tmdb.org/t/p/w500"
const val tmdbimg1280 = "https://image.tmdb.org/t/p/w1280"
const val tmdbimg185  = "https://image.tmdb.org/t/p/w185"

data class WiflixTvResponse(
    val episodes: Map<String, Map<String, List<GenericSource>>>?
)

data class DownloadRes(
    val sources: List<DownloadSource>?
)

data class DownloadSource(val src: String?, val quality: String?, val language: String?, val m3u8: String?)

data class GenericSource(
    val url: String?
)

data class CpasmalRes(
    val links: Map<String, List<GenericSource>>?
)

data class FstreamTvRes(
    val episodes: Map<String, FstreamEpisode>?
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
