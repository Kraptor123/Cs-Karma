//Bu araç @ByAyzen tarafından | Cs-Karma için yazılmıştır.

package com.byayzen

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class YablomExtractor : ExtractorApi() {
    override val name = "ShareCloudy"
    override val mainUrl = "https://sharecloudy.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("Ayzen", "extractorUrl = $url")
        val response = app.get(url, referer = referer).text

        val videoUrl = Regex("""file:\s*"(.*?)"""").find(response)?.groupValues?.get(1)
        Log.d("Ayzen", "videoUrl = $videoUrl")

        videoUrl?.let {
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = it,
                    type = INFER_TYPE,
                    initializer = {
                        this.referer = url
                    }
                )
            )
        }
    }
}