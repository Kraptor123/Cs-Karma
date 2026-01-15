// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import android.util.Log
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

    val smartCric = "https://webcric.watchcric.com/"

    override val mainPage = mainPageOf(
        "LIVE STREAM" to "Live Matches",
        smartCric to "Smart Cric",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        if (request.data.contains(smartCric)) {
            val document = app.get(request.data).document
            val items = document.select("section.matches a").mapNotNull { item ->
                val href = item.attr("href")
                if (href.isBlank()) return@mapNotNull null

                val title = item.selectFirst("h2")?.text()?.trim()
                    ?: return@mapNotNull null

                newMovieSearchResponse(
                    name = title,
                    url = href,
                    type = TvType.Live
                ) {
                    this.posterUrl = "https://placehold.co/400x800/white/black.png?text=Smart\\nCric"
                }
            }

            val homePageList = HomePageList(
                name = "SmartCric",
                list = items
            )

            return newHomePageResponse(
                listOf(homePageList),
                hasNext =false
            )
    }else{
            val home = document.select("div.card.portfolio-item").mapNotNull { item ->
                val status = item.selectFirst("button")?.text()?.uppercase() ?: ""

                if (status != request.data && request.data != "${mainUrl}/") {
                    return@mapNotNull null
                }

                if (request.data == "${mainUrl}/" && status != "LIVE STREAM") {
                    return@mapNotNull null
                }

                item.toMainPageResult()
            }

            return newHomePageResponse(request.name, home, false)
        }
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val titleElement = this.selectFirst("h3.card-title") ?: return null
        val eventTitle = titleElement.text()
            .replace(Regex("(?i)LIVE STREAM|MATCH END|SERIES END|AUCTION END"), "").trim()
        val category = this.selectFirst("h4.card-title")?.text()?.trim() ?: ""

        val fullTitle = if (category.isNotEmpty()) "[$category] $eventTitle" else eventTitle
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val tumKaynaklar = "https://" + this.select("div.card.portfolio-item p.small ~ a").joinToString("|") {
            it.attr("href")
                .replace("https://", "")
        }

        return newMovieSearchResponse(fullTitle, tumKaynaklar, TvType.Live) {
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

        return newSearchResponseList(aramaCevap, false)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        if (!url.contains("|")){
            val document = app.get(url).document
            val title = document.selectFirst("h2 strong")?.text()?.trim()
                ?: document.selectFirst("h2")?.text()?.trim()
                ?: document.title().trim()
            val poster = "https://placehold.co/1600x400/white/black.png?text=Smart\\nCric"

            val description = document.selectFirst("meta[name=description]")?.attr("content")?.trim()

            val data = document.selectFirst("script:containsData(channel)")?.data()
                ?.substringAfter("channel=")
                ?.substringBefore(";")
                ?.replace("'", "")
                ?.replace(" ", "")
                ?.replace(",e=", "/")

            val dataUrl = "https://fembedbuddy.top/hembedplayer/$data/960/540"

            Log.d("kraptor_Smart","data = $data")

            return newMovieLoadResponse(title, url, TvType.Live, dataUrl) {
                this.posterUrl = poster
                this.plot = description
            }
        }else {
            val split = url.split('|')
            val dataUrl = url.replace("|","|https://")
            val url = split[0]
            val document = app.get(url).document
            val title = document.selectFirst("h2 strong")?.text()?.trim()
                ?: document.selectFirst("h2")?.text()?.trim()
                ?: document.title().trim()
            val poster = fixUrlNull(document.selectFirst("div.col-lg-12.text-center img")?.attr("src"))

            val description = document.selectFirst("h5 p strong")?.text()?.trim()

            return newMovieLoadResponse(title, url, TvType.Live, dataUrl) {
                this.posterUrl = poster
                this.plot = description
            }
        }

    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("kraptor_bune", "data = $data")
        if (data.contains("fembedbuddy")){
            val playerSource = app.get(data, referer = "https://webcric.watchcric.com/").text

            val serverHost =
                Regex(pattern = "ea = \"([^\"]+)\"", options = setOf(RegexOption.IGNORE_CASE)).find(playerSource)?.groupValues?.get(1)
                    ?: return false

            val urlPath =
                Regex(pattern = "hlsUrl = \"https://\" \\+ ea \\+ \"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE)).find(
                    playerSource
                )?.groupValues?.get(1)
                    ?: return false

            val duzPk =
                Regex(pattern = "hlsUrl = hlsUrl \\+ enableVideo\\(\"([^\"]*)\"\\);", options = setOf(RegexOption.IGNORE_CASE)).find(playerSource)?.groupValues?.get(1)
                    ?: return false

            val pk = duzPk.replace(Regex("\\s+"), "")
                .replace(Regex("[A-Z]"), "")
                .replace(Regex("[^0-9a-f]"), "")

            val sonUrl = "https://$serverHost$urlPath$pk"
                .replace(Regex("[A-Z]"), "")


            Log.d("kraptor_bune", "sonUrl = $sonUrl")

            callback.invoke(
                newExtractorLink(
                    source = "WebCric",
                    name = "WebCric",
                    url = sonUrl,
                    type = if (sonUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.referer = "https://fembedbuddy.top/"
                    this.quality = Qualities.Unknown.value
                }
            )

        }else {
            val urlSplit = data.split('|')
            urlSplit.forEach { url ->
            Log.d("kraptor_bune", "url = $url")
                try {
                    val userAgent =
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0"
                    val baseHeaders = mapOf(
                        "Referer" to "https://me.webcric.com/",
                        "User-Agent" to userAgent
                    )

                    val mainPageSource = app.get(url, headers = baseHeaders).document
                    val iframe = fixUrlNull(mainPageSource.selectFirst("div.container.text-center iframe")?.attr("src")) ?: ""
                Log.d("kraptor_bune", "iframe = $iframe")
                    val iframeAl = app.get(iframe, mapOf(
                        "Host" to "me.webcric.com",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                        "Accept-Language" to "en-US,en;q=0.5",
                        "Referer" to "${mainUrl}/",
                        "Sec-GPC" to "1",
                        "Connection" to "keep-alive",
                        "Cookie" to "vuid=beaf7280-bff2-4e9f-ad59-d7a35dcf4511",
                        "Upgrade-Insecure-Requests" to "1",
                        "Sec-Fetch-Dest" to "iframe",
                        "Sec-Fetch-Mode" to "navigate",
                        "Sec-Fetch-Site" to "same-origin",
                        "Priority" to "u=4",
                        "Pragma" to "no-cache",
                        "Cache-Control" to "no-cache",
                        "TE" to "trailers"
                    )).text
                    val kanaladi =
                        Regex("""channel\s*=\s*['"]([^'"]+)['"]""").find(iframeAl)?.groupValues?.get(1)
                    val gatewayValue =
                        Regex("""g\s*=\s*['"]([^'"]+)['"]""").find(iframeAl)?.groupValues?.get(1)
                            ?: ""
                    val targetChannel = kanaladi ?: ""
                    val embedUrl =
                        "https://web.wayout.top/hembedplayer/$targetChannel/$gatewayValue/850/480"

                Log.d("kraptor_bune", "embedUrl = $embedUrl")


                    val playerHeaders = baseHeaders.plus("Referer" to url)
                    val playerSource = app.get(embedUrl, headers = playerHeaders).text

                    val duzPk =
                        Regex(pattern = "var pk = \"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE)).find(playerSource)?.groupValues?.get(1)
                            ?.replace(Regex("[^0-9a-f]"), "")
                            ?: return false

                    val serverHost =
                        Regex(pattern = "ea = \"([^\"]+)\"", options = setOf(RegexOption.IGNORE_CASE)).find(playerSource)?.groupValues?.get(1)
                            ?: return false

                    val urlPath =
                        Regex(pattern = "hlsUrl = \"https://\" \\+ ea \\+ \"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE)).find(
                            playerSource
                        )?.groupValues?.get(1)
                            ?: return false

                    val sonUrl = "https://$serverHost$urlPath$duzPk"
                        .replace(Regex("[A-Z]"), "")
                Log.d("kraptor_bune", "sonUrl = $sonUrl")
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
                } catch (e: Exception) {
                    Log.e("kraptor_bune", "error for url=$url", e)
                }
        }
        }
        return true
    }
}