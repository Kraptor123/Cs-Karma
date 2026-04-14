// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.

package com.kraptor

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class WatchWrestling : MainAPI() {
    override var mainUrl = "https://watchwrestling.ae/"
    override var name = "WatchWrestling"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Wrestling",
        "${mainUrl}/ufc41/" to "UFC",
        "${mainUrl}/njpw51/" to "New Japan Pro Wrestling",
        "${mainUrl}/roh24/" to "Ring Of Honor",
        "${mainUrl}/aew65/" to "All Elite Wrestling",
        "${mainUrl}/other-wrestling30/" to "Other Wrestling",
        "${mainUrl}/impact-wrestlingss30/" to "Impact Wrestling",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/page/$page/").document
        val home = document.select("div.loop-content div.item").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
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
        val jobs = mutableListOf<kotlinx.coroutines.Job>()

        val scripts = document.select("script")
        var hiddenHtml = ""

        scripts.forEach { script ->
            val content = script.data()
            if (content.contains("episodeRepeater") && content.contains("textarea")) {
                hiddenHtml = content.substringAfter("<textarea", "").substringAfter("'>", "").substringBefore("</textarea>")
                if (hiddenHtml.isEmpty()) {
                    hiddenHtml = content.substringAfter("<textarea", "").substringAfter("\">", "").substringBefore("</textarea>")
                }
            }
        }

        if (hiddenHtml.isEmpty()) return@coroutineScope false

        val innerDoc = org.jsoup.Jsoup.parse(hiddenHtml)
        val repeaters = innerDoc.select("div.episodeRepeater")

        repeaters.forEach { block ->
            val hostTitle = block.selectFirst("h1")?.text()
                ?.replace("Watch ", "", ignoreCase = true)
                ?.replace("HD", "", ignoreCase = true)
                ?.replace("720P", "", ignoreCase = true)
                ?.trim() ?: "Server"

            val links = block.select("a")

            links.forEach { linkElement ->
                var videoUrl = linkElement.attr("href")
                val partLabel = linkElement.text().trim()

                if (videoUrl.isNotBlank()) {
                    if (videoUrl.startsWith("//")) {
                        videoUrl = "https:$videoUrl"
                    }

                    val job = launch {
                        try {
                            loadCustomExtractor(
                                name = "$hostTitle-$partLabel",
                                url = videoUrl,
                                referer = data,
                                subtitleCallback = subtitleCallback,
                                callback = callback
                            )
                        } catch (e: Exception) {
                        }
                    }
                    jobs.add(job)
                }
            }
        }

        jobs.joinAll()
        true
    }

    suspend fun loadCustomExtractor(
        name: String? = null,
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        quality: Int? = null,
    ) {
        loadExtractor(url, referer, subtitleCallback) { link ->
            if (link.url.isNotBlank() && (link.url.startsWith("http") || link.url.startsWith("https"))) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    callback.invoke(
                        newExtractorLink(
                            source = name ?: link.source,
                            name = name ?: link.name,
                            url = link.url,
                        ) {
                            this.quality = quality ?: link.quality
                            this.type = link.type
                            this.referer = link.referer
                            this.headers = link.headers
                            this.extractorData = link.extractorData
                        }
                    )
                }
            }
        }
    }
    }