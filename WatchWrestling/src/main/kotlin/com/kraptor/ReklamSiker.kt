//Bu araç @kraptor tarafından | Cs-Karma için yazılmıştır!

package com.kraptor

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class ReklamSiker : ExtractorApi() {
    override val name = "ReklamSiker"
    override val mainUrl = "https://punjabeducareapp.com"

    override val requiresReferer = true


    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfterLast("id=").substringBefore("&")
        val mirror = url.substringAfterLast("tuberep_")

        if (url.contains("tuberep_")){
            val link = "https://451nj1za7g9v2kexgxatdrh.linux-developers.top/vgroupWRSc/vsecureWRSc/?line=$id$mirror&waiting=C&background=undefined"

            val videoAl = app.get(link, referer = link).document

            val script = videoAl.selectFirst("script:containsData(source)")?.data().toString()

            val regex = Regex(pattern = "src: '([^']*)'", options = setOf(RegexOption.IGNORE_CASE))

            val video = regex.find(script)?.groupValues[1].toString()

            Log.d("kraptor_${this.name}","video = $video")

            callback.invoke(newExtractorLink(
                this.name,
                this.name,
                video,
                type = ExtractorLinkType.M3U8,
                {
                    this.referer = "https://451nj1za7g9v2kexgxatdrh.linux-developers.top/vgroupWRSc/vsecureWRSc/?line=251122UC1&waiting=C&background=undefined"
                }
            ))
        }

        Log.d("kraptor_${this.name}","farkli geldi")
        val videoAl = app.get(url, referer = referer).document
        val iframe = fixUrl(videoAl.selectFirst("iframe")?.attr("src").toString())
        val iframeAl = app.get(iframe, referer = url).document
        val iframeSon = fixUrl(iframeAl.selectFirst("iframe")?.attr("src").toString())
        if (iframeSon.contains("/null")) {
            return
        } else {
            loadExtractor(iframeSon, subtitleCallback, callback)
        }

        Log.d("kraptor_${this.name}","iframeSon = $iframeSon")



    }
}