package com.byayzen

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.Jsoup

object AnimeSama {
    private const val baseUrl = "https://anime-sama.to"
    private const val searchUrl = "$baseUrl/template-php/defaut/fetch.php"

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:157.0) Gecko/20100101 Firefox/157.0",
        "Accept" to "*/*",
        "X-Requested-With" to "XMLHttpRequest",
        "Origin" to baseUrl,
        "Referer" to "$baseUrl/"
    )

    private suspend fun getJapaneseTitle(title: String): String {
        return try {
            val queryJson = """{"query": "query (${'$'}search: String) { Media (search: ${'$'}search, type: ANIME) { title { romaji english native } } }", "variables": {"search": "$title"}}"""
            val res = app.post(
                "https://graphql.anilist.co",
                headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                json = queryJson
            )
            val text = res.text
            val romajiMatch = """"romaji"\s*:\s*"([^"]+)"""".toRegex().find(text)?.groupValues?.get(1)
            val nativeMatch = """"native"\s*:\s*"([^"]+)"""".toRegex().find(text)?.groupValues?.get(1)
            romajiMatch ?: nativeMatch ?: title
        } catch (_: Exception) {
            title
        }
    }

    suspend fun searchAnimeSama(title: String): List<String> {
        val searchTitle = getJapaneseTitle(title)
        val res = app.post(
            searchUrl,
            headers = headers,
            data = mapOf("query" to searchTitle)
        )
        val doc = Jsoup.parse(res.text, baseUrl)
        return doc.select("a.asn-search-result, a[href*=/catalogue/]").mapNotNull {
            val href = it.attr("href").trim()
            if (href.contains("/catalogue/")) {
                if (href.startsWith("http")) href else "$baseUrl$href"
            } else null
        }.distinct()
    }

    suspend fun fetchAnimeSamaLinks(
        title: String,
        season: String?,
        episode: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val catalogueUrls = searchAnimeSama(title)
        if (catalogueUrls.isEmpty()) return

        catalogueUrls.forEach { catUrl ->
            try {
                val doc = app.get(catUrl, headers = headers).document
                val seasonLinks = mutableListOf<Pair<String, String>>()

                doc.select("a[href]").forEach { a ->
                    val href = a.attr("href").trim()
                    val text = a.text().trim()
                    if (href.contains("vostfr") || href.contains("vf")) {
                        val fullUrl = if (href.startsWith("http")) href else "$catUrl$href".replace("//saison", "/saison").replace("//film", "/film").replace("//oav", "/oav")
                        seasonLinks.add(text to fullUrl)
                    }
                }

                val scriptRegex = """panneauAnime\s*\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)""".toRegex()
                scriptRegex.findAll(doc.html()).forEach { match ->
                    val name = match.groupValues[1]
                    val path = match.groupValues[2]
                    val fullUrl = if (path.startsWith("http")) path else "$catUrl$path".replace("//saison", "/saison")
                    seasonLinks.add(name to fullUrl)
                }

                val distinctSeasonLinks = seasonLinks.distinctBy { it.second }
                val targetSeasonNum = season?.filter { it.isDigit() }?.ifEmpty { "1" } ?: "1"
                val targetEpNum = episode?.filter { it.isDigit() }?.ifEmpty { "1" } ?: "1"

                distinctSeasonLinks.forEach { (name, sUrl) ->
                    val lowerName = name.lowercase()
                    val lowerUrl = sUrl.lowercase()

                    val isSeasonMatch = season == null ||
                            lowerName.contains("saison $targetSeasonNum") ||
                            lowerName.contains("saison$targetSeasonNum") ||
                            lowerUrl.contains("saison$targetSeasonNum") ||
                            (season == "1" && !lowerUrl.contains("saison"))

                    if (!isSeasonMatch) return@forEach

                    val langLabel = if (lowerUrl.contains("/vf")) "VF" else "VOSTFR"
                    val cleanSeasonUrl = if (sUrl.endsWith("/")) sUrl else "$sUrl/"
                    val epJsUrl = "${cleanSeasonUrl}episodes.js"

                    try {
                        val jsRes = app.get(epJsUrl, headers = headers)
                        val jsText = jsRes.text
                        val extractedUrls = parseEpisodesJs(jsText, targetEpNum)

                        extractedUrls.forEach { playerUrl ->
                            val brandName = "ANIME-SAMA | $langLabel"
                            if (playerUrl.contains("ansembed") || playerUrl.contains("ansembed.net")) {
                                Ansembed().getUrl(playerUrl, cleanSeasonUrl, subtitleCallback) { link ->
                                    callback(link)
                                }
                            } else {
                                loadcustomextractor(brandName, playerUrl, cleanSeasonUrl, subtitleCallback, callback)
                            }
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    private fun parseEpisodesJs(jsText: String, episodeNum: String): List<String> {
        val links = mutableListOf<String>()
        val epIdx = episodeNum.toIntOrNull() ?: 1

        val arrayRegex = """var\s+eps(\d+)\s*=\s*\[(.*?)\];""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val arrayMatches = arrayRegex.findAll(jsText).toList()

        if (arrayMatches.isNotEmpty()) {
            arrayMatches.forEach { match ->
                val content = match.groupValues[2]
                val urls = """["'](https?://[^"']+)["']""".toRegex().findAll(content).map { it.groupValues[1] }.toList()
                val targetUrl = urls.getOrNull(epIdx - 1)
                if (!targetUrl.isNullOrBlank()) {
                    links.add(targetUrl)
                }
            }
        }

        if (links.isEmpty()) {
            """https?://[^\s"'<>\\]+""".toRegex().findAll(jsText).forEach {
                val url = it.value.replace("\\", "")
                if (url.contains("embed") || url.contains("video") || url.contains("player") || url.contains("sendvid") || url.contains("sibnet") || url.contains("ansembed") || url.contains("vidmoly")) {
                    links.add(url)
                }
            }
        }

        return links.distinct()
    }
}
