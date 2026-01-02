package com.byayzen

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.nicehttp.NiceResponse
import org.json.JSONArray
import java.net.URLDecoder

class CinemaCity : MainAPI() {
    override var mainUrl = "https://cinemacity.cc"
    override var name = "CinemaCity"
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Cartoon)

    private var dynamicCookies: Map<String, String> = mapOf(
        "PHPSESSID" to "gfec15qbgl66cd1c89gqi312v5",
        "dle_user_id" to "32729",
        "dle_password" to "894171c6a8dab18ee594d5c652009a35",
        "cf_clearance" to "a2.2HChmMb0XVPm0AeD_NTy809V87d45vFJxyTwBOXM-1766577937-1.2.1.1-p5MWgaYjWYrbP71mLZUjRx0jFmXTofKM8TZQyebG1ai_c_4HlbAKubVDiIjLDVAjERu8msILlu1dlsa8LGKB0jxv3kKws0PdhSxYkLm2zPGe0dVpNHWl_lwRCMA23gR7054fFCwJYhS7VECu0h5J08RO8xv79VjePIbBU2J7WwTHdC7M06RYffFYqofwTz.Qi9pPqa114TJiR5yDL3cc498o98_Zh9tXcdbzgLYYUSg",
        "dle_newpm" to "0",
        "viewed_ids" to "186,218,412,477,376,17,312,470,471"
    )

    private val protectionHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/movies/" to "Movies",
        "$mainUrl/tv-series/" to "Series",
        "$mainUrl/xfsearch/genre/animation/" to "Animation",
        "$mainUrl/xfsearch/genre/documentary/" to "Documentary"
    )

    private suspend fun doRequest(url: String): NiceResponse {
        return app.get(
            url,
            headers = protectionHeaders + ("Referer" to "$mainUrl/"),
            cookies = dynamicCookies
        ).also {
            if (it.cookies.isNotEmpty()) dynamicCookies = dynamicCookies + it.cookies
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val base = request.data.trimEnd('/')
        val url = if (page > 1) "$base/page/$page/" else "$base/"

        val doc = doRequest(url).document
        val items = doc.select("div.dar-short_item").mapNotNull { it.toSearchResult() }
        val hasNext = doc.select("a[href*='/page/'], .pnext, .next").isNotEmpty()

        return newHomePageResponse(listOf(HomePageList(request.name, items)), hasNext)
    }




    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = "$mainUrl/index.php?do=search&subaction=search&search_start=$page&full_search=0&story=$query"

        val doc = doRequest(url).document
        val results = doc.select("div.dar-short_item").mapNotNull { it.toSearchResult() }

        val hasNext = doc.select(".pnext, a:contains(Next), a:contains(İleri), a[href*='search_start=']").isNotEmpty()
                || results.size >= 10

        Log.d("kraptor_$name", "Arama Sayfası: $page, URL: $url, Sonuç: ${results.size}")

        return newSearchResponseList(results, hasNext)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val link = this.select("a").firstOrNull {
            val h = it.attr("href")
            (h.contains("/movies/") || h.contains("/tv-series/")) && !h.contains(Regex("\\.(webp|jpg|png)"))
        } ?: return null

        val title = link.text().split(" (", " S0", " -")[0].trim()
        val href = fixUrlNull(link.attr("href")) ?: return null
        val poster = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val isTv = href.contains("/tv-series/") || link.text().contains(" S0", true)
        val score = this.selectFirst("span.rating-color")?.text()
        val date  = this.selectFirst("span a[href*=year]")?.text()?.toIntOrNull()

        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
                this.score = Score.from10(score)
                this.year = date
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
                this.score = Score.from10(score)
                this.year = date
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val response = doRequest(url)
        val doc = response.document

        val title = doc.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(doc.selectFirst("div.dar-full_poster img")?.attr("src"))
        val desc = doc.selectFirst("div.ta-full_text1")?.text()?.trim()
        val score = doc.selectFirst("div.dar-full_meta span.rating-color")?.text()
        val tags = doc.select("div.dar-full_meta span a").map { it.text() }
        val recommend = doc.select("div.ta-rel div.ta-rel_item").mapNotNull { it.toSearchResult() }

        val evalScript = doc.select("script:containsData(atob)")

        var isTvSeries = false
        val episodes = mutableListOf<Episode>()

        evalScript.forEach { script ->
            val scriptData = script.data().substringAfter("eval(atob(\"").substringBeforeLast("\"))")
            val sifreCoz = base64Decode(scriptData)

            val fileRegex = """file:'(\[.*?\])'""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val jsonStr = fileRegex.find(sifreCoz)?.groupValues?.get(1)?.replace("\\/", "/") ?: return@forEach

            try {
                val jsonArray = JSONArray(jsonStr)

                for (seasonIndex in 0 until jsonArray.length()) {
                    val seasonItem = jsonArray.getJSONObject(seasonIndex)
                    if (seasonItem.has("folder")) {
                        isTvSeries = true
                        val seasonTitle = seasonItem.optString("title", "Season ${seasonIndex + 1}")
                        val seasonNum = seasonTitle.filter { it.isDigit() }.toIntOrNull() ?: (seasonIndex + 1)
                        val folderArray = seasonItem.getJSONArray("folder")

                        for (i in 0 until folderArray.length()) {
                            val ep = folderArray.getJSONObject(i)
                            val epTitle = ep.optString("title", "Episode ${i + 1}")
                            val epNum = epTitle.filter { it.isDigit() }.toIntOrNull() ?: (i + 1)

                            val episodeData = Video(ep.getString("file"), ep.optString("subtitle", "")).toStringData()

                            episodes.add(
                                newEpisode(episodeData) {
                                    this.name = epTitle
                                    this.season = seasonNum
                                    this.episode = epNum
                                }
                            )
                        }
                    } else {
                        val movieData = Video(seasonItem.getString("file"), seasonItem.optString("subtitle", "")).toStringData()
                        episodes.add(
                            newEpisode(movieData) {
                                this.name = seasonItem.optString("title", "Movie")
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return if (isTvSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = desc
                this.tags = tags
                this.recommendations = recommend
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, episodes) {
                this.posterUrl = poster
                this.plot = desc
                this.score = Score.from10(score)
                this.tags = tags
                this.recommendations = recommend
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        Log.d("kraptor_Cinema","json = $data")

        try {
            val json: Video = if (data.trimStart().startsWith("[")) {
                val jsonArray = mapper.readValue<List<VideoWrapper>>(data)
                if (jsonArray.isEmpty()) {
                    Log.e("kraptor_Cinema", "Empty array")
                    return false
                }
                mapper.readValue<Video>(jsonArray[0].data)
            } else {
                mapper.readValue<Video>(data)
            }

            Log.d("kraptor_Cinema", "Parsed JSON = $json")

            val fileUrlRaw = json.url
            Log.d("kraptor_Cinema", "fileUrlRaw = $fileUrlRaw")

            val finalUrl = fileUrlRaw.trim()

            val subtitleStr = json.subtitles.split(",")

            subtitleStr.forEach { subtitle ->
                if (subtitle.contains("]")) {
                    val language = subtitle.substringBefore("]").substringAfter("[")
                        .replace("(Full)","").replace("(SDH)","")
                    val url = subtitle.substringAfter("]").trim()
                    subtitleCallback.invoke(newSubtitleFile(language, url))
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
            Log.e("kraptor_Cinema", "Error parsing JSON", e)
            return false
        }

        return true
    }
}

private fun Any.toStringData(): String {
    return mapper.writeValueAsString(this)
}

data class VideoWrapper(
    val data: String
)


data class Video(
    val url: String,
    val subtitles: String
)