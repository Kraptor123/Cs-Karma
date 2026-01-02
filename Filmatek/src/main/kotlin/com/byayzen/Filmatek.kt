package com.byayzen

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors

class Filmatek : MainAPI() {
    override var mainUrl = "https://filmatek.net"
    override var name = "Filmatek"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/tur/aile/" to "Aile", "$mainUrl/tur/aksiyon/" to "Aksiyon",
        "$mainUrl/tur/aksiyon-macera/" to "Aksiyon & Macera", "$mainUrl/tur/animasyon/" to "Animasyon",
        "$mainUrl/tur/belgesel/" to "Belgesel", "$mainUrl/tur/bilim-kurgu-fantazi/" to "Bilim Kurgu & Fantazi",
        "$mainUrl/tur/bilim-kurgu/" to "Bilim-Kurgu", "$mainUrl/tur/biyografi/" to "Biyografi",
        "$mainUrl/tur/dram/" to "Dram", "$mainUrl/tur/edebiyat-uyarlamalari/" to "Edebiyat Uyarlamaları",
        "$mainUrl/tur/fantastik/" to "Fantastik", "$mainUrl/tur/gerilim/" to "Gerilim",
        "$mainUrl/tur/gizem/" to "Gizem", "$mainUrl/tur/komedi/" to "Komedi",
        "$mainUrl/tur/korku/" to "Korku", "$mainUrl/tur/macera/" to "Macera",
        "$mainUrl/tur/muzik/" to "Müzik", "$mainUrl/tur/romantik/" to "Romantik",
        "$mainUrl/tur/savas/" to "Savaş", "$mainUrl/tur/savas-politik/" to "Savaş & Politik",
        "$mainUrl/tur/suc/" to "Suç", "$mainUrl/tur/tarih/" to "Tarih",
        "$mainUrl/tur/tv-film/" to "TV film", "$mainUrl/tur/vahsi-bati/" to "Vahşi Batı"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data.removeSuffix("/")}/page/$page/"
        val items = app.get(url).document.select("div.items article, #archive-content article").mapNotNull { it.toRes() }
        return newHomePageResponse(request.name, items, items.isNotEmpty())
    }

    private fun Element.toRes(): SearchResponse? {
        val a = this.selectFirst("div.data h3 a, h3 a") ?: return null
        val img = this.selectFirst("img")
        return newMovieSearchResponse(a.text(), fixUrlNull(a.attr("href")) ?: return null, TvType.Movie) {
            this.posterUrl = fixUrlNull(img?.attr("data-src")?.ifBlank { null } ?: img?.attr("src"))
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page <= 1) "$mainUrl/?s=$query" else "$mainUrl/page/$page/?s=$query"
        val res = app.get(url).document.select("div.result-item").mapNotNull {
            val img = it.selectFirst("div.image img") ?: return@mapNotNull null
            newMovieSearchResponse(img.attr("alt"), fixUrlNull(it.selectFirst("div.title a")?.attr("href")) ?: return@mapNotNull null, TvType.Movie) { this.posterUrl = fixUrlNull(img.attr("src")) }
        }
        return newSearchResponseList(res, res.isNotEmpty())
    }

    override suspend fun load(url: String): LoadResponse? {
        val d = app.get(url).document
        val t = d.selectFirst("div.data h1, h1")?.text()?.trim() ?: d.selectFirst("meta[property='og:title']")?.attr("content")?.split("-")?.firstOrNull()?.trim() ?: return null
        return newMovieLoadResponse(t, url, TvType.Movie, url) {
            posterUrl = fixUrlNull(d.selectFirst("meta[property='og:image']")?.attr("content") ?: d.selectFirst("div.poster img")?.attr("src"))
            plot = d.selectFirst("div.wp-content p, meta[property='og:description']")?.run { if (tagName() == "meta") attr("content") else text() }?.trim()
            year = d.selectFirst("span.date")?.text()?.trim()?.takeLast(4)?.toIntOrNull()
            tags = d.select("div.sgeneros a").map { it.text() }
            score = d.selectFirst("span.dt_rating_vmanual")?.text()?.toDoubleOrNull()?.let { Score.from10(it.toFloat()) }
            recommendations = d.select(".srelacionados article, #single_relacionados article").mapNotNull {
                val link = it.selectFirst("a") ?: return@mapNotNull null
                val img = link.selectFirst("img") ?: return@mapNotNull null
                newMovieSearchResponse(img.attr("alt").ifBlank { link.text() }, fixUrlNull(link.attr("href")) ?: return@mapNotNull null, TvType.Movie) { this.posterUrl = fixUrlNull(img.attr("src")) }
            }
            addActors(d.select("div.person").mapNotNull { Actor(it.selectFirst("div.name a")?.text() ?: return@mapNotNull null, fixUrlNull(it.selectFirst("img")?.attr("src"))) })
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val id = Regex("""postid-(\d+)""").find(app.get(data).document.selectFirst("body")?.className() ?: "")?.groupValues?.get(1) ?: return false
        val ajax = app.post("$mainUrl/wp-admin/admin-ajax.php", data = mapOf("action" to "doo_player_ajax", "post" to id, "nume" to "1", "type" to "movie"), referer = data, headers = mapOf("X-Requested-With" to "XMLHttpRequest")).text.replace("\\/", "/")
        val u = Regex("""(?:src|url)["']?\s*[:=]\s*["']([^"']+)["']""").find(ajax)?.groupValues?.get(1) ?: return false
        loadExtractor(if (u.startsWith("/")) "$mainUrl$u" else u, data, subtitleCallback, callback)
        return true
    }
}