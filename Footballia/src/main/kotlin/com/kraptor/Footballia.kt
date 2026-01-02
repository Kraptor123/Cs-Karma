// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
package com.kraptor

import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class Footballia : MainAPI() {
    override var mainUrl = "https://footballia.net"
    override var name = "Footballia"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    // mainPage artık bir val değil, ayarlara göre liste döndüren bir fonksiyon.
    override val mainPage = mainPageOf(
        // FootballiaPlugin'deki tüm kategorileri döngüye al
        *FootballiaPlugin.allCategories.mapNotNull { (name, path) ->
            // Eğer kategori ayarlardan etkinse, ana sayfaya ekle
            if (FootballiaPlugin.isCategoryEnabled(name)) {
                "${mainUrl}/tr/$path" to name
            } else {
                null // Eğer etkin değilse, listeye ekleme
            }
        }.toTypedArray()
    )

    // Geri kalan kodunuzda bir değişiklik yapmaya gerek yok.
    // ... (getMainPage, search, load vb. fonksiyonlarınız aynen kalacak)
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageAl = app.get("${request.data}", referer = "${request.data}").document
        val lastPageElement = pageAl.select("ul.pagination li:not(.next)").lastOrNull()
        val lastPageText = lastPageElement?.selectFirst("a")?.text()
        val lastPage = lastPageText?.toIntOrNull() ?: 1

        val actualPage = (lastPage - page + 1).coerceAtLeast(1)

        val document = app.get("${request.data}?page=$actualPage", referer = "${request.data}").document
        val home = document.select("div.search-results div.tab-content tbody tr")
            .mapNotNull { it.toMainPageResult() }

        val hasNext = actualPage > 1

        return newHomePageResponse(
            list = HomePageList(request.name, home, isHorizontalImages = true),
            hasNext = hasNext
        )
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val tarih = this.selectFirst("td.playing_date.hidden-xs")?.text() ?: ""
        val title = this.selectFirst("div.hidden-md.hidden-lg a")?.text() ?: return null
        val sonTitle = "$title - $tarih"
        val href = fixUrlNull(this.selectFirst("div.hidden-md.hidden-lg a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("span.logo img")?.attr("src"))

        return newMovieSearchResponse(sonTitle, href, TvType.Live) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val text = app.get("${mainUrl}/search_by_team?name=${query}", referer = "${mainUrl}/", headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
            "Accept" to "application/json, text/javascript, */*; q=0.01",
            "X-Requested-With" to "XMLHttpRequest",
        )).text

        val mapper = mapper.readValue<List<FootballiaSearch>>(text)

        if (mapper.isEmpty()) {
            return newSearchResponseList(emptyList(), hasNext = false)
        }

        val maxTeams = 3
        val teamsToQuery = mapper.take(maxTeams)
        val allMatches = mutableListOf<SearchResponse>()

        for (team in teamsToQuery) {
            val teamUrl = fixUrlNull("/tr/teams/${team.slug}") ?: continue
            val document = app.get("${teamUrl}?page=$page", referer = teamUrl).document
            val matches = document.select("div.search-results div.tab-content tbody tr")
                .mapNotNull { it.toMainPageResult() }
            allMatches.addAll(matches)
        }

        val hasNext = allMatches.isNotEmpty()
        return newSearchResponseList(allMatches, hasNext = hasNext)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Cookie" to "request_method=POST; _footballia_session=SWdnUGxYRlFvNVRWb2VFS0pac2gyRVY4VDBEZ1VzbExOZ1hYbXdYeUdYQ1lBUmFmSzNEZG1rSFhQM3NyVFFyYVNsRDY5VEROSTdNLzVaN3dyekMxQkZJWUZhaFErNXJTWE1CcEpLaERrMUgxcThvdmkwREIzRGZXNE5RVGZkeHNMdE9vSERBUHRpWDluOW9IT29jaXFIbmMxZ2VGSER6NFFVMWxWNmNKeW5mVDlDeitCaS92NDA3UmI0OVpSR1dtUlBWNVJnNUQzMm9Bc0wyeGZCcnlKeG9FekxqZ1ppbmVRMFpnTTVSdVNEOGxPc2xpWE5wMVYyR1E3TlZReEZ4VEZhZDhheVB0WWk3cTh4MHFxdHJLQUNoTFZrQnQ5T3NIK2FBdzUrMjk5ejZoVzE2cWpOejFwWS9vcUtWcXAxeGNZVG9GVWgvaHFaL3puM2VQVGc5L0dJOVhFM3kzTUNNaUxVOTk0TUVUQy9oRW1GTG1BUUNPT1BveE1HNWlsczFjQkthL0FjUEE2L243QkNFVXl3TGdOdz09LS04Ui9hVThzeVZKVUY0VDF1Uk5CU1FnPT0%3D--c8c42649118bbd95d55667e5f813d54d7815a680",
        )).document

        val scriptText = document.selectFirst("script:containsData(new Video)")?.data() ?: ""

        val uyari = if (scriptText.isEmpty()) {
            "Bu maç, oynanma tarihinden 30 gün sonra izlenebilir olacaktır.<br> <br> This match will be available to watch 30 days after it is played.<br> <br>"
        } else {
            ""
        }

        val regex = Regex(pattern = "Video\\(\"([^\"]*)\"\\)", options = setOf(RegexOption.IGNORE_CASE))
        var video = regex.find(scriptText)?.groupValues[1]?.replace("\\n", "") ?: ""

        if (video.isNotEmpty()) {
            video = base64Decode(video)
        }

        Log.d("kraptor_$name", "video = ${video}")

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")
        val sonDesc = "$uyari$description"
        val year = document.selectFirst("div.extra span.C a")?.text()?.trim()?.toIntOrNull()
        val tags = document.select("div.sgeneros a").map { it.text() }
        val rating = document.selectFirst("span.dt_rating_vgs")?.text()?.trim()?.toIntOrNull()
        val duration = document.selectFirst("span.runtime")?.text()?.split(" ")?.first()?.trim()?.toIntOrNull()
        val recommendations = document.select("div.srelacionados article").mapNotNull { it.toRecommendationResult() }
        val actors = document.select("div.logo img").map { Actor(it.attr("title"), fixUrlNull(it.attr("src"))) }
        val trailer = Regex("""embed\/(.*)\?rel""").find(document.html())?.groupValues?.get(1)?.let { "https://www.youtube.com/embed/$it" }

        if (video.isEmpty()) {
            val regex2 = Regex(pattern = "\"file\":\"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE))
            val videos = regex2.findAll(scriptText)
                .map { base64Decode(it.groupValues[1].replace("\\n", "")) }
                .toList()

            if (videos.size == 2) {
                // İki video varsa TV Series olarak döndür
                val episodes = videos.mapIndexed { index, videoUrl ->
                    newEpisode(videoUrl) {
                        this.name = if (index == 0) "İlk Yarı | First Half" else "İkinci Yarı | Second Half"
                        this.season = 1
                        this.episode = index + 1
                    }
                }

                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.plot = sonDesc
                    this.year = year
                    this.tags = tags
                    this.score = Score.from10(rating)
                    this.duration = duration
                    this.recommendations = recommendations
                    addActors(actors)
                    addTrailer(trailer)
                }
            } else if (videos.size == 1) {
                video = videos[0]
            } else if (videos.isEmpty()) {
                // "file" da yoksa, "video" değişkeninde bak
                val regex3 = Regex(pattern = "\"video\":\"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE))
                val videoEncoded = regex3.find(scriptText)?.groupValues[1]?.replace("\\n", "") ?: ""
                if (videoEncoded.isNotEmpty()) {
                    video = base64Decode(videoEncoded)
                }
            }
        }

        return newMovieLoadResponse(title, url, TvType.Live, video) {
            this.posterUrl = poster
            this.plot = sonDesc
            this.year = year
            this.tags = tags
            this.score = Score.from10(rating)
            this.duration = duration
            this.recommendations = recommendations
            addActors(actors)
            addTrailer(trailer)
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title     = this.selectFirst("a img")?.attr("alt") ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a img")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Live) { this.posterUrl = posterUrl }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("kraptor_$name", "data = ${data}")

        callback.invoke(newExtractorLink(
            this.name,
            this.name,
            data,
            type = ExtractorLinkType.VIDEO
        ) {
            this.referer = "${mainUrl}/"
        })

        return true
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class FootballiaSearch(
    val name: String,
    val slug: String,
    val logo_medium: String
)