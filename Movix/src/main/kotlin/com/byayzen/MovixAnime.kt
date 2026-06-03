package com.byayzen

import android.net.Uri
import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

object MovixAnimeExtractor {

    suspend fun fetchAnimeLinks(
        mainUrl: String,
        apibase: String,
        title: String,
        type: String,
        episode: String?
    ): List<String> {
        val animeApiHeaders = mapOf("Origin" to mainUrl)
        val encoded = Uri.encode(title)
        val url = apibase.substringBeforeLast("/") + "/anime/search/$encoded?includeSeasons=true&includeEpisodes=true"
        Log.d("MovixAnime", url)

        return try {
            val response = app.get(url, headers = animeApiHeaders, timeout = 15).text
            Log.d("MovixAnime", "Response: $response")
            extractanimeplayers(response, type, episode)
        } catch (e: Exception) {
            Log.d("MovixAnime", e.message.toString())
            emptyList()
        }
    }

    private fun extractanimeplayers(
        response: String,
        type: String,
        episode: String?
    ): List<String> {
        val extracted = mutableListOf<String>()
        Log.d("MovixAnime", "Anime Ep: $episode")
        tryParseJson<List<MovixAnimeResponse>>(response)?.forEach { anime ->
            anime.seasons?.forEach { s ->
                s.episodes?.forEach { ep ->
                    val epindex = ep.index?.toString()
                    if (type == "movie" || epindex == episode) {
                        if (epindex != null) Log.d("MovixAnime", "Anime Match: $epindex")
                        ep.streaming_links?.forEach { sl ->
                            sl.players?.let(extracted::addAll)
                        }
                    }
                }
            }
        }
        return extracted.distinct().filter { it.isNotBlank() }
    }
}