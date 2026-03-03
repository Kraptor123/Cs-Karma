package com.byayzen

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class BasketballReplays : MainAPI() {
    override var mainUrl = "https://basketballreplays.net"
    override var name = "BasketballReplays"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live, TvType.Others)

    override val mainPage = mainPageOf(mainUrl to "All Matches")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) mainUrl else "${mainUrl}/?page${page}"
        val document = app.get(url).document
        val items = document.select("div[id^=\"entryID\"]").mapNotNull { it.toMainPageResult() }
        return newHomePageResponse(request.name, items, items.isNotEmpty())
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("div.h_post_title a")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.selectFirst("div.h_post_title a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.h_post_img img")?.attr("src"))
        return newMovieSearchResponse(title, href, TvType.Others) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val baseUrl = "${mainUrl}/search/?q=${query}&m=site&m=publ&t=0"
        val document = app.get(baseUrl).document
        val items = document.select("table.eBlock").mapNotNull { it.toSearchResult() }
        val hasNext = document.selectFirst("a.swchItem-next") != null
        return newSearchResponseList(items, hasNext)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.eTitle a")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.selectFirst("div.eTitle a")?.attr("href")) ?: return null
        return newMovieSearchResponse(title, href, TvType.Others)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.h_title")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.full_img img")?.attr("src"))
        val description = document.selectFirst("meta[name=description]")?.attr("content")?.trim()
        val year = Regex("""(\d{4})""").find(title)?.groupValues?.get(1)?.toIntOrNull()
        val tags = document.select("span.ed-value a.entAllCats").map { it.text() }
        val rating = document.selectFirst("div.h_block.h_block_socials span")?.text()
            ?.substringAfter("Rating: ")?.substringBefore("/")?.trim()?.toFloatOrNull()
        val recommendations = document.select("div.aside_content div.inf_recom")
            .mapNotNull { it.toRecommendationResult() }

        return newMovieLoadResponse(title, url, TvType.Others, url) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            this.score = rating?.let { Score.from10(it) }
            this.recommendations = recommendations
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val titleElement = this.selectFirst("div.inf_recom_descr h4 a") ?: return null
        val title = titleElement.text().trim()
        val href = fixUrlNull(titleElement.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.inf_recom_img img")?.attr("src"))
        return newMovieSearchResponse(title, href, TvType.Others) { this.posterUrl = posterUrl }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val anasayfa = app.get(data).document
        val videolinki = anasayfa.selectFirst("div.h_post_desc a.su-button")?.attr("href")

        if (videolinki.isNullOrEmpty()) {
            return false
        }

        val videosayfasi = app.get(videolinki).document
        val kaynaklar = videosayfasi.select("div.entry__article iframe").mapNotNull {
            it.attr("src").takeIf { src -> src.isNotEmpty() }
        }

        if (kaynaklar.isEmpty()) {
            return false
        }

        for (url in kaynaklar) {
            val link = if (url.startsWith("//")) "https:$url" else url
            loadExtractor(link, videolinki, subtitleCallback, callback)
        }

        return true
    }
}