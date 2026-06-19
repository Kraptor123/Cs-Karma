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
        episode: String?,
        season: String? = null
    ): List<String> {
        val animeApiHeaders = mapOf("Origin" to mainUrl)
        val encoded = Uri.encode(title)
        val url = apibase.substringBeforeLast("/") + "/anime/search/$encoded?includeSeasons=true&includeEpisodes=true"
        Log.d("MovixAnime", url)

        return try {
            val response = app.get(url, headers = animeApiHeaders, timeout = 15).text
            extractAnimePlayers(response, type, episode, season)
        } catch (e: Exception) {
            Log.d("MovixAnime", e.message.toString())
            emptyList()
        }
    }

    private fun extractAnimePlayers(
        response: String,
        type: String,
        episode: String?,
        season: String?
    ): List<String> {
        val extracted = mutableListOf<String>()
        Log.d("MovixAnime", "Ep: $episode, Season: $season")

        tryParseJson<List<MovixAnimeResponse>>(response)?.forEach { anime ->
            anime.seasons?.forEach { s ->
                val seasonName = s.name.orEmpty()
                val isSeasonMatch = season == null || seasonName == season || seasonName.filter { it.isDigit() } == season
                if (!isSeasonMatch) return@forEach

                s.episodes?.forEach { ep ->
                    val epIndex = ep.index?.toString()
                    val isEpisodeMatch = episode == null || epIndex == episode || episode.toIntOrNull() == epIndex?.toIntOrNull()
                    if (isEpisodeMatch) {
                        Log.d("MovixAnime", "Alınan bölüm?: $epIndex")
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