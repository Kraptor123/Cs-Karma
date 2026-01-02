//ByAyzen tarafından falan filan yapıldı işte

package com.byayzen

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors

class `7Ebtv` : MainAPI() {
    override var mainUrl = "https://7abtv.live"
    override var name = "7Ebtv"
    override val hasMainPage = true
    override var lang = "ar"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie)


    override val mainPage = mainPageOf(
        "${mainUrl}/series/page/" to "Series"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${mainUrl}/series/page/$page/"
        val document = app.get(url).document
        val home = document.select("div.row article").mapNotNull { it.toMainPageResult() }
        return newHomePageResponse("Series", home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val title = selectFirst("div.title")?.text() ?: return null
        val posterUrl = selectFirst("div.imgser")
            ?.attr("style")
            ?.let { Regex("url\\((.*?)\\)").find(it)?.groupValues?.get(1) }
            ?.let(::fixUrlNull)

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document

        val aramaCevap = document.select("div.row article")
            .mapNotNull { it.toSearchResult() }

        return aramaCevap
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val title = selectFirst("div.title")?.text() ?: return null

        val posterUrl = selectFirst("div.posterThumb div.imgBG")
            ?.attr("style")
            ?.let { Regex("url\\((.*?)\\)").find(it)?.groupValues?.get(1) }
            ?.let(::fixUrlNull)

        val type = TvType.Movie

        return if (type == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, href, type) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, type) {
                this.posterUrl = posterUrl
            }
        }
    }


    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
