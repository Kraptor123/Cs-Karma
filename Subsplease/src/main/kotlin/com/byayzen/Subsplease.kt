// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import org.json.JSONObject
import org.json.JSONArray
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson


class Subsplease : MainAPI() {
    override var mainUrl = "https://subsplease.org"
    override var name = "Subsplease"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Anime)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        mainUrl to "Newly Added",
    )


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = app.get("$mainUrl/api/?f=latest&tz=Europe/Istanbul&p=$page").text
        val json = parseJson<Map<String, LatestResponse>>(res)

        val home = json.map { (key, value) ->
            val fullUrl = "$mainUrl/shows/${value.page?.trim('/')}/"
            newAnimeSearchResponse("${value.show ?: key} - ${value.episode}", fullUrl, TvType.Anime) {
                this.posterUrl = if (value.image_url?.startsWith("http") == true) value.image_url else "$mainUrl${value.image_url}"
                this.otherName = "Bölüm ${value.episode}"
            }
        }
        return newHomePageResponse(request.name, home, true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get("https://subsplease.org/api/?f=search&tz=Europe/Istanbul&s=$query").text
        if (res.isBlank() || res == "[]") return emptyList()

        return parseJson<Map<String, LatestResponse>>(res).map { (key, value) ->
            val href = if (value.page?.startsWith("http") == true) value.page else "$mainUrl/shows/${value.page}/"
            newAnimeSearchResponse(value.show ?: key, href, TvType.Anime) {
                this.posterUrl = if (value.image_url?.startsWith("http") == true) value.image_url else "$mainUrl${value.image_url}"
                this.otherName = "Bölüm ${value.episode}"
            }
        }.distinctBy { it.url }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)


    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text() ?: return null
        val poster = fixUrlNull(document.selectFirst("div#secondary img")?.attr("src"))
        val synopsis = document.selectFirst("div.series-syn p")?.text()?.trim()

        val sid = document.selectFirst("table#show-release-table")?.attr("sid") ?: return null

        val headers = mapOf(
            "X-Requested-With" to "XMLHttpRequest",
            "Accept" to "application/json"
        )

        val apiUrl = "https://subsplease.org/api/?f=show&tz=Europe/Istanbul&sid=$sid"
        val jsonResponse = app.get(apiUrl, headers = headers).text
        val json = JSONObject(jsonResponse)

        val episodesObj = json.getJSONObject("episode")
        val episodesList = mutableListOf<Episode>()

        episodesObj.keys().forEach { key ->
            val epData = episodesObj.getJSONObject(key)
            val epString = epData.optString("episode")

            val epNum = epString.substringBefore("v").trim().toIntOrNull()
            if (epNum == null) return@forEach

            val downloadsArray = epData.getJSONArray("downloads")
            val episodeData = downloadsArray.toString()

            episodesList.add(
                newEpisode(url) {
                    this.name = "Episode $epString"
                    this.season = 1
                    this.episode = epNum
                    this.data = episodeData
                    this.date = null
                }
            )
        }

        episodesList.sortByDescending { it.episode }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.episodes = mutableMapOf(DubStatus.None to episodesList)
            this.posterUrl = poster
            this.plot = synopsis
            this.year = null
            this.tags = null
            this.score = null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val downloadsArray = JSONArray(data)

            for (i in downloadsArray.length() - 1 downTo 0) {
                val item = downloadsArray.getJSONObject(i)
                val res = item.optString("res")
                val magnet = item.optString("magnet")

                if (magnet.isNotEmpty()) {
                    callback.invoke(newExtractorLink(
                        source = "SubsPlease",
                        name   = "Subsplease $res",
                        url    = magnet,
                        type    = ExtractorLinkType.MAGNET,
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return true
    }
}

data class LatestResponse(
    val page: String? = null,
    val episode: String? = null,
    val show: String? = null,
    val image_url: String? = null
)