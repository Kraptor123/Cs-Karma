// ! Bu araç @ByAyzen tarafından | @CS-Karma için yazılmıştır.

package com.byayzen

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

const val TAG = "GoodShortAPI"

class GoodShort : MainAPI() {
    override var mainUrl = "https://www.goodshort.com"
    override var name = "GoodShort"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/dramas/playlets" to "All Dramas",
        "${mainUrl}/dramas/romance-playlets-286" to "Romance",
        "${mainUrl}/dramas/suspense-playlets-289" to "Suspense",
        "${mainUrl}/dramas/fantasy-playlets-299" to "Male Fantasy"
    )

    // --- Basitleştirilmiş Helper Fonksiyonlar ---
    private fun getBody(bookId: String, pageNo: Int = 0, pageSize: Int = 0): okhttp3.RequestBody {
        val json = JSONObject().apply {
            put("bookId", bookId)
            if (pageNo > 0) put("pageNo", pageNo)
            if (pageSize > 0) put("pageSize", pageSize)
        }.toString()
        return json.toRequestBody("application/json;charset=utf-8".toMediaType())
    }

    private fun getCommonHeaders(url: String) = mapOf(
        "Authority" to "www.goodshort.com",
        "Accept" to "application/json, text/plain, */*",
        "Content-Type" to "application/json;charset=utf-8",
        "Platform" to "WEB",
        "CurrentLanguage" to "en",
        "Origin" to "https://www.goodshort.com",
        "Referer" to url,
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0",
        "Cookie" to "currentLanguage=en"
    )
    // --- Helper Fonksiyonlar Bitiş ---

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}?page=$page"
        val response = app.get(url)
        val jsonData = response.document.select("script").firstOrNull { it.data().contains("window.__INITIAL_STATE__") }?.data()
            ?: return newHomePageResponse(request.name, emptyList())

        return try {
            val marker = "window.__INITIAL_STATE__="
            val jsonStart = jsonData.indexOf(marker) + marker.length
            val jsonEnd = jsonData.indexOf(";(function()", jsonStart)

            val rawJson = jsonData.substring(jsonStart, jsonEnd).replace("\\u002F", "/")
            val json = JSONObject(rawJson)
            val bookList = json.getJSONObject("Browse").getJSONArray("bookList")

            val items = (0 until bookList.length()).mapNotNull { i ->
                runCatching {
                    val book = bookList.getJSONObject(i)
                    val title = book.getString("bookName")
                    val resourceUrl = book.getString("bookResourceUrl")
                    var cover = book.getString("cover")
                    if (!cover.contains("?")) cover = "$cover?w=293&h=410"

                    newTvSeriesSearchResponse(title, "$mainUrl/drama/$resourceUrl", TvType.TvSeries) {
                        this.posterUrl = cover
                    }
                }.getOrNull()
            }
            newHomePageResponse(request.name, items)
        } catch (_: Exception) {
            newHomePageResponse(request.name, emptyList())
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        Log.d(TAG, "Başlangıç: load($url)")
        return try {
            val bookId = url.substringAfterLast("-").filter { it.isDigit() }
            if (bookId.isEmpty()) {
                Log.d(TAG, "HATA: bookId boş.")
                return null
            }
            val headers = getCommonHeaders(url)

            // 1. Kısım: Metadata ve Öneriler (/book/detail)
            val detailApiUrl = "https://www.goodshort.com/hwycreels/book/detail"
            val detailReqBody = getBody(bookId)
            val jsonDetail = app.post(detailApiUrl, headers = headers, requestBody = detailReqBody).text.let(::JSONObject)

            if (!jsonDetail.optBoolean("success", false)) {
                Log.e(TAG, "Kitap detayı çekilemedi.")
                return null
            }

            val data = jsonDetail.getJSONObject("data")
            val book = data.getJSONObject("book")

            val title = book.getString("bookName")
            var poster = book.optString("cover", "")
            if (poster.isNotEmpty() && !poster.contains("?")) poster = "$poster?w=293&h=410"
            val description = book.optString("introduction", "")
            val year = book.optString("lastChapterTime").substringBefore("-").toIntOrNull()

            val tags = mutableListOf<String>().apply {
                book.optJSONArray("typeTwoNames")?.let { for (i in 0 until it.length()) add(it.getString(i)) }
                book.optJSONArray("labels")?.let { for (i in 0 until it.length()) add(it.getString(i)) }
            }

            val recommendations = data.optJSONArray("recommends")?.let { recs ->
                (0 until recs.length()).mapNotNull { i ->
                    runCatching {
                        val rec = recs.getJSONObject(i)
                        newTvSeriesSearchResponse(
                            rec.optString("bookName"),
                            "$mainUrl/drama/${rec.optString("bookResourceUrl")}",
                            TvType.TvSeries
                        ) { this.posterUrl = rec.optString("cover") }
                    }.getOrNull()
                }
            } ?: emptyList()

            // 2. Kısım: Tüm Bölümleri Sayfalama ile çekme (/chapter/page)
            val chapterPageUrl = "https://www.goodshort.com/hwycreels/chapter/page"
            val episodes = mutableListOf<Episode>()
            var pageNo = 1
            var totalPages = 1
            val pageSize = 50

            while (pageNo <= totalPages) {
                val pageReqBody = getBody(bookId, pageNo, pageSize)
                val response = app.post(chapterPageUrl, headers = headers, requestBody = pageReqBody)
                val jsonPage = JSONObject(response.text)

                if (jsonPage.optBoolean("success")) {
                    val pageData = jsonPage.getJSONObject("data")
                    totalPages = pageData.optInt("pages", 1)
                    val records = pageData.getJSONArray("records")

                    for (i in 0 until records.length()) {
                        runCatching {
                            val record = records.getJSONObject(i)
                            val name = record.optString("chapterName")
                            val index = record.optInt("index") + 1
                            val img = record.optString("image")
                            val m3u8Link = record.optString("m3u8Path")

                            val linkData = if (m3u8Link.isNotEmpty()) m3u8Link else "locked_chapter_no_link"

                            episodes.add(newEpisode(linkData) {
                                this.name = "Chapter $name" + if (linkData.startsWith("locked")) " (Locked)" else ""
                                this.episode = index
                                this.posterUrl = img
                            })
                        }.onFailure { Log.e(TAG, "Bölüm kaydı işlenirken hata: ${it.message}") }
                    }
                } else {
                    Log.e(TAG, "Bölüm sayfası $pageNo başarısız.")
                    break
                }
                pageNo++
            }

            Log.d(TAG, "İşlem Başarılı. Toplam ${episodes.size} bölüm listelendi.")

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags.distinct()
                this.recommendations = recommendations
                this.year = year
            }

        } catch (e: Exception) {
            Log.e(TAG, "KRİTİK HATA: load fonksiyonu tamamen başarısız oldu: ${e.message}", e)
            null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isEmpty() || data == "locked_chapter_no_link") return false
        return runCatching {
            callback.invoke(
                newExtractorLink(name, name, data, ExtractorLinkType.M3U8) {
                    this.quality = Qualities.Unknown.value
                }
            )
            true
        }.getOrDefault(false)
    }
}