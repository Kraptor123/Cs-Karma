package com.kraptor

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream

class AyzenTv : MainAPI() {
    override var mainUrl              = "https://uc3893d72e464202fcfb4e097435.dl.dropboxusercontent.com/cd/0/get/CunE5Nj0CcpzDykLSLZW0uoQAEoWrpOp_uwp7nfwthxq-gzz4sjkG_SvsP8nLp-kCWbxtOoWW314GQMRX9yEPNZrGUoNsWuQvtTGzgFS8moaSk_xyqPd72KCBy-SI2iZL8X8QDN5-WzQjWgw_lNhJXuH/file?dl=1"
    override var name                 = "AyzenTv"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = false
    override val supportedTypes       = setOf(TvType.Live)

    // İstenmeyen kategoriler
    private val excludedCategories = setOf(
        "Keşfet & Öğren",
        "Duyuru Alanı",
        "Sinema & Dizi",
        "Film Vizyon",
        "Dizi Vizyon",
        "Cine10 Panorama"
    )

    // Cache ve döngü kontrolü için
    private val processedUrls = mutableSetOf<String>()
    private val categoryCache = mutableMapOf<String, List<PlaylistItem>>()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Cache'i temizle
        processedUrls.clear()
        categoryCache.clear()

        val tumKanallar = loadAllChannels()

        return newHomePageResponse(
            tumKanallar.groupBy { it.attributes["group-title"] }.map { group ->
                val title = group.key ?: "Genel"
                val show  = group.value.map { kanal ->
                    val streamurl   = kanal.url.toString()
                    val channelname = kanal.title.toString()
                    val posterurl   = kanal.attributes["tvg-logo"].toString()
                    val chGroup     = kanal.attributes["group-title"].toString()
                    val nation      = kanal.attributes["tvg-country"] ?: "TR"

                    newLiveSearchResponse(
                        channelname,
                        LoadData(streamurl, channelname, posterurl, chGroup, nation).toJson(),
                        type = TvType.Live
                    ) {
                        this.posterUrl = posterurl
                        this.lang = nation
                    }
                }

                HomePageList(title, show, isHorizontalImages = true)
            },
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val tumKanallar = if (categoryCache.isEmpty()) {
            loadAllChannels()
        } else {
            categoryCache.values.flatten()
        }

        return tumKanallar.filter {
            it.title.toString().lowercase().contains(query.lowercase())
        }.map { kanal ->
            val streamurl   = kanal.url.toString()
            val channelname = kanal.title.toString()
            val posterurl   = kanal.attributes["tvg-logo"].toString()
            val chGroup     = kanal.attributes["group-title"].toString()
            val nation      = kanal.attributes["tvg-country"] ?: "TR"

            newLiveSearchResponse(
                channelname,
                LoadData(streamurl, channelname, posterurl, chGroup, nation).toJson(),
                type = TvType.Live
            ) {
                this.posterUrl = posterurl
                this.lang = nation
            }
        }
    }

    private suspend fun loadAllChannels(): List<PlaylistItem> = coroutineScope {
        val tumKanallar = mutableListOf<PlaylistItem>()

        try {
            val anaKategoriler = withTimeoutOrNull(30000) { // 30 saniye timeout
                IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
            } ?: return@coroutineScope emptyList()

            // Maksimum 5 kategoriyi paralel işle (fazla paralel istek timeout'a neden olabilir)
            val validCategories = anaKategoriler.items
                .filter { kategori ->
                    kategori.url != null &&
                            kategori.title !in excludedCategories &&
                            !kategori.title.isNullOrEmpty() &&
                            !processedUrls.contains(kategori.url)
                }
                .take(10) // Maksimum 10 kategori

            val jobs = validCategories.chunked(3).map { batch -> // 3'erli gruplar halinde işle
                async {
                    val batchResults = mutableListOf<PlaylistItem>()
                    for (kategori in batch) {
                        try {
                            val channels = loadCategoryChannels(kategori.url!!, kategori.title ?: "Genel", 1)
                            batchResults.addAll(channels)
                        } catch (e: Exception) {
                            Log.e("AyzenTv", "Kategori yüklenirken hata: ${kategori.title}", e)
                        }
                    }
                    batchResults
                }
            }

            val results = jobs.awaitAll()
            results.forEach { channelList ->
                tumKanallar.addAll(channelList)
            }

        } catch (e: Exception) {
            Log.e("AyzenTv", "Ana sayfa yüklenirken hata", e)
        }

        tumKanallar
    }

