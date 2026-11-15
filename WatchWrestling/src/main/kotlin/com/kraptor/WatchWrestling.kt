// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.

package com.kraptor

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import java.net.URLDecoder
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class WatchWrestling : MainAPI() {
    override var mainUrl = "https://watchwrestling.ae"
    override var name = "WatchWrestling"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Wrestling",
        "${mainUrl}/wwe56/" to "WWE",
        "${mainUrl}/wwe-raw53/" to "WWE Raw",
        "${mainUrl}/wwe-smackdown56/" to "WWE Smackdown",
        "${mainUrl}/main-events/" to "WWE Main Event",
        "${mainUrl}/wwe-nxt-show/" to "WWE NXT",
        "${mainUrl}/wwe-ppv61/" to "WWE PPV",
        "${mainUrl}/wwe-totaldvas30/" to "WWE Total Divas",
        "${mainUrl}/impact-wrestlingss31/" to "IMPACT Wrestling",
        "${mainUrl}/ufc43/" to "UFC",
        "${mainUrl}/njpw52/" to "NJPW",
        "${mainUrl}/roh25/" to "ROH",
        "${mainUrl}/aew69/" to "AEW (All Elite Wrestling)",
        "${mainUrl}/other-wrestling31/" to "Other Wrestling",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/page/$page/").document
        val home = document.select("div.loop-content div.item").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            )
        )
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Live) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}").document

        return document.select("div.loop-content div.item").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Live) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("img.size-full")?.attr("src"))
        val description = document.selectFirst("div.entry-content p:nth-child(1)")?.text()?.trim()
        val tags = document.select("div#extras a").map { it.text() }
        val recommendations = document.select("div.item").mapNotNull { it.toRecommendationResult() }

        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.recommendations = recommendations
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Live) { this.posterUrl = posterUrl }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = coroutineScope {
        val document = app.get(data).document
        val jobs     = mutableListOf<kotlinx.coroutines.Job>()
        val found    = AtomicBoolean(false)

        val directRepeaters = document.select("div#displayContent div.episodeRepeater")
        val innerDoc = if (directRepeaters.isNotEmpty()) {
            document
        } else {
            var hiddenHtml = ""
            document.select("script").forEach { script ->
                val content = script.data()
                if (content.contains("episodeRepeater")) {
                    hiddenHtml = content.substringAfter("<textarea", "")
                        .substringAfter("'>", "")
                        .substringBefore("</textarea>")
                    if (hiddenHtml.isEmpty()) {
                        hiddenHtml = content.substringAfter("<textarea", "")
                            .substringAfter("\">", "")
                            .substringBefore("</textarea>")
                    }
                }
            }
            if (hiddenHtml.isEmpty()) return@coroutineScope false
            org.jsoup.Jsoup.parse(hiddenHtml)
        }

        innerDoc.select("div.episodeRepeater").forEach { block ->
            val hostTitle = block.selectFirst("h1")?.text()
                ?.replace("Watch ", "", ignoreCase = true)
                ?.replace("HD", "", ignoreCase = true)
                ?.replace("720P", "", ignoreCase = true)
                ?.trim() ?: "Server"

            block.select("a").forEach { linkElement ->
                val href      = linkElement.attr("href")
                val partLabel = linkElement.text().trim()

                if (href.isNotBlank()) {
                    val videoUrl = fixUrl(
                        if (href.contains("away.php")) URLDecoder.decode(href.substringAfter("to="), "UTF-8") else href
                    )

                    val job = launch {
                        try {
                            val midDoc     = app.get(videoUrl, referer = data).document
                            val fastvidSrc = midDoc.selectFirst("iframe")?.attr("src")

                            if (!fastvidSrc.isNullOrBlank() && !fastvidSrc.startsWith("javascript")) {
                                val fastvidUrl = fixUrl(fastvidSrc)
                                val fastDoc    = app.get(fastvidUrl, referer = videoUrl).document
                                val embedSrc   = fastDoc.selectFirst("iframe")?.attr("src")
                                val sourceName = "$hostTitle - $partLabel"

                                if (!embedSrc.isNullOrBlank() && !embedSrc.startsWith("javascript")) {
                                    if (loadCustomExtractor(sourceName, fixUrl(embedSrc), fastvidUrl, subtitleCallback, callback)) {
                                        found.set(true)
                                    }
                                } else {
                                    val encoded = fastDoc.selectFirst("script:containsData(atob)")?.data()
                                        ?.let { Regex("""file:window\.atob\("([^"]+)"\)""").find(it) }
                                        ?.groupValues?.get(1)
                                    if (encoded != null) {
                                        callback.invoke(
                                            newExtractorLink(sourceName, sourceName, fixUrl(base64Decode(encoded)), ExtractorLinkType.M3U8) {
                                                this.referer = fastvidUrl
                                            }
                                        )
                                        found.set(true)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.d("WatchWrestling", "$hostTitle-$partLabel hata: ${e.message}")
                        }
                    }
                    jobs.add(job)
                }
            }
        }

        jobs.joinAll()
        found.get()
    }

    private suspend fun loadCustomExtractor(
        name: String,
        url: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = Collections.synchronizedList(mutableListOf<ExtractorLink>())
        loadExtractor(url, referer, subtitleCallback) { links.add(it) }
        val valid = links.toList().filter { it.url.startsWith("http") }
        valid.forEach { link ->
            callback.invoke(
                newExtractorLink(name, name, link.url) {
                    this.quality       = link.quality
                    this.type          = link.type
                    this.referer       = link.referer
                    this.headers       = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
        return valid.isNotEmpty()
    }
}
