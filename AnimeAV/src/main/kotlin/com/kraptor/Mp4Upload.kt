package com.kraptor

import android.util.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Mp4Upload : ExtractorApi() {
    override var name            = "Mp4Upload"
    override var mainUrl         = "https://www.mp4upload.com"
    override val requiresReferer = true

    private val srcRegex         = Regex("""player\.src\("(.*?)"""")
    private val srcRegex2        = Regex("""player\.src\([\w\W]*src: "(.*?)"""")
    private val idMatch          = Regex("""mp4upload\.com/(embed-|)([A-Za-z0-9]*)""")

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val id      = idMatch.find(url)?.groupValues?.get(2)
        val realUrl = if (id != null) "$mainUrl/embed-$id.html" else url

        Log.d("Mp4Upload", "URL: $realUrl")

        val response     = app.get(realUrl)
        val unpackedText = getAndUnpack(response.text)
        val quality      = unpackedText.lowercase().substringAfter(" height=").substringBefore(" ").toIntOrNull() ?: Qualities.Unknown.value

        val videoUrl     = srcRegex.find(unpackedText)?.groupValues?.get(1)
            ?: srcRegex2.find(unpackedText)?.groupValues?.get(1)

        if (videoUrl.isNullOrBlank()) {
            Log.d("Mp4Upload", "Video bulunamadı")
            return null
        }

        Log.d("Mp4Upload", "Video bağlantısı: $videoUrl | Kalite: $quality")

        return listOf(
            newExtractorLink(
                source = name,
                name   = this.name,
                url    = videoUrl,
            ) {
                this.referer = url
                this.quality = quality
            }
        )
    }
}