    private suspend fun loadCategoryChannels(url: String, categoryName: String, depth: Int = 0): List<PlaylistItem> {
        // Döngü kontrolü - maksimum 2 seviye derinlik
        if (depth > 2 || processedUrls.contains(url)) {
            return emptyList()
        }

        // Cache kontrolü
        if (categoryCache.containsKey(url)) {
            return categoryCache[url] ?: emptyList()
        }

        processedUrls.add(url)
        val processedChannels = mutableListOf<PlaylistItem>()

        try {
            val content = withTimeoutOrNull(15000) { // Her kategori için 15 saniye timeout
                app.get(url).text
            } ?: return emptyList()

            val kanallar = IptvPlaylistParser().parseM3U(content)

            for (kanal in kanallar.items) {
                when {
                    kanal.url == null -> continue

                    // Normal stream URL'i (m3u8, ts vb.)
                    isStreamUrl(kanal.url) -> {
                        val updatedAttributes = kanal.attributes.toMutableMap()
                        if (updatedAttributes["group-title"].isNullOrEmpty()) {
                            updatedAttributes["group-title"] = categoryName
                        }
                        processedChannels.add(kanal.copy(attributes = updatedAttributes))
                    }

                    // Alt kategori (sadece 1 seviye daha derine in)
                    isM3UFile(kanal.url) && depth < 2 -> {
                        try {
                            val subChannels = loadCategoryChannels(
                                kanal.url,
                                kanal.title ?: categoryName,
                                depth + 1
                            )
                            processedChannels.addAll(subChannels)
                        } catch (e: Exception) {
                            Log.e("AyzenTv", "Alt kategori yüklenirken hata: ${kanal.title}", e)
                        }
                    }
                }
            }

            // Cache'e kaydet
            categoryCache[url] = processedChannels

        } catch (e: Exception) {
            Log.e("AyzenTv", "Kategori içeriği yüklenirken hata: $url", e)
        }

        return processedChannels
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val loadData = fetchDataFromUrlOrJson(url)
        val nation: String = if (loadData.group == "NSFW") {
            "⚠️🔞🔞🔞 » ${loadData.group} | ${loadData.nation} « 🔞🔞🔞⚠️"
        } else {
            "» ${loadData.group} | ${loadData.nation} «"
        }

        return newLiveStreamLoadResponse(loadData.title, loadData.url, url) {
            this.posterUrl = loadData.poster
            this.plot = nation
            this.tags = listOf(loadData.group, loadData.nation)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val loadData = fetchDataFromUrlOrJson(data)

        callback.invoke(
            newExtractorLink(
                source  = this.name,
                name    = this.name,
                url     = loadData.url,
                type    = INFER_TYPE, {
                    referer = ""
                    quality = Qualities.Unknown.value
                }
            )
        )

        return true
    }

    data class LoadData(val url: String, val title: String, val poster: String, val group: String, val nation: String)

    private suspend fun fetchDataFromUrlOrJson(data: String): LoadData {
        return if (data.startsWith("{")) {
            parseJson<LoadData>(data)
        } else {
            LoadData(data, "Bilinmeyen Kanal", "", "Genel", "TR")
        }
    }

    // M3U dosyası kontrolü
    private fun isM3UFile(url: String): Boolean {
        return url.contains(".m3u", ignoreCase = true) &&
                (url.contains("dropbox.com", ignoreCase = true) ||
                        url.contains("drive.google.com", ignoreCase = true))
    }

    // Stream URL kontrolü
    private fun isStreamUrl(url: String): Boolean {
        return url.contains(".m3u8", ignoreCase = true) ||
                url.contains(".ts", ignoreCase = true) ||
                url.startsWith("http", ignoreCase = true) && !isM3UFile(url)
    }
}

data class Playlist(
    val items: List<PlaylistItem> = emptyList()
)

data class PlaylistItem(
    val title: String?                  = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String>    = emptyMap(),
    val url: String?                    = null,
    val userAgent: String?              = null
)

class IptvPlaylistParser {

