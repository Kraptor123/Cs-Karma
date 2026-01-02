// ! Bu araç @kerimmkirac tarafından yazılmıştır.

package com.kerimmkirac

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder

import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class FsTv : MainAPI() {
    override var mainUrl              = "https://fstv.online"
    override var name                 = "FsTv"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        "${mainUrl}/live-tv.html" to "Tüm Kanallar"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}").document
        val home = document.select("div.item-channel").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val rawTitle = this.attr("title").takeIf { it.isNotEmpty() } ?: return null
        val channelId = this.attr("data-id").takeIf { it.isNotEmpty() } ?: return null
        val rawPosterUrl = fixUrlNull(this.attr("data-logo"))
        
       
        val posterUrl = rawPosterUrl?.let { fixImageFormat(it) }
        
        val cleanTitle = rawTitle
            .removePrefix("VE-")
            .removePrefix("3CDN -")
            .removePrefix("cdn -")
            .removePrefix("cdn-")
            .removePrefix("CDN-")
            .removePrefix("VEsv2-")
            .removePrefix("uk -")
            .removePrefix("uk-")
            .removePrefix("usa-")
            .removePrefix("usa -")
            .removePrefix("1cdn -")
            .removePrefix("VEuk-sv2-")
            .replace("ve-","")
            .replace("-us-", "-")
            .replace("-uk-", "-")
            .replace("us-", "")
            .replace("usa-", "")
            .replace("uk-", "")
            .replace("de-", "")
            .replace("DE -", "")
            .replace("DE-", "")
            .replace("uk-", "")
            .trim()
        
        val href = "${mainUrl}/channel/${channelId}"

        return newMovieSearchResponse(cleanTitle, href, TvType.Live) { 
            this.posterUrl = posterUrl 
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        
        val document = app.get("${mainUrl}/live-tv.html").document
        val allChannels = document.select("div.item-channel").mapNotNull { it.toMainPageResult() }
        
        
        return allChannels.filter { 
            it.name.contains(query, ignoreCase = true) 
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val channelId = url.substringAfterLast("/")
        
        val document = app.get("${mainUrl}/live-tv.html").document
        val channelElement = document.selectFirst("div.item-channel[data-id='${channelId}']") ?: return null
        
        val rawTitle = channelElement.attr("title")
        val rawPosterUrl = fixUrlNull(channelElement.attr("data-logo"))
        val streamUrl = channelElement.attr("data-link")
        
       
        val posterUrl = rawPosterUrl?.let { fixImageFormat(it) }
        
        val cleanTitle = rawTitle
            .removePrefix("VE-")
            .removePrefix("3CDN -")
            .removePrefix("cdn -")
            .removePrefix("cdn-")
            .removePrefix("CDN-")
            .removePrefix("VEsv2-")
            .removePrefix("uk -")
            .removePrefix("uk-")
            .removePrefix("usa-")
            .removePrefix("usa -")
            .removePrefix("1cdn -")
            .removePrefix("VEuk-sv2-")
            .replace("ve-","")
            .replace("-us-", "-")
            .replace("-uk-", "-")
            .replace("us-", "")
            .replace("usa-", "")
            .replace("uk-", "")
            .replace("de-", "")
            .replace("DE -", "")
            .replace("DE-", "")
            .replace("uk-", "")
            .trim()
        
        return newMovieLoadResponse(cleanTitle, url, TvType.Live, streamUrl) {
            this.posterUrl = posterUrl
            this.plot = "Kanal adı : $cleanTitle"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback(
            newExtractorLink(
                name,
                name,
                data,
                type = if (data.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ){
                this.referer = "https://fstv.us"
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }

    private fun fixImageFormat(url: String): String {
        if (url.isEmpty()) return ""
        
        
        return if (url.contains(".svg", ignoreCase = true)) {
            try {
                val encodedUrl = URLEncoder.encode(url, "UTF-8")
                "https://res.cloudinary.com/di0j4jsa8/image/fetch/f_auto/$encodedUrl"
            } catch (e: Exception) {
                url
            }
        } else {
            url 
        }
    }
}