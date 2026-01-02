//Bu araç @kraptor tarafından | Cs-Karma için yazılmıştır!

package com.byayzen

import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.StringUtils.decodeUri

open class WcoStreamExtractor : ExtractorApi() {
    override val name = "WcoStream"
    override val mainUrl = "https://embed.wcostream.com"

    override val requiresReferer = true


    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
//        Log.d("kraptor_Wco","url = $url")

        val request = app.get(url, referer = referer)

        val digerIstek = fixUrl(request.document.selectFirst("div#d-player a")?.attr("href") ?: "")

        val ikinciReq = app.get(digerIstek, referer = request.url).document

        val script = ikinciReq.selectFirst("script:containsData(jQuery.post)")?.data()?.substringAfter("{ ")?.substringBefore("})") ?: ""

//        Log.d("kraptor_Wco","script = $script")

        val vRegex = Regex(pattern = "v: \"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE))
        val embedRegex = Regex(pattern = "embed: \"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE))
        val hdRegex = Regex(pattern = "hd: \"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE))
        val fullhdRegex = Regex(pattern = "fullhd: \"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE))
        val tokenRegex = Regex(pattern = "token: \"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE))


        val vWco = vRegex.find(script)?.groupValues[1]?.decodeUri()?.trim() ?: ""
        val embedWco = embedRegex.find(script)?.groupValues[1]?.trim() ?: ""
        val hdWco = hdRegex.find(script)?.groupValues[1]?.trim() ?: ""
        val fullhdWco = fullhdRegex.find(script)?.groupValues[1]?.trim() ?: ""
        val tokenWco = tokenRegex.find(script)?.groupValues[1]?.trim() ?: ""

//        Log.d("kraptor_Wco","vWco = $vWco")
//        Log.d("kraptor_Wco","embedWco = $embedWco")
//        Log.d("kraptor_Wco","hdWco = $hdWco")
//        Log.d("kraptor_Wco","fullhdWco= $fullhdWco")
//        Log.d("kraptor_Wco","tokenWco = $tokenWco")

        val postIstegi = app.post("${mainUrl}/inc/embed/getvidlink-nginx.php", referer = "$referer", data = mapOf(
            "v" to "$vWco",
            "embed" to "$embedWco",
            "hd" to "$hdWco",
            "fullhd " to "$fullhdWco",
            "tokenWco " to "$tokenWco",
        )).text

//        Log.d("kraptor_Wco","postIstegi = $postIstegi")

        val mapper = mapper.readValue<WcoCevap>(postIstegi)

        val videoIstek = listOf("${mapper.server}${mapper.enc}&json", "${mapper.server}${mapper.fullhd}&json", "${mapper.server}${mapper.hd}&json")

//        Log.d("kraptor_Wco","videoIstek = $videoIstek")

       videoIstek.forEach { video ->

           if (video.contains("null&json")) {
               return
           } else {

//               Log.d("kraptor_Wco", "video = $video")

               val videoAl = app.get(video, referer = "${mainUrl}/").text

//               Log.d("kraptor_Wco", "videoAl = $videoAl")

               val videomuz = videoAl.replace("\"", "").replace("\\", "")

//               Log.d("kraptor_Wco", "videomuz = $videomuz")


               callback.invoke(
                   newExtractorLink(
                       this.name,
                       this.name,
                       videomuz,
                       INFER_TYPE
                   ) {
                       this.referer = "$referer"
                   }
               )
           }
       }
    }
}

data class WcoCevap(
    val enc: String?,
    val server: String?,
    val cdn: String?,
    val hd: String?,
    val fullhd: String?,
)
