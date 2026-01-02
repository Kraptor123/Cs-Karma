//Bu araç @kraptor tarafından | Cs-Karma için yazılmıştır!

package com.kraptor

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*


class Cavanhabg: StreamHgExtractor(){
    override val name = "Cavanhabg"
    override val mainUrl = "https://cavanhabg.com"
}

class Dumbalag: StreamHgExtractor(){
    override val name = "Dumbalag"
    override val mainUrl = "https://dumbalag.com"
}

class Uasopt: StreamHgExtractor(){
    override val name = "Uasopt"
    override val mainUrl = "https://uasopt.com"
}

open class StreamHgExtractor : ExtractorApi() {
    override val name = "StreamHgExtractor"
    override val mainUrl = "https://haxloppd.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("kraptor_StreamHgx", "url = $url")

        val extRef = referer ?: ""

        Log.d("kraptor_StreamHgx", "extRef = $extRef")

        val document = app.get(
            url,
            headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0")
        ).document

        val response = document.select("script").find { it.data().contains("eval(function(p,a,c,k,e,d)") }?.data()
            ?.let { getAndUnpack(it) } ?: ""

        val regex = Regex(pattern = "\"hls[0-9]+\":\"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE))

        val hlsBul = regex.findAll(response)

        hlsBul.forEach { link ->
            val hlsLink = link.groupValues[1]
            callback.invoke(
                newExtractorLink(
                this.name,
                this.name,
                hlsLink,
                type = ExtractorLinkType.M3U8,
                {
                    this.referer = url
                }
            ))
        }
    }
}