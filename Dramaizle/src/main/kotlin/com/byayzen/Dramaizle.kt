// ! Bu araç @ByAyzen tarafından | @CS-Karma için yazılmıştır.

package com.byayzen

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

            newMovieSearchResponse(seri.title, "$mainUrl/api/series/${seri.slug}", TvType.TvSeries) {
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

            newMovieSearchResponse(seri.title, "$mainUrl/api/series/${seri.slug}", TvType.TvSeries) {
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
                if (img.contains("awscover.netshort.com")) img.replace("https://", "http://") else img
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

        bolum.subtitles?.forEach { altyazi ->
            val label = altyazi.label ?: altyazi.language
            val fixedlabel = if (label.uppercase() == "TR") "Türkçe" else label
            subtitleCallback.invoke(
                newSubtitleFile(fixedlabel, altyazi.url)
            )
        }

        bolum.url?.let { link ->
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = link,
                ) {
                    this.referer = "$mainUrl/"
                    this.type = if (link.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                }
            )
        }
        return true
    }

    @Suppress("PropertyName")
    data class Seri(
        val id: Int,
        val slug: String,
        val title: String,
        val description: String?,
        val cover_image: String,
        val platform: String?,
        val total_episodes: Int?,
        val tags: List<String>?,
        val createdAt: Long?
    )

    data class Detay(
        val series: Seri,
        val episodes: List<Bolum>
    )

    @Suppress("PropertyName")
    data class Bolum(
        val id: Int,
        val episode_number: Int,
        val url: String?,
        val thumbnail: String?,
        val subtitles: List<Altyazi>?
    )

    data class Altyazi(
        val language: String,
        val url: String,
        val label: String?
    )
}