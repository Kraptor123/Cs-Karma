package com.kraptor

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject


class Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
    override val requiresReferer = true
}

open class Hownetwork : ExtractorApi() {
    override val name = "Hownetwork"
    override val mainUrl = "https://stream.hownetwork.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfter("id=")
        val response = app.post(
            "$mainUrl/api2.php?id=$id",
            data = mapOf(
                "r" to "",
                "d" to mainUrl,
            ),
            referer = url,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest"
            )
        ).text
        val json = JSONObject(response)
        val file = json.optString("file")
        callback.invoke(newExtractorLink(
            this.name,
            this.name,
            file,
            type = INFER_TYPE,
            {
                this.referer = file
                this.headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
                    "Accept" to "*/*",
                    "Accept-Language" to "en-US,en;q=0.5",
                    "Sec-GPC" to "1",
                    "Connection" to "keep-alive",
                    "Sec-Fetch-Dest" to "empty",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "same-origin",
                    "Priority" to "u=0",
                    "Pragma" to "no-cache",
                    "Cache-Control" to "no-cache",
                    "TE" to "trailers"
                )
            }
        ))
    }
}

class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

class Cloudhownetwork : Hownetwork() {
    override var mainUrl = "https://cloud.hownetwork.xyz"
}

class Furher2 : Filesim() {
    override val name = "Furher 2"
    override var mainUrl = "https://723qrh1p.fun"
}

class Turbovidhls : Filesim() {
    override val name = "Turbovidhls"
    override var mainUrl = "https://turbovidhls.com"
}