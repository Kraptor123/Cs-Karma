//Bu araç @kraptor tarafından | Cs-Karma için yazılmıştır!

package com.kraptor

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.net.URI

open class SoPlayExtractor : ExtractorApi() {
    override val name = "CricHD"
    override val mainUrl = "https://crichd.one"
    override val requiresReferer = true

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0"

    override suspend fun getUrl(
        url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit
    ) {
        val embedurl = url.replace(mainUrl, "https://soplay.pro").replace("stream.php", "embed2.php")
        val sayfa = app.get(embedurl, headers = mapOf("User-Agent" to userAgent, "Referer" to "$mainUrl/")).text

        val vcon = Regex("v_con=[\"'](.*?)[\"']").find(sayfa)?.groupValues?.get(1) ?: ""
        val vdt = Regex("v_dt=[\"'](.*?)[\"']").find(sayfa)?.groupValues?.get(1) ?: ""
        val fid = Regex("fid=\"([^\"]*)\"").find(sayfa)?.groupValues?.get(1) ?: ""

        var sonucurl: String? = null
        var ispro = false

        if (fid.isNotEmpty()) {
            val ilkurl = "https://bhalocast.com/pzo.php?v=$fid&secure=$vcon&expires=$vdt"
            val ilkcevap = app.get(ilkurl, headers = mapOf("Referer" to embedurl)).text

            Regex("[\"'](https?://kick\\.bhalocast\\.com[^\"']*?m3u8[^\"']*?)[\"']")
                .findAll(ilkcevap)
                .lastOrNull()
                ?.groupValues?.get(1)
                ?.replace("\\/", "/")
                ?.let {
                    sonucurl = it
                }
        }

        if (sonucurl.isNullOrEmpty() && fid.isNotEmpty()) {
            val ikinciurl = "https://bhalocast.pro/playergo1.php?player=desktop&v=$fid"
            val ikincicevap = app.get(ikinciurl, headers = mapOf("Referer" to embedurl)).text

            val eslesme = Regex("source:\\s*[\"']([^\"']+\\.m3u8[^\"']*)[\"']").find(ikincicevap)
                ?: Regex("[\"'](https?://[^\"']+?\\.m3u8[^\"']*?)[\"']").find(ikincicevap)

            eslesme?.groupValues?.get(1)?.replace("\\/", "/")?.let {
                sonucurl = it
                ispro = true
            }
        }

        if (!sonucurl.isNullOrEmpty()) {
            val videouri = URI.create(sonucurl!!)
            val hostadresi = "${videouri.host}${if (videouri.port > 0) ":${videouri.port}" else ""}"
            val kaynakadresi = if (ispro) "https://bhalocast.pro" else "https://bhalocast.com"

            callback.invoke(
                newExtractorLink(
                    name = this.name,
                    source = this.name,
                    url = sonucurl!!,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "$kaynakadresi/"
                    this.headers = mapOf(
                        "Host" to hostadresi,
                        "User-Agent" to userAgent,
                        "Accept" to "*/*",
                        "Origin" to kaynakadresi,
                        "Referer" to "$kaynakadresi/",
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Site" to "same-site"
                    )
                }
            )
        }
    }
}