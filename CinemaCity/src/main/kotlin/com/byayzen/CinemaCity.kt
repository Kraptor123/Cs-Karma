package com.byayzen

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.nicehttp.NiceResponse
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import com.lagradost.cloudstream3.network.CloudflareKiller
import java.text.SimpleDateFormat
import java.util.Locale

class CinemaCity : MainAPI() {
    override var mainUrl = "https://cinemacity.cc"
    override var name = "CinemaCity"
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Cartoon)



    suspend fun doRequest(url: String): NiceResponse {
        val response = app.get(
            url,
            headers = protectionHeaders + ("Referer" to "$mainUrl/"),
            cookies = dynamicCookies,
            interceptor = interceptor
        )

        if (response.cookies.isNotEmpty()) {
            dynamicCookies = dynamicCookies + response.cookies
        }

        val cfheaders = cloudflareKiller.getCookieHeaders(url).toMap()
        val cookiestring = cfheaders["Cookie"] ?: cfheaders["cookie"] ?: ""
        val cfmatch = """cf_clearance=([^;]+)""".toRegex().find(cookiestring)

        if (cfmatch != null) {
            dynamicCookies = dynamicCookies + ("cf_clearance" to cfmatch.groupValues[1])
        }

        val currentcf = dynamicCookies["cf_clearance"]
        if (currentcf.isNullOrBlank()) {
            Log.d("ByAyzen_CinemaCity", "cf_clearance alinmamis")
        } else {
            Log.d("ByAyzen_CinemaCity", "cf_clearance alinmis")
        }

        return response
    }

    override val mainPage = mainPageOf(
        "$mainUrl/movies/" to "Movies",
        "$mainUrl/tv-series/" to "Series",
        "$mainUrl/xfsearch/genre/animation/" to "Animation",
        "$mainUrl/xfsearch/genre/documentary/" to "Documentary"
    )



    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val base = request.data.trimEnd('/')
        val url = if (page > 1) "$base/page/$page/" else "$base/"

        val doc = doRequest(url).document
        val items = doc.select("div.dar-short_item").mapNotNull { it.toSearchResult() }
        val hasNext = doc.select("a[href*='/page/'], .pnext, .next").isNotEmpty()

        return newHomePageResponse(listOf(HomePageList(request.name, items)), hasNext)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = "$mainUrl/engine/ajax/controller.php?mod=search"
        val formdata = mapOf(
            "query" to query,
            "skin" to "cinemacity",
            "user_hash" to "83f28aada1ce377b5f3441e0bf022e4e119a736d"
        )

        val doc = app.post(
            url,
            data = formdata,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:150.0) Gecko/20100101 Firefox/150.0",
                "X-Requested-With" to "XMLHttpRequest",
                "Origin" to mainUrl,
                "Referer" to "$mainUrl/"
            )
        ).document

        val results = doc.select("div.dle-fast_item").mapNotNull { it.toSearchResult() }

        Log.d("kraptor_$name", "${results.size}")

        return newSearchResponseList(results, false)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val link = this.select("a").firstOrNull {
            val h = it.attr("href")
            (h.contains("/movies/") || h.contains("/tv-series/")) && !h.contains(Regex("\\.(webp|jpg|png)"))
        } ?: return null

        val title = link.text().split(" (", " S0", " -")[0].trim()
        val href = fixUrlNull(link.attr("href")) ?: return null
        val poster = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val istv = href.contains("/tv-series/") || link.text().contains(" S0", true)
        val score = this.selectFirst("span.rating-color")?.text()?.toDoubleOrNull()
        val date = this.selectFirst("span a[href*=year]")?.text()?.toIntOrNull()

        Log.d("ayzenarama", "$title | $href")

        return if (istv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
                this.posterHeaders = mapOf("Referer" to "$mainUrl/")
                this.score = Score.from(score, 10)
                this.year = date
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
                this.posterHeaders = mapOf("Referer" to "$mainUrl/")
                this.score = Score.from(score, 10)
                this.year = date
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val response = doRequest(url)
        val doc = response.document

        val sitetitle = doc.selectFirst("h1")?.text()?.trim() ?: return null
        val siteposter = fixUrlNull(doc.selectFirst("div.dar-full_poster img")?.attr("src"))
        val sitedesc = doc.selectFirst("div.ta-full_text1")?.text()?.trim()
        val sitescore = doc.selectFirst("div.dar-full_meta span.rating-color")?.text()?.toDoubleOrNull()
        val sitetags = doc.select("div.dar-full_meta span a").map { it.text() }
        val siterecommendations = doc.select("div.ta-rel div.ta-rel_item").mapNotNull { it.toSearchResult() }

        val type = if (url.contains("movie")) "movie" else "tv"
        val tmdblang = getCurrentLocale()
        val currentlang = tmdblang.split("-").first()

        val imdbid = Regex("tt\\d+").find(doc.html())?.value
        var res: TmdbDetailResponse? = null
        var tmdbid: Int? = null

        if (imdbid != null) {
            val findurl = "$tmdbbase/find/$imdbid?api_key=$tmdbkey&external_source=imdb_id"
            try {
                val findrestext = app.get(findurl).text
                val jsonres = JSONObject(findrestext)

                tmdbid = if (type == "tv") {
                    jsonres.optJSONArray("tv_results")?.optJSONObject(0)?.optInt("id")
                } else {
                    jsonres.optJSONArray("movie_results")?.optJSONObject(0)?.optInt("id")
                }

                if (tmdbid != null && tmdbid != 0) {
                    val tmdburl = "$tmdbbase/$type/$tmdbid?api_key=$tmdbkey&language=$tmdblang&append_to_response=credits,videos,images&include_image_language=$currentlang,en,null"
                    res = app.get(tmdburl).parsedSafe<TmdbDetailResponse>()
                }
            } catch (e: Exception) {
                Log.d("ByAyzen_CinemaCity", "$e")
            }
        }

        val finaltitle = res?.title ?: res?.name ?: res?.original_title ?: res?.original_name ?: sitetitle
        val finalposter = res?.poster_path?.let { "$tmdbimg500$it" } ?: siteposter
        val finalbg = res?.backdrop_path?.let { "$tmdbimg1280$it" }
        val finallogo = res?.images?.logos?.let { logos ->
            logos.firstOrNull { it.iso_639_1 == currentlang }?.file_path
                ?: logos.firstOrNull { it.iso_639_1 == "en" }?.file_path
                ?: logos.firstOrNull()?.file_path
        }?.let { "$tmdbimg500$it" }

        val finalyear = (res?.release_date ?: res?.first_air_date)?.split("-")?.first()?.toIntOrNull()
        val finalplot = res?.overview?.takeIf { it.isNotBlank() } ?: sitedesc
        val finaltags = res?.genres?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() } ?: sitetags
        val finalscore = res?.vote_average ?: sitescore

        val finalactors = res?.credits?.cast?.mapNotNull {
            val actorname = it.name ?: return@mapNotNull null
            Actor(actorname, it.profile_path?.let { p -> "$tmdbimg185$p" }) to it.character
        } ?: emptyList()

        val finaltrailer = res?.videos?.results?.firstOrNull { it.site == "YouTube" && (it.type == "Trailer" || it.type == "Teaser") }?.let {
            "https://www.youtube.com/embed/${it.key}"
        }

        val mappedstatus = when (res?.status) {
            "Returning Series", "In Production", "Planned" -> ShowStatus.Ongoing
            "Canceled", "Ended" -> ShowStatus.Completed
            else -> null
        }

        val evalscript = doc.select("script:containsData(atob)")
        val episodes = mutableListOf<Episode>()

        evalscript.forEach { script ->
            val scriptdata = script.data().substringAfter("eval(atob(\"").substringBeforeLast("\"))")
            val sifrecoz = base64Decode(scriptdata)
            val fileregex = """file:'(\[.*?\])'""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val jsonstr = fileregex.find(sifrecoz)?.groupValues?.get(1)?.replace("\\/", "/") ?: return@forEach

            try {
                val jsonarray = JSONArray(jsonstr)
                for (seasonindex in 0 until jsonarray.length()) {
                    val seasonitem = jsonarray.getJSONObject(seasonindex)
                    if (seasonitem.has("folder")) {
                        val seasontitle = seasonitem.optString("title", "Season ${seasonindex + 1}")
                        val seasonnum = seasontitle.filter { it.isDigit() }.toIntOrNull() ?: (seasonindex + 1)
                        val folderarray = seasonitem.getJSONArray("folder")

                        for (i in 0 until folderarray.length()) {
                            val ep = folderarray.getJSONObject(i)
                            val eptitle = ep.optString("title", "Episode ${i + 1}")
                            val epnum = eptitle.filter { it.isDigit() }.toIntOrNull() ?: (i + 1)
                            val episodedata = mapper.writeValueAsString(Video(ep.getString("file"), ep.optString("subtitle", "")))

                            episodes.add(
                                newEpisode(episodedata) {
                                    this.name = eptitle
                                    this.season = seasonnum
                                    this.episode = epnum
                                    this.posterUrl = finalposter
                                }
                            )
                        }
                    } else {
                        val moviedata = mapper.writeValueAsString(Video(seasonitem.getString("file"), seasonitem.optString("subtitle", "")))
                        episodes.add(
                            newEpisode(moviedata) {
                                this.name = seasonitem.optString("title", "Movie")
                                this.posterUrl = finalposter
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                Log.d("ByAyzen_CinemaCity", "$e")
            }
        }

        return if (type == "movie") {
            newMovieLoadResponse(finaltitle, url, TvType.Movie, episodes) {
                this.posterUrl = finalposter
                this.backgroundPosterUrl = finalbg
                this.logoUrl = finallogo
                this.plot = finalplot
                this.year = finalyear
                this.tags = finaltags
                this.score = Score.from(finalscore, 10)
                this.recommendations = siterecommendations
                addActors(finalactors)
                addTrailer(finaltrailer)
            }
        } else {
            val finalepisodes = if (res?.seasons != null && tmdbid != null && tmdbid != 0) {
                res.seasons.filter { (it.season_number ?: 0) > 0 }.flatMap { s ->
                    val sn = s.season_number ?: 0
                    val seasonurl = "$tmdbbase/tv/$tmdbid/season/$sn?api_key=$tmdbkey&language=$tmdblang"
                    try {
                        app.get(seasonurl).parsedSafe<TmdbSeasonDetail>()?.episodes?.mapNotNull { e ->
                            val matchingepisode = episodes.firstOrNull { it.season == sn && it.episode == e.episode_number } ?: return@mapNotNull null

                            newEpisode(matchingepisode.data) {
                                this.name = e.name
                                this.season = sn
                                this.episode = e.episode_number
                                this.description = e.overview?.takeIf { it.isNotBlank() }
                                this.score = Score.from(e.vote_average, 10)
                                this.runTime = e.runtime
                                this.posterUrl = e.still_path?.let { "$tmdbimg500$it" } ?: finalposter
                            }
                        } ?: emptyList()
                    } catch (ex: Exception) {
                        emptyList()
                    }
                }
            } else {
                emptyList()
            }

            val actualepisodes = finalepisodes.ifEmpty { episodes }

            newTvSeriesLoadResponse(finaltitle, url, TvType.TvSeries, actualepisodes) {
                this.posterUrl = finalposter
                this.backgroundPosterUrl = finalbg
                this.logoUrl = finallogo
                this.showStatus = mappedstatus
                this.plot = finalplot
                this.year = finalyear
                this.tags = finaltags
                this.score = Score.from(finalscore, 10)
                this.recommendations = siterecommendations
                addActors(finalactors)
                addTrailer(finaltrailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        Log.d("kraptor_Cinema", data)

        try {
            val json: Video = if (data.trimStart().startsWith("[")) {
                val jsonArray = mapper.readValue<List<VideoWrapper>>(data)
                if (jsonArray.isEmpty()) {
                    return false
                }
                mapper.readValue<Video>(jsonArray[0].data)
            } else {
                mapper.readValue<Video>(data)
            }

            val fileUrlRaw = json.url
            val finalUrl = fileUrlRaw.trim()
            val subtitleStr = json.subtitles.split(",")

            subtitleStr.forEach { subtitle ->
                if (subtitle.contains("]")) {
                    val language = subtitle.substringBefore("]").substringAfter("[")
                        .replace("(Full)","").replace("(SDH)","")
                    val subUrl = subtitle.substringAfter("]").trim()
                    subtitleCallback.invoke(newSubtitleFile(language, subUrl))
                }
            }

            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = finalUrl,
                    type = if (finalUrl.contains(".m3u8")) {
                        ExtractorLinkType.M3U8
                    } else {
                        ExtractorLinkType.VIDEO
                    },
                    {
                        this.referer = "$mainUrl/"
                        this.quality = getQualityFromName(finalUrl)
                    }
                )
            )

        } catch (e: Exception) {
            Log.d("kraptor_Cinema", "$e")
            return false
        }

        return true
    }
}

