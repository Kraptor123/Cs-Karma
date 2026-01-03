// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup

class Tokybook : MainAPI() {
    override var mainUrl = "https://tokybook.com"
    override var name = "Tokybook"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Others, TvType.AudioBook)


    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0",
        "Accept" to "*/*",
        "Accept-Language" to "tr-TR,tr;q=0.8,en-US;q=0.5,en;q=0.3"
    )

    override val mainPage = mainPageOf(
        "https://tokybook.com/api/v1/search/audiobooks" to "All Audiobooks",
        "https://tokybook.com/api/v1/home/new-books-monthly" to "New Books Monthly"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val limit = 12
        val offset = (page - 1) * limit
        val isSearchApi = request.data.contains("/search/")

        val response = if (isSearchApi) {
            val body = """{"keyword":"","limit":$limit,"offset":$offset}"""
            app.post(
                url = request.data,
                headers = commonHeaders + mapOf(
                    "Content-Type" to "application/json",
                    "Origin" to "https://tokybook.com",
                    "Referer" to "https://tokybook.com/audiobooks",
                    "X-Requested-With" to "XMLHttpRequest"
                ),
                requestBody = body.toRequestBody("application/json".toMediaTypeOrNull())
            )
        } else {
            if (page > 1) return newHomePageResponse(request.name, emptyList(), false)
            app.get(
                url = request.data,
                headers = commonHeaders + mapOf("Referer" to "https://tokybook.com/")
            )
        }

        val responseText = response.text
        if (responseText.isBlank()) return newHomePageResponse(request.name, emptyList(), false)

        val items = try {
            val trimmed = responseText.trim()
            if (trimmed.startsWith("{")) {
                val map = AppUtils.tryParseJson<Map<String, Any>>(trimmed)
                map?.get("content") as? List<Map<String, Any>>
            } else {
                AppUtils.tryParseJson<List<Map<String, Any>>>(trimmed)
            }
        } catch (e: Exception) {
            null
        } ?: emptyList()

        val results = items.mapNotNull { it.toResult() }
        return newHomePageResponse(request.name, results, hasNext = results.isNotEmpty())
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val limit = 20
        val offset = (page - 1) * limit
        val body = """{"query":"${query.trim()}","limit":$limit,"offset":$offset}"""

        val response = app.post(
            url = "https://tokybook.com/api/v1/search/instant",
            headers = commonHeaders + mapOf(
                "Content-Type" to "application/json",
                "Origin" to "https://tokybook.com",
                "Referer" to "https://tokybook.com/search?q=${query.trim()}",
                "X-Requested-With" to "XMLHttpRequest"
            ),
            requestBody = body.toRequestBody("application/json".toMediaTypeOrNull())
        )

        if (response.code != 200 || response.text.isBlank()) return null

        val json = AppUtils.tryParseJson<Map<String, Any>>(response.text)
        val content = json?.get("content") as? List<Map<String, Any>> ?: return null

        return content.mapNotNull { it.toResult() }.toNewSearchResponseList()
    }

    private fun Map<String, Any>.toResult(): SearchResponse? {
        val title = this["title"] as? String ?: return null
        val slug = this["dynamicSlugId"] as? String ?: return null
        val poster = this["coverImage"] as? String

        val href = "$mainUrl/post/$slug"


        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val slug = url.substringAfterLast("/")

        val postData = app.post(
            "https://tokybook.com/api/v1/search/post-details",
            headers = commonHeaders + mapOf("Content-Type" to "application/json"),
            json = mapOf("dynamicSlugId" to slug)
        ).parsedSafe<Map<String, Any>>() ?: return null

        val rawId = postData["audioBookId"] as? String ?: return null
        val cleanId = rawId.substringAfterLast("/")
        val detailToken = postData["postDetailToken"] as? String ?: ""

        val playlistData = app.post(
            "https://tokybook.com/api/v1/playlist",
            headers = commonHeaders + mapOf("Content-Type" to "application/json"),
            json = mapOf("audioBookId" to rawId, "postDetailToken" to detailToken)
        ).parsedSafe<Map<String, Any>>()

        val streamToken = playlistData?.get("streamToken") as? String ?: ""

        val episodes = (playlistData?.get("tracks") as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.mapIndexed { index, track ->
                newEpisode("$cleanId|$streamToken|${track["src"]}") {
                    this.name = (track["trackTitle"] as? String)?.trim() ?: "Part ${index + 1}"
                    this.episode = index + 1
                }
            } ?: emptyList()

        val actors = mutableListOf<Actor>()

        (postData["authors"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach {
            val name = it["name"] as? String
            if (name != null) actors.add(Actor(name, "Author"))
        }

        (postData["narrators"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach {
            val name = it["name"] as? String
            if (name != null) actors.add(Actor(name, "Narrator"))
        }

        return newTvSeriesLoadResponse(postData["title"] as? String ?: "", url, TvType.TvSeries, episodes) {
            this.posterUrl = postData["coverImage"] as? String
            this.plot = (postData["description"] as? String)?.let { Jsoup.parse(it).text().trim() }
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        loadExtractor(data, "https://tokybook.com/", subtitleCallback, callback)
        return true
    }
}