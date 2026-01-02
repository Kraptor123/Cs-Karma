// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors

class FilmBol : MainAPI() {
    override var mainUrl = "https://www.filmbol.org/"
    override var name = "Filmbol"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Documentary)

    override val mainPage = mainPageOf(
        "${mainUrl}/animasyon-filmleri-indir/" to "Animasyon",
        "${mainUrl}/korku-filmleri-indir/" to "Korku",
        "${mainUrl}/film-arsivi/?qualty=4k/" to "4K",
        "${mainUrl}/romantik-filmleri-indir/" to "Romantik",
        "${mainUrl}/fantastik-filmleri-indir/" to "Fantastik",
        "${mainUrl}/film-arsivi/?qualty=4kp/" to "4K Remux",
        "${mainUrl}/aile-filmleri-indir/" to "Aile",
        "${mainUrl}/aksiyon-filmleri-indir/" to "Aksiyon",
        "${mainUrl}/belgesel-filmleri-indir/" to "Belgesel",
        "${mainUrl}/bilim-kurgu-filmleri-indir/" to "Bilim-Kurgu",
        "${mainUrl}/biyografi-filmleri-indir/" to "Biyografi",
        "${mainUrl}/dram-filmleri-indir/" to "Dram",
        "${mainUrl}/genclik-filmleri-indir/" to "Gençlik",
        "${mainUrl}/gerilim-filmleri-indir/" to "Gerilim",
        "${mainUrl}/gizem-filmleri-indir/" to "Gizem",
        "${mainUrl}/komedi-filmleri-indir/" to "Komedi",
        "${mainUrl}/macera-filmleri-indir/" to "Macera",
        "${mainUrl}/muzik-filmleri-indir/" to "Müzik",
        "${mainUrl}/savas-filmleri-indir/" to "Savaş",
        "${mainUrl}/spor-filmleri-indir/" to "Spor",
        "${mainUrl}/suc-filmleri-indir/" to "Suç",
        "${mainUrl}/tarih-filmleri-indir/" to "Tarih",
        "${mainUrl}/vahsi-bati-filmleri-indir/" to "Vahşi Batı",
        "${mainUrl}/vizyon/" to "Vizyon",
        "${mainUrl}/yerli-film-indir/" to "Yerli",
        "${mainUrl}/yetiskin-filmleri-indir/" to "Yetişkin"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.contains("?")) {
            if (page > 1) request.data.replace("/?", "/page/$page/?") else request.data
        } else {
            if (page > 1) "${request.data}page/$page/" else request.data
        }

        val home = app.get(url).document.select("div.row div.film-box").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("div.movie-details h2 a")?.text() ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.attr("src"))
        val score = selectFirst("div.rating span.align-right")?.text()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.score = score?.let { Score.from10(it.toDoubleOrNull() ?: 0.0) }
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val results = app.get("${mainUrl}/page/${page}/?s=${query}").document
            .select("div.row div.film-box").mapNotNull { it.toSearchResult() }

        return newSearchResponseList(results, hasNext = true)
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.movie-left div.poster img")?.attr("src"))
        val description = document.select("div.movies-data p").firstOrNull()?.text()?.trim()

        val recommendations = document.select("div.related-movies .item").mapNotNull {
            it.toRecommendationResult()
        }

        val year = document.selectFirst("li.release span")?.text()?.trim()?.toIntOrNull()
        val tags = document.select("div.senaryo a").map { it.text() }
        val duration = document.selectFirst("li.time span")?.text()?.split(" ")?.first()?.toIntOrNull()

        val actors = document.select("div.indexSwi .swiper-slide").map {
            Actor(
                it.selectFirst("img")?.attr("alt") ?: it.selectFirst("div.index_slider_name span")?.text() ?: "Bilinmeyen",
                it.selectFirst("img")?.attr("src")
            )
        }

        val episodes = document.select("div.popubody").mapIndexedNotNull { index, popbody ->
            val versionName = popbody.selectFirst("div.popheading")?.text()?.trim()
            val dataUrl = popbody.selectFirst("center a")?.attr("href")

            if (dataUrl == null || versionName == null) return@mapIndexedNotNull null

            newEpisode(dataUrl) {
                this.name = versionName.substringAfter("Sürüm: ").trim()
                this.season = 1
                this.episode = index + 1
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            this.duration = duration
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val movieBox = selectFirst("div.movie-box") ?: return null
        val title = movieBox.selectFirst("a img")?.attr("alt") ?: return null
        val href = fixUrlNull(movieBox.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(movieBox.selectFirst("a img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }
    override suspend fun loadLinks(
        data: String, // Bu parametre forum URL'sini içeriyor
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val cookie = FilmbolAyarlar.getCookie()

        if (cookie.isNullOrEmpty()) {
//            Log.e("FilmbolParser", "Cookie ayarlanmamış. Lütfen eklenti ayarlarından cookie'nizi girin.")
            return false
        }

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36",
            "Referer" to "https://forum.filmbol.org/", // <<< KRİTİK BAŞLIK
            "Cookie" to cookie // <<< ARTIK AYARLARDAN GELİYOR
        )

//        Log.d("FilmbolParser", "loadLinks çalıştı. Forum URL'si: $data")
//        Log.d("FilmbolParser", "YENİ ve SADELEŞTİRİLMİŞ Header'lar: $headers")

        try {
//            Log.d("FilmbolParser", "Forum sayfasına sıkıştırılmamış ve Referer'lı istek atılıyor...")
            val response = app.get(data, headers = headers)
//            Log.d("FilmbolParser", "İsteğin HTTP Durum Kodu: ${response.code}")
            val forumDocument = response.document
//            Log.d("FilmbolParser", "Forum sayfası başarıyla alındı.")

            val downloadButtons = forumDocument.select("a.down-buttons")
//            Log.d("FilmbolParser", "Toplam ${downloadButtons.size} adet 'down-buttons' linki bulundu.")

            for (button in downloadButtons) {
                val href = button.attr("href")
//                Log.d("FilmbolParser", "İncelenen Link: $href")

                when {
                    href.contains("downloader-check.php") -> {
//                        Log.d("FilmbolParser", "✅ Doğru indirme linki bulundu: $href")
                        callback.invoke(
                            newExtractorLink(
                                source = this.name,
                                name = "Filmbol",
                                url = href
                            ) {
                                this.referer = "https://alparslan.filmbol.org/"
                                this.headers = headers
                            }
                        )
//                        Log.d("FilmbolParser", "ExtractorLink başarıyla oluşturulup callback'e gönderildi.")
                        return true
                    }
                    href.contains("market.php") -> {
//                        Log.w("FilmbolParser", "⚠️ Premium üyelik gerektiren link bulundu: $href. Hesabınızın premium ve aktif olduğundan emin olun.")
                    }
                    else -> {
//                        Log.w("FilmbolParser", "Bilinmeyen bir 'down-buttons' linki bulundu: $href")
                    }
                }
            }
//            Log.e("FilmbolParser", "❌ Geçerli bir indirme linki bulunamadı. Kütüphanenizin başlıkları doğru gönderdiğinden ve yanıtı doğru işlediğinden emin olun.")
            return false

        } catch (e: Exception) {
//            Log.e("FilmbolParser", "loadLinks sırasında bir hata oluştu: ${e.message}")
            return false
        }
    }
    }