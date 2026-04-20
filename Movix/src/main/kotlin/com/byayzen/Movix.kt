// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll

class Movix : MainAPI() {
    override var mainUrl = MovixHelper.dynamicurl
    override var name = "Movix"
    override val hasMainPage = true
    override var lang = "fr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "movie/28" to "Action",
        "movie/12" to "Aventure",
        "movie/16" to "Animation",
        "movie/35" to "Comédie",
        "movie/80" to "Crime",
        "movie/99" to "Documentaire",
        "movie/18" to "Drame",
        "movie/10751" to "Famille",
        "movie/14" to "Fantastique",
        "movie/36" to "Histoire",
        "movie/27" to "Horreur",
        "movie/10402" to "Musique",
        "movie/9648" to "Mystère",
        "movie/10749" to "Romance",
        "movie/878" to "Science-Fiction",
        "movie/10770" to "Téléfilm",
        "movie/53" to "Thriller",
        "movie/10752" to "Guerre",
        "movie/37" to "Western",
        "tv/10759" to "Action et Aventure",
        "tv/16" to "Animation TV",
        "tv/35" to "Comédie TV",
        "tv/80" to "Crime TV",
        "tv/99" to "Documentaire TV",
        "tv/18" to "Drame TV",
        "tv/10751" to "Famille TV",
        "tv/10762" to "Enfants",
        "tv/9648" to "Mystère TV",
        "tv/10763" to "Actualités",
        "tv/10764" to "Téléréalité",
        "tv/10765" to "SF et Fantastique",
        "tv/10766" to "Feuilleton",
        "tv/10767" to "Talk-show",
        "tv/10768" to "Guerre et Politique"
    )
    private fun TmdbResult.toMainPageResult(type: String): SearchResponse? {
        val titleText =
            this.title ?: this.name ?: this.original_title ?: this.original_name ?: return null
        val poster = (this.poster_path ?: this.backdrop_path)?.let { "$tmdbimg500$it" }
        return if (type == "movie") {
            newMovieSearchResponse(titleText, "$type/$id", TvType.Movie) {
                this.posterUrl = poster
                this.score = Score.from10(vote_average)
            }
        } else {
            newTvSeriesSearchResponse(titleText, "$type/$id", TvType.TvSeries) {
                this.posterUrl = poster
                this.score = Score.from10(vote_average)
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val type = if (request.data.startsWith("movie")) "movie" else "tv"
        val genreid = request.data.split("/").last()
        val url =
            "$tmdbbase/discover/$type?api_key=$tmdbkey&language=$tmdblang&with_genres=$genreid&page=$page&sort_by=popularity.desc&include_adult=false"
        val response = app.get(url).parsed<TmdbMainResponse>()
        val home = response.results.mapNotNull { it.toMainPageResult(type) }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url =
            "$tmdbbase/search/multi?api_key=$tmdbkey&query=$query&language=$tmdblang&page=$page"
        val res = app.get(url).parsed<TmdbMainResponse>()
        val results = res.results.mapNotNull {
            val type = it.media_type ?: return@mapNotNull null
            if (type == "person") return@mapNotNull null
            it.toMainPageResult(type)
        }
        return newSearchResponseList(results, hasNext = page < (res.total_pages ?: 0))
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val id = url.split("/").last()
        val type = if (url.contains("movie")) "movie" else "tv"
        val currentlang = tmdblang.split("-").first()

        val tmdburl =
            "$tmdbbase/$type/$id?api_key=$tmdbkey&language=$tmdblang&append_to_response=credits,recommendations,videos,images&include_image_language=$currentlang,en,null"
        val res = app.get(tmdburl).parsed<TmdbDetailResponse>()

        val titleText =
            res.title ?: res.name ?: res.original_title ?: res.original_name ?: return null
        val poster = res.poster_path?.let { "$tmdbimg500$it" }
        val bg = res.backdrop_path?.let { "$tmdbimg1280$it" }

        val logo = res.images?.logos?.let { logos ->
            logos.firstOrNull { it.iso_639_1 == currentlang }?.file_path
                ?: logos.firstOrNull { it.iso_639_1 == "en" }?.file_path
                ?: logos.firstOrNull()?.file_path
        }?.let { "$tmdbimg500$it" }

        val yearText = (res.release_date ?: res.first_air_date)?.split("-")?.first()?.toIntOrNull()
        val actors = res.credits?.cast?.mapNotNull {
            Actor(
                it.name ?: return@mapNotNull null,
                it.profile_path?.let { p -> "$tmdbimg185$p" }) to it.character
        } ?: emptyList()

        val trailer =
            res.videos?.results?.firstOrNull { it.site == "YouTube" && (it.type == "Trailer" || it.type == "Teaser") }
                ?.let {
                    "https://www.youtube.com/embed/${it.key}"
                }

        return if (type == "movie") {
            newMovieLoadResponse(titleText, url, TvType.Movie, url) {
                this.posterUrl = poster; this.backgroundPosterUrl = bg; this.logoUrl = logo
                this.plot = res.overview; this.year = yearText
                this.tags = res.genres?.mapNotNull { it.name }
                this.score = Score.from10(res.vote_average)
                this.recommendations =
                    res.recommendations?.results?.mapNotNull { it.toMainPageResult(type) }
                addActors(actors); addTrailer(trailer)
            }
        } else {
            val episodes = res.seasons?.filter { (it.season_number ?: 0) > 0 }?.flatMap { s ->
                val sn = s.season_number ?: 0
                val seasonurl = "$tmdbbase/tv/$id/season/$sn?api_key=$tmdbkey&language=$tmdblang"
                app.get(seasonurl).parsed<TmdbSeasonDetail>().episodes?.map { e ->
                    newEpisode("$type/$id-$sn-${e.episode_number}") {
                        this.name = e.name; this.season = sn; this.episode = e.episode_number
                        this.description = e.overview; this.score = Score.from10(e.vote_average)
                        this.runTime = e.runtime; this.posterUrl =
                        e.still_path?.let { "$tmdbimg500$it" } ?: poster
                        this.date = e.air_date?.let {
                            try {
                                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                    .parse(it)?.time
                            } catch (ex: Exception) {
                                null
                            }
                        }
                    }
                } ?: emptyList()
            } ?: emptyList()

            newTvSeriesLoadResponse(titleText, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster; this.backgroundPosterUrl = bg; this.logoUrl = logo
                this.showStatus = when (res.status) {
                    "Returning Series", "In Production", "Planned" -> ShowStatus.Ongoing;
                    "Canceled", "Ended" -> ShowStatus.Completed; else -> null
                }
                this.plot = res.overview; this.year = yearText
                this.tags = res.genres?.mapNotNull { it.name }
                this.score = Score.from10(res.vote_average)
                this.recommendations =
                    res.recommendations?.results?.mapNotNull { it.toMainPageResult(type) }
                addActors(actors); addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = coroutineScope {
        val domainsilici = mainUrl.removePrefix("https://").removePrefix("http://").removeSuffix("/")
        val apibase = "https://api.$domainsilici/api"
        val parts = data.split("-")
        val rawpath = parts[0]
        val id = rawpath.substringAfterLast("/")
        val type = if (rawpath.contains("movie")) "movie" else "tv"
        val season = parts.getOrNull(1)
        val episode = parts.getOrNull(2)
        val query = if (type == "tv" && season != null && episode != null) "?season=$season&episode=$episode" else ""

        Log.d("Movix", "$data $id $type $query")

        val apiheaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:149.0) Gecko/20100101 Firefox/149.0",
            "Origin" to mainUrl,
            "Referer" to "$mainUrl/"
        )

        val requests = mutableListOf<Pair<String, String>>()
        requests.add("Movix" to "$apibase/links/$type/$id$query")
        requests.add("MovixTmdb" to "$apibase/tmdb/$type/$id$query")
        requests.add("IMDB" to "$apibase/imdb/$type/$id")

        if (type == "movie") {
            requests.add("FStream" to "$apibase/fstream/$type/$id")
            requests.add("Wiflix" to "$apibase/wiflix/$id")
            requests.add("Cpasmal" to "$apibase/cpasmal/$type/$id")
            requests.add("Purstream" to "$apibase/purstream/movie/$id/stream")
        } else {
            requests.add("FStream" to "$apibase/fstream/$type/$id/season/$season")
            requests.add("Wiflix" to "$apibase/wiflix/$id/$season")
            requests.add("Cpasmal" to "$apibase/cpasmal/$type/$id/season/$season/episode/$episode")
            requests.add("Purstream" to "$apibase/purstream/tv/$id/stream$query")
        }

        requests.map { (brandname, targeturl) ->
            async {
                try {
                    Log.d("Movix", targeturl)
                    val response = app.get(targeturl, headers = apiheaders, timeout = 15).text

                    if (isvalidresponse(response)) {
                        if (brandname == "Purstream") {
                            parsepurstream(response, callback)
                        } else {
                            val links = extractlinksfromraw(response, targeturl, type, episode)
                            processlinks(brandname, links, subtitleCallback, callback)
                        }
                    }
                } catch (e: Exception) {
                    Log.d("Movix", e.message.toString())
                }
            }
        }.awaitAll()

        launch {
            videolinks(apibase, type, id, season, episode, query, apiheaders, callback)
        }

        true
    }

    private fun isvalidresponse(response: String): Boolean {
        val keywords = listOf("\"success\":true", "player_links", "iframe_src", "\"series\":", "\"sources\":", "\"players\":", "\"links\":")
        return keywords.any { response.contains(it) }
    }

    private suspend fun parsepurstream(response: String, callback: (ExtractorLink) -> Unit) {
        AppUtils.tryParseJson<MovixPurstreamResponse>(response)?.sources?.forEach { source ->
            source.url?.let { link ->
                if (link.isNotBlank()) {
                    val linktype = if (link.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    val qualityval = source.name?.filter { it.isDigit() }?.toIntOrNull() ?: Qualities.Unknown.value

                    val newlink = newExtractorLink("Purstream", "Purstream", link, linktype) {
                        this.quality = qualityval
                    }
                    callback(newlink)
                }
            }
        }
    }

    private suspend fun videolinks(
        apibase: String, type: String, id: String, season: String?, episode: String?,
        query: String, apiheaders: Map<String, String>, callback: (ExtractorLink) -> Unit
    ) {
        try {
            val ismovie = type == "movie"
            val cpasurl = if (ismovie) "$apibase/cpasmal/$type/$id" else "$apibase/cpasmal/$type/$id/season/$season/episode/$episode"
            val infores = app.get(cpasurl, headers = apiheaders, timeout = 15).text

            val titlematch = Regex("\"(?:title|name)\"\\s*:\\s*\"([^\"]+)\"")
            var title = titlematch.find(infores)?.groupValues?.get(1)

            if (title == null) {
                val tmdbres = app.get("$apibase/tmdb/$type/$id$query", headers = apiheaders, timeout = 15).text
                title = titlematch.find(tmdbres)?.groupValues?.get(1)
            }

            if (title.isNullOrBlank()) return

            val encodedtitle = java.net.URLEncoder.encode(title, "UTF-8")
            val searchres = app.get("$apibase/search?title=$encodedtitle", headers = apiheaders, timeout = 15).text
            val downloadid = Regex("\"id\"\\s*:\\s*(\\d+)").find(searchres)?.groupValues?.get(1) ?: return

            val downloadurl = if (ismovie) "$apibase/films/download/$downloadid" else "$apibase/series/download/$downloadid/season/$season/episode/$episode"
            Log.d("Movix", downloadurl)

            val dlres = app.get(downloadurl, headers = apiheaders, timeout = 15).text
            AppUtils.tryParseJson<MovixDownloadResponse>(dlres)?.sources?.forEach { source ->
                val link = source.m3u8 ?: source.src

                if (!link.isNullOrBlank()) {
                    val langname = source.language ?: ""
                    val finalname = if (langname.isNotBlank()) "Movix - $langname" else "Movix"
                    val linktype = if (link.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    val qualityval = source.quality?.filter { it.isDigit() }?.toIntOrNull() ?: Qualities.Unknown.value

                    val newlink = newExtractorLink("Movix", finalname, link, linktype) {
                        this.quality = qualityval
                        this.headers = mapOf()
                        this.referer = ""
                    }
                    callback(newlink)
                }
            }
        } catch (e: Exception) {
            Log.d("Movix", e.message.toString())
        }
    }

    private fun extractlinksfromraw(response: String, url: String, type: String, episode: String?): List<String> {
        val extracted = mutableListOf<String>()

        if (response.contains("player_links") || response.contains("iframe_src")) {
            AppUtils.tryParseJson<MovixTmdbResponse>(response)?.let {
                it.player_links?.forEach { p -> p.decoded_url?.let { u -> extracted.add(u) } }
                it.current_episode?.player_links?.forEach { p -> p.decoded_url?.let { u -> extracted.add(u) } }
                it.iframe_src?.let { src -> extracted.add(src) }
                it.current_episode?.iframe_src?.let { src -> extracted.add(src) }
            }
        }

        when {
            url.contains("/api/links/") -> {
                if (type == "movie") {
                    AppUtils.tryParseJson<MovixMovieLinksResponse>(response)?.data?.links?.let { extracted.addAll(it) }
                } else {
                    AppUtils.tryParseJson<MovixTvLinksResponse>(response)?.data?.forEach { it.links?.let { l -> extracted.addAll(l) } }
                }
            }
            url.contains("/api/cpasmal/") || (response.contains("\"players\":") && !url.contains("/imdb/")) -> {
                AppUtils.tryParseJson<CpasmalRes>(response.replace("\"players\":", "\"links\":"))?.links?.values?.flatten()?.forEach { it.url?.let { u -> extracted.add(u) } }
            }
            url.contains("/api/imdb/") -> {
                AppUtils.tryParseJson<MovixImdbResponse>(response)?.series?.forEach { series ->
                    series.seasons?.forEach { season ->
                        season.episodes?.forEach { ep ->
                            if (episode == null || ep.number == episode) {
                                ep.versions?.values?.forEach { version ->
                                    version.players?.forEach { it.link?.let { u -> extracted.add(u) } }
                                }
                            }
                        }
                    }
                }
            }
            url.contains("/api/fstream/") -> {
                AppUtils.tryParseJson<MovixFstreamResponse>(response)?.let {
                    if (type == "movie") {
                        it.links?.values?.flatten()?.forEach { link -> link.url?.let { u -> extracted.add(u) } }
                    } else {
                        val epmap = it.episodes
                        if (episode != null && epmap?.containsKey(episode) == true) {
                            epmap[episode]?.languages?.values?.flatten()?.forEach { link -> link.url?.let { u -> extracted.add(u) } }
                        } else {
                            epmap?.values?.forEach { e ->
                                e.languages?.values?.flatten()?.forEach { link -> link.url?.let { u -> extracted.add(u) } }
                            }
                        }
                    }
                }
            }
        }
        return extracted.distinct().filter { it.isNotBlank() }
    }

    private suspend fun processlinks(
        brand: String, links: List<String>,
        subtitlecallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit
    ) {
        if (links.isEmpty()) return
        Log.d("Movix", "$brand ${links.size}")

        val cleanbrand = if (brand.contains("Movix")) "Movix" else brand

        links.forEach { link ->
            Log.d("Movix", link)
            if (link.contains("kokoflix.lol") || link.contains("kakaflix.lol")) {
                val redirectedurl = app.get(link, referer = mainUrl, timeout = 10).url
                Log.d("Movix", redirectedurl)
                loadcustomextractor(cleanbrand, redirectedurl, link, subtitlecallback, callback)
            } else {
                loadcustomextractor(cleanbrand, link, mainUrl, subtitlecallback, callback)
            }
        }
    }
    }