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
    override val supportedTypes = setOf(TvType.Live)

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

    // Bu data class'ı ana API sınıfınızın içine (class FilmMirasim : MainAPI() { ... }) ekleyin.
    private data class OkRuVideo(
        @JsonProperty("name") val name: String,
        @JsonProperty("url") val url: String,
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("EklentiAdi", "loadLinks data = $data")
        val document = app.get(data).document

        val iframeUrls = document.select("div.h_post_desc iframe").map {
            it.attr("src").trim()
        }.filter { it.isNotEmpty() }

        if (iframeUrls.isEmpty()) {
            Log.d("EklentiAdi", "Sayfada iframe bulunamadı.")
            return false
        }

        var anyLinkLoaded = false
        for (url in iframeUrls) {
            val fixedUrl = if (url.startsWith("//")) {
                "https:$url"
            } else {
                url
            }

            Log.d("EklentiAdi", "Bulunan iframe URL: $fixedUrl")

            if (fixedUrl.contains("ok.ru")) {
                // --- OK.RU İÇİN ÖZEL MANTIK BAŞLANGICI ---
                Log.d("EklentiAdi", "Ok.ru linki işleniyor...")

                val okRuHeaders = mapOf(
                    "Accept" to "*/*",
                    "Connection" to "keep-alive",
                    "Sec-Fetch-Dest" to "empty",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "cross-site",
                    "Origin" to "https://odnoklassniki.ru",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36",
                    "Referer" to fixedUrl // ÇOK ÖNEMLİ: Referer olarak embed sayfasının kendisi
                )

                val embedUrl = fixedUrl.replace("/video/", "/videoembed/")
                try {
                    val videoReqText = app.get(embedUrl, headers = okRuHeaders).text
                        .replace("\\&quot;", "\"")
                        .replace("\\\\", "\\")
                        .replace(Regex("\\\\u([0-9A-Fa-f]{4})")) { matchResult ->
                            Integer.parseInt(matchResult.groupValues[1], 16).toChar().toString()
                        }

                    val videosStr =
                        Regex(""""videos":(\[[^]]*])""").find(videoReqText)?.groupValues?.get(1)
                    if (videosStr != null) {
                        val videos = AppUtils.tryParseJson<List<OkRuVideo>>(videosStr)
                        if (videos != null) {
                            for (video in videos) {
                                val videoUrl =
                                    if (video.url.startsWith("//")) "https:${video.url}" else video.url
                                val quality = video.name.uppercase()
                                    .replace("MOBILE", "144p")
                                    .replace("LOWEST", "240p")
                                    .replace("LOW", "360p")
                                    .replace("SD", "480p")
                                    .replace("HD", "720p")
                                    .replace("FULL", "1080p")
                                    .replace("QUAD", "1440p")
                                    .replace("ULTRA", "4k")

                                callback.invoke(
                                    newExtractorLink(
                                        source = "Odnoklassniki",
                                        name = "Odnoklassniki $quality",
                                        url = videoUrl,
                                        INFER_TYPE
                                    ) {
                                        this.referer = fixedUrl
                                        this.quality = getQualityFromName(quality)
                                        this.headers = okRuHeaders
                                    }
                                )
                                anyLinkLoaded = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("EklentiAdi", "Ok.ru işlenirken hata: ${e.message}")
                }
                // --- OK.RU İÇİN ÖZEL MANTIK SONU ---

            } else {
                // --- DİĞER SİTELER (FILEMOON VB.) İÇİN LOAD EXTRACTOR ---
                Log.d("EklentiAdi", "Diğer extractor yönlendiriliyor: $fixedUrl")
                val loaded = loadExtractor(fixedUrl, data, subtitleCallback, callback)
                if (loaded) {
                    anyLinkLoaded = true
                }
            }
        }

        return anyLinkLoaded
    }
}