// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

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

    private fun Seri.apiconvert(): SearchResponse {
        return newMovieSearchResponse(this.title, "$mainUrl/api/series/${this.slug}", TvType.TvSeries) {
            this.posterUrl = this@apiconvert.cover_image
            this.id = this@apiconvert.id
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val listeler = mutableListOf<HomePageList>()
        val limit = 25
        val kayma = (page - 1) * limit

        val platformlar = listOf(
            "NetShort",
            "DramaBox",
            "ShortMax",
            "DramaWawe",
            "ReelShort",
            "StarDust"
        )

        platformlar.forEach { platform ->
            val link = "$api?limit=$limit&offset=$kayma&language=TR&platform=$platform"
            val yanit = app.get(link).text
            val veri = AppUtils.parseJson<List<Seri>>(yanit)

            val icerik = veri.map { it.apiconvert() }
            if (icerik.isNotEmpty()) {
                listeler.add(HomePageList(platform, icerik))
            }
        }

        return newHomePageResponse(listeler, true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$api?search=$query&language=TR&limit=500"
        val yanit = app.get(link).text
        val veri = AppUtils.parseJson<List<Seri>>(yanit)

        return veri.map { it.apiconvert() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val yanit = app.get(url).text
        val veri = AppUtils.parseJson<Detay>(yanit)
        val seri = veri.series

        return newTvSeriesLoadResponse(
            seri.title.replaceFirstChar { it.titlecase(Locale.ROOT) },
            url,
            TvType.TvSeries,
            veri.episodes.map { bolum ->
                val data = bolum.toJson()
                newEpisode(data) {
                    this.name = "Bölüm ${bolum.episode_number}"
                    this.episode = bolum.episode_number
                    this.posterUrl = bolum.thumbnail
                }
            }
        ) {
            this.posterUrl = seri.cover_image
            this.plot = seri.description?.replaceFirstChar { it.titlecase(Locale.ROOT) }
            this.tags = seri.tags
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
            subtitleCallback.invoke(
                newSubtitleFile(altyazi.label ?: altyazi.language, altyazi.url)
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