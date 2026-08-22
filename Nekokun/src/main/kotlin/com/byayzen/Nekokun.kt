// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import android.icu.text.SimpleDateFormat
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import java.util.Locale

class Nekokun : MainAPI() {
    override var mainUrl = "https://nekokun.my.id"
    override var name = "Nekokun"
    override val hasMainPage = true
    override var lang = "id"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "${mainUrl}/anime/?status=&type=&order=latest" to "Terbaru",
        "${mainUrl}/anime/?sub=&order=popular" to "Populer",
        "${mainUrl}/anime/?status=&type=&order=update" to "Pembaruan",
        "${mainUrl}/anime/?status=completed&sub=&order=" to "Selesai",
        "${mainUrl}/anime/?status=ongoing&sub=&order=" to "Sedang Tayang",
        "${mainUrl}/genres/fantasy/" to "Fantasy",
        "${mainUrl}/genres/action/" to "Action",
        "${mainUrl}/genres/comedy/" to "Comedy",
        "${mainUrl}/genres/shounen/" to "Shounen",
        "${mainUrl}/genres/adventure/" to "Adventure",
        "${mainUrl}/genres/romance/" to "Romance",
        "${mainUrl}/genres/school/" to "School",
        "${mainUrl}/genres/drama/" to "Drama",
        "${mainUrl}/genres/isekai/" to "Isekai",
        "${mainUrl}/genres/seinen/" to "Seinen"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) {
            request.data
        } else {
            if (request.data.contains("?")) "${request.data}&page=$page"
            else "${request.data.removeSuffix("/")}/page/$page/"
        }
        val document = app.get(url).document
        val home = document.select("div.listupd article.bs").mapNotNull { it.toMainPageResult() }.distinctBy { it.url }
        return newHomePageResponse(request.name, home, hasNext = home.isNotEmpty())
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val href = fixUrlNull(this.selectFirst("div.bsx a")?.attr("href")) ?: return null
        val title = this.selectFirst("div.tt h2")?.text()?.trim() ?: return null
        val posterurl = fixUrlNull(
            this.selectFirst("div.limit img")?.attr("src")
                ?: this.selectFirst("div.limit img")?.attr("data-src")
        )
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterurl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val document = if (page == 1) app.get("${mainUrl}/?s=${query}").document
        else app.get("${mainUrl}/page/$page/?s=${query}").document
        val aramacevap = document.select("div.listupd article.bs").mapNotNull { it.toMainPageResult() }.distinctBy { it.url }
        return newSearchResponseList(aramacevap, hasNext = aramacevap.isNotEmpty())
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    @RequiresApi(Build.VERSION_CODES.N)
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        Log.d("Ayzen", url)
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.thumb img")?.attr("src"))
        val description = document.selectFirst("div.entry-content p")?.text()?.trim()
        val rating = document.selectFirst("meta[itemprop=ratingValue]")?.attr("content")?.toDoubleOrNull()
        val trailer = document.selectFirst("a.trailerbutton")?.attr("href")
        val tags = document.select("div.genxed a").map { it.text() }
        val actors = document.select("div.cvitem").mapNotNull { cvitem ->
            val actorname = cvitem.selectFirst("div.cvactor span.charname")?.text()?.trim()
                ?: cvitem.selectFirst("div.cvchar span.charname")?.text()?.trim()
            if (!actorname.isNullOrEmpty()) Actor(actorname) else null
        }
        val recommendations = document.select("div.listupd article.bs").mapNotNull { it.toRecommendationResult() }
        val infospans = document.select("div.spe span")
        val typetext = infospans.firstOrNull { it.text().contains("Tipe:", ignoreCase = true) }?.text()?.replace("Tipe:", "", true)?.trim().orEmpty()
        val statustext = infospans.firstOrNull { it.text().contains("Status:", ignoreCase = true) }?.text()?.replace("Status:", "", true)?.trim().orEmpty()
        val durationtext = infospans.firstOrNull { it.text().contains("Durasi:", ignoreCase = true) }?.text()?.replace("Durasi:", "", true)?.trim().orEmpty()
        val year = infospans.firstOrNull { it.text().contains("Dirilis:", ignoreCase = true) }?.text()?.let { Regex("""\d{4}""").find(it)?.value?.toIntOrNull() }
        val tvtype = when (typetext) {
            "Movie" -> TvType.Movie
            "Live Action" -> TvType.Live
            "Special" -> TvType.OVA
            else -> TvType.Anime
        }
        val duration = getDurationFromString(durationtext.replace("hr.", "h").replace("min.", "m"))

        if (tvtype == TvType.Movie) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = rating?.let { Score.from(it, 10) }
                this.duration = duration
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        }

        val localeid = Locale("id", "ID")
        val dateformatin = SimpleDateFormat("MMMM dd, yyyy", localeid)
        val dateformatout = SimpleDateFormat("yyyy-MM-dd", localeid)

        val episodes = document.select("div.eplister ul li").mapNotNull { element ->
            val linkelement = element.selectFirst("a") ?: return@mapNotNull null
            val epurl = fixUrlNull(linkelement.attr("href")) ?: return@mapNotNull null
            val epnum = element.selectFirst("div.epl-num")?.text()?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val datestr = element.selectFirst("div.epl-date")?.text()?.trim()
            val parseddate = try {
                if (!datestr.isNullOrEmpty()) dateformatout.format(dateformatin.parse(datestr)!!) else null
            } catch (e: Exception) {
                null
            }
            newEpisode(epurl) {
                this.episode = epnum
                this.posterUrl = poster
                if (parseddate != null) this.addDate(parseddate)
            }
        }.reversed()

        return newAnimeLoadResponse(title, url, tvtype) {
            this.episodes = mutableMapOf(DubStatus.Subbed to episodes)
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            this.score = rating?.let { Score.from(it, 10) }
            this.duration = duration
            this.recommendations = recommendations
            addActors(actors)
            addTrailer(trailer)
            this.showStatus = animestatus(statustext)
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val href = fixUrlNull(this.selectFirst("div.bsx a")?.attr("href")) ?: return null
        val title = this.selectFirst("div.tt h2")?.text()?.trim() ?: return null
        val posterurl = fixUrlNull(
            this.selectFirst("div.limit img")?.attr("src")
                ?: this.selectFirst("div.limit img")?.attr("data-src")
        )
        val typetext = this.selectFirst("div.typez")?.text()?.trim()
        val tvtype = when (typetext) {
            "Movie" -> TvType.Movie
            "Live Action" -> TvType.Live
            "Special", "ONA" -> TvType.OVA
            else -> TvType.Anime
        }
        return newAnimeSearchResponse(title, href, tvtype) {
            this.posterUrl = posterurl
        }
    }

    private fun animestatus(statustext: String): ShowStatus {
        return when {
            statustext.equals("Ongoing", ignoreCase = true) -> ShowStatus.Ongoing
            statustext.equals("Completed", ignoreCase = true) -> ShowStatus.Completed
            else -> ShowStatus.Completed
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("Ayzen", data)
        val document = app.get(data).document
        val linklist = mutableListOf<String>()

        document.selectFirst("div.player-embed iframe")?.attr("src")
            ?: document.selectFirst("iframe")?.attr("src")?.also {
                Log.d("Ayzen", it)
                linklist.add(it)
            }

        document.select("select.mirror option").forEach { option ->
            val base64value = option.attr("value")
            if (base64value.isNotBlank()) {
                try {
                    val decodedhtml = String(android.util.Base64.decode(base64value, android.util.Base64.DEFAULT))
                    Log.d("Ayzen", decodedhtml)
                    Regex("""(?i)src=["']([^"']+)["']""").find(decodedhtml)?.groupValues?.get(1)
                        ?.takeIf { it.isNotEmpty() && !linklist.contains(it) }
                        ?.also {
                            Log.d("Ayzen", it)
                            linklist.add(it)
                        }
                } catch (e: Exception) {
                    Log.d("Ayzen", e.toString())
                }
            }
        }

        document.select("div.soraurlx a").forEach { element ->
            val downloadlink = element.attr("href")
            if (downloadlink.isNotBlank() && !downloadlink.contains("trakteer.id") && !linklist.contains(downloadlink)) {
                Log.d("Ayzen", downloadlink)
                linklist.add(downloadlink)
            }
        }

        if (linklist.isEmpty()) {
            Log.d("Ayzen", "false")
            return false
        }

        linklist.forEach { link ->
            Log.d("Ayzen", link)
            loadExtractor(link, "$mainUrl/", subtitleCallback, callback)
        }

        Log.d("Ayzen", "true")
        return true
    }
}