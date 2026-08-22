package com.byayzen

import android.util.Log
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

object OnRegardeOu {
    data class VideoData(
        val title: String?  = null,
        val servers: List<Server>? = null
    )

    data class Server(
        val name: String?  = null,
        val url: String?   = null,
        val type: String?  = null
    )

    suspend fun invoke(
        url: String,
        mainurl: String,
        subtitlecallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("OnRegardeOu", "$url $mainurl")
        try {
            val response = app.get(url, timeout = 15)
            val doc      = response.text
            Log.d("OnRegardeOu", "${response.code} ${response.url}")

            val dataRegex = "const videoData=(.*?);".toRegex()
            val match     = dataRegex.find(doc)
            if (match == null) {
                return
            }
            val json = match.groupValues[1]
            Log.d("OnRegardeOu", json)

            val videoData = tryParseJson<VideoData>(json)
            if (videoData == null) {
                return
            }
            Log.d("OnRegardeOu", "${videoData.title} ${videoData.servers?.size ?: 0}")

            videoData?.servers?.forEach { server ->
                val serverUrl = server.url?.replace("\\/", "/") ?: run {
                    Log.e("OnRegardeOu", "${server.name}")
                    return@forEach
                }
                Log.d("OnRegardeOu", "${server.name} $serverUrl")
                loadExtractor(serverUrl, response.url, subtitlecallback, callback)
            }
        } catch (e: Exception) {
            Log.e("OnRegardeOu", "${e.message}", e)
        }
    }
}