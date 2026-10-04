// ! Bu araç @kerimmkirac tarafından | @CS-Karma için yazılmıştır!

package com.kerimmkirac

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class FullRaces : MainAPI() {
    override var mainUrl = "https://fullraces.com"
    override var name = "FullRaces"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Live, TvType.Others)

    override val mainPage = mainPageOf(
        "${mainUrl}/f1-race-replays" to "All F1 Races",
        "${mainUrl}/2026" to "Formula 1 2026",
        "${mainUrl}/2025" to "Formula 1 2025",
        "${mainUrl}/watch/formula_1/formula_1_2024/21" to "Formula 1 2024",
        "${mainUrl}/f1-2023" to "Formula 1 2023",
        "${mainUrl}/formula1-2022" to "Formula 1 2022",
        "${mainUrl}/formula1-2021" to "Formula 1 2021",
        "${mainUrl}/f1-2020" to "Formula 1 2020",
        "${mainUrl}/f1-2019" to "Formula 1 2019",
        "${mainUrl}/f1-archive-races" to "F1 Archive Races 2000-2018",
        "${mainUrl}/f2-full-races" to "F2 Races",
        "${mainUrl}/f3-full-races" to "F3 Races",
        "${mainUrl}/nascar" to "Nascar Races",
        "${mainUrl}/indycar" to "Indycar Races",
        "${mainUrl}/formula-e" to "Formula E Races",
        "${mainUrl}/other" to "Others"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/?page$page").document
        val home = document.select("div.short_item").mapNotNull { it.toMainPageResult() }
        return newHomePageResponse(HomePageList(request.name, home, true))
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val anchor = selectFirst("div.short_content h3 a") ?: return null
        val href = fixUrlNull(anchor.attr("href")) ?: return null
        val poster = fixUrlNull(selectFirst("div.poster img")?.attr("src"))

        return newTvSeriesSearchResponse(anchor.text().trim(), href, TvType.TvSeries) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search/?q=$query").document
        return document.select("div.statvidp").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = selectFirst("div.tit33fdsq a") ?: return null
        val href = fixUrlNull(anchor.attr("href")) ?: return null
        val poster = fixUrlNull(selectFirst("div.fhkds54sa img")?.attr("src"))

        return newTvSeriesSearchResponse(anchor.text().trim(), href, TvType.TvSeries) {
            this.posterUrl = poster
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.full_img img")?.attr("src"))
        val description = document.select("div.gp-top p, div.gp-top h2, div[align=center]")
            .joinToString("\n") { it.text().trim() }
            .ifBlank { null }

        val sessions = buildList {
            document.select("nav.gp-bar a.gp-src").forEach { nav ->
                val href = nav.attr("href").ifEmpty { return@forEach }
                val partText =
                    nav.selectFirst("b")?.text()?.trim().takeUnless { it.isNullOrEmpty() }
                        ?: nav.text().trim().ifEmpty { "Full Part" }
                add(SessionItem(partText, fixUrl(httpsify(href))))
            }

            document.select("div.video-responsive iframe").forEach { iframe ->
                val src = iframe.attr("src").ifEmpty { return@forEach }
                val cleanUrl = fixUrl(httpsify(src))
                add(SessionItem(Titlecek(iframe, cleanUrl), cleanUrl))
            }

            document.select("a.su-button").forEach { button ->
                val href = button.attr("href").ifEmpty { return@forEach }
                val cleanUrl = fixUrl(httpsify(href))
                add(SessionItem(Titlecek(button, cleanUrl), cleanUrl))
            }
        }.distinctBy { it.url }.ifEmpty { listOf(SessionItem("Full Race", url)) }

        val episodeList = sessions.mapIndexed { index, session ->
            newEpisode(session.url) {
                this.name = session.name
                this.episode = index + 1
                this.season = 1
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList) {
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
        Log.d("STF", "data » $data")
        val linksFound = AtomicBoolean(false)

        suspend fun extract(name: String, url: String) {
            loadCustomExtractor(name, url, data, subtitleCallback) { link ->
                linksFound.set(true)
                callback(link)
            }
        }

        if (data.contains("fullraces.com")) {
            val document = app.get(data).document

            document.select("nav.gp-bar a.gp-src").amap { nav ->
                val href = nav.attr("href").ifEmpty { return@amap }
                val cleanUrl = fixUrl(httpsify(href))
                val partText =
                    nav.selectFirst("b")?.text()?.trim().takeUnless { it.isNullOrEmpty() }
                        ?: "Full Part"
                Log.d("STF", "Nav link bulundu » $cleanUrl ($partText)")
                extract(partText, cleanUrl)
            }

            document.select("div.video-responsive iframe").amap { iframe ->
                val src = iframe.attr("src").ifEmpty { return@amap }
                val cleanUrl = fixUrl(httpsify(src))
                Log.d("STF", "iframe bulundu » $cleanUrl")
                extract(Titlecek(iframe, cleanUrl), cleanUrl)
            }

            document.select("a.su-button").amap { button ->
                val href = button.attr("href").ifEmpty { return@amap }
                val cleanUrl = fixUrl(httpsify(href))
                val partText = Titlecek(button, cleanUrl)
                Log.d("STF", "Link bulundu » $cleanUrl ($partText)")
                extract(partText, cleanUrl)
            }
        } else {
            loadExtractor(data, "$mainUrl/", subtitleCallback) { link ->
                linksFound.set(true)
                callback(link)
            }
        }

        return linksFound.get()
    }

    private suspend fun loadCustomExtractor(
        name: String,
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        loadExtractor(url, referer, subtitleCallback) { link ->
            val formattedName = "${link.source} | $name"
            CoroutineScope(Dispatchers.IO).launch {
                callback(
                    newExtractorLink(
                        source = link.source,
                        name   = formattedName,
                        url    = link.url,
                    ) {
                        this.referer       = link.referer
                        this.quality       = link.quality
                        this.type          = link.type
                        this.headers       = link.headers
                        this.extractorData = link.extractorData
                    }
                )
            }
        }
    }

    private fun Titlecek(el: Element, href: String): String {
        var sectionHeader: String? = null
        var prev = el.parent()?.previousElementSibling() ?: el.previousElementSibling()
        while (prev != null) {
            val text = prev.text().trim()
            if (text.isNotBlank() && !text.contains("---") && !text.startsWith("Disclaimer") && !text.startsWith(
                    "You can watch"
                )
                && prev.selectFirst("strong, span, h2, h3") != null
            ) {
                sectionHeader = text
                break
            }
            prev = prev.previousElementSibling() ?: prev.parent()?.previousElementSibling()
        }

        val lowerHref = href.lowercase()
        val hostName = when {
            lowerHref.contains("dailymotion.com") -> "Dailymotion"
            lowerHref.contains("ok.ru") || lowerHref.contains("odnoklassniki") -> "OK.RU"
            lowerHref.contains("filemoon") || lowerHref.contains("bysesukior") -> "Filemoon"
            lowerHref.contains("mixdrop") -> "Mixdrop"
            lowerHref.contains("streamtape") -> "Streamtape"
            lowerHref.contains("dood") -> "Doodstream"
            lowerHref.contains("vk.com") -> "VK"
            else -> null
        }

        val buttonText = el.text().trim()
        val isGeneric = buttonText.isBlank() ||
                buttonText.equals("Watch", ignoreCase = true) ||
                buttonText.equals("Full Part", ignoreCase = true)

        val parts = listOfNotNull(
            sectionHeader?.takeIf { it.isNotBlank() },
            buttonText.takeIf {
                !isGeneric && (sectionHeader == null || !sectionHeader.contains(
                    buttonText,
                    ignoreCase = true
                ))
            }
        ).ifEmpty { listOf(hostName ?: "Full Part") }

        val label = parts.joinToString(" - ")
        return if (hostName != null && !label.contains(
                hostName,
                ignoreCase = true
            )
        ) "$label - $hostName" else label
    }
}

private data class SessionItem(val name: String, val url: String)