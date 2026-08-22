// ! This Extension Made By @ByAyzen for GizliKeyif

package com.byayzen

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import org.json.JSONArray

class Sokuja : MainAPI() {
    override var mainUrl = "https://x6.sokuja.uk"
    override var name = "Sokuja"
    override val hasMainPage = true
    override var lang = "id"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)
    override val vpnStatus = VPNStatus.MightBeNeeded

    private val tag = "gizlikeyif_${name}"

    override val mainPage = mainPageOf(
        "${mainUrl}/anime/?type=movie" to "Film",
        "${mainUrl}/anime/?order=update" to "Semua",
        "${mainUrl}/anime/?order=update&status=ongoing" to "Sedang Tayang",
        "${mainUrl}/anime/?status=completed&order=update" to "Tamat"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}&page=$page"
        val document = app.get(url).document
        val home = document.select("div.grid a.group").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(
            list = HomePageList(request.name, home),
            hasNext = home.isNotEmpty()
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = "${mainUrl}/?s=$query&page=$page"
        val document = app.get(url).document
        val results = document.select("div.grid a.group").mapNotNull { it.toMainPageResult() }

        return newSearchResponseList(results, hasNext = results.isNotEmpty())
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query, 1).items

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("h3")?.text() ?: return null
        val href = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        Log.d(tag, "Load asamasi: $url")
        val document = app.get(url).document

        val rawTitle = document.selectFirst("h1")?.text()?.trim() ?: return null
        val title =
            rawTitle.replace(Regex("""\s*Subtitle\s+Indonesia\s*$""", RegexOption.IGNORE_CASE), "")
                .trim()

        val ldJson = document.select("script[type=\"application/ld+json\"]")
            .firstOrNull { it.data().contains("\"image\"") }?.data() ?: ""
        val posterLd = Regex(""""image"\s*:\s*"([^"]+)"""").find(ldJson)?.groupValues?.get(1)
        val posterImg = document.selectFirst("div.flex-shrink-0 img")?.attr("src")?.ifEmpty { null }
        val poster = fixUrlNull(posterLd ?: posterImg)

        val plot = document.select("div.prose p").joinToString("\n\n") { it.text().trim() }
            .ifEmpty { null }
            ?: Regex(""""description"\s*:\s*"([^"]+)"""").find(ldJson)?.groupValues?.get(1)

        val yearText =
            document.select("dt").firstOrNull { it.text().trim() == "Tahun" }?.nextElementSibling()
                ?.text()?.trim()
        val year = yearText?.let { Regex("""\d+(\.\d+)?""").find(it)?.value }?.toIntOrNull()

        val durationRaw =
            document.select("dt").firstOrNull { it.text().trim() == "Durasi" }?.nextElementSibling()
                ?.text()?.trim()
        val duration = durationRaw?.let {
            val hours = Regex("""(\d+)\s*hr""").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val mins = Regex("""(\d+)\s*min""").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            (hours * 60 + mins).takeIf { total -> total > 0 }
        }

        val typeText =
            document.select("dt").firstOrNull { it.text().trim() == "Tipe" }?.nextElementSibling()
                ?.text()?.trim() ?: ""
        val scoreText = document.selectFirst("span.text-2xl.font-bold")?.text()?.trim()
        val scoreVal = scoreText?.let { Regex("""\d+(\.\d+)?""").find(it)?.value }?.toDoubleOrNull()

        val tags =
            document.select("a[href^=/genre/]").map { it.text().trim() }.filter { it.isNotEmpty() }
        val actors = document.select("a[href^=/cast/]").map { Actor(it.text().trim()) }
        val recommendations =
            document.select("div.grid a.group.block").mapNotNull { it.toRecommendationResult() }

        val scriptsContent =
            document.select("script").joinToString("\n") { it.data() }.replace("\\\"", "\"")
        val rawEpisodesStr =
            Regex(""""episodes":\[(.*?)\],"episodesTotal"""").find(scriptsContent)?.groupValues?.get(
                1
            ) ?: ""

        val episodes = mutableListOf<Episode>()
        if (rawEpisodesStr.isNotEmpty()) {
            try {
                val jsonArray = JSONArray("[$rawEpisodesStr]")
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val slug = obj.getString("slug")
                    val epNum = obj.optDouble("episodeNumber", -1.0).toInt().takeIf { it > 0 }
                    val date = obj.optString("createdAt", "").substringBefore("T").ifEmpty { null }
                    val epUrl = fixUrl("/$slug/")

                    episodes.add(
                        newEpisode(epUrl) {
                            this.episode = epNum
                            if (date != null) this.addDate(date)
                        }
                    )
                }
            } catch (e: Exception) {
                Log.d(tag, "Episode JSON parse hatasi: ${e.message}")
            }
        }

        episodes.reverse()

        if (typeText.equals("Movie", true)) {
            val episodeUrl =
                fixUrlNull(document.selectFirst("div.space-y-1 a")?.attr("href")?.ifEmpty { null })
                    ?: url

            return newMovieLoadResponse(title, url, TvType.Anime, episodeUrl) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
                this.duration = duration
                this.score = Score.from(scoreVal, 10)
                this.recommendations = recommendations
                addActors(actors)
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.tags = tags
            this.duration = duration
            this.score = Score.from(scoreVal, 10)
            this.recommendations = recommendations
            addActors(actors)
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val duzhref = this.attr("href").ifEmpty { return null }
        val href = fixUrl(duzhref)
        val duztitle =
            this.selectFirst("h3")?.text()?.trim()?.ifEmpty { return null } ?: return null
        val title =
            duztitle.replace(Regex("""\s*Subtitle\s+Indonesia\s*$""", RegexOption.IGNORE_CASE), "")
                .trim()
        val imgRaw = this.selectFirst("img")?.attr("src")?.ifEmpty { null } ?: return null
        val poster = fixUrlNull(imgRaw)

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d(tag, "loadLinks baslatildi: $data")
        val responseText = app.get(data).text

        val episodeId =
            Regex("""episodeId[^0-9]*(\d{3,})""").find(responseText)?.groupValues?.get(1)
                ?: return false

        val mirrors = app.get(
            "${mainUrl}/api/video-mirrors/?e=$episodeId",
            referer = data
        ).parsedSafe<MirrorResponse>()?.mirrors ?: return false

        mirrors.forEach { mirror ->
            val embedUrl = mirror.embedUrl ?: return@forEach

            callback(
                newExtractorLink(
                    source = mirror.serverName ?: name,
                    name = mirror.serverName ?: name,
                    url = embedUrl,
                    type = INFER_TYPE
                ) {
                    this.referer = data
                    this.quality = getQualityFromName(mirror.quality)
                }
            )
        }

        return true
    }
}


data class MirrorResponse(val mirrors: List<Mirror> = emptyList())
data class Mirror(
    val serverName: String? = null,
    val embedUrl: String? = null,
    val quality: String? = null
)