//Bu araç @kraptor tarafından | Cs-Karma için yazılmıştır!

package com.kraptor

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class ArabveTurk: TurkveArabExtractor(){
    override val name = "ArabveTurk"
    override val mainUrl = "https://v.turkvearab.com"
}

open class iPlayerHls: TurkveArabExtractor(){
    override val name = "iPlayerHls"
    override val mainUrl = "https://iplayerhls.com"
}

open class TurkveArabExtractor : ExtractorApi() {
    override val name = "TurkveArab"
    override val mainUrl = "https://arabveturk.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("kraptor_${this.name}","url = $url")
        val text = app.get(url, referer = "https://qesen.net/").text

        val evalCikar = getAndUnpack(text)

//        Log.d("kraptor_${this.name}","eval = $evalCikar")

        val video = Regex(pattern = "file:\"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE)).find(evalCikar)?.groupValues[1] ?: ""

        if (!evalCikar.contains("var links=")){
            callback.invoke(
                newExtractorLink(
                    name = this.name,
                    source = this.name,
                    url = video,
                    type = ExtractorLinkType.M3U8, /* ExtractorLinkType.M3U8, ExtractorLinkType.VIDEO*/
                    initializer = {
                        this.referer = "${mainUrl}/"
                    }
                ))
        } else {
            val hlsler = Regex(pattern = "\"hls[0-9]+\":\"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE)).findAll(evalCikar)

            hlsler.toList().forEach { video ->
                val m3u8 = video.groupValues[1]
                Log.d("kraptor_${this.name}","m3u8 = $m3u8")
                callback.invoke(
                    newExtractorLink(
                        name = this.name,
                        source = this.name,
                        url = if (!m3u8.contains("http")) { "$mainUrl$m3u8"} else m3u8,
                        type = ExtractorLinkType.M3U8, /* ExtractorLinkType.M3U8, ExtractorLinkType.VIDEO*/
                        initializer = {
                            this.referer = "${mainUrl}/"
                        }
                    ))
            }

        }
    }
}