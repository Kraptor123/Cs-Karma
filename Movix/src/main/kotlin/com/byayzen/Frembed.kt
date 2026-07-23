package com.byayzen

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.api.Log

object FrembedExtractor {
    suspend fun getLinks(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val isMovie = url.contains("/movie/")
            val id = url.substringAfterLast("/")

            val apiUrl = if (isMovie) {
                "https://frembed.click/api/films?id=$id&idType=tmdb"
            } else {
                val sa = url.substringAfter("sa=", "").substringBefore("&")
                val epi = url.substringAfter("epi=", "")
                "https://frembed.click/api/series?id=$id&sa=$sa&epi=$epi"
            }

            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:151.0) Gecko/20100101 Firefox/151.0",
                "Accept" to "*/*",
                "Referer" to url.replace("/embed/", "/"),
                "X-Requested-With" to "XMLHttpRequest"
            )

            val res = app.get(apiUrl, headers = headers)
            var response = res.text
            if (res.code == 301 || res.code == 302) {
                res.headers["location"]?.let { loc ->
                    response = app.get(loc, headers = headers).text
                }
            }

            val linkPattern = """"(link\d+(?:vostfr|vo)?)"\s*:\s*"([^"]+)"""".toRegex()
            val matches = linkPattern.findAll(response)

            matches.forEach { match ->
                val path = match.groupValues[2]
                if (path.isNotBlank() && path.startsWith("/api/stream")) {
                    val streamUrl = "https://frembed.click$path"
                    val streamHeaders = headers.toMutableMap()
                    streamHeaders["Accept"] =
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                    val redirectResponse =
                        app.get(streamUrl, headers = streamHeaders, allowRedirects = false)
                    val finalUrl = redirectResponse.headers["location"]

                    if (!finalUrl.isNullOrBlank()) {
                        Log.d("Frembed", "Final Extractor URL: $finalUrl")
                        loadExtractor(finalUrl, streamUrl, subtitleCallback, callback)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("Frembed", "Error: ${e.message}")
        }
    }
}