//        Log.d("7ebtv","url = $url")
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(
            document.selectFirst("div.imgser")?.attr("style")
                ?.let { Regex("url\\((.*?)\\)").find(it)?.groupValues?.get(1) })
        val description = document.selectFirst("div.story")?.text()?.trim()
        val year = document.selectFirst("div.extra span.C a")?.text()?.trim()?.toIntOrNull()
        val tags = document.select("div.sgeneros a, .info a[href*=\"/genre\"], a[rel~=\"tag\"]")
            .mapNotNull { it.text().trim().takeIf(String::isNotEmpty) }.distinct()
        val actors = tags.map(String::lowercase).toSet().let { s ->
            document.select("span.tax a, span.valor a, .info .tax a")
                .map { it.text().trim() }.filter { it.isNotEmpty() && it.lowercase() !in s }
                .distinct().map(::Actor)
        }
        val status = if (document.selectFirst("span.final") != null) {
            ShowStatus.Completed
        } else {
            ShowStatus.Ongoing
        }
        val episodes = mutableListOf<Episode>()
        val script = document.selectFirst("script:containsData(vo_theme_dir)")?.data().toString()
        val themeDir = """vo_theme_dir\s*=\s*"([^"]+)"""".toRegex().find(script)?.groupValues[1]
        val seasonButtons = document.select("ul.listSeasons2 li")
        if (seasonButtons.isNotEmpty() && themeDir != null) {
            seasonButtons.forEach { seasonButton ->
                val seasonId = seasonButton.attr("data-season")
                val seasonText = seasonButton.text().trim()
                val seasonNumber = seasonText.replace("""[^\d]""".toRegex(), "").toIntOrNull() ?: 1

//                Log.d("7ebtv", "Sezon text: '$seasonText' -> Numara: $seasonNumber")

                if (seasonId.isNotEmpty()) {
                    try {
                        val seasonAjaxUrl = "$themeDir/temp/ajax/seasons2.php?seriesID=$seasonId"
//                        Log.d("7ebtv", "Sezon $seasonNumber (ID: $seasonId) AJAX URL: $seasonAjaxUrl")

                        val seasonResponse = app.get(
                            seasonAjaxUrl,
                            headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
                                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                                "Referer" to "${mainUrl}/",
                            )
                        )

                        val seasonDoc = seasonResponse.document
                        val seasonArticles = seasonDoc.select("article.postEp")
//                        Log.d("7ebtv", "Sezon $seasonNumber - Bölüm sayısı: ${seasonArticles.size}")

                        seasonArticles.forEachIndexed { index, episodeDiv ->
                            val episodeUrl = episodeDiv.selectFirst("a")?.attr("href")?.let { episodeUrl ->
                                if (!episodeUrl.contains("?do=views")) {
                                    "$episodeUrl?do=views"
                                } else {
                                    episodeUrl
                                }
                            }
                            val episodeTitle = episodeDiv.selectFirst("div.title")?.text()

                            val episodePoster = episodeDiv.selectFirst("div.imgser")?.attr("style")?.let { style ->
                                val regex = """url\((.*?)\)""".toRegex()
                                regex.find(style)?.groupValues?.get(1)
                            }?.let { fixUrlNull(it) }

                            val episodeNumber = episodeDiv.selectFirst("div.episodeNum span")?.text()?.toIntOrNull()
                                ?: (index + 1)

                            if (!episodeUrl.isNullOrEmpty() && !episodeTitle.isNullOrEmpty()) {
                                episodes.add(
                                    newEpisode(episodeUrl) {
                                        name = episodeTitle
                                        season = seasonNumber
                                        episode = episodeNumber
                                        posterUrl = episodePoster
                                    }
                                )
                            }
                        }

                    } catch (e: Exception) {
//                        Log.d("7ebtv", "Sezon $seasonNumber hatası: ${e.message}")
                    }
                }
            }
        } else {
            val allArticles = document.select("article.postEp")
//            Log.d("7ebtv", "Tek sezon - Bölüm sayısı: ${allArticles.size}")

            allArticles.forEachIndexed { index, episodeDiv ->
                val episodeUrl = episodeDiv.selectFirst("a")?.attr("href")?.let { episodeUrl ->
                    if (!episodeUrl.contains("?do=views")) {
                        "$episodeUrl?do=views"
                    } else {
                        episodeUrl
                    }
                }
                val episodeTitle = episodeDiv.selectFirst("div.title")?.text()

                val episodePoster = episodeDiv.selectFirst("div.imgser")?.attr("style")?.let { style ->
                    val regex = """url\((.*?)\)""".toRegex()
                    regex.find(style)?.groupValues?.get(1)
                }?.let { fixUrlNull(it) }

                val episodeNumber = episodeDiv.selectFirst("div.episodeNum span")?.text()?.toIntOrNull()
                    ?: (index + 1)

                if (!episodeUrl.isNullOrEmpty() && !episodeTitle.isNullOrEmpty()) {
                    episodes.add(
                        newEpisode(episodeUrl) {
                            name = episodeTitle
                            season = 1
                            episode = episodeNumber
                            posterUrl = episodePoster
                        }
                    )
                }
            }
        }


        return newTvSeriesLoadResponse(
            title,
            url,
            TvType.TvSeries,
            episodes
        ) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.showStatus = status
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
//        Log.d("7ebtv", "LoadLinks çağrıldı - URL: $data")
        val document = app.get(data, referer = "${mainUrl}/", timeout = 10000L).document
        val script = document.selectFirst("script:containsData(vo_theme_dir)")?.data()
//        Log.d("7ebtv", "script: $script")
        val postId = script?.substringBeforeLast("\"")?.substringAfterLast("\"")
        val themeDir = script?.substringAfter("\"")?.substringBefore("\"")
//        Log.d("7ebtv", "Post ID: $postId, Theme Dir: $themeDir")
        val servers = document.select("ul.serversList li")
