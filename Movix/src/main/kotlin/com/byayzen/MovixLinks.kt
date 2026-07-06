package com.byayzen

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

object MovixLinks {

    fun isvalidresponse(response: String): Boolean {
        val lower = response.lowercase()
        val keywords = listOf(
            "success",
            "player_links",
            "iframe_src",
            "series",
            "sources",
            "players",
            "links",
            "purstream_id",
            "frembed",
            "wiflix"
        )
        return keywords.any { lower.contains(it) }
    }

    suspend fun parsepurstream(response: String, client: OkHttpClient, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        tryParseJson<MovixPurstreamResponse>(response)?.sources?.forEach { source ->
            source.url?.let { link ->
                if (link.isNotBlank() && Videovarmiyokmu(link, client)) {
                    val sourcename = source.name ?: ""
                    val finalname = if (sourcename.isNotBlank()) "Purstream - $sourcename" else "Purstream"
                    val linktype = if (link.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    val qualityval = sourcename.filter { it.isDigit() }.toIntOrNull() ?: Qualities.Unknown.value
                    callback(
                        newExtractorLink(
                            "Purstream",
                            finalname,
                            link,
                            linktype
                        ) {
                            this.quality = qualityval
                        }
                    )
                }
            }
        }
    }

    suspend fun parsetmdb(
        response: String,
        mainUrl: String,
        subtitlecallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        tryParseJson<MovixTmdbResponse>(response)?.let { res ->
            val links = mutableListOf<String>()
            res.player_links?.forEach { it.decoded_url?.let(links::add) }
            res.current_episode?.player_links?.forEach { it.decoded_url?.let(links::add) }
            res.iframe_src?.let(links::add)
            res.current_episode?.iframe_src?.let(links::add)
            processlinks("MovixTmdb", links.distinct().filter { it.isNotBlank() }, mainUrl, subtitlecallback, callback)
        }
    }

    suspend fun parselinks(
        response: String,
        type: String,
        mainUrl: String,
        subtitlecallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val links = mutableListOf<String>()
        Log.d("movix_links", "Parsing started for type: $type")
        Regex("https?://[^\"]+").findAll(response).map { it.value }.let(links::addAll)
        val distinctLinks = links.distinct().filter { it.isNotBlank() }
        Log.d("movix_links", "Found ${distinctLinks.size} unique links for type: $type")
        processlinks("Movix", distinctLinks, mainUrl, subtitlecallback, callback)
    }


    suspend fun parseJ1F(
        response: String,
        mainUrl: String,
        subtitlecallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("Movix_J1F", "J1F başladı")
        val links = mutableListOf<String>()
        try {
            val json    = parseToJsonElement(response).jsonObject
            val players = json["players"]?.jsonObject ?: return
            val vf      = players["vf"]?.jsonArray
            val vostfr  = players["vostfr"]?.jsonArray
            listOfNotNull(vf, vostfr).flatten().forEach { element ->
                val encodedUrl = element.jsonObject["url"]?.toString()?.trim('"')
                if (!encodedUrl.isNullOrBlank()) {
                    val decodedUrl = base64Decode(encodedUrl)
                    if (decodedUrl.isNotBlank()) links.add(decodedUrl)
                }
            }
        } catch (e: Exception) {
            Log.d("Movix_J1F", "J1F hata verdi: ${e.message}")
        }
        Log.d("Movix_J1F", "J1F bitti. Bulunan linkler ${links.size}, bu kadar link bulundu.")
        processlinks("J1F", links.distinct(), mainUrl, subtitlecallback, callback)
    }

    suspend fun parsecpasmal(
        response: String,
        mainUrl: String,
        subtitlecallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val links = mutableListOf<String>()
        tryParseJson<CpasmalRes>(response.replace("\"players\":", "\"links\":"))
            ?.links?.values?.flatten()?.forEach { it.url?.let(links::add) }
        processlinks("Cpasmal", links.distinct().filter { it.isNotBlank() }, mainUrl, subtitlecallback, callback)
    }

    suspend fun parseimdb(
        response: String,
        episode: String?,
        mainUrl: String,
        subtitlecallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val links = mutableListOf<String>()
        tryParseJson<MovixImdbResponse>(response)?.series?.forEach { series ->
            series.seasons?.forEach { season ->
                season.episodes?.filter {
                    episode == null || it.number == episode || it.number?.toIntOrNull() == episode?.toIntOrNull()
                }?.forEach { ep ->
                    ep.versions?.values?.forEach { version ->
                        version.players?.forEach { it.link?.let(links::add) }
                    }
                }
            }
        }
        processlinks("IMDB", links.distinct().filter { it.isNotBlank() }, mainUrl, subtitlecallback, callback)
    }

    suspend fun parsefstream(
        response: String,
        type: String,
        episode: String?,
        mainUrl: String,
        subtitlecallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val links = mutableListOf<String>()
        val fixedresponse = response.replace("\"players\":", "\"links\":")
        tryParseJson<MovixFstreamResponse>(fixedresponse)?.let { res ->
            if (type == "movie") {
                res.links?.values?.flatten()?.forEach { it.url?.let(links::add) }
            } else {
                val epmap = res.episodes
                val targetEp = epmap?.entries?.find {
                    it.key == episode || it.key.toIntOrNull() == episode?.toIntOrNull()
                }?.value
                targetEp?.languages?.values?.flatten()?.forEach { it.url?.let(links::add) }
            }
        }
        processlinks("FStream", links.distinct().filter { it.isNotBlank() }, mainUrl, subtitlecallback, callback)
    }

    suspend fun parsefrembed(
        response: String,
        mainUrl: String,
        subtitlecallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val links = mutableListOf<String>()
        tryParseJson<FrembedResponse>(response)?.result?.items?.forEach {
            it.link?.let(links::add)
        }
        processlinks("Frembed", links.distinct().filter { it.isNotBlank() }, mainUrl, subtitlecallback, callback)
    }



    suspend fun parsewiflix(
        response: String,
        type: String,
        episode: String?,
        mainUrl: String,
        subtitlecallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val links = mutableListOf<String>()
        Log.d("movix", "Wiflix parsing started for type: $type")
        tryParseJson<MovixWiflixResponse>(response)?.let { res ->
            val allLinks = mutableListOf<MovixWiflixLink>()
            allLinks.addAll(res.players?.vf ?: emptyList())
            allLinks.addAll(res.players?.vostfr ?: emptyList())
            if (type == "movie") {
                allLinks.forEach { it.url?.let(links::add) }
            } else {
                allLinks.filter {
                    it.episode?.toString() == episode || it.episode == episode?.toIntOrNull()
                }.forEach { it.url?.let(links::add) }
            }
        }
        val distinctLinks = links.distinct().filter { it.isNotBlank() }
        Log.d("movix", "Wiflix parsing finished. Found ${distinctLinks.size} unique links.")
        processlinks("Wiflix", distinctLinks, mainUrl, subtitlecallback, callback)
    }

    suspend fun parsedrama(
        response: String,
        mainUrl: String,
        subtitlecallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val links = mutableListOf<String>()
        tryParseJson<MovixDramaResponse>(response)?.data?.forEach { item ->
            item.link?.let(links::add)
        }
        processlinks("MovixDrama", links.distinct().filter { it.isNotBlank() }, mainUrl, subtitlecallback, callback)
    }

    suspend fun videolinks(
        apibase: String,
        type: String,
        id: String,
        season: String?,
        episode: String?,
        query: String,
        apiheaders: Map<String, String>,
        mainUrl: String,
        tmdbbase: String,
        tmdbkey: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val ismovie = type == "movie"
            val cpasurl = if (ismovie) "$apibase/cpasmal/$type/$id" else "$apibase/cpasmal/$type/$id/$season/$episode"
            val infores = app.get(cpasurl, headers = apiheaders, timeout = 15).text
            val titlematch = Regex("\"(?:title|name)\"\\s*:\\s*\"([^\"]+)\"")
            var title = titlematch.find(infores)?.groupValues?.get(1)

            if (title == null) {
                val tmdbres = app.get("$apibase/tmdb/$type/$id$query", headers = apiheaders, timeout = 15).text
                title = titlematch.find(tmdbres)?.groupValues?.get(1)
            }

            if (title.isNullOrBlank()) return

            val encodedtitle = URLEncoder.encode(title, "UTF-8")
            val searchres = app.get("$apibase/search?title=$encodedtitle", headers = apiheaders, timeout = 15).text
            val downloadid = Regex("\"id\"\\s*:\\s*(\\d+)").find(searchres)?.groupValues?.get(1) ?: return
            val downloadurl = if (ismovie) "$apibase/films/download/$downloadid" else "$apibase/series/download/$downloadid/season/$season/episode/$episode"

            Log.d("movix", downloadurl)

            val dlres = app.get(downloadurl, headers = apiheaders, timeout = 15).text
            tryParseJson<MovixDownloadResponse>(dlres)?.sources?.forEach { source ->
                val link = source.m3u8 ?: source.src
                if (!link.isNullOrBlank()) {
                    val langname = source.language ?: ""
                    val finalname = if (langname.isNotBlank()) "Movix - $langname" else "Movix"
                    val linktype = if (link.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    val qualityval = source.quality?.filter { it.isDigit() }?.toIntOrNull() ?: Qualities.Unknown.value
                    callback(newExtractorLink("Movix", finalname, link, linktype) {
                        this.quality = qualityval
                        this.headers = mapOf()
                        this.referer = ""
                    })
                }
            }
        } catch (e: Exception) {
            Log.d("movix", e.message.toString())
        }
    }

    suspend fun processlinks(
        brand: String,
        links: List<String>,
        mainUrl: String,
        subtitlecallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (links.isEmpty()) return
        Log.d("movix", "$brand ${links.size}")
        val cleanbrand = if (brand.contains("Movix")) "Movix" else brand
        links.forEach { link ->
            Log.d("movix", link)
            if (link.contains("kokoflix.lol") || link.contains("kakaflix.lol")) {
                Kokoflix.invoke(link, mainUrl, subtitlecallback, callback)
            } else if (link.contains("onregardeou.site")) {
                OnRegardeOu.invoke(link, mainUrl, subtitlecallback, callback)
            } else {
                loadcustomextractor(cleanbrand, link, mainUrl, subtitlecallback, callback)
            }
        }
    }
}