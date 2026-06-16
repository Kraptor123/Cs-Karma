// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class OKRU : MainAPI() {
    override var mainUrl = "https://ok.ru"
    override var name = "OKRu"
    override val hasMainPage = true
    override var lang = "ru"
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
        val isSerial = request.data.contains("serial")
        val isShowcase = request.data.contains("showcase")
        var document = app.get(request.data).document
        val lastElement = document.selectFirst("[data-module=Loader]")?.attr("data-last-element") ?: if (isShowcase) "18" else "1669805881145"

        if (page > 1) {
            val postUrl = if (isShowcase) {
                "$mainUrl/video/showcase?st.cmd=anonymVideo&st.m=SHOWCASE&st.ft=showcase&st.furl=%2Fvideo%2Fshowcase&cmd=VideoUniversalContentBlock"
            } else {
                val ft = if (isSerial) "serial" else request.data.substringBeforeLast("/").substringAfterLast("/")
                "$mainUrl${request.data.substringBeforeLast("/")}?st.cmd=anonymVideo&st.fltag=$tag&st.m=ALBUMS_CATALOG&st.ft=$ft&st.furl=${request.data}&cmd=VideoUniversalContentBlock"
            }

            val response = app.post(
                postUrl,
                data = mapOf(
                    "fetch" to "false",
                    "st.page" to page.toString(),
                    "st.lastelem" to lastElement,
                    "gwt.requested" to "9579ea2eT1774883610506"
                ),
                headers = mapOf(
                    "ok-screen" to "anonymVideo",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            )
            document = response.document
        }

        val resultsList = mutableListOf<SearchResponse>()

        if (isSerial) {
            document.select("video-channels-vitrine-slider").forEach { slider ->
                val props = slider.attr("data-props")
                val regex = Regex("""\{"id":"(.*?)".*?"href":"(.*?)","name":"(.*?)","imageUrl":"(.*?)"""")
                regex.findAll(props).forEach { match ->
                    val href = fixUrl(match.groupValues[2].replace("\\u0026", "&"))
                    val name = match.groupValues[3]
                    val poster = match.groupValues[4].replace("\\u0026", "&")
                    resultsList.add(newTvSeriesSearchResponse(name, href, TvType.TvSeries) {
                        this.posterUrl = poster
                    })
                }
            }
        } else {
            document.select("div.ugrid_i").forEach { item ->
                val card = item.selectFirst("div.video-card") ?: return@forEach
                val titleElement = card.selectFirst("a.video-card_n")
                val linkElement = card.selectFirst("a.video-card_lk")
                val imgElement = card.selectFirst("img.video-card_img")

                val name = titleElement?.text() ?: return@forEach
                val href = fixUrl(linkElement?.attr("href") ?: return@forEach)
                val poster = imgElement?.attr("src") ?: ""

                resultsList.add(newMovieSearchResponse(name, href, TvType.Movie) {
                    this.posterUrl = poster
                })
            }
        }

        val finalResults = resultsList.distinctBy { it.url }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = finalResults,
                isHorizontalImages = true
            ),
            hasNext = finalResults.isNotEmpty()
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        var searchdata: SearchData? = null

        if (page == 1) {
            val response = app.post("$mainUrl/video/search?st.cmd=video&st.psft=showcase&st.m=SEARCH&st.ft=search&st.fuvh=on&st.furl=%2Fvideo%2Fshowcase&cmd=VideoContentBlock", data = mapOf("st.v.sq" to query, "gwt.requested" to "9579ea2eT1774883610506")).document
            val jsonstr = response.selectFirst("video-search-result")?.attr("data-props")
            if (!jsonstr.isNullOrEmpty()) searchdata = AppUtils.parseJson<SearchData>(jsonstr)
        } else {
            val offset = (page - 1) * 30
            val reqbodystring = """{"id": $page,"parameters": {"displayMode": "Movie","videosOffset": $offset,"channelsOffset": 0,"searchQuery": "$query","currentStateId": "video","durationType": "ANY","hd": false}}"""
            val jsonstr = app.post("$mainUrl/web-api/v2/video/fetchSearchResult", headers = mapOf("Accept" to "application/json, text/javascript, */*; q=0.01", "ok-screen" to "anonymVideo", "x-client-flags" to "ms:0;dcss:0;mpv2:1;dz:0"), requestBody = reqbodystring.toRequestBody("text/plain;charset=UTF-8".toMediaTypeOrNull())).text
            if (jsonstr.isNotEmpty()) searchdata = AppUtils.parseJson<ApiSearchResponse>(jsonstr).result
        }

        val results = searchdata?.videos?.list?.mapNotNull { item ->
            val title = item.movie?.title ?: item.name ?: return@mapNotNull null
            val id = item.movie?.id ?: return@mapNotNull null
            val poster = item.movie.thumbnail?.big ?: item.movie?.thumbnail?.small ?: item.imageUrl
            newMovieSearchResponse(title, "$mainUrl/video/$id", TvType.Movie) {
                this.posterUrl = fixUrlNull(poster) }
        } ?: emptyList()

        return newSearchResponseList(results, hasNext = searchdata?.videos?.hasMore == true)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val idmatch = Regex("""video/(\d+)""").find(url)
        val curl = idmatch?.let { "$mainUrl/video/${it.groupValues[1]}" } ?: url.split("?").first()
        val doc = app.get(url).document
        val album = url.contains("/video/c")

        val title = (if (album) {
            doc.selectFirst("h3.album-info_name")?.text() ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
        } else {
            doc.selectFirst("meta[property=og:title]")?.attr("content")
        }) ?: doc.selectFirst("title")?.text() ?: return null

        val author = doc.selectFirst("meta[property=ya:ovs:login]")?.attr("content")
        val recs = doc.select("li.recommendations_block_reco-content_item").mapNotNull { it.toOkruRecommendation() }.distinctBy { it.url }

        return if (album) SerieLoad(url, curl, title, author, recs, doc)
        else Filmvideo(curl, title, author, recs, doc)
    }

    private suspend fun SerieLoad(url: String, curl: String, title: String, author: String?, recs: List<SearchResponse>, doc: Document): LoadResponse? {
        val aid = url.substringAfter("/video/c").substringBefore("?").split("/").firstOrNull()
        val eps = mutableListOf<Episode>()
        var page = 1
        var hasnext = true
        var lelem = doc.selectFirst(".loader-container")?.attr("data-last-element")

        val felems = doc.select("div.ugrid_i.js-seen-item-movie")
        val poster = felems.firstOrNull()?.selectFirst("img.video-card_img")?.attr("src")

        eps.addAll(felems.mapNotNull { it.toOkruEpisode(withdescription = true) })

        while (hasnext && !aid.isNullOrEmpty() && !lelem.isNullOrEmpty()) {
            page++
            val pres = app.post(
                "$mainUrl/video/c$aid?st.cmd=anonymVideo&st.m=ALBUM&st.ft=album&st.aid=c$aid&cmd=VideoAlbumBlock",
                data = mapOf("fetch" to "false", "st.page" to page.toString(), "st.lastelem" to lelem),
                headers = mapOf("Referer" to url, "X-Requested-With" to "XMLHttpRequest")
            )
            val pdoc = pres.document
            val items = pdoc.select("div.ugrid_i.js-seen-item-movie")

            if (items.isEmpty()) {
                hasnext = false
            } else {
                eps.addAll(items.mapNotNull { it.toOkruEpisode(withdescription = false) })
                lelem = pres.headers["lastelem"] ?: pdoc.selectFirst(".loader-container")?.attr("data-last-element")
                if (pres.headers["fetchedall"] == "true") hasnext = false
            }
            if (page > 15) hasnext = false
        }

        val feps = eps.distinctBy { it.data }.reversed()
        if (feps.isEmpty()) return null

        return if (feps.size == 1) {
            newMovieLoadResponse(title, curl, TvType.Movie, feps.first().data) {
                this.posterUrl = fixUrlNull(poster)
                this.recommendations = recs
                author?.let { this.tags = listOf(it) }
            }
        } else {
            newTvSeriesLoadResponse(title, curl, TvType.TvSeries, feps) {
                this.posterUrl = fixUrlNull(poster)
                this.recommendations = recs
                this.showStatus = ShowStatus.Completed
                author?.let { this.tags = listOf(it) }
            }
        }
    }

    private suspend fun Filmvideo(curl: String, title: String, author: String?, recs: List<SearchResponse>, doc: Document): LoadResponse {
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val eurl = doc.selectFirst("meta[property=og:video:secure_url]")?.attr("content")
            ?: doc.selectFirst("meta[property=og:video:url]")?.attr("content")
            ?: curl.replace("/video/", "/videoembed/")
        val duration = doc.selectFirst("meta[property=video:duration]")?.attr("content")?.toIntOrNull()?.div(60)

        return newMovieLoadResponse(title, curl, TvType.Movie, eurl) {
            this.posterUrl = fixUrlNull(poster)
            this.duration = duration
            this.recommendations = recs
            author?.let { this.tags = listOf(it) }
        }
    }

    private fun Element.toOkruRecommendation(): SearchResponse? {
        val rraw = this.selectFirst("a.recommendations_block_card-phot-link")?.attr("href")?.let { fixUrl(it) }
        val rid = rraw?.let { Regex("""video/(\d+)""").find(it)?.groupValues?.get(1) }
        val rurl = rid?.let { "$mainUrl/video/$it" } ?: rraw ?: return null
        val rtitle = this.selectFirst("div.recommendations_block_card-title")?.text()
            ?: this.selectFirst("img.recommendations_block_card-photo-img")?.attr("alt")
            ?: return null
        val rposter = this.selectFirst("img.recommendations_block_card-photo-img")?.attr("src")

        return newMovieSearchResponse(rtitle, rurl, TvType.Movie) {
            this.posterUrl = rposter
        }
    }

    private fun Element.toOkruEpisode(withdescription: Boolean): Episode? {
        val id = this.selectFirst("div.video-card")?.attr("data-id") ?: return null
        val name = this.selectFirst("a.video-card_n")?.text()
        val poster = this.selectFirst("img.video-card_img")?.attr("src")
        val description = if (withdescription) this.selectFirst("div.video-card_duration")?.text() else null

        return newEpisode(
            "$mainUrl/video/$id"
        ) {
            this.name = name
            this.posterUrl = poster
            this.description = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit): Boolean
    {
        loadExtractor(data, mainUrl, subtitleCallback, callback)
        return true
    }

data class SearchItem(val name: String? = null, val imageUrl: String? = null, val movie: SearchMovie? = null)
data class SearchMovie(val id: String? = null, val title: String? = null, val thumbnail: SearchThumbnail? = null)
data class SearchThumbnail(val small: String? = null, val big: String? = null)
data class ApiSearchResponse(val result: SearchData? = null)
data class SearchData(val videos: SearchVideos? = null)
data class SearchVideos(val list: List<SearchItem>? = null, val hasMore: Boolean? = null)
}