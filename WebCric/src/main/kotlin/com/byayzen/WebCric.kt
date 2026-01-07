// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
class WebCric: MainAPI() {
    override var mainUrl = "https://me.webcric.com"
    override var name = "WebCric"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "LIVE STREAM" to "Live Matches",
        "MATCH END" to "Finished Matches",
        "SERIES END" to "Ended Series",
        "AUCTION END" to "Completed Events"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val home = document.select("div.card.portfolio-item").mapNotNull {
            val status = it.selectFirst("button")?.text()?.uppercase() ?: ""

            if (status != request.data && request.data != "${mainUrl}/") {
                return@mapNotNull null
            }

            if (request.data == "${mainUrl}/" && status != "LIVE STREAM") {
                return@mapNotNull null
            }

            it.toMainPageResult()
        }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val titleElement = this.selectFirst("h3.card-title") ?: return null
        val eventTitle = titleElement.text()
            .replace(Regex("(?i)LIVE STREAM|MATCH END|SERIES END|AUCTION END"), "").trim()
        val category = this.selectFirst("h4.card-title")?.text()?.trim() ?: ""

        val fullTitle = if (category.isNotEmpty()) "[$category] $eventTitle" else eventTitle
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(fullTitle, href, TvType.Live) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val document = app.get(mainUrl).document
        val aramaCevap = document.select("div.card.portfolio-item").mapNotNull {
            val title = it.selectFirst("h3.card-title")?.text() ?: ""
            val category = it.selectFirst("h4.card-title")?.text() ?: ""

            if (title.contains(query, ignoreCase = true) || category.contains(
                    query,
                    ignoreCase = true
                )
            ) {
                it.toMainPageResult()
            } else {
                null
            }
        }

        return newSearchResponseList(aramaCevap)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h2 strong")?.text()?.trim()
            ?: document.selectFirst("h2")?.text()?.trim()
            ?: document.title().trim()
        val poster = fixUrlNull(document.selectFirst("div.col-lg-12.text-center img")?.attr("src"))

        val description = document.selectFirst("h5 p strong")?.text()?.trim()

        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val userAgent =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0"
            val baseHeaders = mapOf(
                "Referer" to "https://me.webcric.com/",
                "User-Agent" to userAgent
            )

            val mainPageSource = app.get(data, headers = baseHeaders).text
            val kanaladi =
                Regex("""channel\s*=\s*['"]([^'"]+)['"]""").find(mainPageSource)?.groupValues?.get(1)
            val gatewayValue =
                Regex("""g\s*=\s*['"]([^'"]+)['"]""").find(mainPageSource)?.groupValues?.get(1)
                    ?: "6"
            val targetChannel = kanaladi ?: "webcrice09"
            val embedUrl =
                "https://web.wayout.top/hembedplayer/$targetChannel/$gatewayValue/850/480"


            val playerHeaders = baseHeaders.plus("Referer" to data)
            val playerSource = app.get(embedUrl, headers = playerHeaders).text

            val duzPk =
                Regex("""var\s+pk\s*=\s*["']([^"']+)["']""").find(playerSource)?.groupValues?.get(1)
                    ?: return false

            val serverHost =
                Regex("""ea\s*=\s*["']([^"']+)["']""").find(playerSource)?.groupValues?.get(1)
                    ?: return false

            val urlPath =
                Regex("""hlsUrl\s*=\s*["']https?://["']\s*\+\s*ea\s*\+\s*["']([^"']+)["']""").find(
                    playerSource
                )?.groupValues?.get(1)
                    ?: return false

            val pktemizle = duzPk.replace(Regex("(773fb)."), "$1")
            val sonUrl = "https://$serverHost$urlPath$pktemizle"
            callback(
                newExtractorLink(
                    source = "WebCric",
                    name = "WebCric",
                    url = sonUrl,
                    type = if (sonUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.referer = embedUrl
                    this.quality = Qualities.Unknown.value
                }
            )
            return true

        } catch (e: Exception) {
            return false
        }
    }
}