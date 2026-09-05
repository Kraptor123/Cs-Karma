package com.byayzen

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder

open class NekostreamExtractor : ExtractorApi() {
    override val name = "Nekostream"
    override val mainUrl = "https://nekostream.my.id"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("Ayzen", "extractorUrl = $url")
        val responseText = app.get(url, referer = referer ?: "$mainUrl/").text

        val unescapedMatch =
            Regex("""unescape\("([^"]+)"\)""").find(responseText)?.groupValues?.get(1) ?: return
        val decodedScript = withContext(Dispatchers.IO) {
            URLDecoder.decode(unescapedMatch, "UTF-8")
        }
        val unpacked = getAndUnpack(decodedScript)

        val videoUrl =
            Regex("""file:\s*['"]([^'"]+)['"]""").find(unpacked)?.groupValues?.get(1) ?: return
        Log.d("Ayzen", "videoUrl = $videoUrl")

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = videoUrl,
                type = ExtractorLinkType.VIDEO,
                initializer = {
                    this.referer = "$mainUrl/"
                    this.quality = getQualityFromName(videoUrl)
                }
            )
        )
    }
}
