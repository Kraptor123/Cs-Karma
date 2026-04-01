// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class OKRU : MainAPI() {
    override var mainUrl = "https://ok.ru"
    override var name = "OKRu"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/video/showcase" to "Videos",
        "${mainUrl}/video/kino/soviet" to "Soviet",
        "${mainUrl}/video/kino/drama" to "Drama",
        "${mainUrl}/video/kino/action" to "Action",
        "${mainUrl}/video/kino/family" to "Family",
        "${mainUrl}/video/kino/comedy" to "Comedy Movies",
        "${mainUrl}/video/serial" to "Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val tag = request.data.substringAfterLast("/")
        val isserial = request.data.contains("serial")
        val isshowcase = request.data.contains("showcase")
        var doc = app.get(request.data).document

        if (page > 1) {
            val lelem = doc.selectFirst("div.loader-container")?.attr("data-last-element") ?: if (isshowcase) "18" else "1669805881145"
            val purl = if (isshowcase) "$mainUrl/video?st.cmd=anonymVideo&st.m=SHOWCASE&st.furl=%2Fvideo%2Fshowcase&cmd=VideoUniversalContentBlock"
            else "$mainUrl/video/serial?st.cmd=anonymVideo&st.fltag=$tag&st.m=ALBUMS_CATALOG&st.ft=serial&st.furl=%2Fvideo%2Fserial%2F$tag&cmd=VideoUniversalContentBlock"

            val res = app.post(purl, data = mapOf("fetch" to "false", "st.page" to page.toString(), "st.lastelem" to lelem, "gwt.requested" to "9579ea2eT1774883610506"), headers = mapOf("ok-screen" to "anonymVideo", "X-Requested-With" to "XMLHttpRequest"))
            doc = res.document
        }

        val reslist = mutableListOf<SearchResponse>()

        if (isserial) {
            doc.select("video-channels-vitrine-slider").forEach { slider ->
                val props = slider.attr("data-props")
                val reg = Regex("""\{"id":"(.*?)".*?"href":"(.*?)","name":"(.*?)","imageUrl":"(.*?)"""")
                reg.findAll(props).forEach { m ->
                    val href = fixUrl(m.groupValues[2].replace("\\u0026", "&"))
                    val name = m.groupValues[3]
                    val poster = m.groupValues[4].replace("\\u0026", "&")
                    reslist.add(newTvSeriesSearchResponse(name, href, TvType.TvSeries) { this.posterUrl = poster })
                }
            }
        } else {
            doc.select("div.ugrid_i, div.video-card, div.video-slider_i").forEach {
                it.tomainpageresult()?.let { res -> reslist.add(res) }
            }
        }

        val finalres = reslist.distinctBy { it.url }
        return newHomePageResponse(request.name, finalres, hasNext = finalres.isNotEmpty())
    }

    private fun Element.tomainpageresult(): SearchResponse? {
        val telem = this.selectFirst("a.video-card_n, a.video_channels_vitrine_slider_title")
        val title = (telem?.text()?.trim() ?: telem?.attr("title")?.trim() ?: this.selectFirst("img")?.attr("alt")?.trim()) ?: return null
        val href = this.selectFirst("a.video-card_lk, a.video_channels_vitrine_slider_channel-link")?.attr("href")?.takeIf { it != "#" } ?: telem?.attr("href")
        val fhref = fixUrlNull(href) ?: return null
        val purl = fixUrlNull(this.selectFirst("img.video_channels_vitrine_slider_cover2, img.video-card_img, img")?.attr("src"))

        return if (fhref.contains("/video/c")) newTvSeriesSearchResponse(title, fhref, TvType.TvSeries) { this.posterUrl = purl }
        else newMovieSearchResponse(title, fhref, TvType.Movie) { this.posterUrl = purl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val idmatch = Regex("""video/(\d+)""").find(url)
        val curl = idmatch?.let { "https://ok.ru/video/${it.groupValues[1]}" } ?: url.split("?").first()
        val doc = app.get(url).document
        val album = url.contains("/video/c")

        val title = (if (album) doc.selectFirst("h3.album-info_name")?.text() ?: doc.selectFirst("meta[property=og:title]")?.attr("content") else doc.selectFirst("meta[property=og:title]")?.attr("content")) ?: doc.selectFirst("title")?.text() ?: return null
        val author = doc.selectFirst("meta[property=ya:ovs:login]")?.attr("content")

        val recs = doc.select("li.reco-content_item__7rrgi").mapNotNull { el ->
            val rraw = el.selectFirst("a.link__91azp")?.attr("href")?.let { fixUrl(it) }
            val rid = if (rraw != null) Regex("""video/(\d+)""").find(rraw)?.groupValues?.get(1) else null
            val rurl = if (rid != null) "https://ok.ru/video/$rid" else rraw
            val rtitle = el.selectFirst("div.card-title__el6vj")?.text() ?: el.selectFirst("img")?.attr("alt")
            if (rurl != null && rtitle != null) newMovieSearchResponse(rtitle, rurl, TvType.Movie) { this.posterUrl = el.selectFirst("img")?.attr("src") } else null
        }.distinctBy { it.url }

        if (album) {
            val aid = url.substringAfter("/video/c").substringBefore("?").split("/").firstOrNull()
            val eps = mutableListOf<Episode>()
            var page = 1
            var hasnext = true
            var lelem = doc.selectFirst(".loader-container")?.attr("data-last-element")

            val felems = doc.select("div.ugrid_i.js-seen-item-movie")
            val poster = felems.firstOrNull()?.selectFirst("img.video-card_img")?.attr("src")

            felems.forEach { el ->
                el.selectFirst("div.video-card")?.attr("data-id")?.let { id ->
                    eps.add(newEpisode("https://ok.ru/video/$id") {
                        this.name = el.selectFirst("a.video-card_n")?.text()
                        this.posterUrl = el.selectFirst("img.video-card_img")?.attr("src")
                        this.description = el.selectFirst("div.video-card_duration")?.text()
                    })
                }
            }

            while (hasnext && !aid.isNullOrEmpty() && !lelem.isNullOrEmpty()) {
                page++
                val pres = app.post("https://ok.ru/video/c$aid?st.cmd=anonymVideo&st.m=ALBUM&st.ft=album&st.aid=c$aid&cmd=VideoAlbumBlock", data = mapOf("fetch" to "false", "st.page" to page.toString(), "st.lastelem" to lelem!!), headers = mapOf("Referer" to url, "X-Requested-With" to "XMLHttpRequest"))
                val pdoc = pres.document
                val items = pdoc.select("div.ugrid_i.js-seen-item-movie")

                if (items.isEmpty()) hasnext = false else {
                    items.forEach { el ->
                        el.selectFirst("div.video-card")?.attr("data-id")?.let { id ->
                            eps.add(newEpisode("https://ok.ru/video/$id") {
                                this.name = el.selectFirst("a.video-card_n")?.text()
                                this.posterUrl = el.selectFirst("img.video-card_img")?.attr("src")
                            })
                        }
                    }
                    lelem = pres.headers["lastelem"] ?: pdoc.selectFirst(".loader-container")?.attr("data-last-element")
                    if (pres.headers["fetchedall"] == "true") hasnext = false
                }
                if (page > 15) hasnext = false
            }

            val feps = eps.distinctBy { it.data }.reversed()
            if (feps.isEmpty()) return null

            return if (feps.size == 1) newMovieLoadResponse(title, curl, TvType.Movie, feps.first().data) { this.posterUrl = fixUrlNull(poster); this.recommendations = recs; author?.let { this.tags = listOf(it) } }
            else newTvSeriesLoadResponse(title, curl, TvType.TvSeries, feps) { this.posterUrl = fixUrlNull(poster); this.recommendations = recs; this.showStatus = ShowStatus.Completed; author?.let { this.tags = listOf(it) } }
        } else {
            val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
            val eurl = doc.selectFirst("meta[property=og:video:secure_url]")?.attr("content") ?: doc.selectFirst("meta[property=og:video:url]")?.attr("content") ?: curl.replace("/video/", "/videoembed/")
            return newMovieLoadResponse(title, curl, TvType.Movie, eurl) { this.posterUrl = fixUrlNull(poster); this.duration = doc.selectFirst("meta[property=video:duration]")?.attr("content")?.toIntOrNull()?.div(60); this.recommendations = recs; author?.let { this.tags = listOf(it) } }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        loadExtractor(data, mainUrl, subtitleCallback, callback)
        return true
    }

    data class SearchItem(val name: String? = null, val imageUrl: String? = null, val movie: SearchMovie? = null)
    data class SearchMovie(val id: String? = null, val title: String? = null, val thumbnail: SearchThumbnail? = null)
    data class SearchThumbnail(val small: String? = null, val big: String? = null)
}