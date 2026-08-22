// ! Bu araç @Kraptor123 tarafından | @cs-Karma için yazılmıştır.

package com.kraptor

import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.*

class Streamed() : MainAPI() {
    override var mainUrl = "https://streamed.pk"
    override var name = "Streamed"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)
    override val vpnStatus = VPNStatus.MightBeNeeded


    override val mainPage = mainPageOf(
        "${mainUrl}/api/matches/live/popular" to "Live Popular",
        "${mainUrl}/api/matches/live" to "Live",
        "${mainUrl}/api/matches/all-today/popular" to "Today's Popular Matches",
        "${mainUrl}/api/matches/football/popular" to "Football",
        "${mainUrl}/api/matches/fight/popular" to "Fight",
        "${mainUrl}/api/matches/american-football/popular" to "American Football",
        "${mainUrl}/api/matches/basketball/popular" to "Basketball",
        "${mainUrl}/api/matches/tennis/popular" to "Tennis",
        "${mainUrl}/api/matches/hockey/popular" to "Hockey",
        "${mainUrl}/api/matches/baseball/popular" to "Baseball",
        "${mainUrl}/api/matches/darts/popular" to "Darts",
        "${mainUrl}/api/matches/motor-sports/popular" to "Motor Sports",
        "${mainUrl}/api/matches/golf/popular" to "Golf",
        "${mainUrl}/api/matches/billiards/popular" to "Billiards",
        "${mainUrl}/api/matches/afl/popular" to "AFL",
        "${mainUrl}/api/matches/cricket/popular" to "Cricket",
        "${mainUrl}/api/matches/other/popular" to "Other"
    )


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val textdoc = app.get(request.data).text
        val document = textdoc
        val mapper = jacksonObjectMapper().registerKotlinModule()
        val matches: List<Matches> = mapper.readValue(document)

        val items = matches
            .filter { it.sources?.isNotEmpty() == true }
            .mapNotNull { match ->
                val title = match.title ?: return@mapNotNull null
                val firstSourceId =
                    match.sources?.firstOrNull()?.id ?: match.id ?: return@mapNotNull null
                val href = "$mainUrl/watch/${firstSourceId}"
                val poster = "${match.poster}"
                val posterUrl = if (poster.contains("api")) {
                    "${mainUrl}${match.poster}"
                } else {
                    "${mainUrl}/api/images/badge/${match.id}.webp"
                }
                newLiveSearchResponse(title, href, TvType.Live) {
                    this.posterUrl = posterUrl
                    this.posterHeaders =
                        mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:141.0) Gecko/20100101 Firefox/141.0")
                }
            }

        return newHomePageResponse(
            list = HomePageList(
                request.name,
                list = items,
                isHorizontalImages = true,
            ), hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val mapper = jacksonObjectMapper().registerKotlinModule()

        val matches = mutableListOf<Matches>()
        val txt = app.get("$mainUrl/api/matches/all").text
        val list: List<Matches> = mapper.readValue(txt)
        matches.addAll(list)

        if (matches.isEmpty()) return emptyList()

        val unique = matches
            .associateBy { it.id ?: it.title ?: java.util.UUID.randomUUID().toString() }
            .values
            .toList()

        val q = query.lowercase().trim()
        val normalizedQuery = normalizeTurkish(q)

        val sorted = unique.sortedWith(compareByDescending<Matches> {
            val t = it.title?.lowercase() ?: ""
            when {
                t.startsWith(q) -> 2
                t.contains(q) -> 1
                else -> 0
            }
        }.thenBy { it.title ?: "" })

        val filtered = sorted.filter { match ->
            val titleNorm = normalizeTurkish(match.title ?: "")
            val sourceMatch = match.sources?.any { src ->
                normalizeTurkish(src.id ?: "").contains(normalizedQuery)
            } ?: false

            titleNorm.contains(normalizedQuery) || sourceMatch
        }

        if (filtered.isEmpty()) return emptyList()

        return filtered.mapNotNull { match ->
            val title = match.title ?: return@mapNotNull null
            val firstSourceId =
                match.sources?.firstOrNull()?.id ?: match.id ?: return@mapNotNull null
            val href = "$mainUrl/watch/${firstSourceId}"
            val poster = match.poster ?: ""
            val posterUrl = if (poster.contains("api")) {
                "${mainUrl}${match.poster}"
            } else {
                "${mainUrl}/api/images/badge/${match.id}.webp"
            }

            newLiveSearchResponse(title, href, TvType.Live) {
                this.posterUrl = posterUrl
                this.posterHeaders =
                    mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:141.0) Gecko/20100101 Firefox/141.0")
            }
        }
    }

    private fun normalizeTurkish(s: String): String {
        var res = s.lowercase()
        val map = mapOf(
            'ç' to 'c',
            'ğ' to 'g',
            'ı' to 'i',
            'ö' to 'o',
            'ş' to 's',
            'ü' to 'u',
            'İ' to 'i'
        )
        map.forEach { (k, v) -> res = res.replace(k.toString(), v.toString()) }
        res = java.text.Normalizer.normalize(res, java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return res.replace("[^a-z0-9 ]".toRegex(), "").trim()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun load(url: String): LoadResponse? {
        val sourceId = url.substringAfterLast("/")

        val mapper = jacksonObjectMapper().registerKotlinModule()
        val txt = app.get("$mainUrl/api/matches/all").text
        val matches: List<Matches> = mapper.readValue(txt)

        val match = matches.find { match ->
            match.sources?.any { it.id == sourceId } == true || match.id == sourceId
        } ?: return null

        val title = match.title ?: "Bilinmeyen Başlık"

        val poster = match.poster ?: ""
        val posterUrl = if (poster.contains("api")) {
            "${mainUrl}${match.poster}"
        } else {
            "${mainUrl}/api/images/badge/${match.id}.webp"
        }

        val description = match.date?.let { dateTimestamp ->
            val currentTime = System.currentTimeMillis()
            val matchTime = if (dateTimestamp > 1000000000000L) {
                dateTimestamp
            } else {
                dateTimestamp * 1000
            }

            when {
                matchTime > currentTime -> {
                    val remainingTimeMs = matchTime - currentTime
                    val remainingMinutes = remainingTimeMs / (1000 * 60)
                    val hours = remainingMinutes / 60
                    val minutes = remainingMinutes % 60

                    when {
                        hours > 24 -> {
                            val days = hours / 24
                            val remainingHours = hours % 24
                            "Maça kalan süre: $days gün $remainingHours saat"
                        }

                        hours > 0 -> {
                            "Maça kalan süre: $hours saat $minutes dakika"
                        }

                        minutes > 0 -> {
                            "Maça kalan süre: $minutes dakika"
                        }

                        else -> {
                            "Maç yakında başlayacak"
                        }
                    }
                }

                else -> {
                    "Maç şu anda canlı veya tamamlandı - Bağlantı bulunamazsa basılıp tutup bağlantıları yenilemeyi deneyebilirsiniz."
                }
            }
        } ?: "Maç zamanı bilgisi bulunamadı"

        val tags = match.category?.let { listOf(it) } ?: emptyList()

        val sourcesCount = match.sources?.size ?: 0
        val finalDescription = if (sourcesCount > 1) {
            "$description\n\nMevcut kaynak sayısı: $sourcesCount"
        } else {
            description
        }

        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = posterUrl
            this.posterHeaders =
                mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:141.0) Gecko/20100101 Firefox/141.0")
            this.plot = finalDescription
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {

        val sourceId = data.substringAfterLast("/")

        Log.d("Ayzen", sourceId)
        val mapper = jacksonObjectMapper().registerKotlinModule()

        try {
            val apiUrl = "$mainUrl/api/matches/all"

            Log.d("Ayzen", apiUrl)
            val response = app.get(apiUrl)
            val txt = response.text

            val matches: List<Matches> = mapper.readValue(txt)

            Log.d("Ayzen", "${matches.size}")
            val match = matches.find { m ->
                val hasSourceId = m.sources?.any { it.id == sourceId } == true
                val isMatchId = m.id == sourceId
                hasSourceId || isMatchId
            }

            if (match == null) {
                Log.d("Ayzen", sourceId)
                return@withContext false
            }

            Log.d("Ayzen", "${match.title} ${match.sources?.size ?: 0}")

            fun viewersOf(s: Stream): Int {
                return try {
                    when (val v = s.viewers) {
                        is Number -> v.toInt()
                        is String -> v.toIntOrNull() ?: 0
                        else -> 0
                    }
                } catch (e: Exception) { 0 }
            }

            val allStreams = mutableListOf<Pair<Stream, String>>()

            match.sources?.forEachIndexed { index, source ->
                val sType = source.source
                val sId = source.id
                if (sType != null && sId != null) {
                    try {
                        val streamApiUrl = "$mainUrl/api/stream/$sType/$sId"
                        Log.d("Ayzen", streamApiUrl)
                        val sResponse = app.get(streamApiUrl).text
                        val streams: List<Stream> = mapper.readValue(
                            sResponse,
                            object : com.fasterxml.jackson.core.type.TypeReference<List<Stream>>() {}
                        )
                        Log.d("Ayzen", "$index $sType ${streams.size}")

                        streams.forEach {
                            allStreams.add(Pair(it, sType))
                        }
                    } catch (e: Exception) {
                    }
                }
            }

            val streamsWithPositiveViewers = allStreams.filter { viewersOf(it.first) > 0 }
            val streamsToProcess: List<Pair<Stream, String>> =
                if (streamsWithPositiveViewers.size >= 2) {
                    streamsWithPositiveViewers.sortedByDescending { viewersOf(it.first) }
                } else {
                    val zeroViewerStreams = allStreams.filter { viewersOf(it.first) == 0 }
                    streamsWithPositiveViewers.sortedByDescending { viewersOf(it.first) } + zeroViewerStreams
                }

            Log.d("Ayzen", "${streamsToProcess.size}")

            val processedStreams = mutableSetOf<String>()

            streamsToProcess.forEachIndexed { idx, (stream, sourceType) ->
                try {
                    val embedUrl = stream.embedUrl.toString()
                    if (embedUrl.isNotEmpty() && !processedStreams.contains(embedUrl)) {
                        processedStreams.add(embedUrl)

                        Log.d("Ayzen", embedUrl)
                        loadExtractor(
                            url = embedUrl,
                            referer = mainUrl,
                            subtitleCallback = { sub ->
                                subtitleCallback.invoke(sub)
                            },
                            callback = { link ->
                                callback.invoke(link)
                            }
                        )
                    }
                } catch (e: Exception) {
                    Log.e("Ayzen", "$idx")
                }
            }

            return@withContext true
        } catch (e: Exception) {
            Log.e("Streamed", "İframe yok")
            return@withContext false
        }
    }



    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Matches(
        @JsonProperty("id") val id: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("category") val category: String?,
        @JsonProperty("date") val date: Long?,
        @JsonProperty("popular") val popular: Boolean?,
        @JsonProperty("sources") val sources: List<Sources>?,
        @JsonProperty("poster") val poster: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Sources(
        @JsonProperty("id") val id: String?,
        @JsonProperty("source") val source: String?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Stream(
        @JsonProperty("id") val id: String?,
        @JsonProperty("streamNo") val streamNo: Int?,
        @JsonProperty("language") val language: String?,
        @JsonProperty("embedUrl") val embedUrl: String?,
        @JsonProperty("source") val source: String?,
        @JsonProperty("hd") val hd: Boolean?,
        @JsonProperty("viewers") val viewers: Int?
    )
}