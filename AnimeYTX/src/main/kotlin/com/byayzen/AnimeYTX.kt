// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import android.util.Base64
import org.json.JSONObject

import org.json.JSONArray


import com.lagradost.cloudstream3.app
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

class AnimeYTX : MainAPI() {
    private val TAG = "AnimeYTX"

    override var mainUrl = "https://animeyt.cc"
    override var name = "AnimeYTX"
    override val hasMainPage = true
    override var lang = "es"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Anime)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Últimos episodios",
        "$mainUrl/tv/" to "Directorio",
        "$mainUrl/tv/?estado=emision" to "En emisión"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = request.data
        val isTvList = data.contains("/tv")

        val url = if (page <= 1) {
            data
        } else {
            if (data.contains("?")) {
                val base = data.substringBefore("?").trimEnd('/')
                val query = data.substringAfter("?")
                "$base/page/$page/?$query"
            } else {
                "${data.trimEnd('/')}/page/$page/"
            }
        }

        Log.d("AnimeYT", "Request URL: $url | Page: $page | Category: ${request.name}")

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0",
            "Referer" to "$mainUrl/"
        )
        val response = app.get(url, headers = headers)
        Log.d("AnimeYT", "Response Code: ${response.code} | Category: ${request.name}")

        val document = response.document
        Log.d(
            "AnimeYT",
            "Body Length: ${document.body().html().length} | Category: ${request.name}"
        )

        val (items, hasNext) = if (isTvList) {
            val elements = document.select("article.aniyt-anime-card")
            Log.d("AnimeYT", "Anime Cards Selected: ${elements.size} | Category: ${request.name}")
            val parsedList = elements.mapNotNull { it.toTvSearchResult() }
            Log.d("AnimeYT", "Anime Cards Parsed: ${parsedList.size} | Category: ${request.name}")
            val nextExists = document.selectFirst(".aniyt-pagination a.next") != null
            Pair(parsedList, nextExists)
        } else {
            val elements = document.select("article.aniyt-episode-card")
            Log.d("AnimeYT", "Episode Cards Selected: ${elements.size} | Category: ${request.name}")
            val parsedList = elements.mapNotNull { it.toEpisodeSearchResult() }
            Log.d("AnimeYT", "Episode Cards Parsed: ${parsedList.size} | Category: ${request.name}")
            val nextExists = document.selectFirst(".aniyt-pagination a.next") != null
            Pair(parsedList, nextExists)
        }

        return newHomePageResponse(request.name, items, hasNext)
    }

    private fun Element.toTvSearchResult(): SearchResponse? {
        val title =
            selectFirst(".aniyt-anime-body h3 a")?.text()?.trim().takeUnless { it.isNullOrEmpty() }
                ?: return null
        val href = selectFirst("a.aniyt-anime-poster")?.attr("href")?.ifEmpty { return null }
            ?: return null
        val img = selectFirst(".aniyt-anime-poster img")
        val poster = img?.attr("data-src")?.ifEmpty { null } ?: img?.attr("src")?.ifEmpty { null }

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = poster
        }
    }

    private fun Element.toEpisodeSearchResult(): SearchResponse? {
        val title = selectFirst(".aniyt-episode-body h3 a")?.text()?.trim()
            .takeUnless { it.isNullOrEmpty() } ?: return null
        val href = selectFirst("a.aniyt-episode-media")?.attr("href")?.ifEmpty { return null }
            ?: return null
        val img = selectFirst(".aniyt-episode-media img")
        val poster = img?.attr("data-src")?.ifEmpty { null } ?: img?.attr("src")?.ifEmpty { null }
        val epText = selectFirst(".aniyt-card-code")?.text()
        val episode = epText?.let { Regex("(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = poster
            addSub(episode)
        }
    }

    // override suspend fun search(query: String, page: Int): SearchResponseList? {
    //     val url = if (page <= 1) "$mainUrl/?s=$query" else "$mainUrl/page/$page/?s=$query"
    //     val document = app.get(url).document

    //     val items = document.select("article.bs").mapNotNull {
    //         it.toSearchResult()
    //     }

    //     return newSearchResponseList(items)
    // }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:155.0) Gecko/20100101 Firefox/155.0",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
            "Cookie" to "cf_clearance=z4kt4KEpD_kbNAhFvY.RNwZrgFvXtG_2g8R1ILswa.8-1788606738-1.2.1.1-ZZP84dahkkQXQCKL5wXJZSJdT2Pb5TG8p76zs_8RWt0tSuHNxOabyA8kXy_InYDItqvOGTVTHb530VFcPI877AG6y7M6xjkiBjnncY65TK_8FM5um0eCsZcAA8GwiPuBnJmf1DT_ePpyzffdSiteYyi___8EGZIO3xagLUXmnH9n_eWkzQBGtAu0OZU7uh1GqwhGT6s6_57_w5QDQA_nODuGfg8SwIvFsiifhvfyLm0XP1ETKRyf_N2lXZRXWt5IFjCp_S7VXPlYjbbqKYyqEqRYsGv.rdgysriYXhqPZCeEtHK11TMXo1xktIqEHZPn1aQtTGnsND4cnJyH31zCroQdc0H9OaJ5T4MGVh5eRp4",
            "Upgrade-Insecure-Requests" to "1",
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "none",
            "Sec-Fetch-User" to "?1"
        )

        var pageUrl = url
        var document = app.get(pageUrl, headers = headers).document
        Log.d("Ayzen", "Load URL: $pageUrl")

        val seriesHref = fixUrlNull(
            document.selectFirst(".aniyt-watch-series-poster")?.attr("href")
                ?.ifEmpty { null })?.substringBefore("#")
        if (seriesHref != null) {
            pageUrl = seriesHref
            document = app.get(pageUrl, headers = headers).document
            Log.d("Ayzen", "Series Linke Yonlendirildi: $pageUrl")
        }

        val title = document.selectFirst(".aniyt-detail-copy h1, h1.entry-title")?.text()?.trim()
            ?.ifEmpty { null }
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.ifEmpty { null }
                ?.replace("Sub Español - AnimeYT", "")?.trim()?.ifEmpty { null }
            ?: document.selectFirst("title")?.text()?.trim()
                ?.substringBefore("(20")
                ?.replace("Sub Español - AnimeYT", "")
                ?.trim()?.ifEmpty { null }
        Log.d("Ayzen", "Title: $title")
        if (title == null || title.equals("Database Error", ignoreCase = true)) return null

        val posterEl = document.selectFirst(".aniyt-detail-poster img, .thumb img")
        val poster = posterEl?.attr("data-src")?.ifEmpty { null }
            ?: posterEl?.attr("src")?.ifEmpty { null }
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.ifEmpty { null }
        Log.d("Ayzen", "Poster: $poster")

        val plot =
            document.select(".aniyt-detail-synopsis p, .entry-content[itemprop=description] p")
                .joinToString("\n\n") { it.text().trim() }
                .ifEmpty { null }
                ?: document.selectFirst(".aniyt-detail-summary")?.text()?.trim()?.ifEmpty { null }
                ?: document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
                    ?.ifEmpty { null }
        Log.d("Ayzen", "Plot: $plot")

        val releaseText =
            document.selectFirst(".aniyt-meta-list dt:contains(Estreno) + dd, .spe span:contains(Estreno:)")
                ?.text().orEmpty()
        val year = Regex("""(\d{4})""").find(releaseText)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""\((\d{4})\)""").find(
                document.selectFirst("title")?.text().orEmpty()
            )?.groupValues?.get(1)?.toIntOrNull()
        Log.d("Ayzen", "Year: $year (Raw: $releaseText)")

        val type = if (pageUrl.contains("/tv/")) TvType.Anime else TvType.AnimeMovie
        Log.d("Ayzen", "Type: $type")

        val tags = document.select(".aniyt-meta-list a[href*=/genres/], .genxed a")
            .map { it.text().trim() }.distinct()
        Log.d("Ayzen", "Tags Count: ${tags.size}")

        val statusText =
            document.selectFirst(".aniyt-detail-badges span, .aniyt-meta-list dt:contains(Estado) + dd, .spe span:contains(Estado:)")
                ?.text().orEmpty()
        val status = when {
            statusText.contains("Finalizado", true) -> ShowStatus.Completed
            statusText.contains("En emisión", true) || statusText.contains(
                "En curso",
                true
            ) -> ShowStatus.Ongoing

            else -> null
        }
        Log.d("Ayzen", "Status: $status (Raw: $statusText)")

        val cast = if (document.selectFirst(".aniyt-cast-item") != null) {
            document.select(".aniyt-cast-item").mapNotNull { item ->
                val actorName = item.selectFirst("div span")?.text()?.substringBefore("·")?.trim()
                    ?.ifEmpty { null } ?: return@mapNotNull null
                val charName = item.selectFirst("strong")?.text()?.trim()?.ifEmpty { null }
                val imgEl = item.selectFirst("img")
                val actorImg = imgEl?.attr("data-src")?.ifEmpty { null } ?: imgEl?.attr("src")
                    ?.ifEmpty { null }
                Actor(actorName, actorImg) to charName
            }
        } else {
            document.select(".cvlist .cvitem").mapNotNull { item ->
                val actorName =
                    item.selectFirst(".cvactor .charname")?.text()?.trim()?.ifEmpty { null }
                        ?: return@mapNotNull null
                val charName =
                    item.selectFirst(".cvchar .charname")?.text()?.trim()?.ifEmpty { null }
                val imgEl = item.selectFirst(".cvactor img")
                val actorImg = imgEl?.attr("data-src")?.ifEmpty { null } ?: imgEl?.attr("src")
                    ?.ifEmpty { null }
                Actor(actorName, actorImg) to charName
            }
        }
        Log.d("Ayzen", "Cast Count: ${cast.size}")

        val dateFmtIn = SimpleDateFormat("MMMM d, yyyy", Locale("es"))
        val dateFmtOut = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

        val episodes =
            document.select(".aniyt-episode-card, .eplister ul li").mapNotNull { element ->
                val link = element.selectFirst("a")?.attr("href")?.ifEmpty { null }
                    ?: return@mapNotNull null
                val numStr = element.selectFirst(".aniyt-card-code, .epl-num")?.text().orEmpty()
                val num = Regex("""(\d+)""").find(numStr)?.groupValues?.get(1)?.toIntOrNull()
                val epName =
                    element.selectFirst(".epl-title, .aniyt-episode-body h3")?.text()?.trim()
                        ?.ifEmpty { null }
                val epImgEl = element.selectFirst(".aniyt-episode-media img, img")
                val epPoster = epImgEl?.attr("data-src")?.ifEmpty { null } ?: epImgEl?.attr("src")
                    ?.ifEmpty { null } ?: poster
                val dateStr = element.selectFirst("time")?.attr("datetime")?.substringBefore("T")
                    ?.ifEmpty { null }
                    ?: element.selectFirst(".epl-date")?.text()?.trim()?.ifEmpty { null }
                val parsedDate =
                    if (dateStr != null && dateStr.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                        dateStr
                    } else {
                        try {
                            val parsed =
                                if (dateStr.isNullOrEmpty()) null else dateFmtIn.parse(dateStr)
                            if (parsed != null) dateFmtOut.format(parsed) else null
                        } catch (e: Exception) {
                            null
                        }
                    }

                newEpisode(link) {
                    this.name = epName
                    this.episode = num
                    this.posterUrl = epPoster
                    if (parsedDate != null) addDate(parsedDate)
                }
            }.reversed()
        Log.d("Ayzen", "Episodes Count: ${episodes.size}")

        val recommendations =
            document.select(".aniyt-detail-recommend-card, #wpop-items .wpop-weekly li")
                .mapNotNull { element ->
                    val href =
                        element.selectFirst(".aniyt-detail-recommend-poster, .leftseries h4 a, h3 a")
                            ?.attr("href")?.ifEmpty { null } ?: return@mapNotNull null
                    val itemTitle = element.selectFirst("h3 a, .leftseries h4 a")?.text()
                        ?.replace(Regex("""\(.*?\)"""), "")
                        ?.replace(Regex("""Temporada\s*\d*""", RegexOption.IGNORE_CASE), "")
                        ?.trim()?.ifEmpty { null } ?: return@mapNotNull null
                    val imgEl = element.selectFirst("img")
                    val rawImg = imgEl?.attr("data-src")?.ifEmpty { null } ?: imgEl?.attr("src")
                        ?.ifEmpty { null }
                    val itemImg = rawImg?.replace(Regex("""\?resize=\d+,\d+"""), "?resize=300,400")

                    newAnimeSearchResponse(itemTitle, href, type) {
                        this.posterUrl = itemImg
                    }
                }
        Log.d("Ayzen", "Recommendations Count: ${recommendations.size}")

        if (type == TvType.AnimeMovie) {
            val movieEpisodeUrl =
                document.selectFirst(".aniyt-episode-card a, .eplister ul li a")?.attr("href")
                    ?.ifEmpty { null } ?: pageUrl
            Log.d("Ayzen", "Movie Episode Url: $movieEpisodeUrl")
            return newMovieLoadResponse(title, pageUrl, TvType.AnimeMovie, movieEpisodeUrl) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
                this.recommendations = recommendations
                addActors(cast)
            }
        }

        return newAnimeLoadResponse(title, pageUrl, type) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.showStatus = status
            this.tags = tags
            this.recommendations = recommendations
            addActors(cast)
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var linkFound = false
        val headers   = mapOf(
            "User-Agent"                to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:155.0) Gecko/20100101 Firefox/155.0",
            "Accept"                    to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language"           to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
            "Cookie"                    to "cf_clearance=z4kt4KEpD_kbNAhFvY.RNwZrgFvXtG_2g8R1ILswa.8-1788606738-1.2.1.1-ZZP84dahkkQXQCKL5wXJZSJdT2Pb5TG8p76zs_8RWt0tSuHNxOabyA8kXy_InYDItqvOGTVTHb530VFcPI877AG6y7M6xjkiBjnncY65TK_8FM5um0eCsZcAA8GwiPuBnJmf1DT_ePpyzffdSiteYyi___8EGZIO3xagLUXmnH9n_eWkzQBGtAu0OZU7uh1GqwhGT6s6_57_w5QDQA_nODuGfg8SwIvFsiifhvfyLm0XP1ETKRyf_N2lXZRXWt5IFjCp_S7VXPlYjbbqKYyqEqRYsGv.rdgysriYXhqPZCeEtHK11TMXo1xktIqEHZPn1aQtTGnsND4cnJyH31zCroQdc0H9OaJ5T4MGVh5eRp4",
            "Upgrade-Insecure-Requests" to "1",
            "Sec-Fetch-Dest"            to "document",
            "Sec-Fetch-Mode"            to "navigate",
            "Sec-Fetch-Site"            to "none",
            "Sec-Fetch-User"            to "?1"
        )

        Log.d("Ayzen", "Bolum adresi: $data")
        val response = app.get(data, headers = headers)
        val document = response.document
        val rawHtml  = response.text

        val iframeUrls = mutableSetOf<String>()

        document.select("iframe").forEach { el ->
            val src = el.attr("data-src").ifEmpty { el.attr("src") }
            if (src.isNotBlank() && src != "about:blank") {
                iframeUrls.add(src.replace("&amp;", "&"))
            }
        }

        document.select("template, noscript").forEach { el ->
            val inner = el.html()
            Regex("""(?:data-src|src)=["']([^"']+)["']""").findAll(inner).forEach { match ->
                val src = match.groupValues[1]
                if (src.isNotBlank() && src != "about:blank") {
                    iframeUrls.add(src.replace("&amp;", "&"))
                }
            }
        }

        Regex("""https://mytsumi\.com/multiplayer/[^"'\s<>]+""").findAll(rawHtml).forEach { match ->
            iframeUrls.add(match.value.replace("&amp;", "&"))
        }

        Log.d("Ayzen", "Bulunan cerceve sayisi: ${iframeUrls.size}")

        iframeUrls.forEach { iframeUrl ->
            Log.d("Ayzen", "Cerceve adresi: $iframeUrl")
            if (iframeUrl.contains("mytsumi.com")) {
                val containerId = Regex("""[?&]value=([^&]+)""").find(iframeUrl)?.groupValues?.get(1) ?: return@forEach
                val targetUrl   = "https://mytsumi.com/multiplayer/contenedor.php?id=$containerId"
                val pageText    = app.get(targetUrl, referer = iframeUrl).text

                Regex("""const\s+videoTabs\s*=\s*(\[.*?\]);""").find(pageText)?.groupValues?.get(1)?.let { json ->
                    try {
                        val jsonArray = JSONArray(json)
                        for (i in 0 until jsonArray.length()) {
                            val tab     = jsonArray.getJSONObject(i)
                            val rawUrl  = tab.getString("url").replace("\\/", "")
                            val isMp4   = tab.optBoolean("is_mp4", false)
                            val tabName = tab.optString("tab_name", "Mytsumi")

                            if (rawUrl.isNotBlank() && rawUrl != "about:blank") {
                                Log.d("Ayzen", "Oynatici adresi: $rawUrl")
                                if (isMp4) {
                                    callback(
                                        newExtractorLink(
                                            source = tabName,
                                            name   = tabName,
                                            url    = rawUrl,
                                            type   = ExtractorLinkType.VIDEO
                                        )
                                    )
                                    linkFound = true
                                } else {
                                    loadExtractor(rawUrl, targetUrl, subtitleCallback) { link ->
                                        linkFound = true
                                        callback(link)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("Ayzen", "Sekme hatasi: ${e.message}")
                    }
                }

                Regex("""const\s+downloadsByQuality\s*=\s*(\{.*?\});""").find(pageText)?.groupValues?.get(1)?.let { json ->
                    try {
                        val dlJson = JSONObject(json)
                        dlJson.keys().forEach { quality ->
                            val items = dlJson.getJSONArray(quality)
                            for (i in 0 until items.length()) {
                                val item  = items.getJSONObject(i)
                                val dlUrl = item.getString("download_url").replace("\\/", "")
                                Log.d("Ayzen", "Indirme adresi: $dlUrl")
                                loadExtractor(dlUrl, targetUrl, subtitleCallback) { link ->
                                    linkFound = true
                                    callback(link)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("Ayzen", "Indirme hatasi: ${e.message}")
                    }
                }
            } else {
                loadExtractor(iframeUrl, data, subtitleCallback) { link ->
                    linkFound = true
                    callback(link)
                }
            }
        }

        return linkFound
    }
}