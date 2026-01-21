// ! Bu araç @Kraptor123 tarafından | @cs-karma için yazılmıştır.

package com.kraptor

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CricHD : MainAPI() {
    override var mainUrl = "https://crichd.asia"
    override var name = "CricHD"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Live",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}").document
        val home = document.select("a.bg-neutral-800").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("div.text-gray-300")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Live) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val titleTemiz = title.capitalize().split("vs").joinToString("\\nvs\\n").substringBefore("Live").trim()
        val poster = posterHelper(titleTemiz, backgroundColor = "orange", textColor = "black")
        val description = document.selectFirst("div.my-5 h2")?.text()?.trim()
        val year = description?.substringBefore("-")?.substringAfterLast(" ")?.toIntOrNull()
        val tags = document.select("div.text-white.overflow-hidden").map { it.text() }

        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val allLinks = document.select("tbody tr.hover\\:bg-neutral-600")

        allLinks.forEach { link ->
            val href = link.selectFirst("a")?.attr("href") ?: ""
            val name = link.selectFirst("td.py-2.px-4.text-white")?.text()
            val quality = link.selectFirst("td.hidden:nth-child(4)")?.text()
            loadCustomExtractor("$name $quality", href, "${mainUrl}/", subtitleCallback, callback)
        }
        return true
    }
}

fun posterHelper(
    title: String,
    width: Int? = null,
    height: Int? = null,
    backgroundColor: String? = null,
    textColor: String? = null,
    font: String? = null,
): String {
    // Check the documentation on the official website.
    val domain = "https://placehold.co"
    val txtWidth = width ?: 2400
    val txtHeight = height ?: 400
    val bgColor = backgroundColor?.removePrefix("#") ?: "EEE"
    val txtColor = textColor?.removePrefix("#") ?: "31343C"
    val txtFont = font?.lowercase()?.replace(" ", "-") ?: "lato"

    return "$domain/${txtWidth}x${txtHeight}/$bgColor/$txtColor.png?text=$title&font=$txtFont"
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
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    name ?: link.source,
                    name ?: link.name,
                    link.url,
                ) {
                    this.quality = when {
                        else -> quality ?: link.quality
                    }
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
    }
}