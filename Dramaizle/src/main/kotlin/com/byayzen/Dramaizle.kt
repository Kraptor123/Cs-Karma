// ! Bu araç @ByAyzen tarafından | @CS-Karma için yazılmıştır.

package com.byayzen

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.util.Locale

class DramaFlix : MainAPI() {
    override var mainUrl = "https://dramaflix.cc"
    override var name = "DramaFlix"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    private val api = "$mainUrl/api/series"

    override val mainPage = mainPageOf(
        "$mainUrl/api/series?language=TR" to "Newly Added",
        "$mainUrl/api/series?language=TR&platform=ShortMax" to "ShortMax",
        "$mainUrl/api/series?language=TR&platform=NetShort" to "NetShort",
        "$mainUrl/api/series?language=TR&platform=DramaBox" to "DramaBox",
        "$mainUrl/api/series?language=TR&platform=DramaWave" to "DramaWave",
        "$mainUrl/api/series?language=TR&platform=ReelShort" to "ReelShort",
        "$mainUrl/api/series?language=TR&platform=StarDust" to "StarDust",
        "$mainUrl/api/series?language=TR&platform=DramaBite" to "DramaBite",
        "$mainUrl/api/series?language=TR&platform=FlexTV" to "FlexTV",
        "$mainUrl/api/series?language=TR&platform=FreeReels" to "FreeReels",
        "$mainUrl/api/series?language=TR&platform=RapidTV" to "RapidTV",
        "$mainUrl/api/series?language=TR&platform=SodaReels" to "SodaReels"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val limit = 25
        val offset = (page - 1) * limit

        val cleanlink = if (request.data.contains("?")) {
            "${request.data}&limit=$limit&offset=$offset"
        } else {
            "${request.data}?limit=$limit&offset=$offset"
        }

        val response = app.get(cleanlink).text

        val series = if (response.trim().startsWith("{")) {
            val res = AppUtils.parseJson<SeriesResponse>(response)
            res.series
        } else {
            AppUtils.parseJson<List<Seri>>(response)
        }

        val result = series.map { seri ->
            val fixedcover = if (seri.cover_image.contains("awscover.netshort.com")) {
                seri.cover_image.replace("https://", "http://")
            } else {
                seri.cover_image
            }

            newMovieSearchResponse(
                seri.title,
                "$mainUrl/api/series/${seri.slug}",
                TvType.TvSeries
            ) {
                this.posterUrl = fixUrlNull(fixedcover)
                this.id = seri.id
            }
        }

        val listeler = listOf(HomePageList(request.name, result))
        return newHomePageResponse(listeler, result.size >= limit)
    }

    data class SeriesResponse(
        val series: List<Seri>,
        val total: Int,
        val offset: Int,
        val limit: Int
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$api?search=$query&language=TR&limit=500"
        val response = app.get(link).text

        val series = if (response.trim().startsWith("{")) {
            val res = AppUtils.parseJson<SeriesResponse>(response)
            res.series
        } else {
            AppUtils.parseJson<List<Seri>>(response)
        }

        return series.map { seri ->
            val fixedcover = if (seri.cover_image.contains("awscover.netshort.com")) {
                seri.cover_image.replace("https://", "http://")
            } else {
                seri.cover_image
            }

            newMovieSearchResponse(
                seri.title,
                "$mainUrl/api/series/${seri.slug}",
                TvType.TvSeries
            ) {
                this.posterUrl = fixUrlNull(fixedcover)
                this.id = seri.id
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url).text
        val res = AppUtils.parseJson<Detay>(response)
        val series = res.series

        val title = series.title.replaceFirstChar { it.titlecase(Locale.ROOT) }
        val description = series.description?.replaceFirstChar { it.titlecase(Locale.ROOT) }

        val poster = series.cover_image.let { img ->
            if (img.contains("awscover.netshort.com")) img.replace("https://", "http://") else img
        }

        val tagslist = mutableListOf<String>()
        series.tags?.let { tagslist.addAll(it) }
        series.platform?.let { tagslist.add(it) }

        val episodes = res.episodes.map { bolum ->
            val data = bolum.toJson()
            val thumb = bolum.thumbnail?.let { img ->
                if (img.contains("awscover.netshort.com")) img.replace(
                    "https://",
                    "http://"
                ) else img
            }

            newEpisode(data) {
                this.name = "Bölüm ${bolum.episode_number}"
                this.episode = bolum.episode_number
                this.posterUrl = thumb
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
            this.posterUrl = fixUrlNull(poster)
            this.plot = description
            this.tags = tagslist
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val bolum = AppUtils.parseJson<Bolum>(data)
        val ticketResponse = app.get("$mainUrl/api/cdn-ticket", headers = mapOf("Referer" to "$mainUrl/tr"))
        val dfexp = ticketResponse.cookies["dfexp"]?.let { "dfexp=$it" }
        val dfsig = ticketResponse.cookies["dfsig"]?.let { "dfsig=$it" }
        val currentCookie = listOfNotNull(dfexp, dfsig).joinToString("; ")

        val subtitleHeaders = mutableMapOf<String, String>(
            "Referer" to mainUrl
        )
        if (currentCookie.isNotEmpty()) {
            subtitleHeaders["Cookie"] = currentCookie
        }

        bolum.subtitles?.forEach { altyazi ->
            val label = altyazi.label ?: altyazi.language
            val fixedlabel = if (label.uppercase() == "TR") "Türkçe" else label
            Log.d("ayzen", fixedlabel)
            subtitleCallback.invoke(
                newSubtitleFile(fixedlabel, altyazi.url) {
                    this.headers = subtitleHeaders
                }
            )
            Log.d("ayzen", altyazi.url)
        }

        bolum.url?.let { link ->
            Log.d("ayzen", link)
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = link,
                    type = if (link.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.referer = "$mainUrl"
                    if (currentCookie.isNotEmpty()) {
                        this.headers = mutableMapOf("Cookie" to currentCookie)
                    }
                }
            )
        }
        return true
    }



    @Suppress("PropertyName")
    data class Seri(
        @param:JsonProperty("id") val id: Int,
        @param:JsonProperty("slug") val slug: String,
        @param:JsonProperty("title") val title: String,
        @param:JsonProperty("description") val description: String?,
        @param:JsonProperty("cover_image") val cover_image: String,
        @param:JsonProperty("platform") val platform: String?,
        @param:JsonProperty("total_episodes") val total_episodes: Int?,
        @param:JsonProperty("tags") val tags: List<String>?,
        @param:JsonProperty("createdAt") val createdAt: Long?
    )

    data class Detay(
        @param:JsonProperty("series") val series: Seri,
        @param:JsonProperty("episodes") val episodes: List<Bolum>
    )

    @Suppress("PropertyName")
    data class Bolum(
        @param:JsonProperty("id") val id: Int,
        @param:JsonProperty("episode_number") val episode_number: Int,
        @param:JsonProperty("url") val url: String?,
        @param:JsonProperty("thumbnail") val thumbnail: String?,
        @param:JsonProperty("subtitles") val subtitles: List<Altyazi>?
    )

    data class Altyazi(
        @param:JsonProperty("language") val language: String,
        @param:JsonProperty("url") val url: String,
        @param:JsonProperty("label") val label: String?
    )
}