//Bu araç @kraptor tarafından | Cs-Karma için yazılmıştır!

package com.kraptor

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class HgLink : ExtractorApi() {
    override val name = "CavanHabg"
    override val mainUrl = "https://hglink.to"

    override val requiresReferer = true


    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val data = url.substringAfterLast("/")

        val sonUrl = "https://cavanhabg.com/e/$data"

        val document = app.get(sonUrl).document

        val response = document.select("script").find { it.data().contains("eval(function(p,a,c,k,e,d)") }?.data()?.let { getAndUnpack(it) } ?: ""

        val regex = Regex(pattern = "\"hls[0-9]+\":\"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE))

        val hlsBul = regex.findAll(response)

        hlsBul.forEach { link ->
           val hlsLink = link.groupValues[1]
            callback.invoke(newExtractorLink(
                this.name,
                this.name,
                hlsLink,
                type = ExtractorLinkType.M3U8,
                {
                    this.referer = sonUrl
                }
            ))
        }
    }
}