//        Log.d("7ebtv", "Bulunan player sunucu sayısı: ${servers.size}")

        if (postId != null && themeDir != null) {
            servers.forEach { server ->
                val serverId = server.attr("id")
                val serverNumber = serverId.substringAfter("s_")
                val serverName = server.text().trim()

                try {
                    val ajaxIframeUrl = "$themeDir/temp/ajax/iframe2.php?id=$postId&video=$serverNumber"
//                    Log.d("7ebtv", "Player $serverNumber ($serverName) - AJAX URL: $ajaxIframeUrl")
                    val response = app.get(
                        ajaxIframeUrl,
                        headers = mapOf(
                            "Referer" to data,
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                            "X-Requested-With" to "XMLHttpRequest"
                        )
                    )
                    if (response.text.isNotEmpty()) {
                        val ajaxDoc = response.document
                        val videoIframe = ajaxDoc.select("iframe").firstOrNull()?.attr("src")
                        if (!videoIframe.isNullOrEmpty()) {
                            val fixedVideoUrl = fixUrlNull(videoIframe)
//                            Log.d("7ebtv", "Player $serverNumber - Video URL: $fixedVideoUrl")
                            if (!fixedVideoUrl.isNullOrEmpty()) {
                                if (fixedVideoUrl.endsWith(".m3u8") || fixedVideoUrl.contains(".m3u8?")) {
                                    callback.invoke(
                                        newExtractorLink(
                                            source = serverName.ifEmpty { "Server $serverNumber" },
                                            name = serverName.ifEmpty { "Server $serverNumber" },
                                            url = fixedVideoUrl,
                                            type = ExtractorLinkType.M3U8,
                                            {
                                                this.referer = data
                                            }
                                        )
                                    )
//                                    Log.d("7ebtv", "M3U8 link eklendi: $serverName")
                                } else {
                                    loadExtractor(fixedVideoUrl, subtitleCallback, callback)
                                }
                            }
                        }
                    }

                } catch (e: Exception) {
//                    Log.d("7ebtv", "Player $serverNumber hatası: ${e.message}")
                }
            }
        }
        val downloadLinks = document.select("div.downloadsList a.downloadsLink")
//        Log.d("7ebtv", "Bulunan indirme linki sayısı: ${downloadLinks.size}")

        downloadLinks.forEachIndexed { index, link ->
            val downloadUrl = link.attr("href")
            val downloadName = link.text().trim()

//            Log.d("7ebtv", "İndirme linki $index ($downloadName): $downloadUrl")

            if (downloadUrl.isNotEmpty()) {
                try {
                    val fixedDownloadUrl = fixUrlNull(downloadUrl)

                    if (!fixedDownloadUrl.isNullOrEmpty()) {
                        val extracted = loadExtractor(fixedDownloadUrl, subtitleCallback, callback)

                        if (!extracted) {
//                            Log.d("7ebtv", "Extractor çalışmadı, manuel ekleniyor: $fixedDownloadUrl")

                            if (fixedDownloadUrl.contains(".mp4", ignoreCase = true)) {
                                callback.invoke(
                                    newExtractorLink(
                                        source = downloadName.ifEmpty { "Download $index" },
                                        name = downloadName.ifEmpty { "Download $index" },
                                        url = fixedDownloadUrl,
                                        type = ExtractorLinkType.VIDEO
                                    )
                                )
//                                Log.d("7ebtv", "MP4 link eklendi: $downloadName")
                            } else {
                                val downloadPageDoc = app.get(fixedDownloadUrl).document

                                val videoUrl = downloadPageDoc.select("video source, a[href*=.mp4]")
                                    .firstOrNull()?.attr("src") ?:
                                downloadPageDoc.select("a[href*=.mp4]").firstOrNull()?.attr("href")

                                if (!videoUrl.isNullOrEmpty()) {
                                    val fixedVideoUrl = fixUrlNull(videoUrl)
                                    if (!fixedVideoUrl.isNullOrEmpty()) {
                                        callback.invoke(
                                            newExtractorLink(
                                                source = downloadName.ifEmpty { "Download $index" },
                                                name = downloadName.ifEmpty { "Download $index" },
                                                url = fixedVideoUrl,
                                                type = ExtractorLinkType.VIDEO
                                            )
                                        )
//                                        Log.d("7ebtv", "Video URL bulundu ve eklendi: $downloadName")
                                    }
                                }
                            }
                        }
                    }

                } catch (e: Exception) {
//                    Log.d("7ebtv", "İndirme linki $index hatası: ${e.message}")
                }
            }
        }

        return true
    }
}