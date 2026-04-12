package com.byayzen

import android.util.Log
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SubtitleFile

object Kokoflix {
    suspend fun invoke(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val response = app.get(url, referer = "https://movix.rodeo", timeout = 10)
            val realEmbedUrl = response.url
            Log.d("MOVIX", "Kokoflix bulunan url: $realEmbedUrl")
            loadExtractor(realEmbedUrl, url, subtitleCallback, callback)
        } catch (e: Exception) {
            Log.d("MOVIX", "Kokoflix redirect hata verdi: ${e.message}")
        }
    }
}