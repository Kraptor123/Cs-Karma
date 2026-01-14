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
import java.util.regex.Pattern

class AnimeYTX : MainAPI() {
    override var mainUrl = "https://animeytx.net"
    override var name = "AnimeYTX"
    override val hasMainPage = true
    override var lang = "es"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Anime)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "$mainUrl/anime/" to "Anime reciente",
        "$mainUrl/tv/?type=&sub=&order=" to "All Anime",
        "$mainUrl/tv/?status=ongoing" to "Ongoing",
        "$mainUrl/tv/?status=completed&type=&sub=&order=" to "Completed",
        "$mainUrl/tv/?status=hiatus&type=&sub=&order=" to "Hiatus",
        "$mainUrl/tv/?status=upcoming" to "Upcoming"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = request.data
        val isNewCategory = data.contains("/tv/")
        val url = when {
            page <= 1 -> data
            isNewCategory -> "$data${if (data.contains("?")) "&" else "?"}page=$page"
            else -> "${data.trimEnd('/')}/page/$page/"
        }
        val document = app.get(url).document
        val items = document.select("article.bs").mapNotNull {
            if (isNewCategory) it.ikinciSearchResults() else it.toSearchResult()
        }

        return newHomePageResponse(request.name, items, items.isNotEmpty())
    }

    private fun Element.ikinciSearchResults(): SearchResponse? {
        val title = selectFirst(".tt")?.ownText()?.trim()
            ?: selectFirst(".tt h2")?.text()?.trim()
            ?: return null

        val href = selectFirst("a")?.attr("href") ?: return null

        val poster = selectFirst("img")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }

        val isMovie = selectFirst(".typez")?.text()?.contains("Movie", true) == true

        return newAnimeSearchResponse(
            title,
            href,
            if (isMovie) TvType.AnimeMovie else TvType.Anime
        ) {
            this.posterUrl = poster
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst(".tt")?.ownText()?.trim() ?: return null
        val href = selectFirst("a")?.attr("href") ?: return null

        val poster = selectFirst("img")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }

        val isMovie = select(".typez").text().contains("Movie", true)

        return newAnimeSearchResponse(
            title,
            href,
            if (isMovie) TvType.AnimeMovie else TvType.Anime
        ) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val url = if (page <= 1) "$mainUrl/?s=$query" else "$mainUrl/page/$page/?s=$query"
        val document = app.get(url).document

        val items = document.select("article.bs").mapNotNull {
            it.toSearchResult()
        }

        return newSearchResponseList(items)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
                ?.replace("Sub Español - AnimeYT", "")?.trim()
            ?: return null

        val poster = document.selectFirst(".thumb img")?.attr("data-src")
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")
        val plot = document.selectFirst(".entry-content[itemprop=description] p")?.text()
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")
        val year = Regex("""(\d{4})""").find(
            document.select(".spe span").firstOrNull { it.text().contains("Estreno:") }?.text()
                ?: ""
        )?.groupValues?.get(1)?.toIntOrNull()
        val type = if (url.contains("/tv/")) TvType.Anime else TvType.AnimeMovie

        val status =
            document.select(".spe span").firstOrNull { it.text().contains("Estado:") }?.text()
                ?.let {
                    if (it.contains(
                            "Finalizado",
                            true
                        )
                    ) ShowStatus.Completed else if (it.contains(
                            "En curso",
                            true
                        )
                    ) ShowStatus.Ongoing else null
                }

        val cast = document.select(".cvlist .cvitem").mapNotNull { item ->
            val actorName =
                item.selectFirst(".cvactor .charname")?.text()?.trim() ?: return@mapNotNull null
            val charName = item.selectFirst(".cvchar .charname")?.text()?.trim()
            val actorImg = item.selectFirst(".cvactor img")
                ?.let { it.attr("data-src").ifEmpty { it.attr("src") } }
            Actor(actorName, actorImg) to charName
        }

        val episodes = document.select(".eplister ul li").mapNotNull { element ->
            val link = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val num = element.selectFirst(".epl-num")?.text()?.trim()?.toIntOrNull()
            val name = element.selectFirst(".epl-title")?.text()?.trim()

            newEpisode(link) {
                this.name = name
                this.episode = num
            }
        }.reversed()

        return newAnimeLoadResponse(title, url, type) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.showStatus = status
            this.tags = document.select(".genxed a").map { it.text() }
            this.recommendations = document.select("article.bs").mapNotNull { it.toSearchResult() }
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
        try {
            app.get(data).document.select("select.mirror option").forEach { option ->
                val encodedValue = option.attr("value").takeIf { it.isNotBlank() } ?: return@forEach
                val serverName = option.text()

                val iframeUrl = Regex("src=\"(.*?)\"").find(
                    String(Base64.decode(encodedValue, Base64.DEFAULT))
                )?.groupValues?.get(1) ?: return@forEach

                if (iframeUrl.contains("vipbanner") || serverName.contains("VIP", true)) return@forEach

                if (iframeUrl.contains("mytsumi.com/multiplayer") || iframeUrl.contains("/options.php")) {
                    val containerLink = app.get(iframeUrl, referer = data).document.select("div.play a").attr("href")
                    if (containerLink.isBlank()) return@forEach

                    val pageText = app.get(containerLink, referer = iframeUrl).text
                    Regex("""const\s+videoTabs\s*=\s*(\[.*?\]);""", RegexOption.DOT_MATCHES_ALL).find(pageText)?.groupValues?.get(1)?.let { json ->
                        val jsonArray = JSONArray(json)
                        for (i in 0 until jsonArray.length()) {
                            val tab = jsonArray.getJSONObject(i)
                            val url = tab.getString("url").replace("\\/", "/")

                            if (url.isNotBlank() && url != "about:blank") {
                                if (url.contains("mytsumiplay/player.php")) {
                                    val rawUrl = app.get(url, referer = containerLink).document.select("source").attr("src")
                                    if (rawUrl.isNotEmpty()) {
                                        callback(
                                            newExtractorLink(
                                                source = "Mytsumi - Raw",
                                                name = "Mytsumi - Raw",
                                                url = rawUrl,
                                                type = ExtractorLinkType.VIDEO
                                            ) {
                                                this.referer = url
                                                this.quality = Qualities.Unknown.value
                                            }
                                        )
                                    }
                                } else {
                                    loadExtractor(url, containerLink, subtitleCallback, callback)
                                }
                            }
                        }
                    }

                    Regex("""const\s+downloadsByQuality\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL).find(pageText)?.groupValues?.get(1)?.let { json ->
                        val dlJson = JSONObject(json)
                        dlJson.keys().forEach { quality ->
                            val items = dlJson.getJSONArray(quality)
                            for (i in 0 until items.length()) {
                                val item = items.getJSONObject(i)
                                val dlUrl = item.getString("download_url").replace("\\/", "/")
                                if (!dlUrl.contains("fireload.com") && !item.getString("server_name").contains("FireLoad")) {
                                    loadExtractor(dlUrl, containerLink, subtitleCallback, callback)
                                }
                            }
                        }
                    }
                }
                else {
                    callback(
                        newExtractorLink(
                            source = serverName,
                            name = serverName,
                            url = iframeUrl,
                            type = if (iframeUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = data
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }
}