    fun parseM3U(content: String): Playlist {
        return parseM3U(content.byteInputStream())
    }

    @Throws(PlaylistParserException::class)
    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()

        val firstLine = reader.readLine()
        if (firstLine?.isExtendedM3u() != true) {
            throw PlaylistParserException.InvalidHeader()
        }

        val playlistItems: MutableList<PlaylistItem> = mutableListOf()
        var currentIndex = 0

        var line: String? = reader.readLine()

        while (line != null) {
            if (line.isNotEmpty()) {
                if (line.startsWith(EXT_INF)) {
                    val title      = line.getTitle()
                    val attributes = line.getAttributes()

                    playlistItems.add(PlaylistItem(title, attributes))
                } else if (line.startsWith(EXT_VLC_OPT)) {
                    if (currentIndex < playlistItems.size) {
                        val item      = playlistItems[currentIndex]
                        val userAgent = item.userAgent ?: line.getTagValue("http-user-agent")
                        val referrer  = line.getTagValue("http-referrer")

                        val headers = mutableMapOf<String, String>()

                        if (userAgent != null) {
                            headers["user-agent"] = userAgent
                        }

                        if (referrer != null) {
                            headers["referrer"] = referrer
                        }

                        playlistItems[currentIndex] = item.copy(
                            userAgent = userAgent,
                            headers   = headers
                        )
                    }
                } else {
                    if (!line.startsWith("#") && currentIndex < playlistItems.size) {
                        val item       = playlistItems[currentIndex]
                        val url        = line.getUrl()
                        val userAgent  = line.getUrlParameter("user-agent")
                        val referrer   = line.getUrlParameter("referer")
                        val urlHeaders = if (referrer != null) {item.headers + mapOf("referrer" to referrer)} else item.headers

                        playlistItems[currentIndex] = item.copy(
                            url       = url,
                            headers   = item.headers + urlHeaders,
                            userAgent = userAgent ?: item.userAgent
                        )
                        currentIndex++
                    }
                }
            }

            line = reader.readLine()
        }
        return Playlist(playlistItems)
    }

    private fun String.replaceQuotesAndTrim(): String {
        return replace("\"", "").trim()
    }

    private fun String.isExtendedM3u(): Boolean = startsWith(EXT_M3U)

    private fun String.getTitle(): String? {
        return split(",").lastOrNull()?.replaceQuotesAndTrim()
    }

    private fun String.getUrl(): String? {
        return split("|").firstOrNull()?.replaceQuotesAndTrim()
    }

    private fun String.getUrlParameter(key: String): String? {
        val urlRegex     = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val keyRegex     = Regex("$key=(\\w[^&]*)", RegexOption.IGNORE_CASE)
        val paramsString = replace(urlRegex, "").replaceQuotesAndTrim()

        return keyRegex.find(paramsString)?.groups?.get(1)?.value
    }

    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex      = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim().split(",").first()

        return attributesString
            .split(Regex("\\s"))
            .mapNotNull {
                val pair = it.split("=")
                if (pair.size == 2) pair.first() to pair.last().replaceQuotesAndTrim() else null
            }
            .toMap()
    }

    private fun String.getTagValue(key: String): String? {
        val keyRegex = Regex("$key=(.*)", RegexOption.IGNORE_CASE)

        return keyRegex.find(this)?.groups?.get(1)?.value?.replaceQuotesAndTrim()
    }

    companion object {
        const val EXT_M3U     = "#EXTM3U"
        const val EXT_INF     = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }
}

sealed class PlaylistParserException(message: String) : Exception(message) {
    class InvalidHeader : PlaylistParserException("Invalid file header. Header doesn't start with #EXTM3U")
}