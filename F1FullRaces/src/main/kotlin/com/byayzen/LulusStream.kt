package com.byayzen

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.LuluStream
import com.lagradost.cloudstream3.utils.*

class LulusStream : ExtractorApi() {
    override val name = "LuluStream"
    override val mainUrl = "https://luluvid.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = referer).document

        val packed = doc.select("script")
            .map { it.data() }
            .firstOrNull {
                it.contains("eval(function(p,a,c,k,e,d)") && it.contains("m3u8")
            } ?: return

        val unpacked = JsUnpacker(packed).unpack() ?: return

        Log.d("LuluStream", unpacked)

        val m3u8 = Regex("""file:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""")
            .find(unpacked)?.groupValues?.get(1)
            ?: Regex("""(https?://[^\s"']+\.m3u8[^\s"']*)""")
                .find(unpacked)?.groupValues?.get(1)
            ?: return

        callback(
            newExtractorLink(name, name, m3u8) {
                this.referer = mainUrl
                this.quality = Qualities.P1080.value
                this.headers = mapOf("Origin" to mainUrl)
            }
        )
    }
}


open class Luluvdo : LuluStream() {
    override var name = "Filemoon"
    override var mainUrl = "https://luluvdo.com"
}

open class Luluvdoo : LuluStream() {
    override var name = "Filemoon"
    override var mainUrl = "https://luluvdoo.com"
}