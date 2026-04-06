// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

class Monochrome : MainAPI() {
    override var mainUrl        = "https://monochrome.samidy.com"
    override var name           = "Monochrome"
    override val hasMainPage    = true
    override var lang           = "en"
    override val supportedTypes = setOf(TvType.Music, TvType.Others)

    override val mainPage = mainPageOf(
        "editors-picks.json"                                                   to "Editors Choice",
        "album/similar/?id=5943890"                                            to "Similar Albums",
        "v1/artists/35937/albums?countryCode=US&limit=50&filter=EPSANDSINGLES" to "Singles"
    )

    private var tidaltoken: String? = null

    private fun getposterurl(uuid: String?): String? {
        if (uuid.isNullOrEmpty()) return null
        if (uuid.startsWith("http")) return uuid
        val formattedpath = uuid.replace("-", "/")
        return "https://resources.tidal.com/images/$formattedpath/640x640.jpg"
    }

    private suspend fun gettidaltoken(): String {
        tidaltoken?.let { return it }
        val response = app.post(
            url     = "https://auth.tidal.com/v1/oauth2/token",
            headers = mapOf(
                "authorization" to "Basic dHhOb0g0a2tWNDFNZkgyNTpkUWp5ME1pbkNFdnhpMU80VW14dnhXbkRqdDRjZ0hCUHc4bGw2bllCazk4PQ==",
                "content-type"  to "application/x-www-form-urlencoded",
                "origin"        to mainUrl,
                "referer"       to "$mainUrl/"
            ),
            data    = mapOf(
                "client_id"     to "txNoH4kkV41MfH25",
                "client_secret" to "dQjy0MinCEvxi1O4UmxvxWnDjt4cgHBPw8ll6nYBk98=",
                "grant_type"    to "client_credentials"
            )
        ).parsedSafe<TokenResponse>()
        tidaltoken = response?.accesstoken ?: ""
        return tidaltoken!!
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = mutableListOf<SearchResponse>()
        val token = gettidaltoken()

        if (request.data.startsWith("v1/")) {
            val targeturl = "https://api.tidal.com/${request.data}"
            val response  = app.get(targeturl, headers = mapOf("authorization" to "Bearer $token")).parsedSafe<TidalSearchResponse>()
            response?.items?.forEach { item ->
                items.add(newTvSeriesSearchResponse(item.title ?: "", "album|${item.id}", TvType.Others) { this.posterUrl = getposterurl(item.cover) })
            }
        } else if (request.data.contains("similar")) {
            val response = app.get("https://api.monochrome.tf/${request.data}").parsedSafe<SimilarResponse>()
            response?.albums?.forEach { album ->
                items.add(newTvSeriesSearchResponse(album.title ?: "", "album|${album.id}", TvType.Others) { this.posterUrl = getposterurl(album.cover) })
            }
        } else {
            val responseText = app.get("$mainUrl/${request.data}").text
            val picks = parseJson<Array<EditorPick>>(responseText).toList()
            picks.forEach { pick ->
                items.add(newTvSeriesSearchResponse(pick.title ?: "", "album|${pick.id}", TvType.Others) { this.posterUrl = getposterurl(pick.cover) })
            }
        }
        return newHomePageResponse(request.name, items, hasNext = false)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val token     = gettidaltoken()
        val limit     = 25
        val offset    = (page - 1) * limit
        val searchurl = "https://api.tidal.com/v1/search?query=$query&limit=$limit&offset=$offset&types=ALBUMS&countryCode=US"
        val response  = app.get(searchurl, headers = mapOf("authorization" to "Bearer $token", "origin" to mainUrl, "referer" to "$mainUrl/")).parsedSafe<TidalSearchResponseWrapper>()
        val searchresults = response?.albums?.items?.map { album ->
            newTvSeriesSearchResponse(album.title ?: "", "album|${album.id}", TvType.Others) { this.posterUrl = getposterurl(album.cover) }
        } ?: emptyList()
        return newSearchResponseList(searchresults, hasNext = searchresults.size == limit)
    }

    override suspend fun load(url: String): LoadResponse {
        val token     = gettidaltoken()
        val id        = url.split("|").last()
        val albumurl  = "https://api.tidal.com/v1/albums/$id?countryCode=US"
        val tracksurl = "https://api.tidal.com/v1/albums/$id/tracks?countryCode=US"
        val recurl    = "https://hifi.geeked.wtf/recommendations/?id=$id"

        val albumdata = app.get(albumurl, headers = mapOf("authorization" to "Bearer $token")).parsedSafe<AlbumDetails>()
        val trackdata = app.get(tracksurl, headers = mapOf("authorization" to "Bearer $token")).parsedSafe<TrackListResponse>()
        val recdata   = app.get(recurl).parsedSafe<RecommendationResponse>()

        val albumposter = getposterurl(albumdata?.cover)
        val episodes    = trackdata?.items?.map { track ->
            newEpisode(track.id) {
                this.name      = track.title
                this.episode   = track.trackNumber
                this.posterUrl = albumposter
            }
        } ?: emptyList()

        return newTvSeriesLoadResponse(albumdata?.title ?: "", url, TvType.Others, episodes) {
            this.posterUrl       = albumposter
            this.plot            = "Artist: ${albumdata?.artist?.name}\nQuality: ${albumdata?.mediametadata?.tags?.joinToString(" / ")}"
            this.recommendations = recdata?.data?.items?.mapNotNull { rec ->
                val track = rec.track ?: return@mapNotNull null
                newTvSeriesSearchResponse(track.title ?: "", "album|${track.album?.id}", TvType.Music) { this.posterUrl = getposterurl(track.album?.cover) }
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("monochrome_$name", "data = $data")
        val trackid        = data.split("/").last()
        val manifestapiurl = "https://hifi-two.spotisaver.net/trackManifests/?id=$trackid&formats=HEAACV1&formats=AACLC&formats=FLAC_HIRES&formats=FLAC&adaptive=true&manifestType=MPEG_DASH&uriScheme=HTTPS&usage=PLAYBACK"
        val response       = app.get(
            url     = manifestapiurl,
            referer = "$mainUrl/",
            headers = mapOf("origin" to mainUrl,
                "accept" to
                 "application/json")
        )
        val json    = response.parsedSafe<TidalManifestJsonResponse>()
        val dashurl = json?.data?.data?.attributes?.uri ?: return false

        callback.invoke(
            newExtractorLink(
                source = name,
                name   = name,
                url    = dashurl,
                type   = ExtractorLinkType.DASH
            ) {
                this.referer = "$mainUrl/"
            }
        )
        return true
    }
}