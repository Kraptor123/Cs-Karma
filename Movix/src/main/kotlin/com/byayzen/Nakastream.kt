package com.byayzen

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.api.Log
import com.lagradost.cloudstream3.app

object Nakastream {
    private const val mainUrl = "https://nakastream.tv"
    private const val lognick  = "Movix_Nakastream"

    private data class Response(val sources: List<Source>? = null)
    private data class Source(
        val url:        String? = null,
        val isHls:      Boolean? = null,
        val subtitles:  List<Subtitle>? = null,
        val maxQuality: String? = null,
        val sourceName: String? = null
    )
    private data class Subtitle(
        val lang:  String? = null,
        val label: String? = null,
        val url:   String? = null
    )
    private data class BrowseItem(
        val id:         Int? = null,
        val title:      String? = null,
        val posterPath: String? = null
    )

    private fun String?.toFullUrl(): String? =
        if (isNullOrBlank()) null else if (startsWith("/")) "$mainUrl$this" else this

    suspend fun Nakaoynat(
        tmdbId: String,
        type: String,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val browseUrl = "$mainUrl/api/v1/browse/by-tmdb/$type/$tmdbId"
            Log.d(lognick, "Başladı: $browseUrl")

            val browseRes = app.get(
                browseUrl,
                headers = mapOf("Referer" to "$mainUrl/content/$type/$tmdbId")
            ).parsedSafe<BrowseItem>()

            val id = browseRes?.id?.toString() ?: return
            Log.d(lognick, "Id bulundu: $id")

            val apiUrl = if (type == "movie") {
                "$mainUrl/api/v1/streaming/sources/$id?type=movie"
            } else {
                "$mainUrl/api/v1/streaming/sources/$id?type=tv&season=$season&episode=$episode"
            }

            val referer = "$mainUrl/player?id=$id&type=$type" +
                    (if (type == "tv") "&season=$season&episode=$episode" else "") +
                    (browseRes.title?.let { "&title=$it" } ?: "") +
                    (browseRes.posterPath?.let { "&poster=$it" } ?: "")

            val headers = mapOf(
                "Referer" to referer,
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:152.0) Gecko/20100101 Firefox/152.0",
            )

            Log.d(lognick, "API Linki: $apiUrl")
            val response = app.get(apiUrl, headers = headers).parsedSafe<Response>()

            response?.sources?.forEach { source ->
                val videoUrl = source.url.toFullUrl() ?: return@forEach
                Log.d(lognick, "Video Linki: $videoUrl")

                source.subtitles?.forEach { sub ->
                    val subUrl = sub.url.toFullUrl() ?: return@forEach
                    subtitleCallback(
                        newSubtitleFile(
                            sub.lang.orEmpty(),
                            subUrl
                        )
                    )
                }

                val name = "Nakastream" + (source.sourceName?.let { " - $it" } ?: "")
                val quality = source.maxQuality?.filter { it.isDigit() }?.toIntOrNull() ?: Qualities.Unknown.value
                val linkType = if (source.isHls == true || videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO

                callback(
                    newExtractorLink(
                        "Nakastream",
                        name,
                        videoUrl,
                        linkType
                    ) {
                        this.quality = quality
                        this.referer = referer
                    }
                )
            }
        } catch (e: Exception) {
            Log.d(logTag, "Hata: $e")
        }
    }
}