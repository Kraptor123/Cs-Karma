package com.byayzen

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Hexload : ExtractorApi() {
    override val name            = "Hexload"
    override val mainUrl         = "https://hexload.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("Hexload", url)

        val id = url.substringAfterLast("/")
        Log.d("Hexload", id)

        val response = app.post(
            "$mainUrl/download",
            data = mapOf(
                "op"          to "download3",
                "id"          to id,
                "ajax"        to "1",
                "method_free" to "1",
                "dataType"    to "json"
            ),
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer"          to url,
                "Origin"           to mainUrl,
                "User-Agent"       to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
            )
        ).parsedSafe<Response>()

        val videoUrl = response?.result?.url

        if (videoUrl != null) {
            Log.d("Hexload", videoUrl)
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    videoUrl,
                    INFER_TYPE
                ) {
                    this.referer = url
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }

    data class Response(
        val result: Result? = null,
        val status: Int?    = null,
        val msg: String?    = null
    )

    data class Result(
        val url: String?       = null,
        val file_name: String? = null
    )
}