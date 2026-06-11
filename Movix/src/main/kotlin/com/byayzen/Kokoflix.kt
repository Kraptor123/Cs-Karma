package com.byayzen

import android.util.Log
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SubtitleFile

object Kokoflix {
    suspend fun invoke(
        url: String,
        mainurl: String,
        subtitlecallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val response = app.get(url, referer = mainurl, timeout = 10)
            val realembedurl = response.url
            Log.d("Kokoflix", realembedurl)
            loadExtractor(realembedurl, url, subtitlecallback, callback)
        } catch (e: Exception) {
            Log.d("Kokoflix", e.toString())
        }
    }
}